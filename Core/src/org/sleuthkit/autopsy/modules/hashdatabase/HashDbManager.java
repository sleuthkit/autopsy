/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoFileInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoFileSet;
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
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb.KnownFilesType;

/**
 * This class implements a singleton that manages the set of hash databases used
 * to classify files as unknown, known or notable.
 */
public class HashDbManager implements PropertyChangeListener {

    private static final String HASH_DATABASE_FILE_EXTENSON = "kdb"; //NON-NLS
    private static HashDbManager instance = null;
    private List<HashDb> hashSets = new ArrayList<>();
    private Set<String> hashSetNames = new HashSet<>();
    private Set<String> hashSetPaths = new HashSet<>();

    private List<HashDb> officialHashSets = new ArrayList<>();
    private Set<String> officialHashSetNames = new HashSet<>();
    private Set<String> officialHashSetPaths = new HashSet<>();

    PropertyChangeSupport changeSupport = new PropertyChangeSupport(HashDbManager.class);
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());
    private boolean allDatabasesLoadedCorrectly = false;

    private static final String OFFICIAL_HASH_SETS_FOLDER = "OfficialHashSets";
    private static final String KDB_EXT = "kdb";

    private static final String DB_NAME_PARAM = "dbName";
    private static final String KNOWN_STATUS_PARAM = "knownStatus";
    private static final Pattern OFFICIAL_FILENAME = Pattern.compile("(?<" + DB_NAME_PARAM + ">.+?)\\.(?<" + KNOWN_STATUS_PARAM + ">.+?)\\." + KDB_EXT);
    
    private final JFileChooserFactory chooserHelper;

    private static final FilenameFilter DEFAULT_KDB_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith("." + KDB_EXT);
        }
    };

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

    synchronized boolean verifyAllDatabasesLoadedCorrectly() {
        return allDatabasesLoadedCorrectly;
    }

    private HashDbManager() {
        chooserHelper = new JFileChooserFactory();
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
     * classify files as known or notable and saves the configuration.
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

            checkDbCollision(path, hashSetName);

            hashDb = addHashDatabase(SleuthkitJNI.openHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }
        return hashDb;
    }

    /**
     * Adds a new hash database to the set of hash databases used to classify
     * files as known or notable and saves the configuration.
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

            checkDbCollision(path, hashSetName);

            hashDb = addHashDatabase(SleuthkitJNI.createHashDatabase(path), hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);
        } catch (TskCoreException ex) {
            throw new HashDbManagerException(ex.getMessage());
        }
        return hashDb;
    }

    /**
     * Throws an exception if the provided path or hashSetName already belong to
     * an existing database.
     *
     * @param path        The path.
     * @param hashSetName The hash set name.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDbManagerException
     * @throws MissingResourceException
     */
    private void checkDbCollision(String path, String hashSetName) throws HashDbManagerException, MissingResourceException {
        if (hashSetPaths.contains(path) || officialHashSetPaths.contains(path)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.hashDbAlreadyAddedExceptionMsg", path));
        }

        if (hashSetNames.contains(hashSetName) || officialHashSetNames.contains(hashSetName)) {
            throw new HashDbManagerException(NbBundle.getMessage(HashDbManager.class, "HashDbManager.duplicateHashSetNameExceptionMsg", hashSetName));
        }
    }

    private SleuthkitHashSet addHashDatabase(int handle, String hashSetName, boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType) throws TskCoreException {
        // Wrap an object around the handle.
        SleuthkitHashSet hashDb = new SleuthkitHashSet(handle, hashSetName, searchDuringIngest, sendIngestMessages, knownFilesType);

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

        // Add the hash database to the collection
        hashSets.add(hashDb);

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

    CentralRepoHashSet addExistingCentralRepoHashSet(String hashSetName, String version, int referenceSetID,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDb.KnownFilesType knownFilesType,
            boolean readOnly) throws TskCoreException {

        if (!CentralRepository.isEnabled()) {
            throw new TskCoreException("Could not load central repository hash set " + hashSetName + " - central repository is not enabled");
        }

        CentralRepoHashSet db = new CentralRepoHashSet(hashSetName, version, referenceSetID, searchDuringIngest,
                sendIngestMessages, knownFilesType, readOnly);

        if (!db.isValid()) {
            throw new TskCoreException("Error finding hash set " + hashSetName + " in central repository");
        }

        // Add the hash database to the collection
        if(!hashSets.contains(db)) {
            hashSets.add(db);
        
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
        }
        return db;

    }

    synchronized void indexHashDatabase(SleuthkitHashSet hashDb) {
        hashDb.addPropertyChangeListener(this);
        HashDbIndexer creator = new HashDbIndexer(hashDb);
        creator.execute();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals(SleuthkitHashSet.Event.INDEXING_DONE.name())) {
            SleuthkitHashSet hashDb = (SleuthkitHashSet) event.getNewValue();
            if (null != hashDb) {
                try {
                    String indexPath = hashDb.getIndexPath();
                    if (!indexPath.equals("None")) { //NON-NLS
                        hashSetPaths.add(indexPath);
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDb.getHashSetName() + " hash set after indexing", ex); //NON-NLS
                }
            }
        }
    }

    /**
     * Removes a hash database from the set of hash databases used to classify
     * files as known or notable and saves the configuration.
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
        hashSetNames.remove(hashSetName);
        hashSets.remove(hashDb);

        // Now undertake the operations that could throw.
        // Indexing is only relevanet for sleuthkit hashsets
        if (hashDb instanceof SleuthkitHashSet) {
            SleuthkitHashSet hashDatabase = (SleuthkitHashSet) hashDb;
            try {
                if (hashDatabase.hasIndex()) {
                    hashSetPaths.remove(hashDatabase.getIndexPath());
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting index path of " + hashDatabase.getHashSetName() + " hash set when removing the hash set", ex); //NON-NLS
            }

            try {
                if (!hashDatabase.hasIndexOnly()) {
                    hashSetPaths.remove(hashDatabase.getDatabasePath());
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error getting hash set path of " + hashDatabase.getHashSetName() + " hash set when removing the hash set", ex); //NON-NLS
            }

            try {
                hashDatabase.close();
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + hashDb.getHashSetName() + " hash set when removing the hash set", ex); //NON-NLS
            }
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
            if (!HashLookupSettings.writeSettings(new HashLookupSettings(HashLookupSettings.convertHashSetList(this.hashSets)))) {
                throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
            }
        } catch (HashLookupSettings.HashLookupSettingsException ex) {
            throw new HashDbManagerException(NbBundle.getMessage(this.getClass(), "HashDbManager.saveErrorExceptionMsg"));
        }
    }

    /**
     * Gets all of the hash databases used to classify files as known or known
     * bad. Will add any new central repository databases to the list before
     * returning it.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getAllHashSets() {
        try {
            updateHashSetsFromCentralRepository();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error loading central repository hash sets", ex); //NON-NLS
        }

        return Stream.concat(this.officialHashSets.stream(), this.hashSets.stream())
                .collect(Collectors.toList());
    }

    /**
     * Gets all of the hash databases used to classify files as known.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getKnownFileHashSets() {
        return getAllHashSets()
                .stream()
                .filter((db) -> (db.getKnownFilesType() == HashDb.KnownFilesType.KNOWN))
                .collect(Collectors.toList());
    }

    /**
     * Gets all of the hash databases used to classify files as notable.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getKnownBadFileHashSets() {
        return getAllHashSets()
                .stream()
                .filter((db) -> (db.getKnownFilesType() == HashDb.KnownFilesType.KNOWN_BAD))
                .collect(Collectors.toList());
    }

    /**
     * Gets all of the hash databases that accept updates.
     *
     * @return A list, possibly empty, of hash databases.
     */
    public synchronized List<HashDb> getUpdateableHashSets() {
        return getUpdateableHashSets(getAllHashSets());
    }

    private List<HashDb> getUpdateableHashSets(List<HashDb> hashDbs) {
        return hashDbs
                .stream()
                .filter((HashDb db) -> {
                    try {
                        return db.isUpdateable();
                    } catch (TskCoreException ex) {
                        Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error checking updateable status of " + db.getHashSetName() + " hash set", ex); //NON-NLS
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<HashDbInfo> getCentralRepoHashSetsFromDatabase() {
        List<HashDbInfo> crHashSets = new ArrayList<>();
        if (CentralRepository.isEnabled()) {
            try {
                List<CentralRepoFileSet> crSets = CentralRepository.getInstance().getAllReferenceSets(CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID));
                for (CentralRepoFileSet globalSet : crSets) {

                    // Defaults for fields not stored in the central repository:
                    //   searchDuringIngest: false
                    //   sendIngestMessages: true if the hash set is notable
                    boolean sendIngestMessages = KnownFilesType.fromFileKnown(globalSet.getFileKnownStatus()).equals(HashDb.KnownFilesType.KNOWN_BAD);
                    crHashSets.add(new HashDbInfo(globalSet.getSetName(), globalSet.getVersion(),
                            globalSet.getGlobalSetID(), KnownFilesType.fromFileKnown(globalSet.getFileKnownStatus()), globalSet.isReadOnly(), false, sendIngestMessages));
                }
            } catch (CentralRepoException ex) {
                Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error loading central repository hash sets", ex); //NON-NLS
            }
        }
        return crHashSets;
    }

    /**
     * Restores the last saved hash sets configuration. This supports
     * cancellation of configuration panels.
     */
    public synchronized void loadLastSavedConfiguration() {
        closeHashDatabases(this.hashSets);
        hashSetNames.clear();
        hashSetPaths.clear();

        loadHashsetsConfiguration();
    }

    private void closeHashDatabases(List<HashDb> hashDatabases) {
        for (HashDb database : hashDatabases) {
            if (database instanceof SleuthkitHashSet) {
                try {
                    ((SleuthkitHashSet) database).close();
                } catch (TskCoreException ex) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error closing " + database.getHashSetName() + " hash set", ex); //NON-NLS
                }
            }
        }
        hashDatabases.clear();
    }

    private void loadHashsetsConfiguration() {
        loadOfficialHashSets();

        try {
            HashLookupSettings settings = HashLookupSettings.readSettings();
            this.configureSettings(settings, officialHashSetNames);
        } catch (HashLookupSettings.HashLookupSettingsException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Could not read Hash lookup settings from disk.", ex);
        }
    }

    /**
     * Loads official hash sets into officialHashSets and also populates
     * officialHashSetPaths and officialHashSetNames variables.
     */
    private void loadOfficialHashSets() {
        officialHashSetPaths = new HashSet<>();
        officialHashSetNames = new HashSet<>();

        try {
            officialHashSets = loadOfficialHashSetsFromFolder(OFFICIAL_HASH_SETS_FOLDER);
            officialHashSets.forEach(db -> {
                officialHashSetNames.add(db.getHashSetName());
                try {
                    String databasePath = db.getDatabasePath();
                    String indexPath = db.getIndexPath();

                    if (StringUtils.isNotBlank(databasePath) && !databasePath.equals("None")) { //NON-NLS
                        officialHashSetPaths.add(databasePath);
                    }
                    if (StringUtils.isNotBlank(indexPath) && !indexPath.equals("None")) { //NON-NLS
                        officialHashSetPaths.add(indexPath);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "There was an error loading the official hash set name.", ex);
                }
            });
        } catch (HashDbManagerException ex) {
            logger.log(Level.WARNING, "There was an error loading the official hash sets.", ex);
            officialHashSets = new ArrayList<HashDb>();
        }
    }

    /**
     * Handles a potential conflict between official and non-official hash sets.
     * Non-official hashsets have '(Custom)' added. If a conflict is identified,
     * the hashset settings are fixed, saved, reloaded, and returned. Otherwise,
     * the original list is returned.
     *
     * @param curHashsets   The list of non-official hash sets.
     * @param officialNames The set of names for official hash sets.
     *
     * @return The new list of non-official hash sets with conflicts removed.
     */
    @Messages({
        "# {0} - hashSetName",
        "HashDbManager_handleNameConflict_conflictSuffix={0} (Custom)"
    })
    private List<HashDbInfo> handleNameConflict(List<HashDbInfo> curHashsets, Set<String> officialNames) {
        Set<String> curNames = new HashSet<String>(officialNames);
        boolean change = false;
        List<HashDbInfo> newItems = new ArrayList<>();
        for (HashDbInfo hashset : curHashsets) {
            String thisName = hashset.getHashSetName();
            if (curNames.contains(thisName)) {
                while (curNames.contains(thisName)) {
                    thisName = Bundle.HashDbManager_handleNameConflict_conflictSuffix(thisName);
                }

                newItems.add(new HashDbInfo(
                        thisName,
                        hashset.getKnownFilesType(),
                        hashset.getSearchDuringIngest(),
                        hashset.getSendIngestMessages(),
                        hashset.getPath(),
                        hashset.getReferenceSetID(),
                        hashset.getVersion(),
                        hashset.isReadOnly(),
                        hashset.isCentralRepoDatabaseType()
                ));
                change = true;
            } else {
                newItems.add(hashset);
            }

            curNames.add(thisName);
        }

        if (!change) {
            return curHashsets;
        } else {
            try {
                HashLookupSettings.writeSettings(new HashLookupSettings(newItems));
                HashLookupSettings toRet = HashLookupSettings.readSettings();
                return toRet.getHashDbInfo();
            } catch (HashLookupSettings.HashLookupSettingsException ex) {
                logger.log(Level.SEVERE, "There was an error while trying to resave after name conflict.", ex);
                return newItems;
            }
        }
    }

    /**
     * Loads official hash sets from the given folder.
     *
     * @param folder The folder from which to load official hash sets.
     *
     * @return The List of found hash sets.
     *
     * @throws HashDbManagerException If folder does not exist.
     */
    private List<HashDb> loadOfficialHashSetsFromFolder(String folder) throws HashDbManagerException {
        File configFolder = InstalledFileLocator.getDefault().locate(
                folder, HashDbManager.class.getPackage().getName(), false);

        if (configFolder == null || !configFolder.exists() || !configFolder.isDirectory()) {
            throw new HashDbManagerException("Folder provided: " + folder + " does not exist.");
        }

        return Stream.of(configFolder.listFiles(DEFAULT_KDB_FILTER))
                .map((f) -> {
                    try {
                        return getOfficialHashDbFromFile(f);
                    } catch (HashDbManagerException | TskCoreException ex) {
                        logger.log(Level.WARNING, String.format("Hashset: %s could not be properly read.", f.getAbsolutePath()), ex);
                        return null;
                    }
                })
                .filter((hashdb) -> hashdb != null)
                .collect(Collectors.toList());
    }

    /**
     * Loads an official hash set from the given file.
     *
     * @param file The kdb file to load.
     *
     * @return The HashDbInfo of the official set.
     *
     * @throws HashDbManagerException If file does not exist or does not match
     *                                naming convention (See
     *                                HashDbManager.OFFICIAL_FILENAME for
     *                                regex).
     */
    private HashDb getOfficialHashDbFromFile(File file) throws HashDbManagerException, TskCoreException {
        if (file == null || !file.exists()) {
            throw new HashDbManagerException(String.format("No file found for: %s", file == null ? "<null>" : file.getAbsolutePath()));
        }
        String filename = file.getName();
        Matcher match = OFFICIAL_FILENAME.matcher(filename);
        if (!match.find()) {
            throw new HashDbManagerException(String.format("File with name: %s does not match regex of: %s", filename, OFFICIAL_FILENAME.toString()));
        }

        String hashdbName = match.group(DB_NAME_PARAM);
        final String knownStatus = match.group(KNOWN_STATUS_PARAM);

        KnownFilesType knownFilesType = Stream.of(HashDb.KnownFilesType.values())
                .filter(k -> k.getIdentifier().toUpperCase().equals(knownStatus.toUpperCase()))
                .findFirst()
                .orElseThrow(() -> new HashDbManagerException(String.format("No KnownFilesType matches %s for file: %s", knownStatus, filename)));

        return new SleuthkitHashSet(
                SleuthkitJNI.openHashDatabase(file.getAbsolutePath()),
                hashdbName,
                true, //searchDuringIngest
                false, //sendIngestMessages
                knownFilesType,
                true); // official set
    }

    /**
     * Configures the given settings object by adding all contained hash db to
     * the system.
     *
     * @param settings        The settings to configure.
     * @param officialSetNames The official set names. Any name collisions will
     *                        trigger rename for primary file.
     */
    @Messages({"# {0} - hash set name", "HashDbManager.noDbPath.message=Couldn't get valid hash set path for: {0}",
        "HashDbManager.centralRepoLoadError.message=Error loading central repository hash sets"})
    private void configureSettings(HashLookupSettings settings, Set<String> officialSetNames) {
        allDatabasesLoadedCorrectly = true;
        List<HashDbInfo> hashDbInfoList = settings.getHashDbInfo();
        hashDbInfoList = handleNameConflict(hashDbInfoList, officialSetNames);

        for (HashDbInfo hashDbInfo : hashDbInfoList) {
            configureLocalDb(hashDbInfo);
        }

        if (CentralRepository.isEnabled()) {
            configureCrDbs();
        }

        /*
         * NOTE: When RuntimeProperties.coreComponentsAreActive() is "false", I
         * don't think we should overwrite hash db settings file because we were
         * unable to load a database. The user should have to fix the issue or
         * remove the database from settings. Overwiting the settings
         * effectively removes the database from HashLookupSettings and the user
         * may not know about this because the dialogs are not being displayed.
         * The next time user starts Autopsy, HashDB will load without errors
         * and the user may think that the problem was solved.
         */
        if (!allDatabasesLoadedCorrectly && RuntimeProperties.runningWithGUI()) {
            try {
                HashLookupSettings.writeSettings(new HashLookupSettings(HashLookupSettings.convertHashSetList(this.hashSets)));
                allDatabasesLoadedCorrectly = true;
            } catch (HashLookupSettings.HashLookupSettingsException ex) {
                allDatabasesLoadedCorrectly = false;
                logger.log(Level.SEVERE, "Could not overwrite hash set settings.", ex);
            }
        }
    }

    /**
     * Configures central repository hash set databases.
     */
    private void configureCrDbs() {
        try {
            updateHashSetsFromCentralRepository();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash set", ex); //NON-NLS
            
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.HashDbManager_centralRepoLoadError_message(),
                    NbBundle.getMessage(this.getClass(), "HashDbManager.openHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            allDatabasesLoadedCorrectly = false;
        }
    }

    /**
     * Handles configuring a local hash set database.
     * @param hashDbInfo The local hash set database.
     */
    private void configureLocalDb(HashDbInfo hashDbInfo) {
        try {
            if (hashDbInfo.isFileDatabaseType()) {
                String dbPath = this.getValidFilePath(hashDbInfo.getHashSetName(), hashDbInfo.getPath());
                if (dbPath != null) {
                    addHashDatabase(SleuthkitJNI.openHashDatabase(dbPath), hashDbInfo.getHashSetName(), hashDbInfo.getSearchDuringIngest(), hashDbInfo.getSendIngestMessages(), hashDbInfo.getKnownFilesType());
                } else {
                    logger.log(Level.WARNING, Bundle.HashDbManager_noDbPath_message(hashDbInfo.getHashSetName()));
                    allDatabasesLoadedCorrectly = false;
                }
            } else {
                if (CentralRepository.isEnabled()) {
                    addExistingCentralRepoHashSet(hashDbInfo.getHashSetName(), hashDbInfo.getVersion(),
                            hashDbInfo.getReferenceSetID(),
                            hashDbInfo.getSearchDuringIngest(), hashDbInfo.getSendIngestMessages(),
                            hashDbInfo.getKnownFilesType(), hashDbInfo.isReadOnly());
                }
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error opening hash set", ex); //NON-NLS
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbManager.unableToOpenHashDbMsg", hashDbInfo.getHashSetName()),
                    NbBundle.getMessage(this.getClass(), "HashDbManager.openHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            allDatabasesLoadedCorrectly = false;
        }
    }

    private void updateHashSetsFromCentralRepository() throws TskCoreException {
        if (CentralRepository.isEnabled()) {
            List<HashDbInfo> crHashDbInfoList = getCentralRepoHashSetsFromDatabase();
            for (HashDbInfo hashDbInfo : crHashDbInfoList) {
                if (hashDbInfoIsNew(hashDbInfo)) {
                    addExistingCentralRepoHashSet(hashDbInfo.getHashSetName(), hashDbInfo.getVersion(),
                            hashDbInfo.getReferenceSetID(),
                            hashDbInfo.getSearchDuringIngest(), hashDbInfo.getSendIngestMessages(), hashDbInfo.getKnownFilesType(),
                            hashDbInfo.isReadOnly());
                }
            }
        }
    }

    private boolean hashDbInfoIsNew(HashDbInfo dbInfo) {
        for (HashDb db : this.hashSets) {
            if (dbInfo.matches(db)) {
                return false;
            }
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
        if (RuntimeProperties.runningWithGUI()
                && JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
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
        JFileChooser fc = chooserHelper.getChooser();
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

    public static abstract class HashDb {

        /**
         * Indicates how files with hashes stored in a particular hash database
         * object should be classified.
         */
        @Messages({
            "HashDbManager.noChange.text=No Change",
            "HashDbManager.known.text=Known",
            "HashDbManager.knownBad.text=Notable"
        })
        public enum KnownFilesType {

            KNOWN(Bundle.HashDbManager_known_text(), "Known", TskData.FileKnown.KNOWN, false, false),
            KNOWN_BAD(Bundle.HashDbManager_knownBad_text(), "Notable", TskData.FileKnown.BAD, true, true),
            NO_CHANGE(Bundle.HashDbManager_noChange_text(), "NoChange", TskData.FileKnown.UNKNOWN, true, false);

            private final String displayName;
            private final String identifier;
            private final TskData.FileKnown fileKnown;
            private final boolean allowSendInboxMessages;
            private final boolean defaultSendInboxMessages;

            KnownFilesType(String displayName, String identifier, TskData.FileKnown fileKnown,
                    boolean allowSendInboxMessages, boolean defaultSendInboxMessages) {

                this.displayName = displayName;
                this.identifier = identifier;
                this.fileKnown = fileKnown;
                this.allowSendInboxMessages = allowSendInboxMessages;
                this.defaultSendInboxMessages = defaultSendInboxMessages;
            }

            /**
             * Returns whether or not it is allowable to send inbox messages
             * with this known files type.
             *
             * @return Whether or not it is allowable to send inbox messages
             *         with this known files type.
             */
            boolean isInboxMessagesAllowed() {
                return allowSendInboxMessages;
            }

            /**
             * Returns whether or not by default for this type is to send inbox
             * messages.
             *
             * @return Whether or not by default for this type is to send inbox
             *         messages.
             */
            boolean isDefaultInboxMessages() {
                return defaultSendInboxMessages;
            }

            /**
             * Returns the identifier for this KnownFilesType. This is used for
             * Official Hash Sets in their naming convention.
             *
             * @return The identifier for this type.
             */
            String getIdentifier() {
                return identifier;
            }

            public String getDisplayName() {
                return this.displayName;
            }

            /**
             * Retrieves the corresponding TskData.FileKnown enum type that
             * relates to this.
             *
             * @return The corresponding TskData.FileKnown.
             */
            TskData.FileKnown getFileKnown() {
                return this.fileKnown;
            }

            /**
             * Converts a TskData.FileKnown to the corresponding KnownFilesType.
             *
             * @param fileKnown The TskData.FileKnown type.
             *
             * @return The corresponding KnownFilesType.
             */
            static KnownFilesType fromFileKnown(TskData.FileKnown fileKnown) {
                if (fileKnown == null) {
                    return null;
                }

                return Stream.of(KnownFilesType.values())
                        .filter((type) -> type.getFileKnown() == fileKnown)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown TskData.FileKnown type: " + fileKnown));
            }
        }

        /**
         * Property change events published by hash database objects.
         */
        public enum Event {

            INDEXING_DONE
        }

        public abstract String getHashSetName();

        abstract String getDisplayName();

        public abstract String getDatabasePath() throws TskCoreException;

        public abstract HashDb.KnownFilesType getKnownFilesType();

        public abstract boolean getSearchDuringIngest();

        abstract void setSearchDuringIngest(boolean useForIngest);

        public abstract boolean getSendIngestMessages();

        abstract void setSendIngestMessages(boolean showInboxMessages);

        /**
         * Indicates whether the hash database accepts updates.
         *
         * @return True if the database accepts updates, false otherwise.
         *
         * @throws org.sleuthkit.datamodel.TskCoreException
         */
        public abstract boolean isUpdateable() throws TskCoreException;

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         *                are to be added to the hash database.
         *
         * @throws TskCoreException
         */
        public abstract void addHashes(Content content) throws TskCoreException;

        public abstract void addHashes(Content content, String comment) throws TskCoreException;

        public abstract void addHashes(List<HashEntry> hashes) throws TskCoreException;

        public abstract boolean lookupMD5Quick(Content content) throws TskCoreException;

        public abstract HashHitInfo lookupMD5(Content content) throws TskCoreException;

        /**
         * Returns whether this database can be enabled. For file type, this is
         * the same as checking that it has an index
         *
         * @return true if is valid, false otherwise
         *
         * @throws TskCoreException
         */
        abstract boolean isValid() throws TskCoreException;

        public abstract String getIndexPath() throws TskCoreException;

        public abstract boolean hasIndexOnly() throws TskCoreException;

        public abstract void firePropertyChange(String propertyName, Object oldValue, Object newValue);

        public abstract void addPropertyChangeListener(PropertyChangeListener pcl);

        public abstract void removePropertyChangeListener(PropertyChangeListener pcl);

        @Override
        public abstract String toString();

    }

    /**
     * Instances of this class represent hash databases used to classify files
     * as known or know bad.
     */
    class SleuthkitHashSet extends HashDb {

        private static final long serialVersionUID = 1L;
        private final int handle;
        private final String hashSetName;
        private boolean searchDuringIngest;
        private boolean sendIngestMessages;
        private final HashDb.KnownFilesType knownFilesType;
        private boolean indexing;
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private final boolean officialSet;

        private SleuthkitHashSet(int handle, String hashSetName, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) {
            this(handle, hashSetName, useForIngest, sendHitMessages, knownFilesType, false);
        }

        private SleuthkitHashSet(int handle, String hashSetName, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType, boolean officialSet) {
            this.handle = handle;
            this.hashSetName = hashSetName;
            this.searchDuringIngest = useForIngest;
            this.sendIngestMessages = sendHitMessages;
            this.knownFilesType = knownFilesType;
            this.indexing = false;
            this.officialSet = officialSet;
        }

        /**
         * Adds a listener for the events defined in HashDb.Event. Listeners are
         * used during indexing.
         *
         * @param pcl
         */
        @Override
        public void addPropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.addPropertyChangeListener(pcl);
        }

        /**
         * Removes a listener for the events defined in HashDb.Event.
         *
         * @param pcl
         */
        @Override
        public void removePropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.removePropertyChangeListener(pcl);
        }

        int getHandle() {
            return handle;
        }

        @Override
        public String getHashSetName() {
            return hashSetName;
        }

        @Override
        String getDisplayName() {
            return getHashSetName();
        }

        @Override
        public String getDatabasePath() throws TskCoreException {
            return SleuthkitJNI.getHashDatabasePath(handle);
        }

        public void setIndexing(boolean indexing) {
            this.indexing = indexing;
        }

        @Override
        public String getIndexPath() throws TskCoreException {
            return SleuthkitJNI.getHashDatabaseIndexPath(handle);
        }

        @Override
        public KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        @Override
        public boolean getSearchDuringIngest() {
            return searchDuringIngest;
        }

        @Override
        void setSearchDuringIngest(boolean useForIngest) {
            this.searchDuringIngest = useForIngest;
        }

        @Override
        public boolean getSendIngestMessages() {
            return sendIngestMessages;
        }

        @Override
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
        @Override
        public boolean isUpdateable() throws TskCoreException {
            if (isOfficialSet()) {
                return false;
            }

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
        @Override
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
        @Override
        public void addHashes(Content content, String comment) throws TskCoreException {
            // This only works for AbstractFiles and MD5 hashes at present. 
            assert content instanceof AbstractFile;
            officialSetCheck();
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    SleuthkitJNI.addToHashDatabase(null, file.getMd5Hash(), null, null, comment, handle);
                }
            }
        }

        /**
         * Throws an exception if the current set is an official set.
         *
         * @throws TskCoreException
         */
        private void officialSetCheck() throws TskCoreException {
            if (isOfficialSet()) {
                throw new TskCoreException("Hashes cannot be added to an official set");
            }
        }

        /**
         * Adds a list of hashes to the hash database at once
         *
         * @param hashes List of hashes
         *
         * @throws TskCoreException
         */
        @Override
        public void addHashes(List<HashEntry> hashes) throws TskCoreException {
            officialSetCheck();
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
        @Override
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
        @Override
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

        /**
         * Returns whether this database can be enabled. For file type, this is
         * the same as checking that it has an index
         *
         * @return true if is valid, false otherwise
         *
         * @throws TskCoreException
         */
        @Override
        boolean isValid() throws TskCoreException {
            return hasIndex();
        }

        boolean hasIndex() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);
        }

        @Override
        public boolean hasIndexOnly() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseIsIndexOnly(handle);
        }

        boolean canBeReIndexed() throws TskCoreException {
            return SleuthkitJNI.hashDatabaseCanBeReindexed(handle);
        }

        boolean isIndexing() {
            return indexing;
        }

        @Override
        public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
            this.propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
        }

        private void close() throws TskCoreException {
            SleuthkitJNI.closeHashDatabase(handle);
        }

        @Override
        public String toString() {
            return getHashSetName();
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
            final SleuthkitHashSet other = (SleuthkitHashSet) obj;
            if (!Objects.equals(this.hashSetName, other.hashSetName)) {
                return false;
            }
            if (this.knownFilesType != other.knownFilesType) {
                return false;
            }
            return true;
        }

        /**
         * Returns whether or not the set is an official set. If the set is an
         * official set, it is treated as readonly and cannot be deleted.
         *
         * @return Whether or not the set is an official set.
         */
        boolean isOfficialSet() {
            return officialSet;
        }
    }

    /**
     * Instances of this class represent hash databases used to classify files
     * as known or know bad.
     */
    class CentralRepoHashSet extends HashDb {

        private static final long serialVersionUID = 1L;
        private final String hashSetName;
        private boolean searchDuringIngest;
        private boolean sendIngestMessages;
        private final HashDb.KnownFilesType knownFilesType;
        private final int referenceSetID;
        private final String version;
        private String orgName;
        private final boolean readOnly;
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

        @Messages({"HashDbManager.CentralRepoHashDb.orgError=Error loading organization"})
        private CentralRepoHashSet(String hashSetName, String version, int referenceSetID,
                boolean useForIngest, boolean sendHitMessages, HashDb.KnownFilesType knownFilesType,
                boolean readOnly)
                throws TskCoreException {
            this.hashSetName = hashSetName;
            this.version = version;
            this.referenceSetID = referenceSetID;
            this.searchDuringIngest = useForIngest;
            this.sendIngestMessages = sendHitMessages;
            this.knownFilesType = knownFilesType;
            this.readOnly = readOnly;

            try {
                orgName = CentralRepository.getInstance().getReferenceSetOrganization(referenceSetID).getName();
            } catch (CentralRepoException ex) {
                Logger.getLogger(SleuthkitHashSet.class.getName()).log(Level.SEVERE, "Error looking up central repository organization for reference set " + referenceSetID, ex); //NON-NLS
                orgName = Bundle.HashDbManager_CentralRepoHashDb_orgError();
            }
        }

        /**
         * Adds a listener for the events defined in HashDb.Event. Listeners are
         * used during indexing.
         *
         * @param pcl
         */
        @Override
        public void addPropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.addPropertyChangeListener(pcl);
        }

        /**
         * Removes a listener for the events defined in HashDb.Event.
         *
         * @param pcl
         */
        @Override
        public void removePropertyChangeListener(PropertyChangeListener pcl) {
            propertyChangeSupport.removePropertyChangeListener(pcl);
        }

        @Override
        public boolean hasIndexOnly() throws TskCoreException {
            return true;
        }

        @Override
        public String getHashSetName() {
            return hashSetName;
        }

        @Override
        public String getDisplayName() {
            if (!getVersion().isEmpty()) {
                return getHashSetName() + " " + getVersion() + " (remote)";
            } else {
                return getHashSetName() + " (remote)";
            }
        }

        String getVersion() {
            return version;
        }

        String getOrgName() {
            return orgName;
        }

        int getReferenceSetID() {
            return referenceSetID;
        }

        @Override
        public String getDatabasePath() throws TskCoreException {
            return "";
        }

        @Override
        public String getIndexPath() throws TskCoreException {
            return "";
        }

        @Override
        public HashDb.KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        @Override
        public boolean getSearchDuringIngest() {
            return searchDuringIngest;
        }

        @Override
        void setSearchDuringIngest(boolean useForIngest) {
            this.searchDuringIngest = useForIngest;
        }

        @Override
        public boolean getSendIngestMessages() {
            return sendIngestMessages;
        }

        @Override
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
        @Override
        public boolean isUpdateable() throws TskCoreException {
            return (!readOnly);
        }

        /**
         * Adds hashes of content (if calculated) to the hash database.
         *
         * @param content The content for which the calculated hashes, if any,
         *                are to be added to the hash database.
         *
         * @throws TskCoreException
         */
        @Override
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
        @Override
        public void addHashes(Content content, String comment) throws TskCoreException {
            // This only works for AbstractFiles and MD5 hashes at present. 
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    TskData.FileKnown type = knownFilesType.getFileKnown();

                    try {
                        CentralRepoFileInstance fileInstance = new CentralRepoFileInstance(referenceSetID, file.getMd5Hash(),
                                type, comment);
                        CentralRepository.getInstance().addReferenceInstance(fileInstance, CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID));
                    } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                        throw new TskCoreException("Error adding hashes to " + getDisplayName(), ex);	//NON-NLS
                    }
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
        @Override
        public void addHashes(List<HashEntry> hashes) throws TskCoreException {
            Set<CentralRepoFileInstance> globalFileInstances = new HashSet<>();
            for (HashEntry hashEntry : hashes) {
                TskData.FileKnown type = knownFilesType.getFileKnown();

                try {
                    globalFileInstances.add(new CentralRepoFileInstance(referenceSetID, hashEntry.getMd5Hash(), type, hashEntry.getComment()));
                } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                    throw new TskCoreException("Error adding hashes to " + getDisplayName(), ex);
                }
            }

            try {
                CentralRepository.getInstance().bulkInsertReferenceTypeEntries(globalFileInstances,
                        CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID));
            } catch (CentralRepoException ex) {
                throw new TskCoreException("Error adding hashes to " + getDisplayName(), ex);
            }
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
        @Override
        public boolean lookupMD5Quick(Content content) throws TskCoreException {
            // This only works for AbstractFiles and MD5 hashes 
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    try {
                        return CentralRepository.getInstance().isFileHashInReferenceSet(file.getMd5Hash(), this.referenceSetID);
                    } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                        Logger.getLogger(SleuthkitHashSet.class.getName()).log(Level.SEVERE, "Error performing central reposiotry hash lookup for hash "
                                + file.getMd5Hash() + " in reference set " + referenceSetID, ex); //NON-NLS
                        throw new TskCoreException("Error performing central reposiotry hash lookup", ex);
                    }
                }
            }
            return false;
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
        @Override
        public HashHitInfo lookupMD5(Content content) throws TskCoreException {
            HashHitInfo result = null;
            // This only works for AbstractFiles and MD5 hashes 
            assert content instanceof AbstractFile;
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (null != file.getMd5Hash()) {
                    try {
                        return CentralRepository.getInstance().lookupHash(file.getMd5Hash(), referenceSetID);
                    } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                        Logger.getLogger(SleuthkitHashSet.class.getName()).log(Level.SEVERE, "Error performing central reposiotry hash lookup for hash "
                                + file.getMd5Hash() + " in reference set " + referenceSetID, ex); //NON-NLS
                        throw new TskCoreException("Error performing central reposiotry hash lookup", ex);
                    }
                }
            }
            return result;
        }

        /**
         * Returns whether this database can be enabled.
         *
         * @return true if is valid, false otherwise
         */
        @Override
        boolean isValid() {
            if (!CentralRepository.isEnabled()) {
                return false;
            }
            try {
                return CentralRepository.getInstance().referenceSetIsValid(this.referenceSetID, this.hashSetName, this.version);
            } catch (CentralRepoException ex) {
                Logger.getLogger(CentralRepoHashSet.class.getName()).log(Level.SEVERE, "Error validating hash set " + hashSetName, ex); //NON-NLS
                return false;
            }
        }

        @Override
        public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
            this.propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        public int hashCode() {
            int code = 23;
            code = 47 * code + Objects.hashCode(this.hashSetName);
            code = 47 * code + Objects.hashCode(this.version);
            code = 47 * code + Integer.hashCode(this.referenceSetID);
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
            final CentralRepoHashSet other = (CentralRepoHashSet) obj;
            if (!Objects.equals(this.hashSetName, other.hashSetName)) {
                return false;
            }
            if (!Objects.equals(this.version, other.version)) {
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
        private SleuthkitHashSet hashDb = null;

        HashDbIndexer(SleuthkitHashSet hashDb) {
            this.hashDb = hashDb;
        }

        @Override
        protected Object doInBackground() {
            hashDb.setIndexing(true);
            progress = ProgressHandle.createHandle(
                    NbBundle.getMessage(this.getClass(), "HashDbManager.progress.indexingHashSet", hashDb.getHashSetName()));
            progress.start();
            progress.switchToIndeterminate();
            try {
                SleuthkitJNI.createLookupIndexForHashDatabase(hashDb.getHandle());
            } catch (TskCoreException ex) {
                Logger.getLogger(HashDbIndexer.class.getName()).log(Level.SEVERE, "Error indexing hash set " + hashDb.getHashSetName(), ex); //NON-NLS
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
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
            hashDb.setIndexing(false);
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
                hashDb.firePropertyChange(SleuthkitHashSet.Event.INDEXING_DONE.toString(), null, hashDb);
                hashDb.firePropertyChange(HashDbManager.SetEvt.DB_INDEXED.toString(), null, hashDb.getHashSetName());
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
