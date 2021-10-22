/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizer;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizerException;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * Analyzes a URL to determine if the url host is one of a certain kind of
 * category (i.e. webmail, disposable mail). If found, a web category artifact
 * is created.
 */
@Messages({
    "DomainCategoryRunner_moduleName_text=Domain Category Analyzer",
    "DomainCategoryRunner_Progress_Message_Domain_Types=Finding Domain Types",
    "DomainCategoryRunner_parentModuleName=Recent Activity"
})
class DomainCategoryRunner extends Extract {

    // The url regex is based on the regex provided in https://tools.ietf.org/html/rfc3986#appendix-B
    // but expanded to be a little more flexible.  This regex also properly parses user info and port in a url.
    // this regex has optional colon in front of the scheme (i.e. http// instead of http://) since some urls were coming through without the colon.
    private static final String URL_REGEX_SCHEME = "(((?<scheme>[^:\\/?#]+):?)?\\/\\/)";

    private static final String URL_REGEX_USERINFO = "((?<userinfo>[^\\/?#@]*)@)";
    private static final String URL_REGEX_HOST = "(?<host>[^\\/\\.?#:]*\\.[^\\/?#:]*)";
    private static final String URL_REGEX_PORT = "(:(?<port>[0-9]{1,5}))";
    private static final String URL_REGEX_AUTHORITY = String.format("(%s?%s?%s?\\/?)", URL_REGEX_USERINFO, URL_REGEX_HOST, URL_REGEX_PORT);

    private static final String URL_REGEX_PATH = "(?<path>([^?#]*)(\\?([^#]*))?(#(.*))?)";

    private static final String URL_REGEX_STR = String.format("^\\s*%s?%s?%s?", URL_REGEX_SCHEME, URL_REGEX_AUTHORITY, URL_REGEX_PATH);
    private static final Pattern URL_REGEX = Pattern.compile(URL_REGEX_STR);

    private static int DATETIME_ACCESSED_TYPEID = ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID();
    private static int URL_TYPEID = ATTRIBUTE_TYPE.TSK_URL.getTypeID();

    private static final Logger logger = Logger.getLogger(DomainCategoryRunner.class.getName());

    // NOTE: if CustomWebCategorizer ever changes name, this will need to be changed as well.
    private static final String CUSTOM_CATEGORIZER_PATH = "org.sleuthkit.autopsy.url.analytics.domaincategorization.CustomWebCategorizer";

    // the artifact types to be searched for domain categories
    private static final List<BlackboardArtifact.Type> DOMAIN_CATEGORIZATION_TYPES = Stream.of(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY)
            .map(BlackboardArtifact.Type::new)
            .collect(Collectors.toList());
    private final IngestJobContext context;

    /**
     * Get seconds from epoch from the mapping for the attribute type id.
     *
     * @param attrMap    A mapping of attribute type id to BlackboardAttribute
     *                   for an artifact.
     * @param attrTypeId The attribute type id to fetch.
     *
     * @return The time in seconds from epoch or 0 if cannot be found.
     */
    private static long getTimeOrZero(Map<Integer, BlackboardAttribute> attrMap, int attrTypeId) {
        if (attrMap == null) {
            return 0;
        }

        BlackboardAttribute attr = attrMap.get(attrTypeId);
        return attr == null ? 0 : attr.getValueLong();
    }

    /**
     * Get string for attribute type id or "" if cannot be determined.
     *
     * @param attrMap    A mapping of attribute type id to BlackboardAttribute
     *                   for an artifact.
     * @param attrTypeId The attribute type id to fetch.
     *
     * @return The string value or "" if cannot be determined or null.
     */
    private static String getStringOrEmpty(Map<Integer, BlackboardAttribute> attrMap, int attrTypeId) {
        if (attrMap == null) {
            return "";
        }

        BlackboardAttribute attr = attrMap.get(attrTypeId);
        String attrStr = attr == null ? "" : attr.getValueString();
        return attrStr == null ? "" : attrStr;
    }

    /**
     * Comparator for ensuring deterministic order of processing artifacts.
     */
    private static final Comparator<BlackboardArtifact> ARTIFACT_COMPARATOR = (a, b) -> {
        // get attributes in map by type id
        Map<Integer, BlackboardAttribute> attrMapA = null;
        Map<Integer, BlackboardAttribute> attrMapB = null;

        try {
            attrMapA = a.getAttributes()
                    .stream()
                    .collect(Collectors.toMap(attr -> attr.getAttributeType().getTypeID(), attr -> attr, (attr1, attr2) -> attr1));

            attrMapB = b.getAttributes()
                    .stream()
                    .collect(Collectors.toMap(attr -> attr.getAttributeType().getTypeID(), attr -> attr, (attr1, attr2) -> attr1));

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "There was an error fetching attributes for artifacts", ex);
            return 0;
        }

        // sort first on time
        int timeCompare = Long.compare(getTimeOrZero(attrMapA, DATETIME_ACCESSED_TYPEID), getTimeOrZero(attrMapB, DATETIME_ACCESSED_TYPEID));
        if (timeCompare != 0) {
            // negate to push latest times to the front
            return -timeCompare;
        }

        // sort next on url
        int urlCompare = getStringOrEmpty(attrMapA, URL_TYPEID).compareToIgnoreCase(getStringOrEmpty(attrMapB, URL_TYPEID));
        if (urlCompare != 0) {
            return urlCompare;
        }

        // use id as last resort
        return Long.compare(a.getId(), b.getId());
    };

    private Content dataSource;
    private List<DomainCategorizer> domainProviders = Collections.emptyList();

    /**
     * Main constructor.
     */
    DomainCategoryRunner(IngestJobContext context) {
        super(Bundle.DomainCategoryRunner_moduleName_text(), context);
        this.context = context;
    }

    /**
     * Attempts to determine the host from the url string. If none can be
     * determined, returns null.
     *
     * @param urlString The url string.
     *
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

    /**
     * Attempts to find the category for the given host/domain.
     *
     * @param domain The domain for the item.
     * @param host   The host for the item.
     *
     * @return The domain category result or null if none can be determined.
     */
    private DomainCategory findCategory(String domain, String host) {
        List<DomainCategorizer> safeProviders = domainProviders == null ? Collections.emptyList() : domainProviders;
        for (DomainCategorizer provider : safeProviders) {
            DomainCategory result;
            try {
                result = provider.getCategory(domain, host);
                if (result != null) {
                    return result;
                }
            } catch (DomainCategorizerException ex) {
                logger.log(Level.WARNING, "There was an error processing results with " + provider.getClass().getCanonicalName(), ex);
            }

        }

        return null;
    }

    /**
     * Information concerning an artifact's host, domain, and parent file.
     */
    private static class ArtifactHost {

        private final AbstractFile abstractFile;
        private final String host;
        private final String domain;

        /**
         * Main constructor.
         *
         * @param abstractFile The parent file of the artifact.
         * @param host         The host of the artifact found in the url
         *                     attribute.
         * @param domain       The domain of the artifact in the TSK_DOMAIN
         *                     attribute.
         */
        ArtifactHost(AbstractFile abstractFile, String host, String domain) {
            this.abstractFile = abstractFile;
            this.host = host;
            this.domain = domain;
        }

        /**
         * @return The parent file of this artifact.
         */
        AbstractFile getAbstractFile() {
            return abstractFile;
        }

        /**
         * @return The host of this artifact if one can be determined.
         */
        String getHost() {
            return host;
        }

        /**
         * @return The domain of the artifact.
         */
        String getDomain() {
            return domain;
        }
    }

    /**
     * Determines pertinent information in the artifact like host, domain, and
     * parent file.
     *
     * @param artifact The web artifact to parse.
     *
     * @return The pertinent information or null if important information cannot
     *         be determined.
     *
     * @throws TskCoreException
     */
    private ArtifactHost getDomainAndHost(BlackboardArtifact artifact) throws TskCoreException {
        // make sure there is attached file
        AbstractFile file = tskCase.getAbstractFileById(artifact.getObjectID());
        if (file == null) {
            return null;
        }

        // get the host from the url attribute and the domain from the attribute
        BlackboardAttribute urlAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        String urlString = null;
        String host = null;
        if (urlAttr != null) {
            urlString = urlAttr.getValueString();
            if (StringUtils.isNotBlank(urlString)) {
                host = getHost(urlString);
            }
        }

        // get the domain from the attribute
        BlackboardAttribute domainAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
        String domainString = null;
        if (domainAttr != null) {
            domainString = domainAttr.getValueString();
        }

        boolean hasDomain = StringUtils.isNotBlank(domainString);
        boolean hasHost = StringUtils.isNotBlank(host);

        // we need at least a host or a domain, if one is missing, compensate with the other.
        if (!hasDomain && !hasHost) {
            return null;
        } else if (!hasDomain) {
            domainString = NetworkUtils.extractDomain(host);
        } else if (!hasHost) {
            host = domainString;
        }

        return new ArtifactHost(file, host.toLowerCase(), domainString.toLowerCase());
    }

    /**
     * Determines if the given item is already found in the set. If not, the
     * item is added to the set.
     *
     * @param items The set of items.
     * @param item  The item whose existence will be checked in the set.
     *
     * @return True if item is already contained in 'items'. False if the is
     *         null or if not contained in 'items'.
     */
    private static boolean isDuplicateOrAdd(Set<String> items, String item) {
        if (StringUtils.isBlank(item)) {
            return false;
        } else if (items.contains(item)) {
            return true;
        } else {
            items.add(item);
            return false;
        }
    }

    /**
     * Goes through web history artifacts and attempts to determine any hosts of
     * a domain type. If any are found, a TSK_WEB_CATEGORIZATION artifact is
     * created (at most one per host suffix).
     */
    private void findDomainTypes() {
        int artifactsAnalyzed = 0;
        int domainTypeInstancesFound = 0;

        // this will track the different hosts seen to avoid a search for the same host more than once
        Set<String> hostsSeen = new HashSet<>();

        // only one suffix per ingest is captured so this tracks the suffixes seen.
        Set<String> hostSuffixesSeen = new HashSet<>();
        try {
            List<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getBlackboard().getArtifacts(
                    DOMAIN_CATEGORIZATION_TYPES,
                    Arrays.asList(dataSource.getId()));

            logger.log(Level.INFO, "Processing {0} blackboard artifacts.", listArtifacts.size()); //NON-NLS
            Collections.sort(listArtifacts, ARTIFACT_COMPARATOR);

            for (BlackboardArtifact artifact : listArtifacts) {
                // make sure we haven't cancelled
                if (context.dataSourceIngestIsCancelled()) {
                    //User cancelled the process.
                    break;
                }

                // get the pertinent details for this artifact.
                ArtifactHost curArtHost = getDomainAndHost(artifact);
                if (curArtHost == null || isDuplicateOrAdd(hostsSeen, curArtHost.getHost())) {
                    continue;
                }

                // if we reached this point, we are at least analyzing this item
                artifactsAnalyzed++;

                // attempt to get the domain type for the host using the domain categorizers found
                DomainCategory domainEntryFound = findCategory(curArtHost.getDomain(), curArtHost.getHost());
                if (domainEntryFound == null) {
                    continue;
                }

                // make sure both the host suffix and the category are present.
                String hostSuffix = domainEntryFound.getHostSuffix();
                String domainCategory = domainEntryFound.getCategory();
                if (StringUtils.isBlank(hostSuffix) || StringUtils.isBlank(domainCategory)) {
                    continue;
                }

                // if we got this far, we found a domain type, but it may not be unique
                domainTypeInstancesFound++;

                if (isDuplicateOrAdd(hostSuffixesSeen, hostSuffix)) {
                    continue;
                }

                // if we got this far, we have a unique domain category to post.
                addCategoryArtifact(curArtHost, domainCategory);
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for messaging domains", e); //NON-NLS
        } finally {
            if (context.dataSourceIngestIsCancelled()) {
                logger.info("Operation terminated by user."); //NON-NLS
            }
            logger.log(Level.INFO, String.format("Extracted %s distinct messaging domain(s) from the blackboard.  "
                    + "Of the %s artifact(s) with valid hosts, %s url(s) contained messaging domain suffix.",
                    hostSuffixesSeen.size(), artifactsAnalyzed, domainTypeInstancesFound));
        }
    }

    /**
     * Adds a TSK_WEB_CATEGORIZATION artifact for the given information.
     *
     * @param artHost        Pertinent details for the artifact (i.e. host,
     *                       domain, parent file).
     * @param domainCategory The category for this host/domain.
     */
    private void addCategoryArtifact(ArtifactHost artHost, String domainCategory) throws TskCoreException {
        String moduleName = Bundle.DomainCategoryRunner_parentModuleName();
        Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, artHost.getDomain()),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HOST, moduleName, artHost.getHost()),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, domainCategory)
        );
        postArtifact(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_CATEGORIZATION, artHost.getAbstractFile(), bbattributes));
    }

    @Override
    public void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        progressBar.progress(Bundle.DomainCategoryRunner_Progress_Message_Domain_Types());
        this.findDomainTypes();
    }

    @Override
    void startUp() throws IngestModule.IngestModuleException {
        // lookup all providers, filter null providers, and sort providers
        Collection<? extends DomainCategorizer> lookupCollection = Lookup.getDefault().lookupAll(DomainCategorizer.class);
        Collection<? extends DomainCategorizer> lookupList = (lookupCollection == null)
                ? Collections.emptyList()
                : lookupCollection;

        // this will be the class instance of the foundProviders
        List<DomainCategorizer> foundProviders = new ArrayList<>();

        // find the custom domain categories provider if present and add it first to the list
        lookupList.stream()
                .filter(categorizer -> categorizer.getClass().getName().contains(CUSTOM_CATEGORIZER_PATH))
                .findFirst()
                .ifPresent((provider) -> foundProviders.add(provider));

        // add the default priority categorizer
        foundProviders.add(new DefaultPriorityDomainCategorizer());

        // add all others except for the custom web domain categorizer, the default priority 
        // categorizer and the default categorizer
        lookupList.stream()
                .filter(categorizer -> categorizer != null)
                .filter(categorizer -> {
                    String className = categorizer.getClass().getName();
                    return !className.contains(CUSTOM_CATEGORIZER_PATH)
                            && !className.equals(DefaultPriorityDomainCategorizer.class.getName())
                            && !className.equals(DefaultDomainCategorizer.class.getName());
                })
                .sorted((a, b) -> a.getClass().getName().compareToIgnoreCase(b.getClass().getName()))
                .forEach(foundProviders::add);

        // add the default categorizer last
        foundProviders.add(new DefaultDomainCategorizer());

        for (DomainCategorizer provider : foundProviders) {
            try {
                provider.initialize();
            } catch (DomainCategorizerException ex) {
                throw new IngestModule.IngestModuleException("There was an error instantiating the provider: "
                        + provider.getClass().getSimpleName(), ex);
            }
        }

        this.domainProviders = foundProviders;
    }

    @Override
    public void shutDown() {
        if (this.domainProviders != null) {
            for (DomainCategorizer provider : this.domainProviders) {
                try {
                    provider.close();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "There was an error closing " + provider.getClass().getName(), ex);
                }
            }
        }
        super.shutDown();
    }
}
