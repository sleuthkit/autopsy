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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
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
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Messages({
    "MessageURLAnalyzer_moduleName_text=MessageURLAnalyzer",
    "MessageURLAnalyzer_Progress_Message_Find_Message_URLs=Finding Messaging Domains"
})
class MessageURLAnalyzer extends Extract {

    @Messages({
        "MessageType_disposableMail_displayName=Disposable Email",
        "MessageType_webmail_displayName=Web Email"
    })
    private enum MessageType {
        DISPOSABLE_EMAIL("disposable", Bundle.MessageType_disposableMail_displayName()),
        WEBMAIL("webmail", Bundle.MessageType_webmail_displayName());

        private final String id;
        private final String displayName;

        private MessageType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
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
                        .filter((m) -> m.getId().equalsIgnoreCase(messageTypeStr))
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

    private static class SearchEngine {

        private final String engineName;
        private final String domainSubstring;
        private final List<KeyPair> keyPairs;
        private final Pattern domainRegexPattern;
        private int count;

        SearchEngine(String engineName, String domainSubstring, List<KeyPair> keyPairs) {
            this.engineName = engineName;
            this.domainSubstring = domainSubstring;
            domainRegexPattern = Pattern.compile("^(.*[./])?" + domainSubstring + "([./].*)?$");
            this.keyPairs = keyPairs;
            count = 0;
        }

        void increment() {
            ++count;
        }

        String getEngineName() {
            return engineName;
        }

        String getDomainSubstring() {
            return domainSubstring;
        }

        Pattern getDomainRegexPattern() {
            return domainRegexPattern;
        }

        int getTotal() {
            return count;
        }

        /**
         * Get the key values used in the URL to denote the search term
         *
         * @return
         */
        List<KeyPair> getKeys() {
            return this.keyPairs;
        }

        @Override
        public String toString() {
            String split = " ";
            for (KeyPair kp : keyPairs) {
                split = split + "[ " + kp.getKey() + " :: " + kp.getKeyRegExp() + " ]" + ", ";
            }
            return NbBundle.getMessage(this.getClass(), "SearchEngineURLQueryAnalyzer.toString",
                    engineName, domainSubstring, count, split);
        }
    }

    private void loadConfigFile() throws IngestModuleException {
        Document xmlinput;
        try {
            String path = PlatformUtil.getUserConfigDirectory() + File.separator + XMLFILE;
            File f = new File(path);
            logger.log(Level.INFO, "Load successful"); //NON-NLS
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            xmlinput = db.parse(f);

            if (!XMLUtil.xmlIsValid(xmlinput, MessageURLAnalyzer.class, XSDFILE)) {
                logger.log(Level.WARNING, "Error loading Search Engines: could not validate against [" + XSDFILE + "], results may not be accurate."); //NON-NLS
            }

        } catch (IOException e) {
            throw new IngestModuleException(Bundle.cannotLoadSEUQA() + e.getLocalizedMessage(), e); //NON-NLS
        } catch (ParserConfigurationException pce) {
            throw new IngestModuleException(Bundle.cannotBuildXmlParser() + pce.getLocalizedMessage(), pce); //NON-NLS
        } catch (SAXException sxe) {
            throw new IngestModuleException(Bundle.cannotParseXml() + sxe.getLocalizedMessage(), sxe); //NON-NLS
        }

        NodeList nlist = xmlinput.getElementsByTagName("SearchEngine"); //NON-NLS
        MessageURLAnalyzer.SearchEngine[] listEngines = new MessageURLAnalyzer.SearchEngine[nlist.getLength()];
        for (int i = 0; i < nlist.getLength(); i++) {
            NamedNodeMap nnm = nlist.item(i).getAttributes();

            String EngineName = nnm.getNamedItem("engine").getNodeValue(); //NON-NLS
            String EnginedomainSubstring = nnm.getNamedItem("domainSubstring").getNodeValue(); //NON-NLS
            List<KeyPair> keys = new ArrayList<>();

            NodeList listSplits = xmlinput.getElementsByTagName("splitToken"); //NON-NLS
            for (int k = 0; k < listSplits.getLength(); k++) {
                if (listSplits.item(k).getParentNode().getAttributes().getNamedItem("engine").getNodeValue().equals(EngineName)) { //NON-NLS
                    keys.add(new KeyPair(listSplits.item(k).getAttributes().getNamedItem("plainToken").getNodeValue(), listSplits.item(k).getAttributes().getNamedItem("regexToken").getNodeValue())); //NON-NLS
                }
            }

            MessageURLAnalyzer.SearchEngine Se = new MessageURLAnalyzer.SearchEngine(EngineName, EnginedomainSubstring, keys);
            listEngines[i] = Se;
        }
        engines = listEngines;
    }

    /**
     * Returns which of the supported SearchEngines, if any, the given string
     * belongs to.
     *
     * @param domain domain as part of the URL
     *
     * @return supported search engine(s) the domain belongs to (list may be
     * empty)
     *
     */
    private static Collection<MessageURLAnalyzer.SearchEngine> getSearchEngineFromUrl(String domain) {
        List<MessageURLAnalyzer.SearchEngine> supportedEngines = new ArrayList<>();
        if (engines == null) {
            return supportedEngines;
        }
        for (SearchEngine engine : engines) {
            Matcher matcher = engine.getDomainRegexPattern().matcher(domain);
            if (matcher.matches()) {
                supportedEngines.add(engine);
            }
        }
        return supportedEngines;
    }

    /**
     * Attempts to extract the query from a URL.
     *
     * @param url The URL string to be dissected.
     *
     * @return The extracted search query.
     */
    private String extractSearchEngineQuery(MessageURLAnalyzer.SearchEngine eng, String url) {
        String x = ""; //NON-NLS

        for (KeyPair kp : eng.getKeys()) {
            if (url.contains(kp.getKey())) {
                x = getValue(url, kp.getKeyRegExp());
                break;
            }
        }
        try { //try to decode the url
            String decoded = URLDecoder.decode(x, "UTF-8"); //NON-NLS
            return decoded;
        } catch (UnsupportedEncodingException exception) { //if it fails, return the encoded string
            logger.log(Level.FINE, "Error during URL decoding, returning undecoded value:"
                    + "\n\tURL: " + url
                    + "\n\tUndecoded value: " + x
                    + "\n\tEngine name: " + eng.getEngineName()
                    + "\n\tEngine domain: " + eng.getDomainSubstring(), exception); //NON-NLS
            return x;
        } catch (IllegalArgumentException exception) { //if it fails, return the encoded string
            logger.log(Level.SEVERE, "Illegal argument passed to URL decoding, returning undecoded value:"
                    + "\n\tURL: " + url
                    + "\n\tUndecoded value: " + x
                    + "\n\tEngine name: " + eng.getEngineName()
                    + "\n\tEngine domain: " + eng.getDomainSubstring(), exception); //NON-NLS)
            return x;
        }
    }

    /**
     * Splits URLs based on a delimeter (key). .contains() and .split()
     *
     * @param url The URL to be split
     * @param regExpKey the delimeter value used to split the URL into its
     * search token, extracted from the xml.
     *
     * @return The extracted search query
     *
     */
    private String getValue(String url, String regExpKey) {
        /*
         * NOTE: This doesn't seem like the most wonderful way to do this, but
         * we have data that has a bunch of bogus URLs. Such as: - Multiple
         * google "q=" terms, including one after a "#" tag. Google used the
         * last one - Search/query part of the URL starting with a '#'. Attemps
         * at more formal approaches of splitting on the "?" and then on "&"
         * resulting in missing things.
         */
        String value = ""; //NON-NLS
        String v = regExpKey;
        //Want to determine if string contains a string based on splitkey, but we want to split the string on splitKeyConverted due to regex
        if (regExpKey.contains("\\?")) {
            v = regExpKey.replace("\\?", "?");
        }
        String[] sp = url.split(v);
        if (sp.length >= 2) {
            if (sp[sp.length - 1].contains("&")) {
                value = sp[sp.length - 1].split("&")[0];
            } else {
                value = sp[sp.length - 1];
            }
        }
        return value;
    }

    private String getHost(String url) {

    }

    private String getDomain(String host) {

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
                
                

                Collection<MessageURLAnalyzer.SearchEngine> possibleSearchEngines = getSearchEngineFromUrl(urlString);
                for (MessageURLAnalyzer.SearchEngine se : possibleSearchEngines) {
                    String query = extractSearchEngineQuery(se, urlString);
                    // If we have a non-empty query string, add it to the list
                    if (!query.equals("")) {
                        searchQueries.add(query);
                        se.increment();
                    }
                }

                // If we didn't extract any search queries, go on to the next artifact
                if (searchQueries.isEmpty()) {
                    continue;
                }

                // Extract the rest of the fields needed for the web search artifact
                BlackboardAttribute browserAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                if (browserAttr != null) {
                    browser = browserAttr.getValueString();
                }
                BlackboardAttribute domainAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
                if (domainAttr != null) {
                    searchEngineDomain = domainAttr.getValueString();
                }
                BlackboardAttribute lastAccessAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED));
                if (lastAccessAttr != null) {
                    last_accessed = lastAccessAttr.getValueLong();
                }

                // Make an artifact for each distinct query
                for (String query : searchQueries) {
                    // If date doesn't exist, change to 0 (instead of 1969)
                    if (last_accessed == -1) {
                        last_accessed = 0;
                    }
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            NbBundle.getMessage(this.getClass(),
                                    "SearchEngineURLQueryAnalyzer.parentModuleName"), searchEngineDomain));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT,
                            NbBundle.getMessage(this.getClass(),
                                    "SearchEngineURLQueryAnalyzer.parentModuleName"), query));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                            NbBundle.getMessage(this.getClass(),
                                    "SearchEngineURLQueryAnalyzer.parentModuleName"), browser));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                            NbBundle.getMessage(this.getClass(),
                                    "SearchEngineURLQueryAnalyzer.parentModuleName"), last_accessed));
                    postArtifact(createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY, file, bbattributes));
                    ++totalQueries;
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for search engine queries", e); //NON-NLS
        } finally {
            if (context.dataSourceIngestIsCancelled()) {
                logger.info("Operation terminated by user."); //NON-NLS
            }
            logger.log(Level.INFO, "Extracted {0} queries from the blackboard", totalQueries); //NON-NLS
        }
    }

    private String getTotals() {
        String total = "";
        if (engines == null) {
            return total;
        }
        for (MessageURLAnalyzer.SearchEngine se : engines) {
            total += se.getEngineName() + " : " + se.getTotal() + "\n";
        }
        return total;
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
    void configExtractor() throws IngestModuleException {
        try {
            PlatformUtil.extractResourceToUserConfigDir(MessageURLAnalyzer.class, XMLFILE, true);
        } catch (IOException e) {
            String message = Bundle.SearchEngineURLQueryAnalyzer_init_exception_msg(XMLFILE);
            logger.log(Level.SEVERE, message, e);
            throw new IngestModuleException(message, e);
        }
        loadConfigFile();
    }

    @Override
    public void complete() {
        logger.info("Search Engine URL Query Analyzer has completed."); //NON-NLS
    }
}
