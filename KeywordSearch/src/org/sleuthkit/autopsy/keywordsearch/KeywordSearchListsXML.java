/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.sleuthkit.autopsy.coreutils.AutopsyPropFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Manages reading and writing of keyword lists to user settings XML file keywords.xml
 * or to any file provided in constructor
 */
public class KeywordSearchListsXML extends KeywordSearchListsAbstract{

    private static final String ROOT_EL = "keyword_lists";
    private static final String LIST_EL = "keyword_list";
    private static final String LIST_NAME_ATTR = "name";
    private static final String LIST_CREATE_ATTR = "created";
    private static final String LIST_MOD_ATTR = "modified";
    private static final String LIST_USE_FOR_INGEST = "use_for_ingest";
    private static final String LIST_INGEST_MSGS = "ingest_messages";
    private static final String KEYWORD_EL = "keyword";
    private static final String KEYWORD_LITERAL_ATTR = "literal";
    private static final String KEYWORD_SELECTOR_ATTR = "selector";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String ENCODING = "UTF-8";
    private static final Logger logger = Logger.getLogger(KeywordSearchListsXML.class.getName());
    private DateFormat dateFormatter;

    

    /**
     * Constructor to obtain handle on other that the current keyword list
     * (such as for import or export)
     * @param xmlFile xmlFile to obtain KeywordSearchListsXML handle on
     */
    KeywordSearchListsXML(String xmlFile) {
        super(xmlFile);
        dateFormatter = new SimpleDateFormat(DATE_FORMAT);
    }
    
    
    /**
     * writes out current list replacing the last lists file
     */
    @Override
    public boolean save() {
        boolean success = false;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);

            for (String listName : theLists.keySet()) {
                if(listName.equals("IP Addresses") || listName.equals("Email Addresses") ||
                        listName.equals("Phone Numbers") || listName.equals("URLs"))
                    continue;
                KeywordSearchList list = theLists.get(listName);
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
                    String literal = keyword.isLiteral()?"true":"false";
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

            success = saveDoc(doc);
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving keyword list: can't initialize parser.", e);
        }
        return success;
    }

    /**
     * load and parse XML, then dispose
     */
    @Override
    public boolean load() {
        final Document doc = loadDoc();
        if (doc == null) {
            return false;
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Error loading keyword list: invalid file format.");
            return false;
        }
        try {
            NodeList listsNList = root.getElementsByTagName(LIST_EL);
            int numLists = listsNList.getLength();
            for (int i = 0; i < numLists; ++i) {
                Element listEl = (Element) listsNList.item(i);
                final String name = listEl.getAttribute(LIST_NAME_ATTR);
                final String created = listEl.getAttribute(LIST_CREATE_ATTR);
                final String modified = listEl.getAttribute(LIST_MOD_ATTR);
                
                //set these bools to true by default, if they don't exist in XML
                Boolean useForIngestBool;
                Boolean ingestMessagesBool; 
                
                if (listEl.hasAttribute(LIST_USE_FOR_INGEST) ) {
                    useForIngestBool = Boolean.parseBoolean(listEl.getAttribute(LIST_USE_FOR_INGEST));
                }
                else {
                    useForIngestBool = true;
                }

                if (listEl.hasAttribute(LIST_INGEST_MSGS)) {
                    ingestMessagesBool = Boolean.parseBoolean(listEl.getAttribute(LIST_INGEST_MSGS));
                }
                else {
                    ingestMessagesBool = true;
                }
                
                Date createdDate = dateFormatter.parse(created);
                Date modDate = dateFormatter.parse(modified);

                List<Keyword> words = new ArrayList<Keyword>();
                KeywordSearchList list = new KeywordSearchList(name, createdDate, modDate, useForIngestBool, ingestMessagesBool, words);

                //parse all words
                NodeList wordsNList = listEl.getElementsByTagName(KEYWORD_EL);
                final int numKeywords = wordsNList.getLength();
                for (int j = 0; j < numKeywords; ++j) {
                    Element wordEl = (Element) wordsNList.item(j);
                    String literal = wordEl.getAttribute(KEYWORD_LITERAL_ATTR);
                    boolean isLiteral = literal.equals("true");
                    Keyword keyword = new Keyword(wordEl.getTextContent(), isLiteral);
                    String selector = wordEl.getAttribute(KEYWORD_SELECTOR_ATTR);
                    if (! selector.equals("")) {
                        BlackboardAttribute.ATTRIBUTE_TYPE selectorType = BlackboardAttribute.ATTRIBUTE_TYPE.fromLabel(selector);
                        keyword.setType(selectorType);
                    }
                    words.add(keyword);
                    
                }
                theLists.put(name, list);
            }
        } catch (ParseException e) {
            //error parsing dates
            logger.log(Level.SEVERE, "Error loading keyword list: can't parse dates.", e);
            return false;
        }
        return true;
    }

    private Document loadDoc() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        Document ret = null;


        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(filePath));
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error loading keyword list: can't initialize parser.", e);

        } catch (SAXException e) {
            logger.log(Level.SEVERE, "Error loading keyword list: can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            logger.log(Level.SEVERE, "Error loading keyword list: can't read file.", e);

        }
        return ret;

    }

    private boolean saveDoc(final Document doc) {
        TransformerFactory xf = TransformerFactory.newInstance();
        xf.setAttribute("indent-number", new Integer(1));
        boolean success = false;
        try {
            Transformer xformer = xf.newTransformer();
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.ENCODING, ENCODING);
            xformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            xformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            File file = new File(filePath);
            FileOutputStream stream = new FileOutputStream(file);
            Result out = new StreamResult(new OutputStreamWriter(stream, ENCODING));
            xformer.transform(new DOMSource(doc), out);
            stream.flush();
            stream.close();
            success = true;

        } catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE, "Should not happen", e);
        } catch (TransformerConfigurationException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML", e);
        } catch (TransformerException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML", e);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML: cannot write to file: " + filePath, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML: cannot write to file: " + filePath, e);
        }
        return success;
    }
}
