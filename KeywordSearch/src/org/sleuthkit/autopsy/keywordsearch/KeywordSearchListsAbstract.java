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
abstract class KeywordSearchListsAbstract {

    protected String filePath;
    Map<String, KeywordList> theLists; //the keyword data 
    static KeywordSearchListsXML currentInstance = null; 
    private static final String CUR_LISTS_FILE_NAME = "keywords.xml"; // RJCTODO: This will go to the manager
    private static String CUR_LISTS_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_LISTS_FILE_NAME; // RJCTODO: This will go to the manager
    protected static final Logger logger = Logger.getLogger(KeywordSearchListsAbstract.class.getName());
    PropertyChangeSupport changeSupport; // RJCTODO: This will go to the manager, if needed, no listeners right now
    protected List<String> lockedLists; // RJCTODO: This will go to the manager, if needed

    KeywordSearchListsAbstract(String filePath) {
        this.filePath = filePath;
        theLists = new LinkedHashMap<>();
        lockedLists = new ArrayList<>();
        changeSupport = new PropertyChangeSupport(this);
    }

    // RJCTODO: There are no listeners
    // RJCTODO: For manager
    /**
     * Property change event support In events: For all of these enums, the old
     * value should be null, and the new value should be the keyword list name
     * string.
     */
    enum ListsEvt {

        LIST_ADDED, LIST_DELETED, LIST_UPDATED
    };

    // RJCTODO: For manager
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

    // RJCTODO: For manager
    // RJCTODO: There are no listeners
//    public void addPropertyChangeListener(PropertyChangeListener listener) {
//        changeSupport.addPropertyChangeListener(listener);
//    }
    private void prepopulateLists() {
        if (!theLists.isEmpty()) {
            return;
        }
        //phone number
        List<Keyword> phones = new ArrayList<>();
        phones.add(new Keyword("[(]{0,1}\\d\\d\\d[)]{0,1}[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER));
        //phones.add(new Keyword("\\d{8,10}", false));
        //IP address
        List<Keyword> ips = new ArrayList<>();
        ips.add(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS));
        //email
        List<Keyword> emails = new ArrayList<>();
        emails.add(new Keyword("(?=.{8})[a-z0-9%+_-]+(?:\\.[a-z0-9%+_-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,4}(?<!\\.txt|\\.exe|\\.dll|\\.jpg|\\.xml)",
                false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //emails.add(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", 
        //                       false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //URL
        List<Keyword> urls = new ArrayList<>();
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

    // RJCTODO: Manager, reader mixed
    // RJCTODO: This is only called by config type stuff to affect the global list
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
        List<String> toClear = new ArrayList<>();
        for (String list : theLists.keySet()) {
            if (theLists.get(list).isLocked() == false) {
                toClear.add(list);
            }
        }
        for (String clearList : toClear) {
            theLists.remove(clearList);
        }

        if (!listFileExists()) {
            //create new if it doesn't exist
            save();
            created = true;
        }

        //load, if fails to load create new
        if (!load() && !created) {
            //create new if failed to load
            save();
        }
    }

    // RJCTODO: Need a manager and reader version of getting lists
    public List<KeywordList> getListsL() {
        List<KeywordList> ret = new ArrayList<>();
        for (KeywordList list : theLists.values()) {
            ret.add(list);
        }
        return ret;
    }

    // RJCTODO: Need a manager of getting lists
    // RJCTODO: There is one client, KeywordSearchEditListPanel, fetching unlocked lists
    public List<KeywordList> getListsL(boolean locked) {
        List<KeywordList> ret = new ArrayList<>();
        for (KeywordList list : theLists.values()) {
            if (list.isLocked().equals(locked)) {
                ret.add(list);
            }
        }
        return ret;
    }

    // RJCTODO: Used by KeywordSearchListsManagementPanel; for manager, since global list affected
    /**
     * Get list names of all loaded keyword list names
     *
     * @return List of keyword list names
     */
    public List<String> getListNames() {
        return new ArrayList<>(theLists.keySet());
    }

    // RJCTODO: Used by KeywordSearchListsManagementPanel; for manager, since global list affected
    /**
     * Get list names of all locked or unlocked loaded keyword list names
     *
     * @param locked true if look for locked lists, false otherwise
     * @return List of keyword list names
     */
    public List<String> getListNames(boolean locked) {
        ArrayList<String> lists = new ArrayList<>();
        for (String listName : theLists.keySet()) {
            KeywordList list = theLists.get(listName);
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
    public KeywordList getListWithKeyword(String keyword) {
        KeywordList found = null;
        for (KeywordList list : theLists.values()) {
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
            KeywordList list = theLists.get(listName);
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
    public KeywordList getList(String name) {
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
        KeywordList curList = getList(name);
        final Date now = new Date();

        if (curList == null) {
            theLists.put(name, new KeywordList(name, now, now, useForIngest, ingestMessages, newList, locked));
//            if (!locked) {
//                save();
//            }

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, name);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        } else {
            theLists.put(name, new KeywordList(name, curList.getDateCreated(), now, useForIngest, ingestMessages, newList, locked));
//            if (!locked) {
//                save();
//            }
            replaced = true;

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, name);
            } catch (Exception e) {
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

    boolean addList(KeywordList list) {
        return addList(list.getName(), list.getKeywords(), list.getUseForIngest(), list.getIngestMessages(), list.isLocked());
    }

    /**
     * save multiple lists
     *
     * @param lists
     * @return
     */
    boolean saveLists(List<KeywordList> lists) {
        int oldSize = this.getNumberLists();

        List<KeywordList> overwritten = new ArrayList<KeywordList>();
        List<KeywordList> newLists = new ArrayList<KeywordList>();
        for (KeywordList list : lists) {
            if (this.listExists(list.getName())) {
                overwritten.add(list);
            } else {
                newLists.add(list);
            }
            theLists.put(list.getName(), list);
        }
        boolean saved = save(true);
        if (saved) {
            for (KeywordList list : newLists) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                    MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
                }
            }
            for (KeywordList over : overwritten) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
                } catch (Exception e) {
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
    boolean writeLists(List<KeywordList> lists) {
        List<KeywordList> overwritten = new ArrayList<>();
        List<KeywordList> newLists = new ArrayList<>();
        for (KeywordList list : lists) {
            if (this.listExists(list.getName())) {
                overwritten.add(list);
            } else {
                newLists.add(list);
            }
            theLists.put(list.getName(), list);
        }

        for (KeywordList list : newLists) {

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstractr listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }

        for (KeywordList over : overwritten) {

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }

        return true;
    }

    // RJCTODO: For manager
    /**
     * delete list if exists and save new list // RJCTODO: What new list? Nothing is saved (liar!)
     *
     * @param name of list to delete
     * @return true if deleted
     */
    boolean deleteList(String name) {
        KeywordList delList = getList(name);
        if (delList != null && !delList.isLocked()) {
            theLists.remove(name);
        }

        try {
            changeSupport.firePropertyChange(ListsEvt.LIST_DELETED.toString(), null, name); // RJCTODO: Always fired (liar!)
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to KeywordSearchListsAbstract updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
        
        return true; // RJCTODO: LOL, reports that it always succeeds (liar!)
    }

    /**
     * writes out current list replacing the last lists file
     */
    public abstract boolean save();

    /**
     * writes out current list replacing the last lists file
     *
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

    public void setUseForIngest(String key, boolean flag) {
        theLists.get(key).setUseForIngest(flag);
    }
}