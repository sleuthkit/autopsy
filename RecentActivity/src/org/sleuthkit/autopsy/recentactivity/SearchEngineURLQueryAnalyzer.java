/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
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
class SearchEngineURLQueryAnalyzer extends Extract {

    private static final Logger logger = Logger.getLogger(SearchEngineURLQueryAnalyzer.class.getName());
    private static final String XMLFILE = "SEUQAMappings.xml"; //NON-NLS
    private static final String XSDFILE = "SearchEngineSchema.xsd"; //NON-NLS
    private static SearchEngineURLQueryAnalyzer.SearchEngine[] engines;

    private Content dataSource;
    private IngestJobContext context;

    SearchEngineURLQueryAnalyzer() {
        moduleName = NbBundle.getMessage(ExtractIE.class, "SearchEngineURLQueryAnalyzer.moduleName.text");
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

        String getKey() {
            return key;
        }

        String getKeyRegExp() {
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

        void increment() {
            ++count;
        }

        String getEngineName() {
            return engineName;
        }

        String getDomainSubstring() {
            return domainSubstring;
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

            if (!XMLUtil.xmlIsValid(xmlinput, SearchEngineURLQueryAnalyzer.class, XSDFILE)) {
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
        SearchEngineURLQueryAnalyzer.SearchEngine[] listEngines = new SearchEngineURLQueryAnalyzer.SearchEngine[nlist.getLength()];
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

            SearchEngineURLQueryAnalyzer.SearchEngine Se = new SearchEngineURLQueryAnalyzer.SearchEngine(EngineName, EnginedomainSubstring, keys);
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
     * @return supported search engine the domain belongs to or null if no match
     *         is found
     *
     */
    private static SearchEngineURLQueryAnalyzer.SearchEngine getSearchEngineFromUrl(String domain) {
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
    private String extractSearchEngineQuery(SearchEngineURLQueryAnalyzer.SearchEngine eng, String url) {
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
        } catch (UnsupportedEncodingException uee) { //if it fails, return the encoded string
            logger.log(Level.FINE, "Error during URL decoding ", uee); //NON-NLS
            return x;
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

    private void findSearchQueries() {
        int totalQueries = 0;
        try {
            //from blackboard_artifacts
            Collection<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getMatchingArtifacts("WHERE (blackboard_artifacts.artifact_type_id = '" + ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() //NON-NLS
                    + "' OR blackboard_artifacts.artifact_type_id = '" + ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() + "') ");  //List of every 'web_history' and 'bookmark' artifact NON-NLS
            logger.log(Level.INFO, "Processing {0} blackboard artifacts.", listArtifacts.size()); //NON-NLS

            for (BlackboardArtifact artifact : listArtifacts) {
                if (context.dataSourceIngestIsCancelled()) {
                    break;       //User cancelled the process.
                }

                //initializing default attributes
                String query = "";
                String searchEngineDomain = "";
                String browser = "";
                long last_accessed = -1;

                long fileId = artifact.getObjectID();
                boolean isFromSource = tskCase.isFileFromSource(dataSource, fileId);
                if (!isFromSource) {
                    //File was from a different dataSource. Skipping.
                    continue;
                }

                AbstractFile file = tskCase.getAbstractFileById(fileId);
                if (file == null) {
                    continue;
                }

                SearchEngineURLQueryAnalyzer.SearchEngine se = null;
                //from blackboard_attributes
                Collection<BlackboardAttribute> listAttributes = currentCase.getSleuthkitCase().getMatchingAttributes("WHERE artifact_id = " + artifact.getArtifactID()); //NON-NLS

                for (BlackboardAttribute attribute : listAttributes) {
                    if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID()) {
                        final String urlString = attribute.getValueString();
                        se = getSearchEngineFromUrl(urlString);
                        if (se == null) {
                            break;
                        }

                        query = extractSearchEngineQuery(se, attribute.getValueString());
                        if (query.equals("")) //False positive match, artifact was not a query. NON-NLS
                        {
                            break;
                        }

                    } else if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()) {
                        browser = attribute.getValueString();
                    } else if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID()) {
                        searchEngineDomain = attribute.getValueString();
                    } else if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) {
                        last_accessed = attribute.getValueLong();
                    }
                }

                if (se != null && !query.equals("")) { //NON-NLS
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
                    this.addArtifact(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY, file, bbattributes);
                    se.increment();
                    ++totalQueries;
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for search engine queries", e); //NON-NLS
        } finally {
            if (context.dataSourceIngestIsCancelled()) {
                logger.info("Operation terminated by user."); //NON-NLS
            }
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                    NbBundle.getMessage(this.getClass(), "SearchEngineURLQueryAnalyzer.parentModuleName.noSpace"),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY));
            logger.log(Level.INFO, "Extracted {0} queries from the blackboard", totalQueries); //NON-NLS
        }
    }

    private String getTotals() {
        String total = "";
        if (engines == null) {
            return total;
        }
        for (SearchEngineURLQueryAnalyzer.SearchEngine se : engines) {
            total += se.getEngineName() + " : " + se.getTotal() + "\n";
        }
        return total;
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        this.findSearchQueries();
        logger.log(Level.INFO, "Search Engine stats: \n{0}", getTotals()); //NON-NLS
    }

    @Override
    void init() throws IngestModuleException {
        try {
            PlatformUtil.extractResourceToUserConfigDir(SearchEngineURLQueryAnalyzer.class, XMLFILE, true);
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
