/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2014 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashInfo;
import org.sleuthkit.datamodel.HashEntry;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * This class implements a singleton that manages the set of hash databases used
 * to classify files as unknown, known or known bad.
 */
public class HashDbManager implements PropertyChangeListener {

    private static final String ROOT_ELEMENT = "hash_sets";
    private static final String SET_ELEMENT = "hash_set";
    private static final String SET_NAME_ATTRIBUTE = "name";
    private static final String SET_TYPE_ATTRIBUTE = "type";
    private static final String SEARCH_DURING_INGEST_ATTRIBUTE = "use_for_ingest";
    private static final String SEND_INGEST_MESSAGES_ATTRIBUTE = "show_inbox_messages";
    private static final String PATH_ELEMENT = "hash_set_path";
    private static final String LEGACY_PATH_NUMBER_ATTRIBUTE = "number";
    private static final String CONFIG_FILE_NAME = "hashsets.xml";
    private static final String XSD_FILE_NAME = "HashsetsSchema.xsd";
    private static final String ENCODING = "UTF-8";
    private static final String ALWAYS_CALCULATE_HASHES_ELEMENT = "hash_calculate";
    private static final String VALUE_ATTRIBUTE = "value";
    private static final String HASH_DATABASE_FILE_EXTENSON = "kdb";
    private static HashDbManager instance = null;
    private final String configFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + CONFIG_FILE_NAME;
    private List<HashDb> knownHashSets = new ArrayList<>();
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private Set<String> hashSetNames = new HashSet<>();
    private Set<String> hashSetPaths = new HashSet<>();
    private boolean alwaysCalculateHashes = true;
    PropertyChangeSupport changeSupport = new PropertyChangeSupport(HashDbManager.class);
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());

    /**
     * Property change event support In events: For both of these enums, the old
     * value should be null, and the new value should be the hashset name
     * string.
     */
    public enum SetEvt {

        DB_ADDED, DB_DELETED
    };

    /**
     * Gets the singleton instance of this class.
     */
    public static synchronized HashDbManager getInstance() {
        if (instance == null) {
            instance = new HashDbManager();
        }
        return instance;
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    private HashDbManager() {
        if (hashSetsConfigurationFileExists()) {
            readHashSetsConfigurationFromDisk();
        }
    }

    /**
     * Gets the extension, without the dot separator, that the SleuthKit
     * requires for the hash database files that combine a database and an index
     * and can therefore be updated.
     */
    static String getHashDatabaseFileExtension() {
        return HASH_DATABASE_FILE_EXTENSON;
    }

    public class HashDbManagerException extends Exception {

        private HashDbManagerException(String message) {
            super(message);
        }
    }

    /**
     * Adds an existing hash database to the set of hash databases used to
     * classify files as known or known bad and saves the configuration.
     *
     * @param hashSetName Name used to represent the hash database in user
     * interface components.
     * @param path Full path to either a hash database file or a hash database
     * index file.
     * @param searchDuringIngest A flag indicating whether or not the hash
     * database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     * should be sent as ingest messages.
     * @param knownFilesType The classification to apply to files whose hashes
     * are found in the hash database.
     * @return A HashDb representing the hash database.
     * @throws HashDbManagerException
     */
    public HashDb addExistingHashDatabase(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {
        HashDb hashDb = null;
        try {
            addExistingHashDatabaseInternal(hashSetName, path, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }

        // Save the configuration
        if (!save()) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
        }

        return hashDb;
    }

    /**
     * Adds an existing hash database to the set of hash databases used to
     * classify files as known or known bad. Does not save the configuration -
     * the configuration is only saved on demand to support cancellation of
     * configuration panels.
     *
     * @param hashSetName Name used to represent the hash database in user
     * interface components.
     * @param path Full path to either a hash database file or a hash database
     * index file.
     * @param searchDuringIngest A flag indicating whether or not the hash
     * database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     * should be sent as ingest messages.
     * @param knownFilesType The classification to apply to files whose hashes
     * are found in the hash database.
     * @return A HashDb representing the hash database.
     * @throws HashDbManagerException, TskCoreException
     */
    synchronized HashDb addExistingHashDatabaseInternal(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws HashDbManagerException, TskCoreException {
        if (!new File(path).exists()) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbDoesNotExistExceptionMsg", path));
        }

        if (hashSetPaths.contains(path)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbAlreadyAddedExceptionMsg", path));
        }

        if (hashSetNames.contains(hashSetName)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.duplicateHashSetNameExceptionMsg", hashSetName));
        }

        return addHashDatabase(SleuthkitJNI.openHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
    }

    /**
     * Adds a new hash database to the set of hash databases used to classify
     * files as known or known bad and saves the configuration.
     *
     * @param hashSetName Hash set name used to represent the hash database in
     * user interface components.
     * @param path Full path to the database file to be created.
     * @param searchDuringIngest A flag indicating whether or not the hash
     * database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     * should be sent as ingest messages.
     * @param knownFilesType The classification to apply to files whose hashes
     * are found in the hash database.
     * @return A HashDb representing the hash database.
     * @throws HashDbManagerException
     */
    public HashDb addNewHashDatabase(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages,
            HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {

        HashDb hashDb = null;
        try {
            hashDb = addNewHashDatabaseInternal(hashSetName, path, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }

        // Save the configuration
        if (!save()) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
        }

        return hashDb;
    }

    /**
     * Adds a new hash database to the set of hash databases used to classify
     * files as known or known bad. Does not save the configuration - the
     * configuration is only saved on demand to support cancellation of
     * configuration panels.
     *
     * @param hashSetName Hash set name used to represent the hash database in
     * user interface components.
     * @param path Full path to the database file to be created.
     * @param searchDuringIngest A flag indicating whether or not the hash
     * database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     * should be sent as ingest messages.
     * @param knownFilesType The classification to apply to files whose hashes
     * are found in the hash database.
     * @return A HashDb representing the hash database.
     * @throws HashDbManagerException, TskCoreException
     */
    synchronized HashDb addNewHashDatabaseInternal(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws HashDbManagerException, TskCoreException {
        File file = new File(path);
        if (file.exists()) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbFileExistsExceptionMsg", path));
        }
        if (!FilenameUtils.getExtension(file.getName()).equalsIgnoreCase(HASH_DATABASE_FILE_EXTENSON)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.illegalHashDbFileNameExtensionMsg",
                    getHashDatabaseFileExtension()));
        }

        if (hashSetPaths.contains(path)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbAlreadyAddedExceptionMsg", path));
        }

        if (hashSetNames.contains(hashSetName)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.duplicateHashSetNameExceptionMsg", hashSetName));
        }

        return addHashDatabase(SleuthkitJNI.createHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
    }

    private HashDb addHashDatabase(int handle, String hashSetName, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws TskCoreException {
        // Wrap an object around the handle.
        HashDb hashDb = new HashDb(handle, hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);

        // Get the indentity data before updating the collections since the 
        // accessor methods may throw. 
        String databasePath = hashDb.getDatabasePath();
        String indexPath = hashDb.getIndexPath();

        // Update the collections used to ensure that hash set names are unique 
        // and the same database is not added to the configuration more than once.
        hashSetNames.add(hashDb.getHashSetName());
        if (!databasePath.equals("None")) {
            hashSetPaths.add(databasePath);
        }
        if (!indexPath.equals("None")) {
            hashSetPaths.add(indexPath);
        }

        // Add the hash database to the appropriate collection for its type.
        if (hashDb.getKnownFilesType() == HashDb.KnownFilesType.KNOWN) {
            knownHashSets.add(hashDb);
        } else {
            knownBadHashSets.add(hashDb);
        }

        // Let any external listeners know that there's a new set   
        try {
            changeSupport.firePropertyChange(SetEvt.DB_ADDED.toString(), null, hashSetName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "HashDbManager listener threw exception", e);
            MessageNotifyUtil.Notify.show(
                    "Module Error",
                    NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
        return hashDb;
    }

    synchronized void indexHashDatabase(HashDb hashDb) {
        hashDb.addPropertyChangeListener(this);
        HashDbIndexer creator = new HashDbIndexer(hashDb);
        creator.execute();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals(HashDb.Event.INDEXING_DONE.name())) {
            HashDb hashDb = (HashDb) event.getNewValue();
            if (null != hashDb) {
                try {
                    String indexPath = hashDb.getIndexPath();
                    if (!indexPath.equals("None")) {
                        hashSetPaths.add(indexPath);
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDb.getHashSetName() + " hash database after indexing", ex);
                }
            }
        }
    }

    /**
     * Removes a hash database from the set of hash databases used to classify
     * files as known or known bad and saves the configuration.
     *
     * @param hashDb
     * @throws HashDbManagerException
     */
    public synchronized void removeHashDatabase(HashDb hashDb) throws HashDbManagerException {
        // Don't remove a database if ingest is running
        boolean ingestIsRunning = IngestManager.getDefault().isIngestRunning();
        if (ingestIsRunning) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.ingestRunningExceptionMsg"));
        }
        removeHashDatabaseInternal(hashDb);
        if (!save()) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
        }
    }

    /**
     * Removes a hash database from the set of hash databases used to classify
     * files as known or known bad. Does not save the configuration - the
     * configuration is only saved on demand to support cancellation of
     * configuration panels.
     *
     * @throws TskCoreException
     */
    synchronized void removeHashDatabaseInternal(HashDb hashDb) {
        // Remove the database from whichever hash set list it occupies,
        // and remove its hash set name from the hash set used to ensure unique
        // hash set names are used, before undertaking These operations will succeed and constitute
        // a mostly effective removal, even if the subsequent operations fail.
        String hashSetName = hashDb.getHashSetName();
        knownHashSets.remove(hashDb);
        knownBadHashSets.remove(hashDb);
        hashSetNames.remove(hashSetName);

        // Now undertake the operations that could throw.
        try {
            hashSetPaths.remove(hashDb.getIndexPath());
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDb.getHashSetName() + " hash database when removing the database", ex);
        }
        try {
            if (!hashDb.hasIndexOnly()) {
                hashSetPaths.remove(hashDb.getDatabasePath());
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting database path of " + hashDb.getHashSetName() + " hash database when removing the database", ex);
        }
        try {
            hashDb.close();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + hashDb.getHashSetName() + " hash database when removing the database", ex);
        }

        // Let any external listeners know that a set has been deleted

        try {
            changeSupport.firePropertyChange(SetEvt.DB_DELETED.toString(), null, hashSetName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "HashDbManager listener threw exception", e);
            MessageNotifyUtil.Notify.show(
                    "Module Error",
                    NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Gets all of the hash databases used to classify files as known or known
     * bad.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getAllHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        hashDbs.addAll(knownHashSets);
        hashDbs.addAll(knownBadHashSets);
        return hashDbs;
    }

    /**
     * Gets all of the hash databases used to classify files as known.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getKnownFileHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        hashDbs.addAll(knownHashSets);
        return hashDbs;
    }

    /**
     * Gets all of the hash databases used to classify files as known bad.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getKnownBadFileHashSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        hashDbs.addAll(knownBadHashSets);
        return hashDbs;
    }

    /**
     * Gets all of the hash databases that accept updates.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getUpdateableHashSets() {
        List<HashDb> updateableDbs = getUpdateableHashSets(knownHashSets);
        updateableDbs.addAll(getUpdateableHashSets(knownBadHashSets));
        return updateableDbs;
    }

    private List<HashDb> getUpdateableHashSets(List<HashDb> hashDbs) {
        ArrayList<HashDb> updateableDbs = new ArrayList<>();
        for (HashDb db : hashDbs) {
            try {
                if (db.isUpdateable()) {
                    updateableDbs.add(db);
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error checking updateable status of " + db.getHashSetName() + " hash database", ex);
            }
        }
        return updateableDbs;
    }

    /**
     * Sets the value for the flag that indicates whether hashes should be
     * calculated for content even if no hash databases are configured.
     */
    synchronized void setAlwaysCalculateHashes(boolean alwaysCalculateHashes) {
        this.alwaysCalculateHashes = alwaysCalculateHashes;
    }

    /**
     * Gets the flag that indicates whether hashes should be calculated for
     * content even if no hash databases are configured.
     */
    synchronized boolean getAlwaysCalculateHashes() {
        return alwaysCalculateHashes;
    }

    /**
     * Saves the hash sets configuration. Note that the configuration is only
     * saved on demand to support cancellation of configuration panels.
     *
     * @return True on success, false otherwise.
     */
    synchronized boolean save() {
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
        hashSetPaths.clear();

        if (hashSetsConfigurationFileExists()) {
            readHashSetsConfigurationFromDisk();
        }
    }

    private void closeHashDatabases(List<HashDb> hashDatabases) {
        for (HashDb database : hashDatabases) {
            try {
                database.close();
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + database.getHashSetName() + " hash database", ex);
            }
        }
        hashDatabases.clear();
    }

    private boolean writeHashSetConfigurationToDisk() {
        boolean success = false;
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootEl = doc.createElement(ROOT_ELEMENT);
            doc.appendChild(rootEl);

            writeHashDbsToDisk(doc, rootEl, knownHashSets);
            writeHashDbsToDisk(doc, rootEl, knownBadHashSets);

            String calcValue = Boolean.toString(alwaysCalculateHashes);
            Element setCalc = doc.createElement(ALWAYS_CALCULATE_HASHES_ELEMENT);
            setCalc.setAttribute(VALUE_ATTRIBUTE, calcValue);
            rootEl.appendChild(setCalc);

            success = XMLUtil.saveDoc(HashDbManager.class, configFilePath, ENCODING, doc);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error saving hash databases", ex);
        }
        return success;
    }

    private static void writeHashDbsToDisk(Document doc, Element rootEl, List<HashDb> hashDbs) {
        for (HashDb db : hashDbs) {
            // Get the path for the hash database before writing anything, in
            // case an exception is thrown.  
            String path;
            try {
                if (db.hasIndexOnly()) {
                    path = db.getIndexPath();
                } else {
                    path = db.getDatabasePath();
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting path of hash database " + db.getHashSetName() + ", discarding from hash database configuration", ex);
                continue;
            }

            Element setElement = doc.createElement(SET_ELEMENT);
            setElement.setAttribute(SET_NAME_ATTRIBUTE, db.getHashSetName());
            setElement.setAttribute(SET_TYPE_ATTRIBUTE, db.getKnownFilesType().toString());
            setElement.setAttribute(SEARCH_DURING_INGEST_ATTRIBUTE, Boolean.toString(db.getSearchDuringIngest()));
            setElement.setAttribute(SEND_INGEST_MESSAGES_ATTRIBUTE, Boolean.toString(db.getSendIngestMessages()));
            Element pathElement = doc.createElement(PATH_ELEMENT);
            pathElement.setTextContent(path);
            setElement.appendChild(pathElement);
            rootEl.appendChild(setElement);
        }
    }

    private boolean hashSetsConfigurationFileExists() {
        File f = new File(configFilePath);
        return f.exists() && f.canRead() && f.canWrite();
    }

    private boolean readHashSetsConfigurationFromDisk() {
        boolean updatedSchema = false;

        // Open the XML document that implements the configuration file.
        final Document doc = XMLUtil.loadDoc(HashDbManager.class, configFilePath, XSD_FILE_NAME);
        if (doc == null) {
            return false;
        }

        // Get the root element.
        Element root = doc.getDocumentElement();
        if (root == null) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error loading hash sets: invalid file format.");
            return false;
        }

        // Get the hash set elements.
        NodeList setsNList = root.getElementsByTagName(SET_ELEMENT);
        int numSets = setsNList.getLength();
        if (numSets == 0) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "No element hash_set exists.");
        }

        // Create HashDb objects for each hash set element. Skip to the next hash database if the definition of
        // a particular hash database is not well-formed.
        String attributeErrorMessage = " attribute was not set for hash_set at index {0}, cannot make instance of HashDb class";
        String elementErrorMessage = " element was not set for hash_set at index {0}, cannot make instance of HashDb class";
        for (int i = 0; i < numSets; ++i) {
            Element setEl = (Element) setsNList.item(i);

            String hashSetName = setEl.getAttribute(SET_NAME_ATTRIBUTE);
            if (hashSetName.isEmpty()) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SET_NAME_ATTRIBUTE + attributeErrorMessage, i);
                continue;
            }

            // Handle configurations saved before duplicate hash set names were not permitted.
            if (hashSetNames.contains(hashSetName)) {
                int suffix = 0;
                String newHashSetName;
                do {
                    ++suffix;
                    newHashSetName = hashSetName + suffix;
                } while (hashSetNames.contains(newHashSetName));
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(this.getClass(),
                        "HashDbManager.replacingDuplicateHashsetNameMsg",
                        hashSetName, newHashSetName),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.openHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                hashSetName = newHashSetName;
            }

            String knownFilesType = setEl.getAttribute(SET_TYPE_ATTRIBUTE);
            if (knownFilesType.isEmpty()) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SET_TYPE_ATTRIBUTE + attributeErrorMessage, i);
                continue;
            }

            // Handle legacy known files types.
            if (knownFilesType.equals("NSRL")) {
                knownFilesType = HashDb.KnownFilesType.KNOWN.toString();
                updatedSchema = true;
            }

            final String searchDuringIngest = setEl.getAttribute(SEARCH_DURING_INGEST_ATTRIBUTE);
            if (searchDuringIngest.isEmpty()) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SEARCH_DURING_INGEST_ATTRIBUTE + attributeErrorMessage, i);
                continue;
            }
            Boolean seearchDuringIngestFlag = Boolean.parseBoolean(searchDuringIngest);

            final String sendIngestMessages = setEl.getAttribute(SEND_INGEST_MESSAGES_ATTRIBUTE);
            if (searchDuringIngest.isEmpty()) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SEND_INGEST_MESSAGES_ATTRIBUTE + attributeErrorMessage, i);
                continue;
            }
            Boolean sendIngestMessagesFlag = Boolean.parseBoolean(sendIngestMessages);

            String dbPath;
            NodeList pathsNList = setEl.getElementsByTagName(PATH_ELEMENT);
            if (pathsNList.getLength() > 0) {
                Element pathEl = (Element) pathsNList.item(0); // Shouldn't be more than one.

                // Check for legacy path number attribute.
                String legacyPathNumber = pathEl.getAttribute(LEGACY_PATH_NUMBER_ATTRIBUTE);
                if (null != legacyPathNumber && !legacyPathNumber.isEmpty()) {
                    updatedSchema = true;
                }

                dbPath = pathEl.getTextContent();
                if (dbPath.isEmpty()) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, PATH_ELEMENT + elementErrorMessage, i);
                    continue;
                }
            } else {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, PATH_ELEMENT + elementErrorMessage, i);
                continue;
            }
            dbPath = getValidFilePath(hashSetName, dbPath);

            if (null != dbPath) {
                try {
                    addExistingHashDatabaseInternal(hashSetName, dbPath, seearchDuringIngestFlag, sendIngestMessagesFlag, HashDb.KnownFilesType.valueOf(knownFilesType));
                } catch (HashDbManagerException | TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash database", ex);
                    JOptionPane.showMessageDialog(null,
                            NbBundle.getMessage(this.getClass(),
                            "HashDbManager.unableToOpenHashDbMsg", dbPath),
                            NbBundle.getMessage(this.getClass(), "HashDbManager.openHashDbErr"),
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "No valid path for hash_set at index {0}, cannot make instance of HashDb class", i);
            }
        }

        // Get the element that stores the always calculate hashes flag.
        NodeList calcList = root.getElementsByTagName(ALWAYS_CALCULATE_HASHES_ELEMENT);
        if (calcList.getLength() > 0) {
            Element calcEl = (Element) calcList.item(0); // Shouldn't be more than one.
            final String value = calcEl.getAttribute(VALUE_ATTRIBUTE);
            alwaysCalculateHashes = Boolean.parseBoolean(value);
        } else {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, " element ");
            alwaysCalculateHashes = true;
        }

        if (updatedSchema) {
            String backupFilePath = configFilePath + ".v1_backup";
            String messageBoxTitle = NbBundle.getMessage(this.getClass(),
                    "HashDbManager.msgBoxTitle.confFileFmtChanged");
            String baseMessage = NbBundle.getMessage(this.getClass(),
                    "HashDbManager.baseMessage.updatedFormatHashDbConfig");
            try {
                FileUtils.copyFile(new File(configFilePath), new File(backupFilePath));
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(this.getClass(),
                        "HashDbManager.savedBackupOfOldConfigMsg",
                        baseMessage, backupFilePath),
                        messageBoxTitle,
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "Failed to save backup of old format configuration file to " + backupFilePath, ex);
                JOptionPane.showMessageDialog(null, baseMessage, messageBoxTitle, JOptionPane.INFORMATION_MESSAGE);
            }

            writeHashSetConfigurationToDisk();
        }

        return true;
    }

    private String getValidFilePath(String hashSetName, String configuredPath) {
        // Check the configured path.
        File database = new File(configuredPath);
        if (database.exists()) {
            return configuredPath;
        }

        // Give the user an opportunity to find the desired file.
        String newPath = null;
        if (JOptionPane.showConfirmDialog(null,
                NbBundle.getMessage(this.getClass(), "HashDbManager.dlgMsg.dbNotFoundAtLoc",
                hashSetName, configuredPath),
                NbBundle.getMessage(this.getClass(), "HashDbManager.dlgTitle.MissingDb"),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            newPath = searchForFile();
            if (null != newPath && !newPath.isEmpty()) {
                database = new File(newPath);
                if (!database.exists()) {
                    newPath = null;
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
        String[] EXTENSION = new String[]{"txt", "idx", "hash", "Hash", "kdb"};
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "HashDbManager.fileNameExtensionFilter.title"), EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                filePath = f.getCanonicalPath();
            } catch (IOException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "Couldn't get selected file path", ex);
            }
        }
        return filePath;
    }

    /**
     * Instances of this class represent hash databases used to classify files
     * as known or know bad.
     */
    public static class HashDb {

        /**
         * Indicates how files with hashes stored in a particular hash database
         * object should be classified.
         */
        public enum KnownFilesType {

            KNOWN(NbBundle.getMessage(HashDbManager.class, "HashDbManager.known.text")),
            KNOWN_BAD(NbBundle.getMessage(HashDbManager.class, "HashDbManager.knownBad.text"));
            private String displayName;

            private KnownFilesType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return this.displayName;
            }
        }

        /**
         * Property change events published by hash database objects.
         */
        public enum Event {

            INDEXING_DONE
        }
        private int handle;
        private String hashSetName;
        private boolean searchDuringIngest;
        private boolean sendIngestMessages;
        private KnownFilesType knownFilesType;
        private boolean indexing;
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

        private HashDb(int handle, String hashSetName, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) {
            this.handle = handle;
            this.hashSetName = hashSetName;
            this.searchDuringIngest = useForIngest;
            this.sendIngestMessages = sendHitMessages;
            this.knownFilesType = knownFilesType;
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

        public String getDatabasePath() throws TskCoreException {
            return SleuthkitJNI.getHashDatabasePath(handle);
        }

        public String getIndexPath() throws TskCoreException {
            return SleuthkitJNI.getHashDatabaseIndexPath(handle);
        }

        public KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        public boolean getSearchDuringIngest() {
            return searchDuringIngest;
        }

        void setSearchDuringIngest(boolean useForIngest) {
            this.searchDuringIngest = useForIngest;
        }

        public boolean getSendIngestMessages() {
            return sendIngestMessages;
        }

        void setSendIngestMessages(boolean showInboxMessages) {
            this.sendIngestMessages = showInboxMessages;
        }

        /**
         * Indicates whether the hash database accepts updates.
         *
         * @return True if the database accepts updates, false otherwise.
         */
        public boolean isUpdateable() throws TskCoreException {
            return SleuthkitJNI.isUpdateableHashDatabase(this.handle);
        }

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         * are to be added to the hash database.
         * @throws TskCoreException
         */
        public void addHashes(Content content) throws TskCoreException {
            addHashes(content, null);
        }

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         * are to be added to the hash database.
         * @param comment A comment to associate with the hashes, e.g., the name
         * of the case in which the content was encountered.
         * @throws TskCoreException
         */
        public void addHashes(Content content, String comment) throws TskCoreException {
            // This only works for AbstractFiles and MD5 hashes at present. 
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    SleuthkitJNI.addToHashDatabase(null, file.getMd5Hash(), null, null, comment, handle);
                }
            }
        }

        /**
         * Adds a list of hashes to the hash database at once
         *
         * @param hashes List of hashes
         * @throws TskCoreException
         */
        public void addHashes(List<HashEntry> hashes) throws TskCoreException {
            SleuthkitJNI.addToHashDatabase(hashes, handle);
        }

        public boolean hasMd5HashOf(Content content) throws TskCoreException {
            boolean result = false;
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    result = SleuthkitJNI.lookupInHashDatabase(file.getMd5Hash(), handle);
                }
            }
            return result;
        }

        public HashInfo lookUp(Content content) throws TskCoreException {
            HashInfo result = null;
            // This only works for AbstractFiles and MD5 hashes at present. 
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    result = SleuthkitJNI.lookupInHashDatabaseVerbose(file.getMd5Hash(), handle);
                }
            }
            return result;
        }

        boolean hasIndex() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);
        }

        boolean hasIndexOnly() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseIsIndexOnly(handle);
        }

        boolean canBeReIndexed() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseCanBeReindexed(handle);
        }

        boolean isIndexing() {
            return indexing;
        }

        private void close() throws TskCoreException {
            SleuthkitJNI.closeHashDatabase(handle);
        }
    }

    /**
     * Worker thread to make an index of a database
     */
    private class HashDbIndexer extends SwingWorker<Object, Void> {

        private ProgressHandle progress = null;
        private HashDb hashDb = null;

        HashDbIndexer(HashDb hashDb) {
            this.hashDb = hashDb;
        }

        ;

        @Override
        protected Object doInBackground() {
            hashDb.indexing = true;
            progress = ProgressHandleFactory.createHandle(
                    NbBundle.getMessage(this.getClass(), "HashDbManager.progress.indexingHashSet", hashDb.hashSetName));
            progress.start();
            progress.switchToIndeterminate();
            try {
                SleuthkitJNI.createLookupIndexForHashDatabase(hashDb.handle);
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDb.class.getName()).log(Level.SEVERE, "Error indexing hash database", ex);
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(this.getClass(),
                        "HashDbManager.dlgMsg.errorIndexingHashSet",
                        hashDb.getHashSetName()),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.hashDbIndexingErr"),
                        JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }

        @Override
        protected void done() {
            hashDb.indexing = false;
            progress.finish();

            // see if we got any errors
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Error creating index", ex);
                MessageNotifyUtil.Notify.show("Error creating index",
                        "Error creating index: " + ex.getMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
            }

            try {
                hashDb.propertyChangeSupport.firePropertyChange(HashDb.Event.INDEXING_DONE.toString(), null, hashDb);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "HashDbManager listener threw exception", e);
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
    }
}