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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import java.util.logging.Level;

/**
 * Keyword list saving, loading, and editing abstract class.
 */
public abstract class KeywordSearchListsAbstract {

    protected String filePath;
    Map<String, KeywordSearchList> theLists; //the keyword data
    static KeywordSearchListsXML currentInstance = null;
    private static final String CUR_LISTS_FILE_NAME = "keywords.xml";
    private static String CUR_LISTS_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_LISTS_FILE_NAME;
    protected static final Logger logger = Logger.getLogger(KeywordSearchListsAbstract.class.getName());
    PropertyChangeSupport changeSupport;
    protected List<String> lockedLists;

    public KeywordSearchListsAbstract(String filePath) {
        this.filePath = filePath;
        theLists = new LinkedHashMap<String, KeywordSearchList>();
        lockedLists = new ArrayList<String>();
        changeSupport = new PropertyChangeSupport(this);
    }

    /**
     * Property change event support
     * In events: For all of these enums, the old value should be null, and
     *  the new value should be the keyword list name string.
     */
    public enum ListsEvt {

        LIST_ADDED, LIST_DELETED, LIST_UPDATED
    };

    /**
     * get instance for managing the current keyword list of the application
     */
    public static KeywordSearchListsXML getCurrent() {
        if (currentInstance == null) {
            currentInstance = new KeywordSearchListsXML(CUR_LISTS_FILE);
            currentInstance.reload();
        }
        return currentInstance;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    private void prepopulateLists() {
        if (! theLists.isEmpty()) {
            return;
        }
        //phone number
        List<Keyword> phones = new ArrayList<Keyword>();
        phones.add(new Keyword("[(]{0,1}\\d\\d\\d[)]{0,1}[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER));
        //phones.add(new Keyword("\\d{8,10}", false));
        //IP address
        List<Keyword> ips = new ArrayList<Keyword>();
        ips.add(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS));
        //email
        List<Keyword> emails = new ArrayList<Keyword>();
        emails.add(new Keyword("(?=.{8})[a-z0-9%+_-]+(?:\\.[a-z0-9%+_-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,4}(?<!\\.txt|\\.exe|\\.dll|\\.jpg|\\.xml)", 
                               false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL)); 
        //emails.add(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", 
        //                       false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //URL
        List<Keyword> urls = new ArrayList<Keyword>();
        //urls.add(new Keyword("http://|https://|^www\\.", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        urls.add(new Keyword("((((ht|f)tp(s?))\\://)|www\\.)[a-zA-Z0-9\\-\\.]+\\.([a-zA-Z]{2,5})(\\:[0-9]+)*(/($|[a-zA-Z0-9\\.\\,\\;\\?\\'\\\\+&amp;%\\$#\\=~_\\-]+))*", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));

        //urls.add(new Keyword("ssh://", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));

        //disable messages for harcoded/locked lists
        String name;
        
        name = "Phone Numbers";
        lockedLists.add(name);
        addList(name, phones, false, false, true);
        
        name = "IP Addresses";
        lockedLists.add(name);
        addList(name, ips, false, false, true);
        
        name = "Email Addresses";
        lockedLists.add(name);
        addList(name, emails, true, false, true);
        
        name = "URLs";
        lockedLists.add(name);
        addList(name, urls, false, false, true);
    }

    /**
     * load the file or create new
     */
    public void reload() {
        boolean created = false;

        //theLists.clear();
        //populate only the first time
        prepopulateLists();
        
        //reset all the lists other than locked lists (we don't save them to XML)
        //we want to preserve state of locked lists
        List<String> toClear = new ArrayList<String>();
        for (String list : theLists.keySet()) {
            if (theLists.get(list).isLocked() == false) {
                toClear.add(list);
            }
        }
        for (String clearList : toClear) {
            theLists.remove(clearList);
        } 
        
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

    public List<KeywordSearchList> getListsL() {
        List<KeywordSearchList> ret = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : theLists.values()) {
            ret.add(list);
        }
        return ret;
    }

    public List<KeywordSearchList> getListsL(boolean locked) {
        List<KeywordSearchList> ret = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : theLists.values()) {
            if (list.isLocked().equals(locked)) {
                ret.add(list);
            }
        }
        return ret;
    }

    /**
     * Get list names of all loaded keyword list names
     *
     * @return List of keyword list names
     */
    public List<String> getListNames() {
        return new ArrayList<String>(theLists.keySet());
    }

    /**
     * Get list names of all locked or unlocked loaded keyword list names
     *
     * @param locked true if look for locked lists, false otherwise
     * @return List of keyword list names
     */
    public List<String> getListNames(boolean locked) {
        ArrayList<String> lists = new ArrayList<String>();
        for (String listName : theLists.keySet()) {
            KeywordSearchList list = theLists.get(listName);
            if (locked == list.isLocked()) {
                lists.add(listName);
            }
        }

        return lists;
    }

    /**
     * return first list that contains the keyword
     *
     * @param keyword
     * @return found list or null
     */
    public KeywordSearchList getListWithKeyword(Keyword keyword) {
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
     *
     * @param keyword
     * @return found list or null
     */
    public KeywordSearchList getListWithKeyword(String keyword) {
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
     *
     * @return number of lists currently stored
     */
    int getNumberLists() {
        return theLists.size();
    }

    /**
     * get number of unlocked or locked lists currently stored
     *
     * @param locked true if look for locked lists, false otherwise
     * @return number of unlocked lists currently stored
     */
    public int getNumberLists(boolean locked) {
        int numLists = 0;
        for (String listName : theLists.keySet()) {
            KeywordSearchList list = theLists.get(listName);
            if (locked == list.isLocked()) {
                ++numLists;
            }
        }
        return numLists;
    }

    /**
     * get list by name or null
     *
     * @param name id of the list
     * @return keyword list representation
     */
    public KeywordSearchList getList(String name) {
        return theLists.get(name);
    }

    /**
     * check if list with given name id exists
     *
     * @param name id to check
     * @return true if list already exists or false otherwise
     */
    boolean listExists(String name) {
        return getList(name) != null;
    }

    /**
     * adds the new word list using name id replacing old one if exists with the
     * same name
     *
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
//            if (!locked) {
//                save();
//            }
           
            try {
                 changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, name);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        } else {
            theLists.put(name, new KeywordSearchList(name, curList.getDateCreated(), now, useForIngest, ingestMessages, newList, locked));
//            if (!locked) {
//                save();
//            }
            replaced = true;
            
            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, name);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }
        
        return replaced;
    }

    boolean addList(String name, List<Keyword> newList, boolean useForIngest, boolean ingestMessages) {
        //make sure that the list is readded as a locked/built in list 
        boolean isLocked = this.lockedLists.contains(name);
        return addList(name, newList, useForIngest, ingestMessages, isLocked);
    }

    boolean addList(String name, List<Keyword> newList) {
        boolean isLocked = this.lockedLists.contains(name);
        return addList(name, newList, true, isLocked);
    }

    boolean addList(KeywordSearchList list) {
        return addList(list.getName(), list.getKeywords(), list.getUseForIngest(), list.getIngestMessages(), list.isLocked());
    }

    /**
     * save multiple lists
     *
     * @param lists
     * @return
     */
    boolean saveLists(List<KeywordSearchList> lists) {
        int oldSize = this.getNumberLists();

        List<KeywordSearchList> overwritten = new ArrayList<KeywordSearchList>();
        List<KeywordSearchList> newLists = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : lists) {
            if (this.listExists(list.getName())) {
                overwritten.add(list);
            } else {
                newLists.add(list);
            }
            theLists.put(list.getName(), list);
        }
        boolean saved = save(true);
        if (saved) {
            for (KeywordSearchList list : newLists) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());
                }
                catch (Exception e) {
                    logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                    MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
                }
            }
            for (KeywordSearchList over : overwritten) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
                }
                catch (Exception e) {
                    logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                    MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
                }
            }
        }

        return saved;
    }

    /**
     * write out multiple lists
     *
     * @param lists
     * @return
     */
    boolean writeLists(List<KeywordSearchList> lists) {
        int oldSize = this.getNumberLists();

        List<KeywordSearchList> overwritten = new ArrayList<KeywordSearchList>();
        List<KeywordSearchList> newLists = new ArrayList<KeywordSearchList>();
        for (KeywordSearchList list : lists) {
            if (this.listExists(list.getName())) {
                overwritten.add(list);
            } else {
                newLists.add(list);
            }
            theLists.put(list.getName(), list);
        }
        //boolean saved = save();

        for (KeywordSearchList list : newLists) {
             
            try {
                 changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());  
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstractr listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }
        for (KeywordSearchList over : overwritten) {
            
            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }

        return true;
    }

    /**
     * delete list if exists and save new list
     *
     * @param name of list to delete
     * @return true if deleted
     */
    boolean deleteList(String name) {
        boolean deleted = false;
        KeywordSearchList delList = getList(name);
        if (delList != null && !delList.isLocked()) {
            theLists.remove(name);
            //deleted = save();
        }
        
            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_DELETED.toString(), null, name);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        return true;

    }

    /**
     * writes out current list replacing the last lists file
     */
    public abstract boolean save();
    
    /**
     * writes out current list replacing the last lists file
     * @param isExport true is this save operation is an export and not a 'Save
     * As'
     */
    public abstract boolean save(boolean isExport);

    /**
     * load and parse List, then dispose
     */
    public abstract boolean load();

    private boolean listFileExists() {
        File f = new File(filePath);
        return f.exists() && f.canRead() && f.canWrite();
    }
    
    public void setUseForIngest(String key, boolean flag)
    {
        theLists.get(key).setUseForIngest(flag);
    }
    /**
     * a representation of a single keyword list created or loaded
     */
    public class KeywordSearchList {

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

        public String getName() {
            return name;
        }

        public Date getDateCreated() {
            return created;
        }

        public Date getDateModified() {
            return modified;
        }

        public Boolean getUseForIngest() {
            return useForIngest;
        }

        void setUseForIngest(boolean use) {
            this.useForIngest = use;
        }

        public Boolean getIngestMessages() {
            return ingestMessages;
        }

        void setIngestMessages(boolean ingestMessages) {
            this.ingestMessages = ingestMessages;
        }

        public List<Keyword> getKeywords() {
            return keywords;
        }

        boolean hasKeyword(Keyword keyword) {
            return keywords.contains(keyword);
        }

        public boolean hasKeyword(String keyword) {
            //note, this ignores isLiteral
            for (Keyword k : keywords) {
                if (k.getQuery().equals(keyword)) {
                    return true;
                }
            }
            return false;
        }

        public Boolean isLocked() {
            return locked;
        }
    }
}