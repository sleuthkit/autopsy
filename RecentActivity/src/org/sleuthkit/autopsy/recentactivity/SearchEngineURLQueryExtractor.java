/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This recent activity extractor attempts to extract web queries from major
 * search engines by querying the blackboard for web history and bookmark
 * artifacts, and extracting search text from them.
 *
 *
 * To add search engines, edit SearchEngines.xml under RecentActivity
 *
 */
@NbBundle.Messages({
    "cannotBuildXmlParser=Unable to build XML parser: ",
    "cannotLoadSEUQA=Unable to load Search Engine URL Query Analyzer settings file, SEUQAMappings.xml: ",
    "cannotParseXml=Unable to parse XML file: ",
    "# {0} - file name", "SearchEngineURLQueryAnalyzer.init.exception.msg=Unable to find {0}."
})
final class SearchEngineURLQueryExtractor extends Extract {

    private static final Logger logger = Logger.getLogger(SearchEngineURLQueryExtractor.class.getName());
    private static final String PARENT_MODULE_NAME = NbBundle.getMessage(SearchEngineURLQueryExtractor.class,
            "SearchEngineURLQueryAnalyzer.parentModuleName");

    private static final String XMLFILE = "SEUQAMappings.xml"; //NON-NLS
    private static final String XSDFILE = "SearchEngineSchema.xsd"; //NON-NLS
    private static SearchEngine[] engines;

    private Content dataSource;
    private IngestJobContext context;

    @Override
    protected String getModuleName() {
        return NbBundle.getMessage(ExtractIE.class, "SearchEngineURLQueryAnalyzer.moduleName.text");
    }

    /**
     * Stores the regular expression and non-reg exp pair of keys. Key in the
     * case of "?q=foo" would be "?q=".
     */
    private static class KeyPair {

        private final String key;
        private final String keyRegExp;

        KeyPair(String key, String keyRegExp) {
            this.key = key;
            this.keyRegExp = keyRegExp;
        }

        private String getKey() {
            return key;
        }

        private String getKeyRegExp() {
            return keyRegExp;
        }

    }

    private static class SearchEngine {

        private final String engineName;
        private final String domainSubstring;
        private final List<KeyPair> keyPairs;
        private int count;

        SearchEngine(String engineName, String domainSubstring, List<KeyPair> keyPairs) {
            this.engineName = engineName;
            this.domainSubstring = domainSubstring;
            this.keyPairs = keyPairs;
            count = 0;
        }

        private void increment() {
            ++count;
        }

        private String getEngineName() {
            return engineName;
        }

        private String getDomainSubstring() {
            return domainSubstring;
        }

        private int getTotal() {
            return count;
        }

        /**
         * Get the key values used in the URL to denote the search term
         *
         * @return
         */
        private List<KeyPair> getKeys() {
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
            File configFile = new File(path);
            logger.log(Level.INFO, "Load successful"); //NON-NLS
            xmlinput = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configFile);

            if (!XMLUtil.xmlIsValid(xmlinput, SearchEngineURLQueryExtractor.class, XSDFILE)) {
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
        SearchEngine[] listEngines = new SearchEngine[nlist.getLength()];
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

            SearchEngine searchEngine = new SearchEngine(EngineName, EnginedomainSubstring, keys);
            listEngines[i] = searchEngine;
        }
        engines = listEngines;
    }

    /**
     * Returns which of the supported SearchEngines, if any, the given string
     * belongs to.
     *
     * @param domain domain as part of the URL
     *
     * @return supported search engine the domain belongs to or null if no match
     *         is found
     *
     */
    private static SearchEngine getSearchEngineFromUrl(String domain) {
        if (engines == null) {
            return null;
        }
        for (SearchEngine engine : engines) {
            if (domain.contains(engine.getDomainSubstring())) {
                return engine;
            }
        }
        return null;
    }

    /**
     * Attempts to extract the query from a URL.
     *
     * @param url The URL string to be dissected.
     *
     * @return The extracted search query.
     */
    private String extractSearchEngineQuery(SearchEngine eng, String url) {
        String value = ""; //NON-NLS

        for (KeyPair kp : eng.getKeys()) {
            if (url.contains(kp.getKey())) {
                value = getValue(url, kp.getKeyRegExp());
                break;
            }
        }
        try { //try to decode the url
            return URLDecoder.decode(value, "UTF-8"); //NON-NLS
        } catch (UnsupportedEncodingException exception) { //if it fails, return the encoded string
            logger.log(Level.FINE, "Error during URL decoding, returning undecoded value:"
                                   + "\n\tURL: " + url
                                   + "\n\tUndecoded value: " + value
                                   + "\n\tEngine name: " + eng.getEngineName()
                                   + "\n\tEngine domain: " + eng.getDomainSubstring(), exception); //NON-NLS
            return value;
        } catch (IllegalArgumentException exception) { //if it fails, return the encoded string
            logger.log(Level.SEVERE, "Illegal argument passed to URL decoding, returning undecoded value:"
                                     + "\n\tURL: " + url
                                     + "\n\tUndecoded value: " + value
                                     + "\n\tEngine name: " + eng.getEngineName()
                                     + "\n\tEngine domain: " + eng.getDomainSubstring(), exception); //NON-NLS)
            return value;
        }
    }

    /**
     * Splits URLs based on a delimeter (key). .contains() and .split()
     *
     * @param url       The URL to be split
     * @param regExpKey the delimeter value used to split the URL into its
     *                  search token, extracted from the xml.
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

        String[] tokens = url.split(regExpKey.replace("\\?", "?"));
        if (tokens.length >= 2) {
            if (tokens[tokens.length - 1].contains("&")) {
                value = tokens[tokens.length - 1].split("&")[0];
            } else {
                value = tokens[tokens.length - 1];
            }
        }
        return value;
    }

    private void findSearchQueries() {

        Collection<BlackboardArtifact> sourceArtifacts = new ArrayList<>();
        try {
            //List of every 'web_history' and 'bookmark' 
            sourceArtifacts.addAll(tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
            sourceArtifacts.addAll(tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_WEB_HISTORY));
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting TSK_WEB_BOOKMARK or TSK_WEB_HISTORY artifacts", tskCoreException); //NON-NLS
        }
        logger.log(Level.INFO, "Processing {0} blackboard artifacts.", sourceArtifacts.size()); //NON-NLS

        Collection<BlackboardArtifact> queryArtifacts = new ArrayList<>();
        for (BlackboardArtifact sourceArtifact : sourceArtifacts) {
            if (context.dataSourceIngestIsCancelled()) {
                break;    //User cancelled the process.
            }
            long fileId = sourceArtifact.getObjectID();
            try {
                if (false == tskCase.isFileFromSource(dataSource, fileId)) {
                    continue;  //File was from a different dataSource. Skipping.
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Encountered error determining if file " + fileId + "is from datasource " + dataSource.getId(), ex); //NON-NLS
                continue;
            }

            AbstractFile file;
            try {
                file = tskCase.getAbstractFileById(fileId);
                if (file == null) {
                    logger.log(Level.WARNING, "There was no file for id {0}", fileId); //NON-NLS
                    continue;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting file for id " + fileId, ex); //NON-NLS
                continue;
            }

            try {
                final String urlString = sourceArtifact.getAttribute(new BlackboardAttribute.Type(TSK_URL)).getValueString();
                SearchEngine searchEngine = getSearchEngineFromUrl(urlString);
                if (searchEngine == null) {
                    continue;
                }

                String query = extractSearchEngineQuery(searchEngine, urlString);
                if (query.isEmpty()) { //False positive match, artifact was not a query.  
                    continue;
                }

                String browser = sourceArtifact.getAttribute(new BlackboardAttribute.Type(TSK_PROG_NAME)).getValueString();
                String searchEngineDomain = sourceArtifact.getAttribute(new BlackboardAttribute.Type(TSK_DOMAIN)).getValueString();
                long last_accessed = sourceArtifact.getAttribute(new BlackboardAttribute.Type(TSK_DATETIME_ACCESSED)).getValueLong();

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                searchEngineDomain),
                        new BlackboardAttribute(
                                TSK_TEXT, PARENT_MODULE_NAME,
                                query),
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                browser),
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                last_accessed));

                BlackboardArtifact bbart = file.newArtifact(TSK_WEB_SEARCH_QUERY);
                bbart.addAttributes(bbattributes);
                queryArtifacts.add(bbart);
                searchEngine.increment();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Encountered error creating search query artifacts.", ex); //NON-NLS
            }
        }

        try {
            blackboard.postArtifacts(queryArtifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Encountered error posting search query artifacts.", ex); //NON-NLS
        }

        logger.log(Level.INFO, "Extracted {0} queries from the blackboard", queryArtifacts.size()); //NON-NLS
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        this.findSearchQueries();

        String totals = "";
        for (SearchEngine se : engines) {
            totals += se.getEngineName() + " : " + se.getTotal() + "\n";
        }
        logger.log(Level.INFO, "Search Engine stats: \n{0}", totals); //NON-NLS
    }

    @Override
    void configExtractor() throws IngestModuleException {
        try {
            PlatformUtil.extractResourceToUserConfigDir(SearchEngineURLQueryExtractor.class,
                    XMLFILE, true);
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
