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
import java.util.logging.Logger;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Manages reading and writing of keyword lists to user settings XML file keywords.xml
 */
public class KeywordSearchListsXML {

    private static final String ROOT_EL = "keyword_lists";
    private static final String LIST_EL = "keyword_list";
    private static final String LIST_NAME_ATTR = "name";
    private static final String LIST_CREATE_ATTR = "created";
    private static final String LIST_MOD_ATTR = "modified";
    private static final String KEYWORD_EL = "keyword";
    private static final String LISTS_FILE_NAME = "keywords.xml";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String ENCODING = "UTF-8";
    private String LISTS_FILE = AutopsyPropFile.getUserDirPath() + File.separator + LISTS_FILE_NAME;
    private static final Logger logger = Logger.getLogger(KeywordSearchListsXML.class.getName());
    
    Map<String, KeywordSearchList> theLists; //the keyword data
 
    static KeywordSearchListsXML theInstance = null;

    private KeywordSearchListsXML() {
    }

    static KeywordSearchListsXML getInstance() {
        if (theInstance == null) {
            theInstance = new KeywordSearchListsXML();
            theInstance.reload();
        }
        return theInstance;
    }

    /**
     * load the file or create new
     */
    public void reload() {
        boolean created = false;
        theLists = new LinkedHashMap<String, KeywordSearchList>();
        if (!this.listFileExists()) {
            //create new if it doesn't exist
            save();
            created = true;
        }

        if (!load() && !created) {
            //create new if failed to load
            save();
        }

    }

    /**
     * get all loaded keyword lists
     * @return List of keyword list objects
     */
    Map<String, KeywordSearchList> getLists() {
        return theLists;
    }
    
    /**
     * get list of all loaded keyword list names
     * @return List of keyword list names
     */
    List<String>getListNames() {
        return new ArrayList(theLists.keySet());
    }
    
    /**
     * get number of lists currently stored
     * @return number of lists currently stored
     */
    int getNumberLists() {
        return theLists.size();
    }

    /**
     * get list by name or null
     * @param name id of the list
     * @return keyword list representation
     */
    KeywordSearchList getList(String name) {
        return theLists.get(name);
    }

    /**
     * check if list with given name id exists
     * @param name id to check
     * @return true if list already exists or false otherwise
     */
    boolean listExists(String name) {
        return getList(name) != null;
    }

    /**
     * adds the new word list using name id
     * replacing old one if exists with the same name
     * requires following call to save() to make permanent changes
     * @param name the name of the new list or list to replace
     * @param newList list of keywords
     * @return true if old list was replaced
     */
    boolean addList(String name, List<String> newList) {
        boolean replaced = false;
        KeywordSearchList curList = getList(name);
        final Date now = new Date();
        if (curList == null) {
            theLists.put(name, new KeywordSearchList(name, now, now, newList));
        } else {
            theLists.put(name, new KeywordSearchList(name, curList.getDateCreated(), now, newList));
            replaced = true;
        }
        return replaced;
    }

    /**
     * writes out current list replacing the last lists file
     */
    boolean save() {
        boolean success = false;
        DateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT);
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);

            for (String listName : theLists.keySet()) {
                KeywordSearchList list = theLists.get(listName);
                String created = dateFormatter.format(list.getDateCreated());
                String modified = dateFormatter.format(list.getDateModified());
                List<String> keywords = list.getKeywords();

                Element listEl = doc.createElement(LIST_EL);
                listEl.setAttribute(LIST_NAME_ATTR, listName);
                listEl.setAttribute(LIST_CREATE_ATTR, created);
                listEl.setAttribute(LIST_MOD_ATTR, modified);

                for (String keyword : keywords) {
                    Element keywordEl = doc.createElement(KEYWORD_EL);
                    keywordEl.setTextContent(keyword);
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
    private boolean load() {
        final Document doc = loadDoc();
        if (doc == null) {
            return false;
        }
        DateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT);


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
                Date createdDate = dateFormatter.parse(created);
                Date modDate = dateFormatter.parse(modified);
                List<String> words = new ArrayList<String>();
                KeywordSearchList list = new KeywordSearchList(name, createdDate, modDate, words);

                //parse all words
                NodeList wordsNList = listEl.getElementsByTagName(KEYWORD_EL);
                final int numKeywords = wordsNList.getLength();
                for (int j = 0; j < numKeywords; ++j) {
                    Element wordEl = (Element) wordsNList.item(j);
                    words.add(wordEl.getTextContent());

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

    private boolean listFileExists() {
        File f = new File(LISTS_FILE);
        return f.exists() && f.canRead() && f.canWrite();
    }

    private Document loadDoc() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        Document ret = null;


        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(LISTS_FILE));
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
            Result out = new StreamResult(new OutputStreamWriter(new FileOutputStream(new File(LISTS_FILE)), ENCODING));
            xformer.transform(new DOMSource(doc), out);
            success = true;
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE, "Should not happen", e);
        } catch (TransformerConfigurationException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML", e);
        } catch (TransformerException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML", e);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML: cannot write to file: " + LISTS_FILE, e);
        }
        return success;
    }
}

/**
 * a representation of a single keyword list
 * created or loaded
 */
class KeywordSearchList {

    private String name;
    private Date created;
    private Date modified;
    private List<String> keywords;

    KeywordSearchList(String name, Date created, Date modified, List<String> keywords) {
        this.name = name;
        this.created = created;
        this.modified = modified;
        this.keywords = keywords;
    }

    String getName() {
        return name;
    }

    Date getDateCreated() {
        return created;
    }

    Date getDateModified() {
        return modified;
    }

    List<String> getKeywords() {
        return keywords;
    }
}
