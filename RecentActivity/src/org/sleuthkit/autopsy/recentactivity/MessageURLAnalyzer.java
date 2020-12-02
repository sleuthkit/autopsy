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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

@Messages({
    "MessageURLAnalyzer_moduleName_text=MessageURLAnalyzer",
    "MessageURLAnalyzer_Progress_Message_Find_Message_URLs=Finding Messaging Domains",
    "MessageURLAnalyzer_parentModuleName=Recent Activity"
})
class MessageURLAnalyzer extends Extract {

    @Messages({
        "MessageType_disposableMail_displayName=Disposable Email",
        "MessageType_webmail_displayName=Web Email"
    })
    private enum MessageType {
        DISPOSABLE_EMAIL("disposable", Bundle.MessageType_disposableMail_displayName()),
        WEBMAIL("webmail", Bundle.MessageType_webmail_displayName());

        private final String csvId;
        private final String attrDisplayName;

        private MessageType(String csvId, String attrDisplayName) {
            this.csvId = csvId;
            this.attrDisplayName = attrDisplayName;
        }

        public String getCsvId() {
            return csvId;
        }

        public String getAttrDisplayName() {
            return attrDisplayName;
        }
    }

    private static class MessageDomainTrieNode {

        private final Map<String, MessageDomainTrieNode> children = new HashMap<>();
        private MessageType messageType = null;

        MessageDomainTrieNode getOrAddChild(String childKey) {
            MessageDomainTrieNode child = children.get(childKey);
            if (child == null) {
                child = new MessageDomainTrieNode();
                children.put(childKey, child);
            }

            return child;
        }

        MessageDomainTrieNode getChild(String childKey) {
            return children.get(childKey);
        }

        MessageType getMessageType() {
            return messageType;
        }

        void setMessageType(MessageType messageType) {
            this.messageType = messageType;
        }
    }

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

    private static void addItem(MessageDomainTrieNode trie, String line, int lineNumber) {
        String[] csvItems = line.split(CSV_DELIMITER);
        if (csvItems.length < 2) {
            logger.log(Level.WARNING, String.format("Unable to properly parse line of \"%s\" at line %d", line, lineNumber));
            return;
        }

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

        String domainSuffix = csvItems[0];
        if (StringUtils.isBlank(domainSuffix)) {
            logger.log(Level.WARNING, String.format("Could not determine domain suffix for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        String[] domainTokens = domainSuffix.trim().toLowerCase().split(DELIMITER);

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

    private static MessageDomainTrieNode trieSingleton = null;
    private static final Object trieLock = new Object();

    private static MessageDomainTrieNode getTrie() {
        synchronized (trieLock) {
            if (trieSingleton == null) {
                try {
                    trieSingleton = loadTrie();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Unable to load message domain csv", ex);
                }
            }
        }

        return trieSingleton;
    }

    // Character for joining domain segments.
    private static final String JOINER = ".";
    // delimiter when used with regex for domains
    private static final String DELIMITER = "\\" + JOINER;

    // csv delimiter
    private static final String CSV_DELIMITER = ",";

    private static final String MESSAGE_TYPE_CSV = "message_types.csv"; //NON-NLS
    private static final Logger logger = Logger.getLogger(MessageURLAnalyzer.class.getName());

    private final MessageDomainTrieNode rootTrie;

    private Content dataSource;
    private IngestJobContext context;

    MessageURLAnalyzer() {
        moduleName = null;
        rootTrie = getTrie();
    }

    private String getHost(String url) {

    }

    private Pair<String, MessageType> findHostSuffix(String host) {
        if (StringUtils.isBlank(host)) {
            return null;
        }

        List<String> tokens = Stream.of(host.toLowerCase().split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        int idx = tokens.size() - 1;
        MessageDomainTrieNode node = rootTrie;

        for (; idx >= 0; idx--) {
            MessageDomainTrieNode newNode = node.getChild(tokens.get(idx));
            if (newNode == null) {
                break;
            } else {
                node = newNode;
            }
        }

        MessageType messageType = node.getMessageType();
        if (messageType == null) {
            return null;
        } else {
            int minIndex = Math.max(0, idx);
            List<String> subList = tokens.subList(minIndex, tokens.size());
            String hostSuffix = String.join(JOINER, subList);
            return Pair.of(hostSuffix, messageType);
        }
    }

    private void findMessageDomains() {
        if (this.rootTrie == null) {
            logger.log(Level.SEVERE, "Not analyzing message domain.  No root trie loaded.");
            return;
        }

        int artifactsAnalyzed = 0;
        int messageDomainInstancesFound = 0;
        Set<String> domainSuffixesSeen = new HashSet<>();

        try {
            //from blackboard_artifacts
            Collection<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getBlackboard().getArtifacts(
                    Arrays.asList(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_HISTORY)),
                    Arrays.asList(dataSource.getId()));

            logger.log(Level.INFO, "Processing {0} blackboard artifacts.", listArtifacts.size()); //NON-NLS

            for (BlackboardArtifact artifact : listArtifacts) {
                if (context.dataSourceIngestIsCancelled()) {
                    break;       //User cancelled the process.
                }

                AbstractFile file = tskCase.getAbstractFileById(artifact.getObjectID());
                if (file == null) {
                    continue;
                }

                BlackboardAttribute urlAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
                if (urlAttr == null) {
                    continue;
                }

                String urlString = urlAttr.getValueString();
                String host = getHost(urlString);
                if (StringUtils.isBlank(host)) {
                    continue;
                }

                artifactsAnalyzed++;

                Pair<String, MessageType> messageEntryFound = findHostSuffix(host);
                if (messageEntryFound == null) {
                    continue;
                }

                messageDomainInstancesFound++;

                String hostSuffix = messageEntryFound.getLeft();
                MessageType messageType = messageEntryFound.getRight();
                if (StringUtils.isBlank(hostSuffix) || messageType == null || domainSuffixesSeen.contains(hostSuffix)) {
                    continue;
                }

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
            logger.log(Level.INFO, "Extracted {0} messaging domains from the blackboard", totalQueries); //NON-NLS
        }
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;

        progressBar.progress(Bundle.Progress_Message_Find_Search_Query());
        this.findMessageDomains();
        logger.log(Level.INFO, "Messaging Domain stats: \n{0}", getTotals()); //NON-NLS
    }

    @Override
    public void complete() {
        logger.info("Search Engine URL Query Analyzer has completed."); //NON-NLS
    }
}
