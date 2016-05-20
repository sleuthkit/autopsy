/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Manages reading and writing of keyword lists to user settings XML file
 * keywords.xml or to any file provided in constructor
 */
final class XmlKeywordListImportExport {

    private static final Logger xmlListslogger = Logger.getLogger(XmlKeywordListImportExport.class.getName());
    private static final String CUR_LISTS_FILE_NAME = "keywords.xml";     //NON-NLS
    private static final String CUR_LISTS_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_LISTS_FILE_NAME;
    private static final String ROOT_EL = "keyword_lists"; //NON-NLS
    private static final String LIST_EL = "keyword_list"; //NON-NLS
    private static final String LIST_NAME_ATTR = "name"; //NON-NLS
    private static final String LIST_CREATE_ATTR = "created"; //NON-NLS
    private static final String LIST_MOD_ATTR = "modified"; //NON-NLS
    private static final String LIST_USE_FOR_INGEST = "use_for_ingest"; //NON-NLS
    private static final String LIST_INGEST_MSGS = "ingest_messages"; //NON-NLS
    private static final String KEYWORD_EL = "keyword"; //NON-NLS
    private static final String KEYWORD_LITERAL_ATTR = "literal"; //NON-NLS
    private static final String KEYWORD_SELECTOR_ATTR = "selector"; //NON-NLS
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"; //NON-NLS
    private static final String ENCODING = "UTF-8"; //NON-NLS
    private static XmlKeywordListImportExport currentInstance = null;
    private final DateFormat dateFormatter;
    private String filePath;

    static synchronized XmlKeywordListImportExport getCurrent() {
        if (currentInstance == null) {
            currentInstance = new XmlKeywordListImportExport(CUR_LISTS_FILE);
        }
        return currentInstance;
    }

    /**
     * Constructor to obtain handle on other that the current keyword list (such
     * as for import or export)
     *
     * @param xmlFile xmlFile to obtain XmlKeywordSearchList handle on
     */
    XmlKeywordListImportExport(String xmlFile) {
        this.filePath = xmlFile;
        dateFormatter = new SimpleDateFormat(DATE_FORMAT);
    }

    public boolean save(List<KeywordList> keywordLists) {
        boolean success = false;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            for (KeywordList list : keywordLists) {
                Element rootEl = doc.createElement(ROOT_EL);
                doc.appendChild(rootEl);
                String listName = list.getName();
                String created = dateFormatter.format(list.getDateCreated());
                String modified = dateFormatter.format(list.getDateModified());
                String useForIngest = list.getUseForIngest().toString();
                String ingestMessages = list.getIngestMessages().toString();
                List<Keyword> keywords = list.getKeywords();

                Element listEl = doc.createElement(LIST_EL);
                listEl.setAttribute(LIST_NAME_ATTR, listName);
                listEl.setAttribute(LIST_CREATE_ATTR, created);
                listEl.setAttribute(LIST_MOD_ATTR, modified);
                listEl.setAttribute(LIST_USE_FOR_INGEST, useForIngest);
                listEl.setAttribute(LIST_INGEST_MSGS, ingestMessages);

                for (Keyword keyword : keywords) {
                    Element keywordEl = doc.createElement(KEYWORD_EL);
                    String literal = keyword.isLiteral() ? "true" : "false"; //NON-NLS
                    keywordEl.setAttribute(KEYWORD_LITERAL_ATTR, literal);
                    BlackboardAttribute.ATTRIBUTE_TYPE selectorType = keyword.getType();
                    if (selectorType != null) {
                        keywordEl.setAttribute(KEYWORD_SELECTOR_ATTR, selectorType.getLabel());
                    }
                    keywordEl.setTextContent(keyword.getQuery());
                    listEl.appendChild(keywordEl);
                }
                rootEl.appendChild(listEl);
            }

            success = XMLUtil.saveDoc(XmlKeywordListImportExport.class, filePath, ENCODING, doc);
        } catch (ParserConfigurationException e) {
            xmlListslogger.log(Level.SEVERE, "Error saving keyword list: can't initialize parser.", e); //NON-NLS
        }
        return success;
    }

    /**
     * load and parse XML, then dispose
     */
    public List<KeywordList> load() {
        final Document doc;
        doc = XMLUtil.loadDoc(XmlKeywordListImportExport.class, filePath);
        if (doc == null) {
            return null;
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            xmlListslogger.log(Level.SEVERE, "Error loading keyword list: invalid file format."); //NON-NLS
            return null;
        }
        try {
            NodeList listsNList = root.getElementsByTagName(LIST_EL);
            int numLists = listsNList.getLength();
            List<KeywordList> lists = new ArrayList<>();
            for (int i = 0; i < numLists; ++i) {
                Element listEl = (Element) listsNList.item(i);
                final String name = listEl.getAttribute(LIST_NAME_ATTR);
                final String created = listEl.getAttribute(LIST_CREATE_ATTR);
                final String modified = listEl.getAttribute(LIST_MOD_ATTR);

                //set these bools to true by default, if they don't exist in XML
                Boolean useForIngestBool;
                Boolean ingestMessagesBool;

                if (listEl.hasAttribute(LIST_USE_FOR_INGEST)) {
                    useForIngestBool = Boolean.parseBoolean(listEl.getAttribute(LIST_USE_FOR_INGEST));
                } else {
                    useForIngestBool = true;
                }

                if (listEl.hasAttribute(LIST_INGEST_MSGS)) {
                    ingestMessagesBool = Boolean.parseBoolean(listEl.getAttribute(LIST_INGEST_MSGS));
                } else {
                    ingestMessagesBool = true;
                }

                Date createdDate = dateFormatter.parse(created);
                Date modDate = dateFormatter.parse(modified);

                List<Keyword> words = new ArrayList<>();
                KeywordList list = new KeywordList(name, createdDate, modDate, useForIngestBool, ingestMessagesBool, words);

                //parse all words
                NodeList wordsNList = listEl.getElementsByTagName(KEYWORD_EL);
                final int numKeywords = wordsNList.getLength();
                for (int j = 0; j < numKeywords; ++j) {
                    Element wordEl = (Element) wordsNList.item(j);
                    String literal = wordEl.getAttribute(KEYWORD_LITERAL_ATTR);
                    boolean isLiteral = literal.equals("true"); //NON-NLS
                    Keyword keyword = new Keyword(wordEl.getTextContent(), isLiteral);
                    String selector = wordEl.getAttribute(KEYWORD_SELECTOR_ATTR);
                    if (!selector.equals("")) {
                        BlackboardAttribute.ATTRIBUTE_TYPE selectorType = BlackboardAttribute.ATTRIBUTE_TYPE.fromLabel(selector);
                        keyword.setType(selectorType);
                    }
                    words.add(keyword);

                }
                lists.add(list);
            }
            return lists;
        } catch (ParseException e) {
            //error parsing dates
            xmlListslogger.log(Level.SEVERE, "Error loading keyword list: can't parse dates.", e); //NON-NLS
            return null;
        }
    }
}
