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
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class is a singleton that manages the set of hash databases
 * used to classify files as known or known bad. 
 */
public class HashDbManager {
    private static final String ROOT_EL = "hash_sets";
    private static final String SET_EL = "hash_set";
    private static final String SET_NAME_ATTR = "name";
    private static final String SET_TYPE_ATTR = "type"; 
    private static final String SET_USE_FOR_INGEST_ATTR = "use_for_ingest";
    private static final String SET_SHOW_INBOX_MESSAGES = "show_inbox_messages";
    private static final String PATH_EL = "hash_set_path";
    private static final String CUR_HASHSETS_FILE_NAME = "hashsets.xml";
    private static final String XSDFILE = "HashsetsSchema.xsd";
    private static final String ENCODING = "UTF-8";
    private static final String SET_CALC = "hash_calculate";
    private static final String SET_VALUE = "value";
    private static final String LEGACY_INDEX_FILE_EXTENSION = "-md5.idx";
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());
    private static HashDbManager instance;
    private String xmlFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_HASHSETS_FILE_NAME;
    private List<HashDb> knownHashSets = new ArrayList<>();
    private List<HashDb> knownBadHashSets = new ArrayList<>();
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
     * Adds a hash database to the configuration. Does not check for duplication 
     * of hash set names and does not save the configuration - the configuration 
     * is only saved on demand to support cancellation of configuration panels.
     */
    public void addHashSet(HashDb hashDb) {
        if (hashDb.getKnownFilesType() == HashDb.KnownFilesType.KNOWN) {
            knownHashSets.add(hashDb);
        }
        else {
            knownBadHashSets.add(hashDb);
        }
    }
    
    /**
     * Removes a hash database from the configuration. Does not save the 
     * configuration - the configuration is only saved on demand to support 
     * cancellation of configuration panels.
     */
    public void removeHashSet(HashDb hashDb) {
        try {
            hashDb.close();
        }
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error closing hash database at " + hashDb.getDatabasePath(), ex);            
        }
        knownHashSets.remove(hashDb);
        knownBadHashSets.remove(hashDb);
    }     

    /**
     * Gets all of the configured hash sets.
     * @return A list, possibly empty, of HashDb objects representing the hash 
     * sets.
     */
    public List<HashDb> getAllHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        hashDbs.addAll(knownHashSets);
        hashDbs.addAll(knownBadHashSets);
        return Collections.unmodifiableList(hashDbs);
    }    
    
    /** 
     * Gets the configured known files hash sets.
     * @return A list, possibly empty, of HashDb objects.
     */
    public List<HashDb> getKnownHashSets() {
        return Collections.unmodifiableList(knownHashSets);
    }
        
    /** 
     * Gets the configured known bad files hash sets.
     * @return A list, possibly empty, of HashDb objects.
     */
    public List<HashDb> getKnownBadHashSets() {
        return Collections.unmodifiableList(knownBadHashSets);
    }
                
   /**
     * Gets all of the configured hash sets that accept updates. 
     * @return A list, possibly empty, of HashDb objects. 
     */
    public List<HashDb> getUpdateableHashSets() {
        List<HashDb> updateableDbs = getUpdateableHashSets(knownHashSets);
        updateableDbs.addAll(getUpdateableHashSets(knownBadHashSets));        
        return Collections.unmodifiableList(updateableDbs);
    }

    private List<HashDb> getUpdateableHashSets(List<HashDb> hashDbs) {
        ArrayList<HashDb> updateableDbs = new ArrayList<>();
        for (HashDb db : hashDbs) {
            try {
                if (db.isUpdateable()) {
                    updateableDbs.add(db);
                }
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking updateable status of hash database at " + db.getDatabasePath(), ex);
            }
        }   
        return updateableDbs;        
    }
    
    /**
     * Sets the value for the flag that indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public void alwaysCalculateHashes(boolean alwaysCalculateHashes) {
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
        closeHashDatabases(knownHashSets);
        closeHashDatabases(knownBadHashSets);
                
        if (hashSetsConfigurationFileExists()) {
            readHashSetsConfigurationFromDisk();            
        }
    }

    private void closeHashDatabases(List<HashDb> hashDbs) {
        String dbPath = "";
        try {
            for (HashDb db : hashDbs) {
                dbPath = db.getDatabasePath();
                db.close();
            }
        }
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error closing hash database at " + dbPath, ex);
        }        
        hashDbs.clear();            
    }
    
    private boolean hashSetsConfigurationFileExists() {
        File f = new File(xmlFilePath);
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

            writeHashDbsToDisk(doc, rootEl, knownHashSets);
            writeHashDbsToDisk(doc, rootEl, knownBadHashSets);
            
            String calcValue = Boolean.toString(alwaysCalculateHashes);
            Element setCalc = doc.createElement(SET_CALC);
            setCalc.setAttribute(SET_VALUE, calcValue);
            rootEl.appendChild(setCalc);

            success = XMLUtil.saveDoc(HashDbManager.class, xmlFilePath, ENCODING, doc);
        } 
        catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving hash databases", e);
        }
        return success;        
    }
    
    private static void writeHashDbsToDisk(Document doc, Element rootEl, List<HashDb> hashDbs) {
        for (HashDb db : hashDbs) {
            Element setEl = doc.createElement(SET_EL);
            setEl.setAttribute(SET_NAME_ATTR, db.getHashSetName());
            setEl.setAttribute(SET_TYPE_ATTR, db.getKnownFilesType().toString());
            setEl.setAttribute(SET_USE_FOR_INGEST_ATTR, Boolean.toString(db.getUseForIngest()));
            setEl.setAttribute(SET_SHOW_INBOX_MESSAGES, Boolean.toString(db.getShowInboxMessages()));
            String path = null;
            try {
                if (db.hasIndexOnly()) {
                    path = db.getIndexPath();
                }
                else {
                    path = db.getDatabasePath();
                }                
                Element pathEl = doc.createElement(PATH_EL);
                pathEl.setTextContent(path);
                setEl.appendChild(pathEl);            
                rootEl.appendChild(setEl);                
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting path of hash database " + db.getHashSetName() + ", unable to save configuration", ex);                
            }            
        }        
    }
    
    // TODO: The return value from this function is never checked. Failure is not indicated to the user. Is this desired?
    private boolean readHashSetsConfigurationFromDisk() {
        // Open the XML document that implements the configuration file.
        final Document doc = XMLUtil.loadDoc(HashDbManager.class, xmlFilePath, XSDFILE);
        if (doc == null) {
            return false;
        }

        // Get the root element.
        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Error loading hash sets: invalid file format.");
            return false;
        }
        
        // Get the hash set elements.
        NodeList setsNList = root.getElementsByTagName(SET_EL);
        int numSets = setsNList.getLength();
        if(numSets == 0) {
            logger.log(Level.WARNING, "No element hash_set exists.");
        }
        
        // Create HashDb objects for each hash set element.
        // TODO: Does this code implement the correct policy for handling a malformed config file?
        String attributeErrorMessage = " attribute was not set for hash_set at index {0}, cannot make instance of HashDb class";
        String elementErrorMessage = " element was not set for hash_set at index {0}, cannot make instance of HashDb class";
        for (int i = 0; i < numSets; ++i) {
            Element setEl = (Element) setsNList.item(i);
                                   
            final String hashSetName = setEl.getAttribute(SET_NAME_ATTR);
            if (hashSetName.isEmpty()) {
                logger.log(Level.SEVERE, SET_NAME_ATTR + attributeErrorMessage, i);
                continue;
            }                                 
            
            String knownFilesType = setEl.getAttribute(SET_TYPE_ATTR);
            if(knownFilesType.isEmpty()) {
                logger.log(Level.SEVERE, SET_TYPE_ATTR + attributeErrorMessage, i);
                continue;
            }
            if (knownFilesType.equals("NSRL")) {
                knownFilesType = KnownFilesType.KNOWN.toString();
            }                                    
            
            final String useForIngest = setEl.getAttribute(SET_USE_FOR_INGEST_ATTR);
            if (useForIngest.isEmpty()) {
                logger.log(Level.SEVERE, SET_USE_FOR_INGEST_ATTR + attributeErrorMessage, i);
                continue;                
            }
            Boolean useForIngestFlag = Boolean.parseBoolean(useForIngest);

            final String showInboxMessages = setEl.getAttribute(SET_SHOW_INBOX_MESSAGES);
            if (useForIngest.isEmpty()) {
                logger.log(Level.SEVERE, SET_SHOW_INBOX_MESSAGES + attributeErrorMessage, i);
                continue;                
            }
            Boolean showInboxMessagesFlag = Boolean.parseBoolean(showInboxMessages);

            String dbPath;
            NodeList pathsNList = setEl.getElementsByTagName(PATH_EL);
            if (pathsNList.getLength() > 0) {                
                Element pathEl = (Element) pathsNList.item(0); // Shouldn't be more than one.
                dbPath = pathEl.getTextContent();
                if (dbPath.isEmpty()) {
                    logger.log(Level.SEVERE, PATH_EL + elementErrorMessage, i);
                    continue;                                                    
                }                                
            }
            else {
                logger.log(Level.SEVERE, PATH_EL + elementErrorMessage, i);
                continue;                                
            }
            dbPath = getValidFilePath(hashSetName, dbPath);
                        
            if (null != dbPath) {
                try {
                    addHashSet(HashDb.openHashDatabase(hashSetName, dbPath, useForIngestFlag, showInboxMessagesFlag, KnownFilesType.valueOf(knownFilesType)));
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash database", ex);                
                    JOptionPane.showMessageDialog(null, "Unable to open " + dbPath + " hash database.", "Open Hash Database Error", JOptionPane.ERROR_MESSAGE);
                }
            } 
            else {
                logger.log(Level.WARNING, "No valid path for hash_set at index {0}, cannot make instance of HashDb class", i);
            }
        }
        
        // Get the element that stores the always calculate hashes flag.
        NodeList calcList = root.getElementsByTagName(SET_CALC);
        if (calcList.getLength() > 0) {
            Element calcEl = (Element) calcList.item(0); // Shouldn't be more than one.
            final String value = calcEl.getAttribute(SET_VALUE);
            alwaysCalculateHashes = Boolean.parseBoolean(value);            
        }
        else {
            logger.log(Level.WARNING, " element ");
            alwaysCalculateHashes = false;
        }

        return true;
    }

    private String getValidFilePath(String hashSetName, String configuredPath) {
        // Check the configured path.
        File database = new File(configuredPath);
        if (database.exists()) {
            return configuredPath;
        }

        // Try a path that could be in an older version of the configuration file.
        String legacyPath = configuredPath + LEGACY_INDEX_FILE_EXTENSION;
        database = new File(legacyPath); 
        if (database.exists()) {
            return legacyPath;
        }
        
        // Give the user an opportunity to find the desired file.
        String newPath = null;
        if (JOptionPane.showConfirmDialog(null, "Database " + hashSetName + " could not be found at location\n" + configuredPath + "\nWould you like to search for the file?", "Missing Database", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            newPath = searchForFile();
            if (null != newPath && !newPath.isEmpty()) {                
                database = new File(newPath); 
                if (!database.exists()) {
                    newPath =  null;
                } 
            }
        }
        return newPath;
    }
    
    private String searchForFile() {
        String filePath = null;
        JFileChooser fc = new JFileChooser();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash", "kdb" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
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