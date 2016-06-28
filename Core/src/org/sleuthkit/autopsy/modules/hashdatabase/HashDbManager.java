/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FilenameUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupSettings.HashDbInfo;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashEntry;
import org.sleuthkit.datamodel.HashHitInfo;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class implements a singleton that manages the set of hash databases used
 * to classify files as unknown, known or known bad.
 */
public class HashDbManager implements PropertyChangeListener {

    private static final String HASH_DATABASE_FILE_EXTENSON = "kdb"; //NON-NLS
    private static HashDbManager instance = null;
    private List<HashDb> knownHashSets = new ArrayList<>();
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private Set<String> hashSetNames = new HashSet<>();
    private Set<String> hashSetPaths = new HashSet<>();
    PropertyChangeSupport changeSupport = new PropertyChangeSupport(HashDbManager.class);
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());
    private boolean allDatabasesLoadedCorrectly = false;

    /**
     * Property change event support In events: For both of these enums, the old
     * value should be null, and the new value should be the hashset name
     * string.
     */
    public enum SetEvt {

        DB_ADDED, DB_DELETED, DB_INDEXED
    };

    /**
     * Gets the singleton instance of this class.
     *
     * @return HashDbManager The manager
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

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }
    
    synchronized boolean verifyAllDatabasesLoadedCorrectly(){
        return allDatabasesLoadedCorrectly;
    }

    private HashDbManager() {
        loadHashsetsConfiguration();
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

        private static final long serialVersionUID = 1L;

        private HashDbManagerException(String message) {
            super(message);
        }

        private HashDbManagerException(String message, Throwable exception) {
            super(message, exception);
        }
    }

    /**
     * Adds an existing hash database to the set of hash databases used to
     * classify files as known or known bad and saves the configuration.
     *
     * @param hashSetName        Name used to represent the hash database in
     *                           user interface components.
     * @param path               Full path to either a hash database file or a
     *                           hash database index file.
     * @param searchDuringIngest A flag indicating whether or not the hash
     *                           database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     *                           should be sent as ingest messages.
     * @param knownFilesType     The classification to apply to files whose
     *                           hashes are found in the hash database.
     *
     * @return A HashDb representing the hash database.
     *
     * @throws HashDbManagerException
     */
    public synchronized HashDb addExistingHashDatabase(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {
        HashDb hashDb = null;
        hashDb = this.addExistingHashDatabaseNoSave(hashSetName, path, searchDuringIngest, sendIngestMessages, knownFilesType);
        this.save();
        return hashDb;
    }

    synchronized HashDb addExistingHashDatabaseNoSave(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {
        HashDb hashDb = null;
        try {
            if (!new File(path).exists()) {
                throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbDoesNotExistExceptionMsg", path));
            }

            if (hashSetPaths.contains(path)) {
                throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbAlreadyAddedExceptionMsg", path));
            }

            if (hashSetNames.contains(hashSetName)) {
                throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.duplicateHashSetNameExceptionMsg", hashSetName));
            }

            hashDb = addHashDatabase(SleuthkitJNI.openHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }
        return hashDb;
    }

    /**
     * Adds a new hash database to the set of hash databases used to classify
     * files as known or known bad and saves the configuration.
     *
     * @param hashSetName        Hash set name used to represent the hash
     *                           database in user interface components.
     * @param path               Full path to the database file to be created.
     * @param searchDuringIngest A flag indicating whether or not the hash
     *                           database should be searched during ingest.
     * @param sendIngestMessages A flag indicating whether hash set hit messages
     *                           should be sent as ingest messages.
     * @param knownFilesType     The classification to apply to files whose
     *                           hashes are found in the hash database.
     *
     * @return A HashDb representing the hash database.
     *
     * @throws HashDbManagerException
     */
    public synchronized HashDb addNewHashDatabase(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages,
            HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {

        HashDb hashDb = null;
        hashDb = this.addNewHashDatabaseNoSave(hashSetName, path, searchDuringIngest, sendIngestMessages, knownFilesType);

        this.save();

        return hashDb;
    }

    public synchronized HashDb addNewHashDatabaseNoSave(String hashSetName, String path, boolean searchDuringIngest, boolean sendIngestMessages,
            HashDb.KnownFilesType knownFilesType) throws HashDbManagerException {
        HashDb hashDb = null;
        try {
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

            hashDb = addHashDatabase(SleuthkitJNI.createHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }
        return hashDb;
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
        if (!databasePath.equals("None")) { //NON-NLS
            hashSetPaths.add(databasePath);
        }
        if (!indexPath.equals("None")) { //NON-NLS
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
            logger.log(Level.SEVERE, "HashDbManager listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(
                    NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErr"),
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
                    if (!indexPath.equals("None")) { //NON-NLS
                        hashSetPaths.add(indexPath);
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDb.getHashSetName() + " hash database after indexing", ex); //NON-NLS
                }
            }
        }
    }

    /**
     * Removes a hash database from the set of hash databases used to classify
     * files as known or known bad and saves the configuration.
     *
     * @param hashDb
     *
     * @throws HashDbManagerException
     */
    public synchronized void removeHashDatabase(HashDb hashDb) throws HashDbManagerException {
        this.removeHashDatabaseNoSave(hashDb);
        this.save();
    }
    
    public synchronized void removeHashDatabaseNoSave(HashDb hashDb) throws HashDbManagerException {
        // Don't remove a database if ingest is running
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();
        if (ingestIsRunning) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.ingestRunningExceptionMsg"));
        }
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
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDb.getHashSetName() + " hash database when removing the database", ex); //NON-NLS
        }
        try {
            if (!hashDb.hasIndexOnly()) {
                hashSetPaths.remove(hashDb.getDatabasePath());
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting database path of " + hashDb.getHashSetName() + " hash database when removing the database", ex); //NON-NLS
        }
        try {
            hashDb.close();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + hashDb.getHashSetName() + " hash database when removing the database", ex); //NON-NLS
        }

        // Let any external listeners know that a set has been deleted
        try {
            changeSupport.firePropertyChange(SetEvt.DB_DELETED.toString(), null, hashSetName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "HashDbManager listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(
                    NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErr"),
                    NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    void save() throws HashDbManagerException {
        try {
            if (!HashLookupSettings.writeSettings(new HashLookupSettings(this.knownHashSets, this.knownBadHashSets))) {
                throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
            }
        } catch (HashLookupSettings.HashLookupSettingsException ex) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
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
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error checking updateable status of " + db.getHashSetName() + " hash database", ex); //NON-NLS
            }
        }
        return updateableDbs;
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

        loadHashsetsConfiguration();
    }

    private void closeHashDatabases(List<HashDb> hashDatabases) {
        for (HashDb database : hashDatabases) {
            try {
                database.close();
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + database.getHashSetName() + " hash database", ex); //NON-NLS
            }
        }
        hashDatabases.clear();
    }

    private void loadHashsetsConfiguration() {
        try {
            HashLookupSettings settings = HashLookupSettings.readSettings();
            this.configureSettings(settings);
        } catch (HashLookupSettings.HashLookupSettingsException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Could not read Hash lookup settings from disk.", ex);
        }
    }

    /**
     * Configures the given settings object by adding all contained hash db to
     * the system.
     *
     * @param settings The settings to configure.
     */
    @Messages({"# {0} - database name", "HashDbManager.noDbPath.message=Couldn't get valid database path for: {0}"})
    private void configureSettings(HashLookupSettings settings) {
        allDatabasesLoadedCorrectly = true;
        List<HashDbInfo> hashDbInfoList = settings.getHashDbInfo();
        for (HashDbInfo hashDb : hashDbInfoList) {
            try {
                String dbPath = this.getValidFilePath(hashDb.getHashSetName(), hashDb.getPath());
                if (dbPath != null) {
                    addHashDatabase(SleuthkitJNI.openHashDatabase(dbPath), hashDb.getHashSetName(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), hashDb.getKnownFilesType());
                } else {
                    logger.log(Level.WARNING, Bundle.HashDbManager_noDbPath_message(hashDb.getHashSetName()));
                    allDatabasesLoadedCorrectly = false;
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash database", ex); //NON-NLS
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbManager.unableToOpenHashDbMsg", hashDb.getHashSetName()),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.openHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                allDatabasesLoadedCorrectly = false;
            }
        }
        
        /* NOTE: When RuntimeProperties.coreComponentsAreActive() is "false", 
        I don't think we should overwrite hash db settings file because we 
        were unable to load a database. The user should have to fix the issue or 
        remove the database from settings. Overwiting the settings effectively removes 
        the database from HashLookupSettings and the user may not know about this 
        because the dialogs are not being displayed. The next time user starts Autopsy, HashDB 
        will load without errors and the user may think that the problem was solved.*/
        if (!allDatabasesLoadedCorrectly && RuntimeProperties.coreComponentsAreActive()) {
            try {
                HashLookupSettings.writeSettings(new HashLookupSettings(this.knownHashSets, this.knownBadHashSets));
                allDatabasesLoadedCorrectly = true;
            } catch (HashLookupSettings.HashLookupSettingsException ex) {
                allDatabasesLoadedCorrectly = false;
                logger.log(Level.SEVERE, "Could not overwrite hash database settings.", ex);
            }
        }
    }

    private String getValidFilePath(String hashSetName, String configuredPath) {
        // Check the configured path.
        File database = new File(configuredPath);
        if (database.exists()) {
            return configuredPath;
        }

        // Give the user an opportunity to find the desired file.
        String newPath = null;
        if (RuntimeProperties.coreComponentsAreActive() && 
                JOptionPane.showConfirmDialog(null,
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
        String[] EXTENSION = new String[]{"txt", "idx", "hash", "Hash", "kdb"}; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "HashDbManager.fileNameExtensionFilter.title"), EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                filePath = f.getCanonicalPath();
            } catch (IOException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "Couldn't get selected file path", ex); //NON-NLS
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
            private final String displayName;

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
        private static final long serialVersionUID = 1L;
        private final int handle;
        private final String hashSetName;
        private boolean searchDuringIngest;
        private boolean sendIngestMessages;
        private final KnownFilesType knownFilesType;
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
         *
         * @param pcl
         */
        public void addPropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.addPropertyChangeListener(pcl);
        }

        /**
         * Removes a listener for the events defined in HashDb.Event.
         *
         * @param pcl
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
         *
         * @throws org.sleuthkit.datamodel.TskCoreException
         */
        public boolean isUpdateable() throws TskCoreException {
            return SleuthkitJNI.isUpdateableHashDatabase(this.handle);
        }

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         *                are to be added to the hash database.
         *
         * @throws TskCoreException
         */
        public void addHashes(Content content) throws TskCoreException {
            addHashes(content, null);
        }

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         *                are to be added to the hash database.
         * @param comment A comment to associate with the hashes, e.g., the name
         *                of the case in which the content was encountered.
         *
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
         *
         * @throws TskCoreException
         */
        public void addHashes(List<HashEntry> hashes) throws TskCoreException {
            SleuthkitJNI.addToHashDatabase(hashes, handle);
        }

        /**
         * Perform a basic boolean lookup of the file's hash.
         *
         * @param content
         *
         * @return True if file's MD5 is in the hash database
         *
         * @throws TskCoreException
         */
        public boolean lookupMD5Quick(Content content) throws TskCoreException {
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

        /**
         * Lookup hash value in DB and provide details on file.
         *
         * @param content
         *
         * @return null if file is not in database.
         *
         * @throws TskCoreException
         */
        public HashHitInfo lookupMD5(Content content) throws TskCoreException {
            HashHitInfo result = null;
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

        public boolean hasIndexOnly() throws TskCoreException {
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

        @Override
        public int hashCode() {
            int code = 23;
            code = 47 * code + Integer.hashCode(handle);
            code = 47 * code + Objects.hashCode(this.hashSetName);
            code = 47 * code + Objects.hashCode(this.propertyChangeSupport);
            code = 47 * code + Objects.hashCode(this.knownFilesType);
            return code;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HashDb other = (HashDb) obj;
            if (!Objects.equals(this.hashSetName, other.hashSetName)) {
                return false;
            }
            if (this.searchDuringIngest != other.searchDuringIngest) {
                return false;
            }
            if (this.sendIngestMessages != other.sendIngestMessages) {
                return false;
            }
            if (this.knownFilesType != other.knownFilesType) {
                return false;
            }
            return true;
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
            progress = ProgressHandle.createHandle(
                    NbBundle.getMessage(this.getClass(), "HashDbManager.progress.indexingHashSet", hashDb.hashSetName));
            progress.start();
            progress.switchToIndeterminate();
            try {
                SleuthkitJNI.createLookupIndexForHashDatabase(hashDb.handle);
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDb.class.getName()).log(Level.SEVERE, "Error indexing hash database", ex); //NON-NLS
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
                logger.log(Level.SEVERE, "Error creating index", ex); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "HashDbManager.errCreatingIndex.title"),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.errCreatingIndex.msg", ex.getMessage()),
                        MessageNotifyUtil.MessageType.ERROR);
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            }

            try {
                hashDb.propertyChangeSupport.firePropertyChange(HashDb.Event.INDEXING_DONE.toString(), null, hashDb);
                hashDb.propertyChangeSupport.firePropertyChange(HashDbManager.SetEvt.DB_INDEXED.toString(), null, hashDb.getHashSetName());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "HashDbManager listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErr"),
                        NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
    }
}
