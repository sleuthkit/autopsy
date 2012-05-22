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
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Manages reading and writing of keyword lists to user settings XML file keywords.xml
 * or to any file provided in constructor
 */
public class KeywordSearchListsXML {

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
    private static final String CUR_LISTS_FILE_NAME = "keywords.xml";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String ENCODING = "UTF-8";
    private static String CUR_LISTS_FILE = AutopsyPropFile.getUserDirPath() + File.separator + CUR_LISTS_FILE_NAME;
    private static final Logger logger = Logger.getLogger(KeywordSearchListsXML.class.getName());
    Map<String, KeywordSearchList> theLists; //the keyword data
    static KeywordSearchListsXML currentInstance = null;
    private String xmlFile;
    private DateFormat dateFormatter;

    //property support
    public enum ListsEvt {

        LIST_ADDED, LIST_DELETED, LIST_UPDATED
    };
    private PropertyChangeSupport changeSupport;

    /**
     * Constructor to obtain handle on other that the current keyword list
     * (such as for import or export)
     * @param xmlFile xmlFile to obtain KeywordSearchListsXML handle on
     */
    KeywordSearchListsXML(String xmlFile) {
        theLists = new LinkedHashMap<String, KeywordSearchList>();
        this.xmlFile = xmlFile;
        changeSupport = new PropertyChangeSupport(this);

        dateFormatter = new SimpleDateFormat(DATE_FORMAT);
    }
    
    private void prepopulateLists() {
        //phone number
        List<Keyword> phones = new ArrayList<Keyword>();
        phones.add(new Keyword("[(]{0,1}\\d\\d\\d[)]{0,1}[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER));
        //phones.add(new Keyword("\\d{8,10}", false));
        //IP address
        List<Keyword> ips = new ArrayList<Keyword>();
        ips.add(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS));
        //email
        List<Keyword> emails = new ArrayList<Keyword>();
        emails.add(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //URL
        List<Keyword> urls = new ArrayList<Keyword>();
        //urls.add(new Keyword("http://|https://|^www\\.", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        urls.add(new Keyword("((((ht|f)tp(s?))\\://)|www\\.)[a-zA-Z0-9\\-\\.]+\\.([a-zA-Z]{2,5})(\\:[0-9]+)*(/($|[a-zA-Z0-9\\.\\,\\;\\?\\'\\\\+&amp;%\\$#\\=~_\\-]+))*", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        
        //urls.add(new Keyword("ssh://", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        
        //disable messages for harcoded/locked lists
        addList("Phone Numbers", phones, true, false, true);
        addList("IP Addresses", ips, true, false, true);
        addList("Email Addresses", emails, true, false, true);
        addList("URLs", urls, true, false, true); 
    }

    /**
     * get instance for managing the current keyword list of the application
     */
    static KeywordSearchListsXML getCurrent() {
        if (currentInstance == null) {
            currentInstance = new KeywordSearchListsXML(CUR_LISTS_FILE);
            currentInstance.reload();
        }
        return currentInstance;
    }

    void addPropertyChangeListener(PropertyChangeListener l) {
        changeSupport.addPropertyChangeListener(l);
    }

    /**
     * load the file or create new
     */
    public void reload() {
        boolean created = false;

        theLists.clear();
        prepopulateLists();
        if (!this.listFileExists()) {
            //create new if it doesn't exist
            save();
            created = true;
        }

        //load, if fails to laod create new
        if (!load() && !created) {
            //create new if failed to load
            save();
        }


    }

    List<KeywordSearchList> getListsL() {
        List<KeywordSearchList> ret = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : theLists.values()) {
            ret.add(list);
        }
        return ret;
    }

    /**
     * get list of all loaded keyword list names
     * @return List of keyword list names
     */
    List<String> getListNames() {
        return new ArrayList<String>(theLists.keySet());
    }
    
    /**
     * return first list that contains the keyword
     * @param keyword
     * @return found list or null
     */
    KeywordSearchList getListWithKeyword(Keyword keyword) {
        KeywordSearchList found = null;
        for (KeywordSearchList list : theLists.values()) {
            if (list.hasKeyword(keyword)) {
                found = list;
                break;
            }
        }
        return found;
    }

    /**
     * return first list that contains the keyword
     * @param keyword
     * @return found list or null
     */
    KeywordSearchList getListWithKeyword(String keyword) {
        KeywordSearchList found = null;
        for (KeywordSearchList list : theLists.values()) {
            if (list.hasKeyword(keyword)) {
                found = list;
                break;
            }
        }
        return found;
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
     * @param name the name of the new list or list to replace
     * @param newList list of keywords
     * @param useForIngest should this list be used for ingest
     * @return true if old list was replaced
     */
    boolean addList(String name, List<Keyword> newList, boolean useForIngest, boolean ingestMessages, boolean locked) {
        boolean replaced = false;
        KeywordSearchList curList = getList(name);
        final Date now = new Date();
        if (curList == null) {
            theLists.put(name, new KeywordSearchList(name, now, now, useForIngest, ingestMessages, newList, locked));
            if(!locked)
                save();
            changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, name);
        } else {
            theLists.put(name, new KeywordSearchList(name, curList.getDateCreated(), now, useForIngest, ingestMessages, newList, locked));
            if(!locked)
                save();
            replaced = true;
            changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, name);
        }

        return replaced;
    }
    
    boolean addList(String name, List<Keyword> newList, boolean useForIngest, boolean ingestMessages) {
        KeywordSearchList curList = getList(name);
        if (curList == null) {
            return addList(name, newList, useForIngest, ingestMessages, false);
        } else {
            return addList(name, newList, curList.getUseForIngest(), ingestMessages, false);
        }
    }
    
    boolean addList(String name, List<Keyword> newList) {
        return addList(name, newList, true, true);
    }
    

    /**
     * write out multiple lists
     * @param lists
     * @return 
     */
    boolean writeLists(List<KeywordSearchList> lists) {
        int oldSize = this.getNumberLists();
        
        List<KeywordSearchList> overwritten = new ArrayList<KeywordSearchList>();
        List<KeywordSearchList> newLists = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : lists) {
            if (this.listExists(list.getName()))
                overwritten.add(list);
            else
                newLists.add(list);
            theLists.put(list.getName(), list);
        }
        boolean saved = save();
        if (saved) {
            for (KeywordSearchList list : newLists) {
                changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());
            }
            for (KeywordSearchList over : overwritten) {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
            }
        }
        return saved;
    }

    /**
     * delete list if exists and save new list
     * @param name of list to delete
     * @return true if deleted
     */
    boolean deleteList(String name) {
        boolean deleted = false;
        KeywordSearchList delList = getList(name);
        if (delList != null) {
            theLists.remove(name);
            deleted = save();
        }
        changeSupport.firePropertyChange(ListsEvt.LIST_DELETED.toString(), null, name);
        return deleted;

    }

    /**
     * writes out current list replacing the last lists file
     */
    private boolean save() {
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
                final String useForIngest = listEl.getAttribute(LIST_USE_FOR_INGEST);
                final String ingestMessages = listEl.getAttribute(LIST_INGEST_MSGS);
                
                Date createdDate = dateFormatter.parse(created);
                Date modDate = dateFormatter.parse(modified);
                Boolean useForIngestBool = Boolean.parseBoolean(useForIngest);
                Boolean ingestMessagesBool = Boolean.parseBoolean(ingestMessages);
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

    private boolean listFileExists() {
        File f = new File(xmlFile);
        return f.exists() && f.canRead() && f.canWrite();
    }

    private Document loadDoc() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        Document ret = null;


        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(xmlFile));
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
            File file = new File(xmlFile);
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
            logger.log(Level.SEVERE, "Error writing keyword lists XML: cannot write to file: " + xmlFile, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing keyword lists XML: cannot write to file: " + xmlFile, e);
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
    private Boolean useForIngest;
    private Boolean ingestMessages;
    private List<Keyword> keywords;
    private Boolean locked;

    KeywordSearchList(String name, Date created, Date modified, Boolean useForIngest, Boolean ingestMessages, List<Keyword> keywords, boolean locked) {
        this.name = name;
        this.created = created;
        this.modified = modified;
        this.useForIngest = useForIngest;
        this.ingestMessages = ingestMessages;
        this.keywords = keywords;
        this.locked = locked;
    }
    
    KeywordSearchList(String name, Date created, Date modified, Boolean useForIngest, Boolean ingestMessages, List<Keyword> keywords) {
        this(name, created, modified, useForIngest, ingestMessages, keywords, false);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KeywordSearchList other = (KeywordSearchList) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        return hash;
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
    
    Boolean getUseForIngest() {
        return useForIngest;
    }
    
    void setUseForIngest(boolean use) {
        this.useForIngest = use;
    }
    
    Boolean getIngestMessages() {
        return ingestMessages;
    }
    
    void setIngestMessages(boolean ingestMessages) {
        this.ingestMessages = ingestMessages;
    }

    List<Keyword> getKeywords() {
        return keywords;
    }
    
    boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }
    
     boolean hasKeyword(String keyword) {
        //note, this ignores isLiteral
         for (Keyword k : keywords) {
             if (k.getQuery().equals(keyword))
                 return true;
         }
         return false;
    }
    
    Boolean isLocked() {
        return locked;
    }
}
