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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import java.util.logging.Level;

/**
 * Keyword list saving, loading, and editing abstract class.
 */
abstract class KeywordSearchList {

    protected String filePath;
    Map<String, KeywordList> theLists; //the keyword data 
    protected static final Logger logger = Logger.getLogger(KeywordSearchList.class.getName());
    PropertyChangeSupport changeSupport; 
    protected List<String> lockedLists;

    KeywordSearchList(String filePath) {
        this.filePath = filePath;
        theLists = new LinkedHashMap<>();
        lockedLists = new ArrayList<>();
        changeSupport = new PropertyChangeSupport(this);
    }

    /**
     * Property change event support In events: For all of these enums, the old
     * value should be null, and the new value should be the keyword list name
     * string.
     */
    enum ListsEvt {

        LIST_ADDED, LIST_DELETED, LIST_UPDATED
    };

    enum LanguagesEvent {
        LANGUAGES_CHANGED, ENCODINGS_CHANGED
    }
    
    void fireLanguagesEvent(LanguagesEvent event) {
        try {
            changeSupport.firePropertyChange(event.toString(), null, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
        }        
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }
    
    private void prepopulateLists() {
        if (!theLists.isEmpty()) {
            return;
        }
        //phone number
        List<Keyword> phones = new ArrayList<>();
        phones.add(new Keyword("[(]{0,1}\\d\\d\\d[)]{0,1}[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)); //NON-NLS
        //phones.add(new Keyword("\\d{8,10}", false));
        //IP address
        List<Keyword> ips = new ArrayList<>();
        ips.add(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS));
        //email
        List<Keyword> emails = new ArrayList<>();
        emails.add(new Keyword("(?=.{8})[a-z0-9%+_-]+(?:\\.[a-z0-9%+_-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,4}(?<!\\.txt|\\.exe|\\.dll|\\.jpg|\\.xml)", //NON-NLS
                false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //emails.add(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", 
        //                       false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //URL
        List<Keyword> urls = new ArrayList<>();
        //urls.add(new Keyword("http://|https://|^www\\.", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        urls.add(new Keyword("((((ht|f)tp(s?))\\://)|www\\.)[a-zA-Z0-9\\-\\.]+\\.([a-zA-Z]{2,5})(\\:[0-9]+)*(/($|[a-zA-Z0-9\\.\\,\\;\\?\\'\\\\+&amp;%\\$#\\=~_\\-]+))*", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)); //NON-NLS

        //urls.add(new Keyword("ssh://", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));

        //disable messages for harcoded/locked lists
        String name;

        name = "Phone Numbers"; //NON-NLS
        lockedLists.add(name);
        addList(name, phones, false, false, true);

        name = "IP Addresses"; //NON-NLS
        lockedLists.add(name);
        addList(name, ips, false, false, true);

        name = "Email Addresses"; //NON-NLS
        lockedLists.add(name);
        addList(name, emails, true, false, true);

        name = "URLs"; //NON-NLS
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

    public List<KeywordList> getListsL() {
        List<KeywordList> ret = new ArrayList<>();
        for (KeywordList list : theLists.values()) {
            ret.add(list);
        }
        return ret;
    }

    public List<KeywordList> getListsL(boolean locked) {
        List<KeywordList> ret = new ArrayList<>();
        for (KeywordList list : theLists.values()) {
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
        return new ArrayList<>(theLists.keySet());
    }

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
            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, name);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.addList.errMsg1.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        } else {
            theLists.put(name, new KeywordList(name, curList.getDateCreated(), now, useForIngest, ingestMessages, newList, locked));
            replaced = true;

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, name);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.addList.errMsg2.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
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
        return addList(name, newList, true, true);
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
        boolean saved = save(true);
        if (saved) {
            for (KeywordList list : newLists) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_ADDED.toString(), null, list.getName());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                    MessageNotifyUtil.Notify.show(
                            NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                            NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.saveList.errMsg1.msg"),
                                                  MessageNotifyUtil.MessageType.ERROR);
                }
            }
            for (KeywordList over : overwritten) {
                try {
                    changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                    MessageNotifyUtil.Notify.show(
                            NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                            NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.saveList.errMsg2.msg"),
                            MessageNotifyUtil.MessageType.ERROR);
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
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.writeLists.errMsg1.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }

        for (KeywordList over : overwritten) {

            try {
                changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.toString(), null, over.getName());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.writeLists.errMsg2.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
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
        KeywordList delList = getList(name);
        if (delList != null && !delList.isLocked()) {
            theLists.remove(name);
        }

        try {
            changeSupport.firePropertyChange(ListsEvt.LIST_DELETED.toString(), null, name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(
                    NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.moduleErr"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchListsAbstract.deleteList.errMsg1.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
        
        return true;
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