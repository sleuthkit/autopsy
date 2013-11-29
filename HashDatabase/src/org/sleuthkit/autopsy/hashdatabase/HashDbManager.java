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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.apache.commons.io.FilenameUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashInfo;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * This class is a singleton that manages the set of hash databases
 * used to identify files as known files or known bad files. 
 */
public class HashDbManager {
    /**
     * Characterizes the files whose hashes are stored in a hash database.
     */
    public enum KnownFilesType{
        KNOWN("Known"), 
        KNOWN_BAD("Known Bad");

        private String displayName;

        private KnownFilesType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    // RJCTODO: Consider making these local 
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
    private static final String HASH_DATABASE_FILE_EXTENSON = "kdb"; 
    private static final String LEGACY_INDEX_FILE_EXTENSION = "-md5.idx";
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());
    private static HashDbManager instance;        
    private final String configFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_HASHSETS_FILE_NAME;
    private List<HashDb> knownHashSets = new ArrayList<>();
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private Set<String> hashSetNames = new HashSet<>();
    private Set<String> hashSetPaths = new HashSet<>();
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

    private boolean hashSetsConfigurationFileExists() {
        File f = new File(configFilePath);
        return f.exists() && f.canRead() && f.canWrite();
    }                
    
    public static String getHashDatabaseFileExtension() {
        return HASH_DATABASE_FILE_EXTENSON;
    }
    
    public class DuplicateHashSetNameException extends Exception {  
        private DuplicateHashSetNameException() {
            super("The hash set name has already been used for another hash database.");
        }
    }
    
    public class HashDatabaseDoesNotExistException extends Exception {        
        private HashDatabaseDoesNotExistException() {
            super("Attempt to add a hash database that does not exist to the configuration");
        }
    }
    
    public class FileAlreadyExistsException extends Exception {
        private FileAlreadyExistsException() {
            super("A hash database file already exists at the selected location.");
        }
    }
    
    public class HashDatabaseAlreadyAddedException extends Exception {
        private HashDatabaseAlreadyAddedException() {
            super("The hash database has already been created.");
        }
    }
    
    public class IllegalHashDatabaseFileNameExtensionException extends Exception {
        private IllegalHashDatabaseFileNameExtensionException() {
            super("The hash database file must have a ." + getHashDatabaseFileExtension() + " extension.");
        }
    }
    
    /**
     * Adds an existing hash database to the set of hash databases used to classify files as known or known bad.
     * @param hashSetName Name used to represent the hash database in user interface components. 
     * @param filePath Full path to either a hash database file or a hash database index file.
     * @param searchDuringIngest A flag indicating whether or not the hash database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages should be sent as ingest messages.
     * @param knownFilesType The classification to apply to files whose hashes are found in the hash database. 
     * @return A HashDb object representation of the hash database.
     * @throws HashDatabaseDoesNotExistException, DuplicateHashSetNameException, HashDatabaseAlreadyAddedException, TskCoreException 
     */
    public synchronized HashDb addExistingHashDatabase(String hashSetName, String filePath, boolean searchDuringIngest, boolean sendIngestMessages, KnownFilesType knownFilesType) throws HashDatabaseDoesNotExistException, DuplicateHashSetNameException, HashDatabaseAlreadyAddedException, TskCoreException {
        if (new File(filePath).exists()) {
            throw new HashDatabaseDoesNotExistException();
        }
        
        if (hashSetNames.contains(hashSetName)) {
            throw new DuplicateHashSetNameException();
        }
        
        if (hashSetPaths.contains(filePath)) {
            throw new HashDatabaseAlreadyAddedException();            
        }
        
        int handle = SleuthkitJNI.openHashDatabase(filePath);
        HashDb hashDb = new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
        addToConfiguration(hashDb);
        return hashDb;
    }

    /**
     * Adds a new hash database to the set of hash databases used to classify files as known or known bad.
     * @param hashSetName Hash set name used to represent the hash database in user interface components. 
     * @param filePath Full path to the database file to be created. The file name component of the path must have a ".kdb" extension.
     * @param useForIngest A flag indicating whether or not the data base should be used during the file ingest process.
     * @param showInboxMessages A flag indicating whether messages indicating lookup hits should be sent to the application in box.
     * @param hashSetType The type of hash set to associate with the database. 
     * @return A HashDb object representation of the opened hash database.
     * @throws TskCoreException 
     */
    public synchronized HashDb addNewHashDatabase(String hashSetName, String filePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType knownFilesType) throws FileAlreadyExistsException, DuplicateHashSetNameException, HashDatabaseAlreadyAddedException, IllegalHashDatabaseFileNameExtensionException, TskCoreException {
        File file = new File(filePath);
        if (file.exists()) {
            throw new FileAlreadyExistsException();
        }
        if (!FilenameUtils.getExtension(file.getName()).equalsIgnoreCase(HASH_DATABASE_FILE_EXTENSON)) {
            throw new IllegalHashDatabaseFileNameExtensionException();            
        }
        
        if (hashSetNames.contains(hashSetName)) {
            throw new DuplicateHashSetNameException();
        }
        
        if (hashSetPaths.contains(filePath)) {
            throw new HashDatabaseAlreadyAddedException();            
        }
        
        int handle = SleuthkitJNI.createHashDatabase(filePath);
        HashDb hashDb = new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, useForIngest, showInboxMessages, knownFilesType);
        addToConfiguration(hashDb);
        return hashDb;
    }    

    private void addToConfiguration(HashDb hashDb) {
        hashSetNames.add(hashDb.getHashSetName());
        hashSetPaths.add(hashDb.getDatabasePath());
        hashSetPaths.add(hashDb.getIndexPath());
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
    public synchronized void removeHashDatabase(HashDb hashDb) {
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
    public synchronized List<HashDb> getAllHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        hashDbs.addAll(knownHashSets);
        hashDbs.addAll(knownBadHashSets);
        return Collections.unmodifiableList(hashDbs);
    }    
    
    /** 
     * Gets the configured known files hash sets.
     * @return A list, possibly empty, of HashDb objects.
     */
    public synchronized List<HashDb> getKnownHashSets() {
        return Collections.unmodifiableList(knownHashSets);
    }
        
    /** 
     * Gets the configured known bad files hash sets.
     * @return A list, possibly empty, of HashDb objects.
     */
    public synchronized List<HashDb> getKnownBadHashSets() {
        return Collections.unmodifiableList(knownBadHashSets);
    }
                
   /**
     * Gets all of the configured hash sets that accept updates. 
     * @return A list, possibly empty, of HashDb objects. 
     */
    public synchronized List<HashDb> getUpdateableHashSets() {
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
    public synchronized void alwaysCalculateHashes(boolean alwaysCalculateHashes) {
        this.alwaysCalculateHashes = alwaysCalculateHashes;
    }
    
    /**
     * Accesses the flag that indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public synchronized boolean shouldAlwaysCalculateHashes() {
        return alwaysCalculateHashes;
    }
    
    /**
     * Saves the hash sets configuration. Note that the configuration is only 
     * saved on demand to support cancellation of configuration panels.
     * @return True on success, false otherwise.
     */
    public synchronized boolean save() {
        return writeHashSetConfigurationToDisk();
    }

    /**
     * Restores the last saved hash sets configuration. This supports 
     * cancellation of configuration panels.
     */
    public synchronized void loadLastSavedConfiguration() {        
        closeHashDatabases(knownHashSets);
        closeHashDatabases(knownBadHashSets);
        hashSetNames.clear();
                
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

            success = XMLUtil.saveDoc(HashDbManager.class, configFilePath, ENCODING, doc);
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
        final Document doc = XMLUtil.loadDoc(HashDbManager.class, configFilePath, XSDFILE);
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
                                   
            String hashSetName = setEl.getAttribute(SET_NAME_ATTR);
            if (hashSetName.isEmpty()) {
                logger.log(Level.SEVERE, SET_NAME_ATTR + attributeErrorMessage, i);
                continue;
            }                                 
            
            // Handle configurations saved before duplicate hash set names were not permitted.
            if (hashSetNames.contains(hashSetName)) {
                int suffix = 0;
                String newHashSetName;
                do {
                    ++suffix;
                    newHashSetName = hashSetName + suffix; 
                }
                while (hashSetNames.contains(newHashSetName));
                JOptionPane.showMessageDialog(null, "Duplicate hash set name " + hashSetName + " found.\nReplacing with " + newHashSetName + ".", "Open Hash Database Error", JOptionPane.ERROR_MESSAGE);
                hashSetName = newHashSetName;
            }
           
            String knownFilesType = setEl.getAttribute(SET_TYPE_ATTR);
            if(knownFilesType.isEmpty()) {
                logger.log(Level.SEVERE, SET_TYPE_ATTR + attributeErrorMessage, i);
                continue;
            }
            
            // Handle legacy known files types.
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
                    hashSetNames.add(hashSetName);
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
    
    /**
     * Instances of this class represent hash databases used to classify files as known or know bad. 
     */
    public static class HashDb {
        /**
         * Property change events published by hash database objects.
         */
        public enum Event {
            INDEXING_DONE
        }

        private int handle;
        private KnownFilesType knownFilesType;
        private String databasePath;
        private String indexPath;
        private String hashSetName;
        private boolean useForIngest;
        private boolean sendHitMessages;
        private boolean indexing;
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);    

        /**
         * Opens an existing hash database. 
         * @param hashSetName Name used to represent the hash database in user interface components. 
         * @param selectedFilePath Full path to either a hash database file or a hash database index file.
         * @param useForIngest A flag indicating whether or not the hash database should be used during ingest.
         * @param sendHitMessages A flag indicating whether hash set hit messages should be sent to the application in box.
         * @param knownFilesType The classification to apply to files whose hashes are stored in the hash database. 
         * @return A HashDb object representation of the new hash database.
         * @throws TskCoreException 
         */
        public static HashDb openHashDatabase(String hashSetName, String selectedFilePath, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) throws TskCoreException {
            int handle = SleuthkitJNI.openHashDatabase(selectedFilePath);
            return new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, useForIngest, sendHitMessages, knownFilesType);
        }

        /**
         * Creates a new hash database. 
         * @param hashSetName Hash set name used to represent the hash database in user interface components. 
         * @param databasePath Full path to the database file to be created. The file name component of the path must have a ".kdb" extension.
         * @param useForIngest A flag indicating whether or not the data base should be used during the file ingest process.
         * @param showInboxMessages A flag indicating whether messages indicating lookup hits should be sent to the application in box.
         * @param hashSetType The type of hash set to associate with the database. 
         * @return A HashDb object representation of the opened hash database.
         * @throws TskCoreException 
         */
        public static HashDb createHashDatabase(String hashSetName, String databasePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType knownFilesType) throws TskCoreException {
            int handle = SleuthkitJNI.createHashDatabase(databasePath);
            return new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, useForIngest, showInboxMessages, knownFilesType);
        }    

        private HashDb(int handle, String databasePath, String indexPath, String name, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) {
            this.databasePath = databasePath;
            this.indexPath = indexPath;
            this.hashSetName = name;
            this.useForIngest = useForIngest;
            this.sendHitMessages = sendHitMessages;
            this.knownFilesType = knownFilesType;
            this.handle = handle;
            this.indexing = false;
        }

        /**
         * Adds a listener for the events defined in HashDb.Event. 
         */
        public void addPropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.addPropertyChangeListener(pcl);
        }

        /**
         * Removes a listener for the events defined in HashDb.Event. 
         */
        public void removePropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.removePropertyChangeListener(pcl);
        }

        public String getHashSetName() {
            return hashSetName;
        }

        public String getDatabasePath() {
            return databasePath;
        }

        public String getIndexPath() {
            return indexPath;
        }

        public KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        public boolean getUseForIngest() {
            return useForIngest;
        }

        void setUseForIngest(boolean useForIngest) {
            this.useForIngest = useForIngest;
        }

        public boolean getShowInboxMessages() {
            return sendHitMessages;
        }

        void setShowInboxMessages(boolean showInboxMessages) {
            this.sendHitMessages = showInboxMessages;
        }

        public boolean hasLookupIndex() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);        
        }

        public boolean canBeReindexed() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseCanBeReindexed(handle);
        }

        public boolean hasIndexOnly() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseHasLegacyLookupIndexOnly(handle);        
        }

        /**
         * Indicates whether the hash database accepts updates.
         * @return True if the database accepts updates, false otherwise.
         */
        public boolean isUpdateable() throws TskCoreException {
            return SleuthkitJNI.isUpdateableHashDatabase(this.handle);        
        }

        /**
         * Adds hashes of content (if calculated) to the hash database. 
         * @param content The content for which the calculated hashes, if any, are to be added to the hash database.
         * @throws TskCoreException 
         */
        public void add(Content content) throws TskCoreException {
            add(content, null);
        }    

        /**
         * Adds hashes of content (if calculated) to the hash database. 
         * @param content The content for which the calculated hashes, if any, are to be added to the hash database.
         * @param comment A comment to associate with the hashes, e.g., the name of the case in which the content was encountered.
         * @throws TskCoreException 
         */
        public void add(Content content, String comment) throws TskCoreException {
            // TODO: This only works for AbstractFiles at present. Change when Content
            // can be queried for hashes.
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile)content;
                // TODO: Add support for SHA-1 and SHA-256 hashes.
                if (null != file.getMd5Hash()) {
                    SleuthkitJNI.addToHashDatabase(file.getName(), file.getMd5Hash(), null, null, comment, handle);
                }
            }
        }

         public boolean hasHashOfContent(Content content) throws TskCoreException {         
            boolean result = false; 
             // TODO: This only works for AbstractFiles at present. Change when Content can be queried for hashes.
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile)content;
                // TODO: Add support for SHA-1 and SHA-256 hashes.
                if (null != file.getMd5Hash()) {
                    result = SleuthkitJNI.lookupInHashDatabase(file.getMd5Hash(), handle);
                }
            }         
            return result;
         }

        public HashInfo lookUp(Content content) throws TskCoreException {
            HashInfo result = null;
            // TODO: This only works for AbstractFiles at present. Change when Content can be queried for hashes.
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile)content;
                // TODO: Add support for SHA-1 and SHA-256 hashes.
                if (null != file.getMd5Hash()) {
                    result = SleuthkitJNI.lookupInHashDatabaseVerbose(file.getMd5Hash(), handle);
                }
            }             
            return result;
        }         

        boolean isIndexing() {
            return indexing;
        }

        // Tries to index the database (overwrites any existing index) using a 
        // SwingWorker.
        void createIndex(boolean deleteIndexFile) {
            CreateIndex creator = new CreateIndex(deleteIndexFile);
            creator.execute();
        }

        private class CreateIndex extends SwingWorker<Object,Void> {
            private ProgressHandle progress;
            private boolean deleteIndexFile;

            CreateIndex(boolean deleteIndexFile) {
                this.deleteIndexFile = deleteIndexFile;
            };

            @Override
            protected Object doInBackground() {
                indexing = true;
                progress = ProgressHandleFactory.createHandle("Indexing " + hashSetName);
                progress.start();
                progress.switchToIndeterminate();
                try {
                    SleuthkitJNI.createLookupIndexForHashDatabase(handle, deleteIndexFile);
                    indexPath = SleuthkitJNI.getHashDatabaseIndexPath(handle);
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDb.class.getName()).log(Level.SEVERE, "Error indexing hash database", ex);                
                    JOptionPane.showMessageDialog(null, "Error indexing hash database for " + getHashSetName() + ".", "Hash Database Index Error", JOptionPane.ERROR_MESSAGE);                
                }
                return null;
            }

            @Override
            protected void done() {
                indexing = false;
                progress.finish();
                propertyChangeSupport.firePropertyChange(Event.INDEXING_DONE.toString(), null, hashSetName);
            }
        }

        public void close() throws TskCoreException {
            SleuthkitJNI.closeHashDatabase(handle);
        }
    }
}