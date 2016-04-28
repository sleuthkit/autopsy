/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchIngestModule.UpdateFrequency;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Class for managing the settings for keyword search, including both the
 * keyword lists and the properties for keyword search.
 */
class KeywordSearchSettingsManager {

    private KeywordSearchSettings settings = new KeywordSearchSettings();

    private static final String CUR_LISTS_FILE_NAME = "keywords.settings";     //NON-NLS
    private static final String CUR_LISTS_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_LISTS_FILE_NAME;
    private static final Logger logger = Logger.getLogger(KeywordSearchSettingsManager.class.getName());
    private static KeywordSearchSettingsManager instance;
    private PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

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

    /**
     * Constructs a KeywordSearchSettingsManager.
     *
     * @throws KeywordSearchSettingsManagerException When the settings cannot be
     *                                               read from disk.
     */
    private KeywordSearchSettingsManager() throws KeywordSearchSettingsManagerException {
        readSettings();
    }

    /**
     * Gets the singleton instance of the KeywordSearchSettingsManager.
     *
     * @return The singleton KeywordSearchSettingsManager.
     *
     * @throws KeywordSearchSettingsManagerException When the settings cannot be
     *                                               read from disk.
     */
    static synchronized KeywordSearchSettingsManager getInstance() throws KeywordSearchSettingsManagerException {
        if (instance == null) {
            instance = new KeywordSearchSettingsManager();
        }
        return instance;
    }

    /**
     * Writes the settings to disk.
     *
     * @throwsKeywordSearchSettingsManagerException If the settings could not be
     * written to disk.
     */
    private void writeSettings() throws KeywordSearchSettingsManagerException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(CUR_LISTS_FILE))) {
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new KeywordSearchSettingsManagerException("Couldn't keyword search settings.", ex);
        }
    }

    /**
     * Reads in the settings from disk, in the following order of priority:
     * Serialized settings, XML/Props files, no settings (loads defaults).
     *
     * @throws KeywordSearchSettingsManagerException If the settings cannot be
     *                                               read from disk.
     */
    private void readSettings() throws KeywordSearchSettingsManagerException {
        File serializedDefs = new File(CUR_LISTS_FILE);
        if (serializedDefs.exists()) {
            try {
                try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedDefs))) {
                    settings = (KeywordSearchSettings) in.readObject();
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new KeywordSearchSettingsManagerException("Couldn't read keyword search settings.", ex);
            }
        } else {
            this.loadDefaultSettings();
            XmlKeywordSearchList xmlReader = XmlKeywordSearchList.getCurrent();
            List<KeywordList> newKeywordLists = new ArrayList<>();
            List<KeywordList> keywordLists = this.settings.getKeywordLists();
            newKeywordLists.addAll(keywordLists);
            List<KeywordList> xmlLists = xmlReader.load();
            if (xmlLists != null) {
                newKeywordLists.addAll(xmlLists);
            }
            this.settings.setKeywordLists(newKeywordLists);
            this.writeSettings();
        }
    }

    private void loadDefaultSettings() {
        List<KeywordList> keywordLists = this.prepopulateLists();
        this.settings.setKeywordLists(keywordLists);
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_NSRL, "SkipKnown")) { //NON-NLS
            settings.setSkipKnown(true);
        }
        //setting default Update Frequency
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, "UpdateFrequency")) { //NON-NLS
            settings.setUpdateFrequency(UpdateFrequency.DEFAULT);
        }
        //setting default Extract UTF8
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, TextExtractor.ExtractOptions.EXTRACT_UTF8.toString())) {
            settings.setStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString(), Boolean.TRUE.toString());
        }
        //setting default Extract UTF16
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, TextExtractor.ExtractOptions.EXTRACT_UTF16.toString())) {
            settings.setStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString(), Boolean.TRUE.toString());
        }
        //setting default Latin-1 Script
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_SCRIPTS, SCRIPT.LATIN_1.name())) {
            settings.setStringExtractOption(SCRIPT.LATIN_1.name(), Boolean.toString(true));
        }
    }

    /**
     * Creates the default lists to be loaded upon creating the settings.
     *
     * @return The default lists.
     */
    List<KeywordList> prepopulateLists() {
        List<KeywordList> keywordLists = new ArrayList<>();
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
        keywordLists.add(new KeywordList(name, new Date(), new Date(), false, false, phones, true));

        name = "IP Addresses"; //NON-NLS
        keywordLists.add(new KeywordList(name, new Date(), new Date(), false, false, ips, true));

        name = "Email Addresses"; //NON-NLS
        keywordLists.add(new KeywordList(name, new Date(), new Date(), false, false, emails, true));

        name = "URLs"; //NON-NLS
        keywordLists.add(new KeywordList(name, new Date(), new Date(), false, false, urls, true));

        return keywordLists;
    }

    /**
     * Adds the given property change listener
     *
     * @param listener The listener to add
     */
    void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes the given property change listener
     *
     * @param listener The listener to remove
     */
    void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Fires the given language change event so all listeners know that there
     * was a change to the language setting.
     *
     * @param event The event to fire.
     */
    void fireLanguagesEvent(LanguagesEvent event) {
        try {
            changeSupport.firePropertyChange(event.toString(), null, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearchListsAbstract listener threw exception", e); //NON-NLS
        }
    }

    /**
     * Adds the given keyword list to the settings and then saves it.
     *
     * @param list The list to add to the settings.
     *
     * @throws KeywordSearchSettingsManagerException If the settings could not
     *                                               be written or there is
     *                                               already a keyword list of
     *                                               the given name.
     */
    synchronized void addList(KeywordList list) throws KeywordSearchSettingsManagerException {
        List<KeywordList> oldKeywordLists = settings.getKeywordLists();
        List<KeywordList> newKeywordLists = new ArrayList<>();
        newKeywordLists.addAll(oldKeywordLists);

        for (int i = 0; i < newKeywordLists.size(); i++) {
            if (newKeywordLists.get(i).getName().equals(list.getName())) {
                throw new KeywordSearchSettingsManagerException("There is already a keyword list with the same name as the given list.");
            }
        }
        newKeywordLists.add(list);
        settings.setKeywordLists(newKeywordLists);
        try {
            this.writeSettings();
            changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.name(), null, settings.getKeywordLists());
        } catch (KeywordSearchSettingsManagerException ex) {
            this.settings.setKeywordLists(oldKeywordLists);
            throw ex;
        }
    }

    /**
     * Removes the given keyword list from the settings.
     *
     * @param list The keyword list to remove.
     *
     * @throws KeywordSearchSettingsManagerException If the settings could not
     *                                               be written or there is no
     *                                               keyword list that matches
     *                                               the given list.
     */
    synchronized void removeList(KeywordList list) throws KeywordSearchSettingsManagerException {
        List<KeywordList> oldKeywordLists = settings.getKeywordLists();
        List<KeywordList> newKeywordLists = new ArrayList<>();
        newKeywordLists.addAll(oldKeywordLists);
        for (int i = 0; i < newKeywordLists.size(); i++) {
            if (newKeywordLists.get(i).getName().equals(list.getName())) {
                newKeywordLists.remove(i);
                i = newKeywordLists.size();
            } else if (i == newKeywordLists.size() - 1) {
                throw new KeywordSearchSettingsManagerException("There is no keyword list that matches the given keyword list.");
            }
        }
        settings.setKeywordLists(newKeywordLists);
        try {
            this.writeSettings();
            changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.name(), null, settings.getKeywordLists());
        } catch (KeywordSearchSettingsManagerException ex) {
            this.settings.setKeywordLists(oldKeywordLists);
            throw ex;
        }
    }

    /**
     * Updates the keyword list with the same name as the given list.
     *
     * @param list The list to update to.
     *
     * @throws KeywordSearchSettingsManagerException If there is no list with
     *                                               the same name as the given
     *                                               list or if the settings
     *                                               could not be written.
     */
    synchronized void updateList(KeywordList list) throws KeywordSearchSettingsManagerException {
        List<KeywordList> oldKeywordLists = settings.getKeywordLists();
        List<KeywordList> newKeywordLists = new ArrayList<>();
        newKeywordLists.addAll(oldKeywordLists);
        for (int i = 0; i < newKeywordLists.size(); i++) {
            if (newKeywordLists.get(i).getName().equals(list.getName())) {
                newKeywordLists.remove(i);
                i = newKeywordLists.size();
            }
            if (i == newKeywordLists.size() - 1) {
                throw new KeywordSearchSettingsManagerException("No keyword list with same name as given keyword list, couldn't update.");
            }
        }
        newKeywordLists.add(list);
        settings.setKeywordLists(newKeywordLists);
        try {
            this.writeSettings();
            changeSupport.firePropertyChange(ListsEvt.LIST_UPDATED.name(), null, settings.getKeywordLists());
        } catch (KeywordSearchSettingsManagerException ex) {
            this.settings.setKeywordLists(oldKeywordLists);
            throw ex;
        }
    }

    /**
     * Gets the update frequency
     *
     * @return KeywordSearchIngestModule's update frequency
     */
    synchronized KeywordSearchIngestModule.UpdateFrequency getUpdateFrequency() {
        return settings.getUpdateFrequency();
    }

    /**
     * Sets the update frequency and writes the settings
     *
     * @param freq Sets KeywordSearchIngestModule to this value.
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setUpdateFrequency(KeywordSearchIngestModule.UpdateFrequency freq) throws KeywordSearchSettingsManagerException {

        UpdateFrequency oldFreq = this.settings.getUpdateFrequency();
        try {
            settings.setUpdateFrequency(freq);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            settings.setUpdateFrequency(oldFreq);
            throw ex;

        }
    }

    /**
     * Sets whether or not to skip adding known good files to the search during
     * index.
     *
     * @param skip Whether or not to skip.
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setSkipKnown(boolean skip) throws KeywordSearchSettingsManagerException {

        boolean oldSkipKnown = settings.getSkipKnown();
        try {
            settings.setSkipKnown(skip);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            settings.setSkipKnown(oldSkipKnown);
            throw ex;
        }
    }

    /**
     * Gets the setting for whether or not this ingest is skipping adding known
     * good files to the index.
     *
     * @return skip setting
     */
    synchronized boolean getSkipKnown() {
        return settings.getSkipKnown();
    }

    /**
     *
     */
    /**
     * Sets what scripts to extract during ingest
     *
     * @param scripts List of scripts to extract
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setStringExtractScripts(List<SCRIPT> scripts) throws KeywordSearchSettingsManagerException {
        List<SCRIPT> oldScripts = settings.getStringExtractScripts();
        try {
            settings.setStringExtractScripts(scripts);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            settings.setStringExtractScripts(oldScripts);
            throw ex;
        }
    }

    /**
     * Set / override string extract option.
     *
     * @param key option name to set
     * @param val option value to set
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setStringExtractOption(String key, String val) throws KeywordSearchSettingsManagerException {
        String oldVal = this.settings.getStringExtractOption(key);
        try {
            settings.setStringExtractOption(key, val);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            settings.setStringExtractOption(key, oldVal);
            throw ex;
        }
    }

    /**
     * Sets the show snippets setting and writes it.
     *
     * @param showSnippets The new show snippet setting.
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setShowSnippets(boolean showSnippets) throws KeywordSearchSettingsManagerException {
        boolean oldShowSnippets = this.settings.getShowSnippets();
        try {
            settings.setShowSnippets(showSnippets);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            settings.setShowSnippets(oldShowSnippets);
            throw ex;
        }
    }

    /**
     * Gets the show snippets setting.
     *
     * @return The show snippets setting.
     */
    synchronized boolean getShowSnippets() {
        return settings.getShowSnippets();
    }

    /**
     * Gets the currently set scripts to use.
     *
     * @return The list of currently used script.
     */
    synchronized List<StringExtract.StringExtractUnicodeTable.SCRIPT> getStringExtractScripts() {
        return this.settings.getStringExtractScripts();
    }

    /**
     * Get string extract option for the key.
     *
     * @param key The option name
     *
     * @return The option string value, or empty string if the option is not
     *         set.
     */
    synchronized String getStringExtractOption(String key) {
        return this.settings.getStringExtractOption(key);
    }

    /**
     * Gets the map of string extract options.
     *
     * @return The map of string extract options.
     */
    synchronized Map<String, String> getStringExtractOptions() {
        return this.settings.getStringExtractOptions();
    }

    /**
     * Gets the list of keyword lists.
     *
     * @return The list of keyword lists.
     */
    synchronized List<KeywordList> getKeywordLists() {
        return this.settings.getKeywordLists();
    }

    /**
     * Sets the keyword lists and writes it.
     *
     * @param keywordLists The new keyword lists.
     *
     * @throws KeywordSearchSettingsManagerException If the setting could not be
     *                                               written.
     */
    synchronized void setKeywordLists(List<KeywordList> keywordLists) throws KeywordSearchSettingsManagerException {
        List<KeywordList> oldKeywordList = new ArrayList<>();
        try {
            oldKeywordList = settings.getKeywordLists();
            this.settings.setKeywordLists(keywordLists);
            this.writeSettings();
        } catch (KeywordSearchSettingsManagerException ex) {
            this.settings.setKeywordLists(oldKeywordList);
            throw ex;
        }
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined file types manager.
     */
    static class KeywordSearchSettingsManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        KeywordSearchSettingsManagerException(String message) {
            super(message);
        }

        KeywordSearchSettingsManagerException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}
