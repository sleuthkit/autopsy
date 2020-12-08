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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Analyzes a URL to determine if the url host is one of a certain kind of category
 * (i.e. webmail, disposable mail). If found, a web category artifact is
 * created.
 *
 * CSV entries describing these domain types are compiled from sources.
 * webmail: https://github.com/mailcheck/mailcheck/wiki/List-of-Popular-Domains
 * disposable mail: https://www.npmjs.com/package/disposable-email-domains
 */
@Messages({
    "DomainCategorizer_moduleName_text=DomainCategorizer",
    "DomainCategorizer_Progress_Message_Domain_Types=Finding Domain Types",
    "DomainCategorizer_parentModuleName=Recent Activity"
})
class DomainCategorizer extends Extract {

    /**
     * The domain type (i.e. webmail, disposable mail).
     */
    @Messages({
        "DomainType_disposableMail_displayName=Disposable Email",
        "DomainType_webmail_displayName=Web Email"
    })
    private enum DomainType {
        DISPOSABLE_EMAIL("Disposable Email", Bundle.DomainType_disposableMail_displayName()),
        WEBMAIL("Web Email", Bundle.DomainType_webmail_displayName());

        private final String csvId;
        private final String attrDisplayName;

        /**
         * Main constructor.
         *
         * @param csvId The identifier within the csv for this type.
         * @param attrDisplayName The display name in the artifact for this
         * domain category.
         */
        private DomainType(String csvId, String attrDisplayName) {
            this.csvId = csvId;
            this.attrDisplayName = attrDisplayName;
        }

        /**
         * @return The identifier within the csv for this type.
         */
        String getCsvId() {
            return csvId;
        }

        /**
         * @return The display name in the artifact for this domain category.
         */
        String getAttrDisplayName() {
            return attrDisplayName;
        }
    }

    /**
     * A node in the trie indicating a domain suffix token. For instance, the
     * csv entry: "hotmail.com,Web Email" would get parsed to a node, "com" having
     * a child of "hotmail". That child node, as a leaf, would have a webmail
     * message type.
     */
    private static class DomainTypeTrieNode {

        private final Map<String, DomainTypeTrieNode> children = new HashMap<>();
        private DomainType domainType = null;

        /**
         * Retrieves the child node of the given key. If that child key does not
         * exist, a child node of that key is created and returned.
         *
         * @param childKey The key for the child (i.e. "com").
         * @return The retrieved or newly created child node.
         */
        DomainTypeTrieNode getOrAddChild(String childKey) {
            DomainTypeTrieNode child = children.get(childKey);
            if (child == null) {
                child = new DomainTypeTrieNode();
                children.put(childKey, child);
            }

            return child;
        }

        /**
         * Retrieves the child node of the given key or returns null if child
         * does not exist.
         *
         * @param childKey The key for the child node (i.e. "com").
         * @return The child node or null if it does not exist.
         */
        DomainTypeTrieNode getChild(String childKey) {
            return children.get(childKey);
        }

        /**
         * @return If this is a leaf node, the type of domain for this node.
         */
        DomainType getDomainType() {
            return domainType;
        }

        /**
         * If this is a leaf node, this sets the domain type for this node.
         *
         * @param domainType The domain type for this leaf node.
         */
        void setDomainType(DomainType domainType) {
            this.domainType = domainType;
        }
    }

    /**
     * Loads the trie of suffixes from the csv resource file.
     *
     * @return The root trie node.
     * @throws IOException
     */
    private static DomainTypeTrieNode loadTrie() throws IOException {
        try (InputStream is = DomainCategorizer.class.getResourceAsStream(DOMAIN_TYPE_CSV);
                InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isReader)) {

            DomainTypeTrieNode trie = new DomainTypeTrieNode();
            int lineNum = 1;
            while (reader.ready()) {
                String line = reader.readLine();
                if (!StringUtils.isBlank(line)) {
                    addItem(trie, line.trim(), lineNum);
                    lineNum++;
                }
            }

            return trie;
        }
    }

    /**
     * Adds a trie node based on the csv line.
     *
     * @param trie The root trie node.
     * @param line The line to be parsed.
     * @param lineNumber The line number of this csv line.
     */
    private static void addItem(DomainTypeTrieNode trie, String line, int lineNumber) {
        // make sure this isn't a blank line.
        if (StringUtils.isBlank(line)) {
            return;
        }

        String[] csvItems = line.split(CSV_DELIMITER);
        // line should be a key value pair
        if (csvItems.length < 2) {
            logger.log(Level.WARNING, String.format("Unable to properly parse line of \"%s\" at line %d", line, lineNumber));
            return;
        }

        // determine the domain type from the value, and return if can't be determined.
        String domainTypeStr = csvItems[1].trim();

        DomainType domainType = (StringUtils.isNotBlank(domainTypeStr))
                ? Stream.of(DomainType.values())
                        .filter((m) -> m.getCsvId().equalsIgnoreCase(domainTypeStr))
                        .findFirst()
                        .orElse(null)
                : null;

        if (domainType == null) {
            logger.log(Level.WARNING, String.format("Could not determine domain type for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        // gather the domainSuffix and parse into domain trie tokens
        String domainSuffix = csvItems[0];
        if (StringUtils.isBlank(domainSuffix)) {
            logger.log(Level.WARNING, String.format("Could not determine domain suffix for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        String[] domainTokens = domainSuffix.trim().toLowerCase().split(DELIMITER);

        // add into the trie
        DomainTypeTrieNode node = trie;
        for (int i = domainTokens.length - 1; i >= 0; i--) {
            String token = domainTokens[i];
            if (StringUtils.isBlank(token)) {
                continue;
            }

            node = node.getOrAddChild(domainTokens[i]);
        }

        node.setDomainType(domainType);

    }

    // Character for joining domain segments.
    private static final String JOINER = ".";
    // delimiter when used with regex for domains
    private static final String DELIMITER = "\\" + JOINER;

    // csv delimiter
    private static final String CSV_DELIMITER = ",";

    private static final String DOMAIN_TYPE_CSV = "default_domain_categories.csv"; //NON-NLS

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

    // the root node for the trie containing suffixes for domain categories.
    private DomainTypeTrieNode rootTrie = null;

    private Content dataSource;
    private IngestJobContext context;

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

    /**
     * Determines if the host is a known type of domain. If so, returns the
     * portion of the host suffix that signifies the domain type (i.e.
     * "hotmail.com" or "mail.google.com") and the domain type.
     *
     * @param host The host.
     * @return A pair of the host suffix and domain type for that suffix if
     * found. Otherwise, returns null.
     */
    private Pair<String, DomainType> findHostSuffix(String host) {
        // if no host, return none.
        if (StringUtils.isBlank(host)) {
            return null;
        }

        // parse the tokens splitting on delimiter
        List<String> tokens = Stream.of(host.toLowerCase().split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        DomainTypeTrieNode node = rootTrie;
        // the root node is null indicating we won't be able to do a lookup.
        if (node == null) {
            return null;
        }

        // iterate through tokens in reverse order
        int idx = tokens.size() - 1;
        for (; idx >= 0; idx--) {
            node = node.getChild(tokens.get(idx));
            // if we hit a leaf node or we have no matching child node, continue.
            if (node == null || node.getDomainType() != null) {
                break;
            }
        }

        DomainType domainType = node != null ? node.getDomainType() : null;

        if (domainType == null) {
            return null;
        } else {
            // if there is a domain type, we have a result.  Concatenate the 
            // appropriate domain tokens and return.
            int minIndex = Math.max(0, idx);
            List<String> subList = tokens.subList(minIndex, tokens.size());
            String hostSuffix = String.join(JOINER, subList);
            return Pair.of(hostSuffix, domainType);
        }
    }

    /**
     * Goes through web history artifacts and attempts to determine any hosts of
     * a domain type. If any are found, a TSK_WEB_CATEGORIZATION artifact is
     * created (at most one per host suffix).
     */
    private void findDomainTypes() {
        if (this.rootTrie == null) {
            logger.log(Level.SEVERE, "Not analyzing domain types.  No root trie loaded.");
            return;
        }

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
                if (StringUtils.isBlank(host)) {
                    continue;
                }

                // if we reached this point, we are at least analyzing this item
                artifactsAnalyzed++;

                // attempt to get the domain type for the host using the suffix trie
                Pair<String, DomainType> domainEntryFound = findHostSuffix(host);
                if (domainEntryFound == null) {
                    continue;
                }

                // if we got this far, we found a domain type, but it may not be unique
                domainTypeInstancesFound++;

                String hostSuffix = domainEntryFound.getLeft();
                DomainType domainType = domainEntryFound.getRight();
                if (StringUtils.isBlank(hostSuffix) || domainType == null || domainSuffixesSeen.contains(hostSuffix)) {
                    continue;
                }

                // if we got this far, this is a unique suffix.  Add to the set, so we don't create
                // multiple of same suffix and add an artifact.
                domainSuffixesSeen.add(hostSuffix);

                String moduleName = Bundle.DomainCategorizer_parentModuleName();

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(host)),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HOST, moduleName, host),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, domainType.getAttrDisplayName())
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

        progressBar.progress(Bundle.Progress_Message_Find_Search_Query());
        this.findDomainTypes();
    }

    @Override
    void configExtractor() throws IngestModule.IngestModuleException {
        try {
            this.rootTrie = loadTrie();
        } catch (IOException ex) {
            throw new IngestModule.IngestModuleException("Unable to load domain type csv for domain category analysis", ex);
        }
    }

    @Override
    public void complete() {
        logger.info("Search Engine URL Query Analyzer has completed."); //NON-NLS
    }
}
