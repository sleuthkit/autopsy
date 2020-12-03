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
import org.openide.util.Exceptions;
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
 * Analyzes a URL to determine if the url host is one that handles messages
 * (i.e. webmail, disposable mail). If found, a domain category type artifact is
 * created.
 */
@Messages({
    "MessageURLAnalyzer_moduleName_text=MessageURLAnalyzer",
    "MessageURLAnalyzer_Progress_Message_Find_Message_URLs=Finding Messaging Domains",
    "MessageURLAnalyzer_parentModuleName=Recent Activity"
})
class MessageURLAnalyzer extends Extract {

    /**
     * The message service type (i.e. webmail, disposable mail).
     */
    @Messages({
        "MessageType_disposableMail_displayName=Disposable Email",
        "MessageType_webmail_displayName=Web Email"
    })
    private enum MessageType {
        DISPOSABLE_EMAIL("disposable", Bundle.MessageType_disposableMail_displayName()),
        WEBMAIL("webmail", Bundle.MessageType_webmail_displayName());

        private final String csvId;
        private final String attrDisplayName;

        /**
         * Main constructor.
         *
         * @param csvId The identifier within the csv for this type.
         * @param attrDisplayName The display name in the artifact for this
         * domain category.
         */
        private MessageType(String csvId, String attrDisplayName) {
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
     * csv entry: "hotmail.com,webmail" would get parsed to a node, "com" having
     * a child of "hotmail". That child node, as a leaf, would have a webmail
     * message type.
     */
    private static class MessageDomainTrieNode {

        private final Map<String, MessageDomainTrieNode> children = new HashMap<>();
        private MessageType messageType = null;

        /**
         * Retrieves the child node of the given key. If that child key does not
         * exist, a child node of that key is created and returned.
         *
         * @param childKey The key for the child (i.e. "com").
         * @return The retrieved or newly created child node.
         */
        MessageDomainTrieNode getOrAddChild(String childKey) {
            MessageDomainTrieNode child = children.get(childKey);
            if (child == null) {
                child = new MessageDomainTrieNode();
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
        MessageDomainTrieNode getChild(String childKey) {
            return children.get(childKey);
        }

        /**
         * @return If this is a leaf node, the type of message for this node.
         */
        MessageType getMessageType() {
            return messageType;
        }

        /**
         * If this is a leaf node, this sets the message type for this node.
         *
         * @param messageType The message type for this leaf node.
         */
        void setMessageType(MessageType messageType) {
            this.messageType = messageType;
        }
    }

    /**
     * Loads the trie of suffixes from the csv resource file.
     *
     * @return The root trie node.
     * @throws IOException
     */
    private static MessageDomainTrieNode loadTrie() throws IOException {
        try (InputStream is = MessageURLAnalyzer.class.getResourceAsStream(MESSAGE_TYPE_CSV);
                InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isReader)) {

            MessageDomainTrieNode trie = new MessageDomainTrieNode();
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
    private static void addItem(MessageDomainTrieNode trie, String line, int lineNumber) {
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

        // determine the message type from the value, and return if can't be determined.
        String messageTypeStr = csvItems[1].trim();

        MessageType messageType = (StringUtils.isNotBlank(messageTypeStr))
                ? Stream.of(MessageType.values())
                        .filter((m) -> m.getCsvId().equalsIgnoreCase(messageTypeStr))
                        .findFirst()
                        .orElse(null)
                : null;

        if (messageType == null) {
            logger.log(Level.WARNING, String.format("Could not determine message type for this line: \"%s\" at line %d", line, lineNumber));
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
        MessageDomainTrieNode node = trie;
        for (int i = domainTokens.length - 1; i >= 0; i--) {
            String token = domainTokens[i];
            if (StringUtils.isBlank(token)) {
                continue;
            }

            node = node.getOrAddChild(domainTokens[i]);
        }

        node.setMessageType(messageType);

    }

    // Character for joining domain segments.
    private static final String JOINER = ".";
    // delimiter when used with regex for domains
    private static final String DELIMITER = "\\" + JOINER;

    // csv delimiter
    private static final String CSV_DELIMITER = ",";

    private static final String MESSAGE_TYPE_CSV = "message_types.csv"; //NON-NLS

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

    private static final Logger logger = Logger.getLogger(MessageURLAnalyzer.class.getName());

    // the root node for the trie containing suffixes for domain categories.
    private MessageDomainTrieNode rootTrie = null;

    private Content dataSource;
    private IngestJobContext context;

    /**
     * Main constructor.
     */
    MessageURLAnalyzer() {
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
     * Determines if the host is a message type domain. If so, returns the
     * portion of the host suffix that signifies the message domain (i.e.
     * "hotmail.com" or "mail.google.com") and the message type.
     *
     * @param host The host.
     * @return A pair of the host suffix and message type for that suffix if
     * found. Otherwise, returns null.
     */
    private Pair<String, MessageType> findHostSuffix(String host) {
        // if no host, return none.
        if (StringUtils.isBlank(host)) {
            return null;
        }

        // parse the tokens splitting on delimiter
        List<String> tokens = Stream.of(host.toLowerCase().split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        MessageDomainTrieNode node = rootTrie;
        // the root node is null indicating we won't be able to do a lookup.
        if (node == null) {
            return null;
        }

        // iterate through tokens in reverse order
        int idx = tokens.size() - 1;
        for (; idx >= 0; idx--) {
            node = node.getChild(tokens.get(idx));
            // if we hit a leaf node or we have no matching child node, continue.
            if (node == null || node.getMessageType() != null) {
                break;
            }
        }

        MessageType messageType = node != null ? node.getMessageType() : null;

        if (messageType == null) {
            return null;
        } else {
            // if there is a message type, we have a result.  Concatenate the 
            // appropriate domain tokens and return.
            int minIndex = Math.max(0, idx);
            List<String> subList = tokens.subList(minIndex, tokens.size());
            String hostSuffix = String.join(JOINER, subList);
            return Pair.of(hostSuffix, messageType);
        }
    }

    /**
     * Goes through web history artifacts and attempts to determine any hosts of
     * a message type. If any are found, a TSK_DOMAIN_CATEGORY artifact is
     * created (at most one per host suffix).
     */
    private void findMessageDomains() {
        if (this.rootTrie == null) {
            logger.log(Level.SEVERE, "Not analyzing message domain.  No root trie loaded.");
            return;
        }

        int artifactsAnalyzed = 0;
        int messageDomainInstancesFound = 0;
        
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

                // attempt to get the message type for the host using the suffix trie
                Pair<String, MessageType> messageEntryFound = findHostSuffix(host);
                if (messageEntryFound == null) {
                    continue;
                }

                // if we got this far, we found a message domain, but it may not be unique
                messageDomainInstancesFound++;

                String hostSuffix = messageEntryFound.getLeft();
                MessageType messageType = messageEntryFound.getRight();
                if (StringUtils.isBlank(hostSuffix) || messageType == null || domainSuffixesSeen.contains(hostSuffix)) {
                    continue;
                }

                // if we got this far, this is a unique suffix.  Add to the set, so we don't create
                // multiple of same suffix and add an artifact.
                domainSuffixesSeen.add(hostSuffix);

                String moduleName = Bundle.MessageURLAnalyzer_parentModuleName();

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(host)),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REALM, moduleName, host),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, messageType.getAttrDisplayName())
                );
                postArtifact(createArtifactWithAttributes(ARTIFACT_TYPE.TSK_DOMAIN_CATEGORY, file, bbattributes));
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for messaging domains", e); //NON-NLS
        } finally {
            if (context.dataSourceIngestIsCancelled()) {
                logger.info("Operation terminated by user."); //NON-NLS
            }
            logger.log(Level.INFO, String.format("Extracted %s distinct messaging domain(s) from the blackboard.  "
                    + "Of the %s artifact(s) with valid hosts, %s url(s) contained messaging domain suffix ",
                    domainSuffixesSeen.size(), artifactsAnalyzed, messageDomainInstancesFound));
        }
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;

        progressBar.progress(Bundle.Progress_Message_Find_Search_Query());
        this.findMessageDomains();
    }

    @Override
    void configExtractor() throws IngestModule.IngestModuleException {
        try {
            this.rootTrie = loadTrie();
        } catch (IOException ex) {
            throw new IngestModule.IngestModuleException("Unable to load message type csv for domain category analysis", ex);
        }
    }

    @Override
    public void complete() {
        logger.info("Search Engine URL Query Analyzer has completed."); //NON-NLS
    }
}
