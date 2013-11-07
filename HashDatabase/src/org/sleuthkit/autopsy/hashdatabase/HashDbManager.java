/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.hashdatabase.HashDb.KnownFilesType;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class is a singleton that manages the configuration of the hash databases
 * that serve as hash sets for the identification of known files, known good files, 
 * and known bad files. 
 */
public class HashDbManager {
    private static final String ROOT_EL = "hash_sets";
    private static final String SET_EL = "hash_set";
    private static final String SET_NAME_ATTR = "name";
    private static final String SET_TYPE_ATTR = "type"; 
    private static final String SET_USE_FOR_INGEST_ATTR = "use_for_ingest";
    private static final String SET_SHOW_INBOX_MESSAGES = "show_inbox_messages";
    private static final String PATH_EL = "hash_set_path";
    private static final String PATH_NUMBER_ATTR = "number";
    private static final String CUR_HASHSETS_FILE_NAME = "hashsets.xml";
    private static final String XSDFILE = "HashsetsSchema.xsd";
    private static final String ENCODING = "UTF-8";
    private static final String SET_CALC = "hash_calculate";
    private static final String SET_VALUE = "value";
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());
    private static HashDbManager instance;
    private String xmlFile = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_HASHSETS_FILE_NAME;
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private HashDb nsrlHashSet;
    private boolean alwaysCalculateHashes;
        
    /**
     * Gets the singleton instance of this class.
     */
    public static synchronized HashDbManager getInstance() {
        if (instance == null) {
            instance = new HashDbManager();
        }
        return instance;
    }

    private HashDbManager() {
        if (hashSetsConfigurationFileExists()) {
            readHashSetsConfigurationFromDisk();            
        }
    }
     
    /**
     * Adds a hash set to the configuration as the designated National Software
     * Reference Library (NSRL) hash set. Assumes that the hash set previously 
     * designated as NSRL set, if any, is not being indexed. Does not save the 
     * configuration.
     */
    public void setNSRLHashSet(HashDb set) {
        if (nsrlHashSet != null) {
            // RJCTODO: When the closeHashDatabase() API exists, close the existing database
        }
        nsrlHashSet = set;
    }

    /** 
     * Gets the hash set from the configuration, if any, that is designated as 
     * the National Software Reference Library (NSRL) hash set.
     * @return A HashDb object representing the hash set or null.
     */
    public HashDb getNSRLHashSet() {
        return nsrlHashSet;
    }

    /** 
     * Removes the hash set designated as the National Software Reference 
     * Library (NSRL) hash set from the configuration. Does not save the 
     * configuration.
     */
    public void removeNSRLHashSet() {
        if (nsrlHashSet != null) {
            // RJCTODO: When the closeHashDatabase() API exists, close the existing database
        }
        nsrlHashSet = null;
    }    
    
    /**
     * Adds a hash set to the configuration as a known bad files hash set. Does 
     * not check for duplication of sets and does not save the configuration.
     */
    public void addKnownBadHashSet(HashDb set) {
        knownBadHashSets.add(set);
    }        
        
    /** 
     * Gets the configured known bad files hash sets.
     * @return A list, possibly empty, of HashDb objects.
     */
    public List<HashDb> getKnownBadHashSets() {
        return Collections.unmodifiableList(knownBadHashSets);
    }
    
    /**
     * Adds a hash set to the configuration. If the hash set is designated as 
     * the National Software Reference Library (NSRL) hash set, it is assumed 
     * the the hash set previously designated as the NSRL set, if any, is not 
     * being indexed. Does not check for duplication of sets and does not save 
     * the configuration.
     */
    public void addHashSet(HashDb hashSet) {
        if (hashSet.getKnownFilesType() == HashDb.KnownFilesType.NSRL) {
            setNSRLHashSet(hashSet);
        }
        else {
            addKnownBadHashSet(hashSet);
        }
    }
    
    /**
     * Removes a hash set from the hash sets configuration.
     */
    public void removeHashSet(HashDb hashSetToRemove) {
        if (nsrlHashSet != null && nsrlHashSet.equals(hashSetToRemove)) {
            removeNSRLHashSet();          
        }
        else {
            knownBadHashSets.remove(hashSetToRemove);
            // RJCTODO: Close HashDb
        }
    } 
            
   /**
     * Gets the configured known files hash sets that accept updates. 
     * @return A list, possibly empty, of HashDb objects. 
     */
    public List<HashDb> getUpdateableHashSets() {
        ArrayList<HashDb> updateableDbs = new ArrayList<>();
        for (HashDb db : knownBadHashSets) {
            try {
                if (db.isUpdateable()) {
                    updateableDbs.add(db);
                }
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking updateable status of " + db.getDatabasePath(), ex);
            }
        }
        return Collections.unmodifiableList(updateableDbs);
    }

    /**
     * Gets all of the configured hash sets.
     * @return A list, possibly empty, of HashDb objects representing the hash 
     * sets.
     */
    public List<HashDb> getAllHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        if (nsrlHashSet != null) {
            hashDbs.add(nsrlHashSet);
        }
        hashDbs.addAll(knownBadHashSets);
        return Collections.unmodifiableList(hashDbs);
    }
    
    /** Gets the configured hash set, if any, with a given name.
     * @return A HashDb object or null. 
     */    
    public HashDb getHashSetByName(String name) {
        if (nsrlHashSet != null && nsrlHashSet.getDisplayName().equals(name)) {
            return nsrlHashSet;
        }
        
        for (HashDb hashSet : knownBadHashSets) {
            if (hashSet.getDisplayName().equals(name)) {
                return hashSet;
            }
        }
        
        return null;
    }
        
//    public HashDb getHashSetAt(int index)
    
    // RJCTODO: Get rid of this
    /**
     * Adds a hash set to the configuration as a known bad files hash set. The 
     * set is added to the internal known bad sets collection at the index 
     * specified by the caller. Does not save the configuration.
     */
    public void addKnownBadSet(int index, HashDb set) {
        knownBadHashSets.add(index, set);
    }

    // RJCTODO: Get rid of this
    /** 
     * Removes the known bad files hash set from the internal known bad files 
     * hash sets collection at the specified index. Does not save the configuration.
     */
    public void removeKnownBadSetAt(int index) {
        knownBadHashSets.remove(index);
    }
    
    /**
     * Sets the value for the flag indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public void setShouldAlwaysCalculateHashes(boolean alwaysCalculateHashes) {
        this.alwaysCalculateHashes = alwaysCalculateHashes;
    }
    
    /**
     * Accesses the flag that indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public boolean shouldAlwaysCalculateHashes() {
        return alwaysCalculateHashes;
    }
    
    /**
     * Saves the hash sets configuration. Note that the configuration is only 
     * saved on demand to support cancellation of configuration panels.
     * @return True on success, false otherwise.
     */
    public boolean save() {
        return writeHashSetConfigurationToDisk();
    }

    /**
     * Restores the last saved hash sets configuration. This supports 
     * cancellation of configuration panels.
     */
    public void loadLastSavedConfiguration() {
        try {
            SleuthkitJNI.closeHashDatabases();                    
        }
        catch (TskCoreException ex) {
            // RJCTODO: Log
        }
        
        nsrlHashSet = null;
        knownBadHashSets.clear();        
        if (hashSetsConfigurationFileExists()) {
            readHashSetsConfigurationFromDisk();            
        }
    }
        
    private boolean hashSetsConfigurationFileExists() {
        File f = new File(xmlFile);
        return f.exists() && f.canRead() && f.canWrite();
    }    
        
    private boolean writeHashSetConfigurationToDisk() {
        boolean success = false;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);

            // TODO: Remove all the multiple database paths stuff, it was a mistake. 
            for (HashDb set : knownBadHashSets) {
                String useForIngest = Boolean.toString(set.getUseForIngest());
                String showInboxMessages = Boolean.toString(set.getShowInboxMessages());
                List<String> paths = Collections.singletonList(set.getDatabasePath());
                String type = KnownFilesType.KNOWN_BAD.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, set.getDisplayName());
                setEl.setAttribute(SET_TYPE_ATTR, type);
                setEl.setAttribute(SET_USE_FOR_INGEST_ATTR, useForIngest);
                setEl.setAttribute(SET_SHOW_INBOX_MESSAGES, showInboxMessages);

                for (int i = 0; i < paths.size(); i++) {
                    String path = paths.get(i);
                    Element pathEl = doc.createElement(PATH_EL);
                    pathEl.setAttribute(PATH_NUMBER_ATTR, Integer.toString(i));
                    pathEl.setTextContent(path);
                    setEl.appendChild(pathEl);
                }
                rootEl.appendChild(setEl);
            }
            
            // TODO: Remove all the multiple database paths stuff. 
            if(nsrlHashSet != null) {
                String useForIngest = Boolean.toString(nsrlHashSet.getUseForIngest());
                String showInboxMessages = Boolean.toString(nsrlHashSet.getShowInboxMessages());
                List<String> paths = Collections.singletonList(nsrlHashSet.getDatabasePath());
                String type = KnownFilesType.NSRL.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, nsrlHashSet.getDisplayName());
                setEl.setAttribute(SET_TYPE_ATTR, type);
                setEl.setAttribute(SET_USE_FOR_INGEST_ATTR, useForIngest);
                setEl.setAttribute(SET_SHOW_INBOX_MESSAGES, showInboxMessages);

                for (int i = 0; i < paths.size(); i++) {
                    String path = paths.get(i);
                    Element pathEl = doc.createElement(PATH_EL);
                    pathEl.setAttribute(PATH_NUMBER_ATTR, Integer.toString(i));
                    pathEl.setTextContent(path);
                    setEl.appendChild(pathEl);
                }
                rootEl.appendChild(setEl);
            }

            String calcValue = Boolean.toString(alwaysCalculateHashes);
            Element setCalc = doc.createElement(SET_CALC);
            setCalc.setAttribute(SET_VALUE, calcValue);
            rootEl.appendChild(setCalc);

            success = XMLUtil.saveDoc(HashDbManager.class, xmlFile, ENCODING, doc);
        } 
        catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving hash sets: can't initialize parser.", e);
        }
        return success;        
    }
    
    private boolean readHashSetsConfigurationFromDisk() {
        final Document doc = XMLUtil.loadDoc(HashDbManager.class, xmlFile, XSDFILE);
        if (doc == null) {
            return false;
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Error loading hash sets: invalid file format.");
            return false;
        }
        NodeList setsNList = root.getElementsByTagName(SET_EL);
        int numSets = setsNList.getLength();
        if(numSets==0) {
            logger.log(Level.WARNING, "No element hash_set exists.");
        }
        for (int i = 0; i < numSets; ++i) {
            Element setEl = (Element) setsNList.item(i);
            final String name = setEl.getAttribute(SET_NAME_ATTR);
            final String type = setEl.getAttribute(SET_TYPE_ATTR);
            final String useForIngest = setEl.getAttribute(SET_USE_FOR_INGEST_ATTR);
            final String showInboxMessages = setEl.getAttribute(SET_SHOW_INBOX_MESSAGES);
            Boolean useForIngestBool = Boolean.parseBoolean(useForIngest);
            Boolean showInboxMessagesBool = Boolean.parseBoolean(showInboxMessages);
            List<String> paths = new ArrayList<>();

            // TODO: Remove all the multiple database paths stuff. 
            // RJCTODO: Rework this to do a search a bit differently, or simply indicate the file is missing...
            NodeList pathsNList = setEl.getElementsByTagName(PATH_EL);
            final int numPaths = pathsNList.getLength();
            for (int j = 0; j < numPaths; ++j) {
                Element pathEl = (Element) pathsNList.item(j);
                String number = pathEl.getAttribute(PATH_NUMBER_ATTR);
                String path = pathEl.getTextContent();
                
                // If either the database or it's index exist
                File database = new File(path);
                File index = new File(HashDb.toIndexPath(path));
                if(database.exists() || index.exists()) {
                    paths.add(path);
                } else {
                    // Ask for new path
                    int ret = JOptionPane.showConfirmDialog(null, "Database " + name + " could not be found at location\n"
                            + path + "\n"
                            + " Would you like to search for the file?", "Missing Database", JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION) {
                        String filePath = searchForFile(name);
                        if(filePath!=null) {
                            paths.add(filePath);
                        }
                    }
                }
            }
            
            // Check everything was properly set
            if(name.isEmpty()) {
                logger.log(Level.WARNING, "Name was not set for hash_set at index {0}.", i);
            }
            if(type.isEmpty()) {
                logger.log(Level.SEVERE, "Type was not set for hash_set at index {0}, cannot make instance of HashDb class.", i);
                return false; // exit because this causes a fatal error
            }
            if(useForIngest.isEmpty()) {
                logger.log(Level.WARNING, "UseForIngest was not set for hash_set at index {0}.", i);
            }
            if(showInboxMessages.isEmpty()) {
                logger.log(Level.WARNING, "ShowInboxMessages was not set for hash_set at index {0}.", i);
            }
            
            if(paths.isEmpty()) {
                // No paths for this entry, the user most likely declined to search for them
                logger.log(Level.WARNING, "No paths were set for hash_set at index {0}. Removing the database.", i);
            } 
            else {
                KnownFilesType typeDBType = KnownFilesType.valueOf(type);
                try {
                    HashDb db = HashDb.openHashDatabase(name, paths.get(0), useForIngestBool, showInboxMessagesBool, typeDBType);
                    if (typeDBType == KnownFilesType.NSRL) {
                        setNSRLHashSet(db);
                    }
                    else {
                        addKnownBadHashSet(db);
                    }
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash database", ex);                
                    JOptionPane.showMessageDialog(null, "Unable to open " + paths.get(0) + " hash database.", "Open Hash Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        NodeList calcList = root.getElementsByTagName(SET_CALC);
        int numCalc = calcList.getLength(); // Shouldn't be more than 1
        if(numCalc==0) {
            logger.log(Level.WARNING, "No element hash_calculate exists.");
        }
        for(int i=0; i<numCalc; i++) {
            Element calcEl = (Element) calcList.item(i);
            final String value = calcEl.getAttribute(SET_VALUE);
            alwaysCalculateHashes = Boolean.parseBoolean(value);
        }
        return true;
    }
    
    /**
     * Ask the user to browse to a new Hash Database file with the same database
     * name as the one provided. If the names do not match, the database cannot
     * be added. If the user cancels the search, return null, meaning the user
     * would like to remove the entry for the missing database.
     * 
     * @param name the name of the database to add
     * @return the file path to the new database, or null if the user wants to
     *         delete the old database
     */
    private String searchForFile(String name) {
        // Initialize the file chooser and only allow hash databases to be opened
        JFileChooser fc = new JFileChooser();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);

        String filePath = null;
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                filePath = f.getCanonicalPath();
            } 
            catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path", ex);
            } 
        }
        
        return filePath;
    }    
}