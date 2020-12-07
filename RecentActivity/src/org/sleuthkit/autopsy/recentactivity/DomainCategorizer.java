/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.url.analytics.DomainCategoryProvider;
import org.sleuthkit.autopsy.url.analytics.DomainCategoryResult;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Analyzes a URL to determine if the url host is one of a certain kind of
 * category (i.e. webmail, disposable mail). If found, a web category artifact
 * is created.
 *
 * CSV entries describing these domain types are compiled from sources. webmail:
 * https://github.com/mailcheck/mailcheck/wiki/List-of-Popular-Domains
 * disposable mail: https://www.npmjs.com/package/disposable-email-domains
 */
@Messages({
    "DomainCategorizer_moduleName_text=DomainCategorizer",
    "DomainCategorizer_Progress_Message_Domain_Types=Finding Domain Types",
    "DomainCategorizer_parentModuleName=Recent Activity"
})
class DomainCategorizer extends Extract {

    // The url regex is based on the regex provided in https://tools.ietf.org/html/rfc3986#appendix-B
    // but expanded to be a little more flexible, and also properly parses user info and port in a url
    // this item has optional colon since some urls were coming through without the colon
    private static final String URL_REGEX_SCHEME = "(((?<scheme>[^:\\/?#]+):?)?\\/\\/)";

    private static final String URL_REGEX_USERINFO = "((?<userinfo>[^\\/?#@]*)@)";
    private static final String URL_REGEX_HOST = "(?<host>[^\\/\\.?#:]*\\.[^\\/?#:]*)";
    private static final String URL_REGEX_PORT = "(:(?<port>[0-9]{1,5}))";
    private static final String URL_REGEX_AUTHORITY = String.format("(%s?%s?%s?\\/?)", URL_REGEX_USERINFO, URL_REGEX_HOST, URL_REGEX_PORT);

    private static final String URL_REGEX_PATH = "(?<path>([^?#]*)(\\?([^#]*))?(#(.*))?)";

    private static final String URL_REGEX_STR = String.format("^\\s*%s?%s?%s?", URL_REGEX_SCHEME, URL_REGEX_AUTHORITY, URL_REGEX_PATH);
    private static final Pattern URL_REGEX = Pattern.compile(URL_REGEX_STR);

    private static final Logger logger = Logger.getLogger(DomainCategorizer.class.getName());

    private Content dataSource;
    private IngestJobContext context;
    private List<DomainCategoryProvider> domainProviders = Collections.emptyList();

    /**
     * Main constructor.
     */
    DomainCategorizer() {
        moduleName = null;
    }

    /**
     * Attempts to determine the host from the url string. If none can be
     * determined, returns null.
     *
     * @param urlString The url string.
     * @return The host or null if cannot be determined.
     */
    private String getHost(String urlString) {
        String host = null;
        try {
            // try first using the built-in url class to determine the host.
            URL url = new URL(urlString);
            if (url != null) {
                host = url.getHost();
            }
        } catch (MalformedURLException ignore) {
            // ignore this and go to fallback regex
        }

        // if the built-in url parsing doesn't work, then use more flexible regex.
        if (StringUtils.isBlank(host)) {
            Matcher m = URL_REGEX.matcher(urlString);
            if (m.find()) {
                host = m.group("host");
            }
        }

        return host;
    }
    
    
    private DomainCategoryResult findCategory(String domain, String host) {
        List<DomainCategoryProvider> safeProviders = domainProviders == null ? Collections.emptyList() : domainProviders;
        for (DomainCategoryProvider provider : safeProviders) {
            DomainCategoryResult result = provider.getCategory(domain, host);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }

    /**
     * Goes through web history artifacts and attempts to determine any hosts of
     * a domain type. If any are found, a TSK_WEB_CATEGORIZATION artifact is
     * created (at most one per host suffix).
     */
    private void findDomainTypes() {
        int artifactsAnalyzed = 0;
        int domainTypeInstancesFound = 0;

        // only one suffix per ingest is captured so this tracks the suffixes seen.
        Set<String> domainSuffixesSeen = new HashSet<>();

        try {
            Collection<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getBlackboard().getArtifacts(
                    Arrays.asList(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_HISTORY)),
                    Arrays.asList(dataSource.getId()));

            logger.log(Level.INFO, "Processing {0} blackboard artifacts.", listArtifacts.size()); //NON-NLS

            for (BlackboardArtifact artifact : listArtifacts) {
                // make sure we haven't cancelled
                if (context.dataSourceIngestIsCancelled()) {
                    break;       //User cancelled the process.
                }

                // make sure there is attached file
                AbstractFile file = tskCase.getAbstractFileById(artifact.getObjectID());
                if (file == null) {
                    continue;
                }

                // get the url string from the artifact
                BlackboardAttribute urlAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
                if (urlAttr == null) {
                    continue;
                }

                String urlString = urlAttr.getValueString();

                // atempt to get the host from the url provided.
                String host = getHost(urlString);
                
                // get the url string from the artifact
                BlackboardAttribute domainAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
                String domainString = domainAttr.getValueString();
                
                // make sure we have at least one of host or domain
                if (StringUtils.isBlank(host) && StringUtils.isBlank(domainString)) {
                    continue;
                }
                
                // if we reached this point, we are at least analyzing this item
                artifactsAnalyzed++;

                // attempt to get the domain type for the host using the suffix trie
                DomainCategoryResult domainEntryFound = findCategory(host, domainString);
                if (domainEntryFound == null) {
                    continue;
                }

                // if we got this far, we found a domain type, but it may not be unique
                domainTypeInstancesFound++;

                String hostSuffix = domainEntryFound.getHostSuffix();
                String domainCategory = domainEntryFound.getCategory();
                if (StringUtils.isBlank(hostSuffix) || domainCategory == null || domainSuffixesSeen.contains(hostSuffix)) {
                    continue;
                }

                // if we got this far, this is a unique suffix.  Add to the set, so we don't create
                // multiple of same suffix and add an artifact.
                domainSuffixesSeen.add(hostSuffix);

                String moduleName = Bundle.DomainCategorizer_parentModuleName();

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(host)),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HOST, moduleName, host),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, domainCategory)
                );
                postArtifact(createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_CATEGORIZATION, file, bbattributes));
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for messaging domains", e); //NON-NLS
        } finally {
            if (context.dataSourceIngestIsCancelled()) {
                logger.info("Operation terminated by user."); //NON-NLS
            }
            logger.log(Level.INFO, String.format("Extracted %s distinct messaging domain(s) from the blackboard.  "
                    + "Of the %s artifact(s) with valid hosts, %s url(s) contained messaging domain suffix.",
                    domainSuffixesSeen.size(), artifactsAnalyzed, domainTypeInstancesFound));
        }
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;

        progressBar.progress(Bundle.DomainCategorizer_Progress_Message_Domain_Types());
        this.findDomainTypes();
    }

    private static final Comparator<DomainCategoryProvider> PROVIDER_COMPARATOR
            = (a, b) -> {
                // if one item is the DefaultDomainCategoryProvider, and one is it, compare based on that.
                int isDefaultCompare = Integer.compare(
                        a instanceof DefaultDomainCategoryProvider ? 0 : 1,
                        b instanceof DefaultDomainCategoryProvider ? 0 : 1);

                if (isDefaultCompare != 0) {
                    return isDefaultCompare;
                }

                // otherwise, sort by the name of the fully qualified class for deterministic results.
                return a.getClass().getName().compareToIgnoreCase(b.getClass().getName());
            };

    @Override
    void configExtractor() throws IngestModule.IngestModuleException {
        List<DomainCategoryProvider> foundProviders
                = Lookup.getDefault().lookupAll(DomainCategoryProvider.class).stream()
                        .filter(provider -> provider != null)
                        .sorted(PROVIDER_COMPARATOR)
                        .collect(Collectors.toList());

        for (DomainCategoryProvider provider : foundProviders) {
            provider.initialize();
        }

        this.domainProviders = foundProviders == null ? 
                Collections.emptyList() : 
                foundProviders;
    }

    @Override
    public void complete() {
        logger.info("Search Engine URL Query Analyzer has completed."); //NON-NLS
    }
}
