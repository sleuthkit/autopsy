/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 - 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.zip.CRC32;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import org.apache.commons.io.FileUtils;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.keywordsearch.KeywordListsManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestSettingsPanel.UpdateConfigSwingWorker;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;

/*
 * A utility class for loading and saving shared configuration data
 */
public class SharedConfiguration {

    // Files
    private static final String AUTO_MODE_CONTEXT_FILE = "AutoModeContext.properties"; //NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.settings"; //NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE_LEGACY = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String INTERESTING_FILES_SET_DEFS_FILE = "InterestingFileSets.settings"; //NON-NLS
    private static final String INTERESTING_FILES_SET_DEFS_FILE_LEGACY = "InterestingFilesSetDefs.xml"; //NON-NLS
    private static final String KEYWORD_SEARCH_SETTINGS = "keywords.settings"; //NON-NLS
    private static final String KEYWORD_SEARCH_SETTINGS_LEGACY = "keywords.xml"; //NON-NLS
    private static final String KEYWORD_SEARCH_GENERAL_LEGACY = "KeywordSearch.properties"; //NON-NLS
    private static final String KEYWORD_SEARCH_NSRL_LEGACY = "KeywordSearch_NSRL.properties"; //NON-NLS
    private static final String KEYWORD_SEARCH_OPTIONS_LEGACY = "KeywordSearch_Options.properties"; //NON-NLS
    private static final String KEYWORD_SEARCH_SCRIPTS_LEGACY = "KeywordSearch_Scripts.properties"; //NON-NLS
    private static final String FILE_EXT_MISMATCH_SETTINGS = "mismatch_config.settings"; //NON-NLS
    private static final String FILE_EXT_MISMATCH_SETTINGS_LEGACY = "mismatch_config.xml"; //NON-NLS
    private static final String ANDROID_TRIAGE = "AndroidTriage_Options.properties"; //NON-NLS
    private static final String AUTO_INGEST_PROPERTIES = "AutoIngest.properties"; //NON-NLS
    private static final String HASHDB_CONFIG_FILE_NAME = "hashLookup.settings"; //NON-NLS
    private static final String HASHDB_CONFIG_FILE_NAME_LEGACY = "hashsets.xml"; //NON-NLS
    public static final String FILE_EXPORTER_SETTINGS_FILE = "fileexporter.settings"; //NON-NLS
    private static final String CENTRAL_REPOSITORY_PROPERTIES_FILE = "CentralRepository.properties"; //NON-NLS
    private static final String SHARED_CONFIG_VERSIONS = "SharedConfigVersions.txt"; //NON-NLS

    // Folders
    private static final String AUTO_MODE_FOLDER = "AutoModeContext"; //NON-NLS
    private static final String REMOTE_HASH_FOLDER = "hashDb"; //NON-NLS
    public static final String FILE_EXPORTER_FOLDER = "Automated File Exporter"; //NON-NLS

    private static final String UPLOAD_IN_PROGRESS_FILE = "uploadInProgress"; // NON-NLS
    private static final String moduleDirPath = PlatformUtil.getUserConfigDirectory();
    private static final String SHARED_DIR_PATH = PlatformUtil.getModuleConfigDirectory();
    private static final String INGEST_MODULES_PATH = Paths.get(SHARED_DIR_PATH, "IngestSettings").toString();
    private static final String INGEST_MODULES_REL_PATH = new File(moduleDirPath).toURI().relativize(new File(INGEST_MODULES_PATH).toURI()).getPath();
    private static final Logger logger = Logger.getLogger(SharedConfiguration.class.getName());
    private static final String CENTRAL_REPO_DIR_PATH = Paths.get(SHARED_DIR_PATH, "CentralRepository").toAbsolutePath().toString();
    private static final String HASH_SETTINGS_PATH = Paths.get(SHARED_DIR_PATH, "HashLookup").toAbsolutePath().toString();
    private static final String VIEW_PREFERENCE_FILE = "ViewPreferences.properties";
    private static final String MACHINE_SPECIFIC_PREFERENCE_FILE = "MachineSpecificPreferences.properties";
    private static final String MODE_PREFERENCE_FILE = "ModePreferences.properties";
    private static final String EXTERNAL_SERVICE_PREFERENCE_FILE = "ExternalServicePreferences.properties";
    
    
    private final UpdateConfigSwingWorker swingWorker;
    private UserPreferences.SelectedMode mode;
    private String sharedConfigFolder;
    private int fileIngestThreads;
    private boolean sharedConfigMaster;
    private boolean showToolsWarning;
    private boolean displayLocalTime;
    private boolean hideKnownFilesInDataSource;
    private boolean hideKnownFilesInViews;
    private boolean hideSlackFilesInDataSource;
    private boolean hideSlackFilesInViews;
    private boolean keepPreferredViewer;

    

    /**
     * Exception type thrown by shared configuration.
     */
    public final static class SharedConfigurationException extends Exception {

        private static final long serialVersionUID = 1L;

        private SharedConfigurationException(String message) {
            super(message);
        }

        private SharedConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Using this so we can indicate whether a read/write failed because the lock file is present,
    // which we need to know to display the correct message in the auto-ingest dashboard about why
    // processing has been paused.
    public enum SharedConfigResult {

        SUCCESS, LOCKED
    }

    public SharedConfiguration() {
        swingWorker = null;
    }

    /**
     * Construct with a SwingWorker reference to allow status update messages to
     * go to the GUI
     *
     * @param worker
     */
    public SharedConfiguration(UpdateConfigSwingWorker worker) {
        this.swingWorker = worker;
    }

    private void publishTask(String task) {
        if (swingWorker != null) {
            swingWorker.publishStatus(task);
        }
    }

    /**
     * Upload the current multi-user ingest settings to a shared folder.
     *
     * @return
     *
     * @throws SharedConfigurationException
     * @throws CoordinationServiceException
     * @throws InterruptedException
     */
    public SharedConfigResult uploadConfiguration() throws SharedConfigurationException, CoordinationServiceException, InterruptedException {
        publishTask("Starting shared configuration upload");

        File remoteFolder = getSharedFolder();

        try (Lock writeLock = CoordinationService.getInstance().tryGetExclusiveLock(CategoryNode.CONFIG, remoteFolder.getAbsolutePath(), 30, TimeUnit.MINUTES)) {
            if (writeLock == null) {
                logger.log(Level.INFO, String.format("Failed to lock %s - another node is currently uploading or downloading configuration", remoteFolder.getAbsolutePath()));
                return SharedConfigResult.LOCKED;
            }

            // Make sure the local settings have been initialized
            if (!isConfigFolderPopulated(new File(moduleDirPath), false)) {
                logger.log(Level.INFO, "Local configuration has not been initialized.");
                throw new SharedConfigurationException("Local configuration has not been initialized. Please verify/update and save the settings using the Ingest Module Settings button and then retry the upload.");
            }

            // Write a file to indicate that uploading is in progress. If we crash or
            // have an error, this file will remain in the shared folder.
            File uploadInProgress = new File(remoteFolder, UPLOAD_IN_PROGRESS_FILE);
            if (!uploadInProgress.exists()) {
                try {
                    Files.createFile(uploadInProgress.toPath());
                } catch (IOException ex) {
                    throw new SharedConfigurationException(String.format("Failed to create %s", uploadInProgress.toPath()), ex);
                }
            }

            // Make sure all recent changes are saved to the preference file
            // Current testing suggests that we do not need to do this for the ingest settings
            // because there is a longer delay between setting them and copying the files.
            try {
                // Make sure all recent changes are saved to the preference file
                // Current testing suggests that we do not need to do this for the ingest settings
                // because there is a longer delay between setting them and copying the files.
                UserPreferences.saveToStorage();
            } catch (BackingStoreException ex) {
                throw new SharedConfigurationException("Failed to save shared configuration settings", ex);
            }

            uploadAutoModeContextSettings(remoteFolder);
            uploadEnabledModulesSettings(remoteFolder);
            uploadFileTypeSettings(remoteFolder);
            uploadInterestingFilesSettings(remoteFolder);
            uploadKeywordSearchSettings(remoteFolder);
            uploadFileExtMismatchSettings(remoteFolder);
            uploadAndroidTriageSettings(remoteFolder);
            uploadMultiUserAndGeneralSettings(remoteFolder);
            uploadHashDbSettings(remoteFolder);
            uploadFileExporterSettings(remoteFolder);
            uploadCentralRepositorySettings(remoteFolder);
            uploadObjectDetectionClassifiers(remoteFolder);
            uploadPythonModules(remoteFolder);
            uploadYARASetting(remoteFolder);

            try {
                Files.deleteIfExists(uploadInProgress.toPath());
            } catch (IOException ex) {
                throw new SharedConfigurationException(String.format("Failed to delete %s", uploadInProgress.toPath()), ex);
            }
        }

        return SharedConfigResult.SUCCESS;
    }

    /**
     * Download the multi-user settings from a shared folder.
     *
     * @return
     *
     * @throws SharedConfigurationException
     * @throws InterruptedException
     */
    public synchronized SharedConfigResult downloadConfiguration() throws SharedConfigurationException, InterruptedException {
        publishTask("Starting shared configuration download");

        // Save local settings that should not get overwritten
        saveNonSharedSettings();

        File remoteFolder = getSharedFolder();

        try (Lock readLock = CoordinationService.getInstance().tryGetSharedLock(CategoryNode.CONFIG, remoteFolder.getAbsolutePath(), 30, TimeUnit.MINUTES)) {
            if (readLock == null) {
                return SharedConfigResult.LOCKED;
            }

            // Make sure the shared configuration was last uploaded successfully
            File uploadInProgress = new File(remoteFolder, UPLOAD_IN_PROGRESS_FILE);
            if (uploadInProgress.exists()) {
                logger.log(Level.INFO, String.format("Shared configuration folder %s is corrupt - re-upload configuration", remoteFolder.getAbsolutePath()));
                throw new SharedConfigurationException(String.format("Shared configuration folder %s is corrupt - re-upload configuration", remoteFolder.getAbsolutePath()));
            }

            // Make sure the shared configuration folder isn't empty
            if (!isConfigFolderPopulated(remoteFolder, true)) {
                logger.log(Level.INFO, String.format("Shared configuration folder %s is missing files / may be empty. Aborting download.", remoteFolder.getAbsolutePath()));
                throw new SharedConfigurationException(String.format("Shared configuration folder %s is missing files / may be empty. Aborting download.", remoteFolder.getAbsolutePath()));
            }

            try {
                /*
                 * Make sure all recent changes are saved to the preference
                 * file. This also releases open file handles to the preference
                 * files. If this is not done, then occasionally downloading of
                 * shared configuration fails silently, likely because Java/OS
                 * is still holding the file handle. The problem manifests
                 * itself by some of the old/original configuration files
                 * sticking around after shared configuration has seemingly been
                 * successfully updated.
                 */
                UserPreferences.saveToStorage();
            } catch (BackingStoreException ex) {
                throw new SharedConfigurationException("Failed to save shared configuration settings", ex);
            }

            downloadAutoModeContextSettings(remoteFolder);
            downloadEnabledModuleSettings(remoteFolder);
            downloadFileTypeSettings(remoteFolder);
            downloadInterestingFilesSettings(remoteFolder);
            downloadKeywordSearchSettings(remoteFolder);
            downloadFileExtMismatchSettings(remoteFolder);
            downloadAndroidTriageSettings(remoteFolder);
            downloadFileExporterSettings(remoteFolder);
            downloadCentralRepositorySettings(remoteFolder);
            downloadObjectDetectionClassifiers(remoteFolder);
            downloadPythonModules(remoteFolder);
            downloadYARASettings(remoteFolder);

            // Download general settings, then restore the current
            // values for the unshared fields
            downloadMultiUserAndGeneralSettings(remoteFolder);
            try {
                UserPreferences.reloadFromStorage();
            } catch (BackingStoreException ex) {
                throw new SharedConfigurationException("Failed to read shared configuration settings", ex);
            }

            restoreNonSharedSettings();
            downloadHashDbSettings(remoteFolder);
        } catch (CoordinationServiceException ex) {
            throw new SharedConfigurationException(String.format("Coordination service error acquiring exclusive lock on shared configuration source %s", remoteFolder.getAbsolutePath()), ex);
        }

        // Check Solr service
        if (!isServiceUp(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString())) {
            throw new SharedConfigurationException("Keyword search service is down");
        }

        // Check PostgreSQL service
        if (!isServiceUp(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString())) {
            throw new SharedConfigurationException("Case database server is down");
        }

        // Check ActiveMQ service
        if (!isServiceUp(ServicesMonitor.Service.MESSAGING.toString())) {
            throw new SharedConfigurationException("Messaging service is down");
        }

        // Check input folder permissions
        String inputFolder = AutoIngestUserPreferences.getAutoModeImageFolder();
        if (!FileUtil.hasReadWriteAccess(Paths.get(inputFolder))) {
            throw new SharedConfigurationException("Cannot read input folder " + inputFolder + ". Check that the folder exists and that you have permissions to access it.");
        }

        // Check output folder permissions
        String outputFolder = AutoIngestUserPreferences.getAutoModeResultsFolder();
        if (!FileUtil.hasReadWriteAccess(Paths.get(outputFolder))) {
            throw new SharedConfigurationException("Cannot read output folder " + outputFolder + ". Check that the folder exists and that you have permissions to access it.");
        }

        return SharedConfigResult.SUCCESS;
    }

    /**
     * Tests service of interest to verify that it is running.
     *
     * @param serviceName Name of the service.
     *
     * @return True if the service is running, false otherwise.
     */
    private boolean isServiceUp(String serviceName) {
        try {
            return (ServicesMonitor.getInstance().getServiceStatus(serviceName).equals(ServicesMonitor.ServiceStatus.UP.toString()));
        } catch (ServicesMonitor.ServicesMonitorException ex) {
            logger.log(Level.SEVERE, String.format("Problem checking service status for %s", serviceName), ex);
            return false;
        }
    }

    /**
     * Save any settings that should not be overwritten by the shared
     * configuration.
     */
    private void saveNonSharedSettings() {
        sharedConfigMaster = AutoIngestUserPreferences.getSharedConfigMaster();
        sharedConfigFolder = AutoIngestUserPreferences.getSharedConfigFolder();
        showToolsWarning = AutoIngestUserPreferences.getShowToolsWarning();
        displayLocalTime = UserPreferences.displayTimesInLocalTime();
        hideKnownFilesInDataSource = UserPreferences.hideKnownFilesInDataSourcesTree();
        hideKnownFilesInViews = UserPreferences.hideKnownFilesInViewsTree();
        keepPreferredViewer = UserPreferences.keepPreferredContentViewer();
        fileIngestThreads = UserPreferences.numberOfFileIngestThreads();
        hideSlackFilesInDataSource = UserPreferences.hideSlackFilesInDataSourcesTree();
        hideSlackFilesInViews = UserPreferences.hideSlackFilesInViewsTree();
    }

    /**
     * Restore the settings that may have been overwritten.
     */
    private void restoreNonSharedSettings() {
        AutoIngestUserPreferences.setSharedConfigFolder(sharedConfigFolder);
        AutoIngestUserPreferences.setSharedConfigMaster(sharedConfigMaster);
        AutoIngestUserPreferences.setShowToolsWarning(showToolsWarning);
        UserPreferences.setDisplayTimesInLocalTime(displayLocalTime);
        UserPreferences.setHideKnownFilesInDataSourcesTree(hideKnownFilesInDataSource);
        UserPreferences.setHideKnownFilesInViewsTree(hideKnownFilesInViews);
        UserPreferences.setKeepPreferredContentViewer(keepPreferredViewer);
        UserPreferences.setNumberOfFileIngestThreads(fileIngestThreads);
        UserPreferences.setHideSlackFilesInDataSourcesTree(hideSlackFilesInDataSource);
        UserPreferences.setHideSlackFilesInViewsTree(hideSlackFilesInViews);
    }

    /**
     * Get the base folder being used to store the shared config settings.
     *
     * @return The shared configuration folder
     *
     * @throws SharedConfigurationException
     */
    private static File getSharedFolder() throws SharedConfigurationException {
        // Check that the shared folder is set and exists
        String remoteConfigFolderPath = AutoIngestUserPreferences.getSharedConfigFolder();
        if (remoteConfigFolderPath.isEmpty()) {
            logger.log(Level.SEVERE, "Shared configuration folder is not set.");
            throw new SharedConfigurationException("Shared configuration folder is not set.");
        }
        File remoteFolder = new File(remoteConfigFolderPath);
        if (!remoteFolder.exists()) {
            logger.log(Level.SEVERE, "Shared configuration folder {0} does not exist", remoteConfigFolderPath);
            throw new SharedConfigurationException("Shared configuration folder " + remoteConfigFolderPath + " does not exist");
        }
        return remoteFolder;
    }

    /**
     * Do a basic check to determine whether settings have been stored to the
     * given folder. There may still be missing files/errors, but this will stop
     * empty/corrupt settings from overwriting local settings or being uploaded.
     *
     * Currently we check for: - A non-empty AutoModeContext folder - An
     * AutoModeContext properties file
     *
     * @param folder         Folder to check the contents of
     * @param isSharedFolder True if the folder being tested is the shared
     *                       folder, false if its the local folder
     *
     * @return true if the folder appears to have been initialized, false
     *         otherwise
     */
    private static boolean isConfigFolderPopulated(File folder, boolean isSharedFolder) {

        if (!folder.exists()) {
            return false;
        }

        // Check that the context directory exists and is not empty
        File contextDir;
        if (isSharedFolder) {
            contextDir = Paths.get(folder.getAbsolutePath(), INGEST_MODULES_REL_PATH, AUTO_MODE_FOLDER).toFile();
        } else {
            IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
            contextDir = ingestJobSettings.getSavedModuleSettingsFolder().toFile();
        }

        if ((!contextDir.exists()) || (!contextDir.isDirectory())) {
            return false;
        }
        if (contextDir.listFiles().length == 0) {
            return false;
        }

        // Check that the automode context properties file exists
        File contextProperties = Paths.get(folder.getAbsolutePath(), INGEST_MODULES_REL_PATH, AUTO_MODE_CONTEXT_FILE).toFile();
        return contextProperties.exists();
    }

    /**
     * Copy a local settings file to the remote folder.
     *
     * @param fileName      Name of the file to copy
     * @param localFolder   Local settings folder
     * @param remoteFolder  Shared settings folder
     * @param missingFileOk True if it's not an error if the source file is not
     *                      found
     *
     * @throws SharedConfigurationException
     */
    private static void copyToRemoteFolder(String fileName, String localFolder, File remoteFolder, boolean missingFileOk) throws SharedConfigurationException {
        logger.log(Level.INFO, "Uploading {0} to {1}", new Object[]{fileName, remoteFolder.getAbsolutePath()});
        File localFile = new File(localFolder, fileName);
        if (!localFile.exists()) {
            Path deleteRemote = Paths.get(remoteFolder.toString(), fileName);
            try {
                if (deleteRemote.toFile().exists()) {
                    deleteRemote.toFile().delete();
                }
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Shared configuration {0} does not exist on local node, but unable to remove remote copy", fileName);
                throw new SharedConfigurationException("Shared configuration file " + deleteRemote.toString() + " could not be deleted.");
            }
            if (!missingFileOk) {
                logger.log(Level.SEVERE, "Local configuration file {0} does not exist", localFile.getAbsolutePath());
                throw new SharedConfigurationException("Local configuration file " + localFile.getAbsolutePath() + " does not exist");
            } else {
                logger.log(Level.INFO, "Local configuration file {0} does not exist", localFile.getAbsolutePath());
                return;
            }
        }

        try {
            FileUtils.copyFileToDirectory(localFile, remoteFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", localFile.getAbsolutePath(), remoteFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Copy a shared settings file to the local settings folder.
     *
     * @param fileName      Name of the file to copy
     * @param localFolder   Local settings folder
     * @param remoteFolder  Shared settings folder
     * @param missingFileOk True if it's not an error if the source file is not
     *                      found
     *
     * @throws SharedConfigurationException
     */
    private static void copyToLocalFolder(String fileName, String localFolder, File remoteFolder, boolean missingFileOk) throws SharedConfigurationException {
        logger.log(Level.INFO, "Downloading {0} from {1}", new Object[]{fileName, remoteFolder.getAbsolutePath()});

        File remoteFile = new File(remoteFolder, fileName);
        if (!remoteFile.exists()) {
            Path deleteLocal = Paths.get(localFolder, fileName);
            try {
                if (deleteLocal.toFile().exists()) {
                    deleteLocal.toFile().delete();
                }
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Shared configuration {0} does not exist on remote node, but unable to remove local copy", fileName);
                throw new SharedConfigurationException("Shared configuration file " + deleteLocal.toString() + " could not be deleted.");
            }
            if (!missingFileOk) {
                logger.log(Level.SEVERE, "Shared configuration file {0} does not exist", remoteFile.getAbsolutePath());
                throw new SharedConfigurationException("Shared configuration file " + remoteFile.getAbsolutePath() + " does not exist");
            } else {
                logger.log(Level.INFO, "Shared configuration file {0} does not exist", remoteFile.getAbsolutePath());
                return;
            }
        }

        File localSettingsFolder = new File(localFolder);
        try {
            FileUtils.copyFileToDirectory(remoteFile, localSettingsFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", remoteFile.getAbsolutePath(), localSettingsFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Copy an entire local settings folder to the remote folder, deleting any
     * existing files.
     *
     * @param localFolder      The local folder to copy
     * @param remoteBaseFolder The remote folder that will hold a copy of the
     *                         original folder
     *
     * @throws SharedConfigurationException
     */
    private void copyLocalFolderToRemoteFolder(File localFolder, File remoteBaseFolder) throws SharedConfigurationException {
        logger.log(Level.INFO, "Uploading {0} to {1}", new Object[]{localFolder.getAbsolutePath(), remoteBaseFolder.getAbsolutePath()});

        File newRemoteFolder = new File(remoteBaseFolder, localFolder.getName());

        if (newRemoteFolder.exists()) {
            try {
                FileUtils.deleteDirectory(newRemoteFolder);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to delete remote folder {0}", newRemoteFolder.getAbsolutePath());
                throw new SharedConfigurationException(String.format("Failed to delete remote folder {0}", newRemoteFolder.getAbsolutePath()), ex);
            }
        }

        try {
            FileUtils.copyDirectoryToDirectory(localFolder, remoteBaseFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", localFolder, remoteBaseFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Copy an entire remote settings folder to the local folder, deleting any
     * existing files. No error if the remote folder does not exist.
     *
     * @param localFolder      The local folder that will be overwritten.
     * @param remoteBaseFolder The remote folder holding the folder that will be
     *                         copied
     *
     * @throws SharedConfigurationException
     */
    private void copyRemoteFolderToLocalFolder(File localFolder, File remoteBaseFolder) throws SharedConfigurationException {
        logger.log(Level.INFO, "Downloading {0} from {1}", new Object[]{localFolder.getAbsolutePath(), remoteBaseFolder.getAbsolutePath()});

        // Clean out the local folder regardless of whether the remote version exists. leave the 
        // folder in place since Autopsy expects it to exist.
        if (localFolder.exists()) {
            try {
                FileUtils.cleanDirectory(localFolder);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to delete files from local folder {0}", localFolder.getAbsolutePath());
                throw new SharedConfigurationException(String.format("Failed to delete files from local folder {0}", localFolder.getAbsolutePath()), ex);
            }
        }

        File remoteSubFolder = new File(remoteBaseFolder, localFolder.getName());
        if (!remoteSubFolder.exists()) {
            logger.log(Level.INFO, "{0} does not exist", remoteSubFolder.getAbsolutePath());
            return;
        }

        try {
            FileUtils.copyDirectory(remoteSubFolder, localFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s from %s", localFolder, remoteBaseFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Upload the basic set of auto-ingest settings to the shared folder.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws Exception
     */
    private void uploadAutoModeContextSettings(File remoteFolder) throws SharedConfigurationException {
        logger.log(Level.INFO, "Uploading shared configuration to {0}", remoteFolder.getAbsolutePath());
        publishTask("Uploading AutoModeContext configuration files");

        // Make a subfolder
        File remoteAutoConfFolder = Paths.get(remoteFolder.getAbsolutePath(), INGEST_MODULES_REL_PATH, AUTO_MODE_FOLDER).toFile();
        try {
            if (remoteAutoConfFolder.exists()) {
                FileUtils.deleteDirectory(remoteAutoConfFolder);
            }
            Files.createDirectories(remoteAutoConfFolder.toPath());
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Failed to create clean shared configuration subfolder " + remoteAutoConfFolder.getAbsolutePath(), ex); //NON-NLS
            throw new SharedConfigurationException("Failed to create clean shared configuration subfolder " + remoteAutoConfFolder.getAbsolutePath());
        }

        IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
        File localFolder = ingestJobSettings.getSavedModuleSettingsFolder().toFile();

        if (!localFolder.exists()) {
            logger.log(Level.SEVERE, "Local configuration folder {0} does not exist", localFolder.getAbsolutePath());
            throw new SharedConfigurationException("Local configuration folder " + localFolder.getAbsolutePath() + " does not exist");
        }

        try {
            FileUtils.copyDirectory(localFolder, remoteAutoConfFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", localFolder.getAbsolutePath(), remoteAutoConfFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Download the basic set of auto-ingest settings from the shared folder
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadAutoModeContextSettings(File remoteFolder) throws SharedConfigurationException {
        logger.log(Level.INFO, "Downloading shared configuration from {0}", remoteFolder.getAbsolutePath());
        publishTask("Downloading AutoModeContext configuration files");

        // Check that the remote subfolder exists
        File remoteAutoConfFolder = Paths.get(remoteFolder.getAbsolutePath(), INGEST_MODULES_REL_PATH, AUTO_MODE_FOLDER).toFile();
        if (!remoteAutoConfFolder.exists()) {
            logger.log(Level.SEVERE, "Shared configuration folder {0} does not exist", remoteAutoConfFolder.getAbsolutePath());
            throw new SharedConfigurationException("Shared configuration folder " + remoteAutoConfFolder.getAbsolutePath() + " does not exist");
        }

        // Get/create the local subfolder
        IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
        File localFolder = ingestJobSettings.getSavedModuleSettingsFolder().toFile();

        try {
            if (localFolder.exists()) {
                FileUtils.deleteDirectory(localFolder);
            }
            Files.createDirectories(localFolder.toPath());
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Failed to create clean local configuration folder " + localFolder.getAbsolutePath(), ex); //NON-NLS
            throw new SharedConfigurationException("Failed to create clean local configuration folder " + localFolder.getAbsolutePath());
        }

        try {
            FileUtils.copyDirectory(remoteAutoConfFolder, localFolder);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", remoteFolder.getAbsolutePath(), localFolder.getAbsolutePath()), ex);
        }
    }

    /**
     * Upload settings file containing enabled ingest modules.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadEnabledModulesSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading enabled module configuration");
        copyToRemoteFolder(AUTO_MODE_CONTEXT_FILE, INGEST_MODULES_PATH, Paths.get(remoteFolder.getAbsolutePath(), INGEST_MODULES_REL_PATH).toFile(), false);
    }

    /**
     * Download settings file containing enabled ingest modules.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadEnabledModuleSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading enabled module configuration");
        copyToLocalFolder(AUTO_MODE_CONTEXT_FILE, INGEST_MODULES_PATH, Paths.get(remoteFolder.getAbsolutePath(), INGEST_MODULES_REL_PATH).toFile(), false);
    }

    /**
     * Upload settings file containing file type settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadFileTypeSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading FileType module configuration");
        copyToRemoteFolder(USER_DEFINED_TYPE_DEFINITIONS_FILE, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(USER_DEFINED_TYPE_DEFINITIONS_FILE_LEGACY, moduleDirPath, remoteFolder, true);
    }

    /**
     * Download settings file containing file type settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadFileTypeSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading FileType module configuration");
        copyToLocalFolder(USER_DEFINED_TYPE_DEFINITIONS_FILE, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(USER_DEFINED_TYPE_DEFINITIONS_FILE_LEGACY, moduleDirPath, remoteFolder, true);
    }

    /**
     * Upload settings for the interesting files module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadInterestingFilesSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading InterestingFiles module configuration");
        copyToRemoteFolder(INTERESTING_FILES_SET_DEFS_FILE_LEGACY, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(INTERESTING_FILES_SET_DEFS_FILE, SHARED_DIR_PATH, remoteFolder, true);
    }

    /**
     * Download settings for the interesting files module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadInterestingFilesSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading InterestingFiles module configuration");
        copyToLocalFolder(INTERESTING_FILES_SET_DEFS_FILE_LEGACY, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(INTERESTING_FILES_SET_DEFS_FILE, SHARED_DIR_PATH, remoteFolder, true);
    }

    /**
     * Upload settings for the keyword search module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadKeywordSearchSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading KeywordSearch module configuration");
        copyToRemoteFolder(KEYWORD_SEARCH_SETTINGS, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(KEYWORD_SEARCH_SETTINGS_LEGACY, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(KEYWORD_SEARCH_GENERAL_LEGACY, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(KEYWORD_SEARCH_NSRL_LEGACY, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(KEYWORD_SEARCH_OPTIONS_LEGACY, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(KEYWORD_SEARCH_SCRIPTS_LEGACY, moduleDirPath, remoteFolder, true);
    }

    /**
     * Download settings for the keyword search module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadKeywordSearchSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading KeywordSearch module configuration");
        copyToLocalFolder(KEYWORD_SEARCH_SETTINGS, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(KEYWORD_SEARCH_SETTINGS_LEGACY, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(KEYWORD_SEARCH_GENERAL_LEGACY, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(KEYWORD_SEARCH_NSRL_LEGACY, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(KEYWORD_SEARCH_OPTIONS_LEGACY, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(KEYWORD_SEARCH_SCRIPTS_LEGACY, moduleDirPath, remoteFolder, true);
        KeywordListsManager.reloadKeywordLists();
    }

    /**
     * Upload settings for the file extension mismatch module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadFileExtMismatchSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading File Extension Mismatch module configuration");
        copyToRemoteFolder(FILE_EXT_MISMATCH_SETTINGS, moduleDirPath, remoteFolder, true);
        copyToRemoteFolder(FILE_EXT_MISMATCH_SETTINGS_LEGACY, moduleDirPath, remoteFolder, false);
    }

    /**
     * Download settings for the file extension mismatch module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadFileExtMismatchSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading File Extension Mismatch module configuration");
        copyToLocalFolder(FILE_EXT_MISMATCH_SETTINGS, moduleDirPath, remoteFolder, true);
        copyToLocalFolder(FILE_EXT_MISMATCH_SETTINGS_LEGACY, moduleDirPath, remoteFolder, false);
    }

    /**
     * Upload settings for the android triage module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadAndroidTriageSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading Android Triage module configuration");
        copyToRemoteFolder(ANDROID_TRIAGE, moduleDirPath, remoteFolder, true);
    }

    /**
     * Download settings for the android triage module.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadAndroidTriageSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading Android Triage module configuration");
        copyToLocalFolder(ANDROID_TRIAGE, moduleDirPath, remoteFolder, true);
    }

    /**
     * Upload File Exporter settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadFileExporterSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading File Exporter configuration");
        File fileExporterFolder = new File(moduleDirPath, FILE_EXPORTER_FOLDER);
        copyToRemoteFolder(FILE_EXPORTER_SETTINGS_FILE, fileExporterFolder.getAbsolutePath(), remoteFolder, true);
    }

    /**
     * Download File Exporter settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadFileExporterSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading File Exporter configuration");
        File fileExporterFolder = new File(moduleDirPath, FILE_EXPORTER_FOLDER);
        copyToLocalFolder(FILE_EXPORTER_SETTINGS_FILE, fileExporterFolder.getAbsolutePath(), remoteFolder, true);
    }

    /**
     * Upload central repository settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadCentralRepositorySettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading central repository configuration");
        copyToRemoteFolder(CENTRAL_REPOSITORY_PROPERTIES_FILE, CENTRAL_REPO_DIR_PATH, remoteFolder, true);
    }

    /**
     * Download central repository settings.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadCentralRepositorySettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading central repository configuration");
        copyToLocalFolder(CENTRAL_REPOSITORY_PROPERTIES_FILE, CENTRAL_REPO_DIR_PATH, remoteFolder, true);
    }

    /**
     * Upload multi-user settings and other general Autopsy settings
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadMultiUserAndGeneralSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading multi user configuration");
        
        copyToRemoteFolder(VIEW_PREFERENCE_FILE, SHARED_DIR_PATH, remoteFolder, false);
        copyToRemoteFolder(MACHINE_SPECIFIC_PREFERENCE_FILE, moduleDirPath, remoteFolder, false);
        copyToRemoteFolder(MODE_PREFERENCE_FILE, moduleDirPath, remoteFolder, false);
        copyToRemoteFolder(EXTERNAL_SERVICE_PREFERENCE_FILE, SHARED_DIR_PATH, remoteFolder, false);

        copyToRemoteFolder(AUTO_INGEST_PROPERTIES, moduleDirPath, remoteFolder, false);
    }

    /**
     * Download multi-user settings and other general Autopsy settings
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadMultiUserAndGeneralSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading multi user configuration");
        
        copyToLocalFolder(VIEW_PREFERENCE_FILE, SHARED_DIR_PATH, remoteFolder, false);
        copyToLocalFolder(MACHINE_SPECIFIC_PREFERENCE_FILE, moduleDirPath, remoteFolder, false);
        copyToLocalFolder(MODE_PREFERENCE_FILE, moduleDirPath, remoteFolder, false);
        copyToLocalFolder(EXTERNAL_SERVICE_PREFERENCE_FILE, SHARED_DIR_PATH, remoteFolder, false);
        
        copyToLocalFolder(AUTO_INGEST_PROPERTIES, moduleDirPath, remoteFolder, false);
    }

    /**
     * Upload the object detection classifiers.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadObjectDetectionClassifiers(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading object detection classfiers");
        File classifiersFolder = new File(PlatformUtil.getObjectDetectionClassifierPath());
        copyLocalFolderToRemoteFolder(classifiersFolder, remoteFolder);
    }

    /**
     * Download the object detection classifiers.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadObjectDetectionClassifiers(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading object detection classfiers");
        File classifiersFolder = new File(PlatformUtil.getObjectDetectionClassifierPath());
        copyRemoteFolderToLocalFolder(classifiersFolder, remoteFolder);
    }

    /**
     * Upload the Python modules.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadPythonModules(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading python modules");
        File classifiersFolder = new File(PlatformUtil.getUserPythonModulesPath());
        copyLocalFolderToRemoteFolder(classifiersFolder, remoteFolder);
    }

    /**
     * Download the Python modules.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadPythonModules(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading python modules");
        File classifiersFolder = new File(PlatformUtil.getUserPythonModulesPath());
        copyRemoteFolderToLocalFolder(classifiersFolder, remoteFolder);
    }

    /**
     * Upload settings and hash databases to the shared folder. The general
     * algorithm is: - Copy the general settings in hashsets.xml - For each hash
     * database listed in hashsets.xml: - Calculate the CRC of the database - If
     * the CRC does not match the one listed for that database in the shared
     * folder, (or if no entry exists), copy the database - Store the CRCs for
     * each database in the shared folder and locally
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void uploadHashDbSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading HashDb module configuration");

        // Keep track of everything being uploaded
        File localVersionFile = new File(moduleDirPath, SHARED_CONFIG_VERSIONS);
        File sharedVersionFile = new File(remoteFolder, SHARED_CONFIG_VERSIONS);
        Map<String, String> newVersions = new HashMap<>();
        Map<String, String> sharedVersions = readVersionsFromFile(sharedVersionFile);

        // Copy the settings file
        copyToRemoteFolder(HASHDB_CONFIG_FILE_NAME, HASH_SETTINGS_PATH, remoteFolder, true);
        copyToRemoteFolder(HASHDB_CONFIG_FILE_NAME_LEGACY, HASH_SETTINGS_PATH, remoteFolder, true);

        // Get the list of databases from the file
        List<String> databases = getHashFileNamesFromSettingsFile();
        for (String fullPathToDbFile : databases) {

            // Compare the CRC of the local copy with what is stored in the shared folder
            publishTask("Deciding whether to upload " + fullPathToDbFile);
            String crc = calculateCRC(fullPathToDbFile);

            // Determine full path to db file in remote folder
            String sharedName = convertLocalDbPathToShared(fullPathToDbFile);
            File sharedDbBaseFolder = new File(remoteFolder, REMOTE_HASH_FOLDER);
            File sharedDb = new File(sharedDbBaseFolder, sharedName);

            if (!(sharedVersions.containsKey(fullPathToDbFile)
                    && sharedVersions.get(fullPathToDbFile).equals(crc)
                    && sharedDb.exists())) {

                publishTask("Uploading " + fullPathToDbFile);
                File sharedDbPath = sharedDb.getParentFile();

                if (!sharedDbPath.exists()) {
                    if (!sharedDbPath.mkdirs()) {
                        throw new SharedConfigurationException("Error creating shared hash set directory " + sharedDbPath.getAbsolutePath());
                    }
                }

                File dbFile = new File(fullPathToDbFile);
                // copy hash db file to the remote folder
                copyFile(sharedDbPath, dbFile);

                // check whether the hash db has an index file (.idx) that should also be copied.
                // NOTE: only text hash databases (.txt, .hash, .Hash) can have index file.
                // it is possible that the hash db file itself is the index file
                String fullPathToIndexFile = "";
                if (fullPathToDbFile.endsWith(".txt") || fullPathToDbFile.endsWith(".hash") || fullPathToDbFile.endsWith(".Hash")) {
                    // check whether index file for this text database is present
                    // For example, if text db name is "hash_db.txt" then index file name will be "hash_db.txt-md5.idx"
                    fullPathToIndexFile = fullPathToDbFile + "-md5.idx";

                    // if index file exists, copy it to the remote location
                    File dbIndexFile = new File(fullPathToIndexFile);
                    if (dbIndexFile.exists()) {
                        // copy index file to the remote folder
                        copyFile(sharedDbPath, dbIndexFile);
                    } else {
                        fullPathToIndexFile = "";
                    }
                } else if (fullPathToDbFile.endsWith(".idx")) {
                    // hash db file itself is the index file and it has already been copied to the remote location
                    fullPathToIndexFile = fullPathToDbFile;
                }

                // check whether "index of the index" file exists for this hash DB's index file.
                // NOTE: "index of the index" file may only exist
                // for text hash database index files (i.e ".idx" extension).  The index of the 
                // index file will always have the same name as the index file, 
                // distinguished only by the "2" in the extension. "index of the index" file
                // is optional and may not be present. 
                if (fullPathToIndexFile.endsWith(".idx")) {
                    String fullPathToIndexOfIndexFile = fullPathToIndexFile + "2"; // "index of the index" file has same file name and extension ".idx2"
                    File dbIndexOfIndexFile = new File(fullPathToIndexOfIndexFile);
                    if (dbIndexOfIndexFile.exists()) {
                        // copy index of the index file to the remote folder
                        copyFile(sharedDbPath, dbIndexOfIndexFile);
                    }
                }
            }

            newVersions.put(fullPathToDbFile, crc);
        }

        // Write the versions of all uploaded files to a file (make local and shared copies)
        writeVersionsToFile(localVersionFile, newVersions);
        writeVersionsToFile(sharedVersionFile, newVersions);
    }

    /**
     * Utility method to copy a file
     *
     * @param sharedDbPath File object of the folder to copy to
     * @param dbFile       File object of the file to copy
     *
     * @throws
     * org.sleuthkit.autopsy.configuration.SharedConfiguration.SharedConfigurationException
     */
    private void copyFile(File sharedDbPath, File dbFile) throws SharedConfigurationException {
        try {
            Path path = Paths.get(sharedDbPath.toString(), dbFile.getName());
            if (path.toFile().exists()) {
                path.toFile().delete();
            }
            FileUtils.copyFileToDirectory(dbFile, sharedDbPath);
        } catch (IOException | SecurityException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", dbFile.getAbsolutePath(), sharedDbPath.getAbsolutePath()), ex);
        }
    }

    /**
     * Upload settings and hash databases to the shared folder. The general
     * algorithm is: - Copy the general settings in hashsets.xml - For each hash
     * database listed in hashsets.xml: - Compare the recorded CRC in the shared
     * directory with the one in the local directory - If different, download
     * the database - Update the local list of database CRCs Note that databases
     * are downloaded to the exact path they were uploaded from.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws SharedConfigurationException
     */
    private void downloadHashDbSettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading HashDb module configuration");

        // Read in the current local and shared database versions
        File localVersionFile = new File(moduleDirPath, SHARED_CONFIG_VERSIONS);
        File remoteVersionFile = new File(remoteFolder, SHARED_CONFIG_VERSIONS);
        Map<String, String> localVersions = readVersionsFromFile(localVersionFile);
        Map<String, String> remoteVersions = readVersionsFromFile(remoteVersionFile);

        /*
         * Iterate through remote list If local needs it, download
         *
         * Download remote settings files to local Download remote versions file
         * to local HashDbManager reload
         */
        File localDb = new File("");
        File sharedDb = new File("");
        try {
            for (String path : remoteVersions.keySet()) {
                localDb = new File(path);
                if ((!localVersions.containsKey(path))
                        || (!localVersions.get(path).equals(remoteVersions.get(path)))
                        || !localDb.exists()) {
                    // Need to download a new copy if
                    //  - We have no entry for the database in the local versions file
                    //  - The CRC in the local versions file does not match the one in the shared file
                    //  - Local copy of the database does not exist

                    if (localDb.exists()) {
                        String crc = calculateCRC(path);
                        if (crc.equals(remoteVersions.get(path))) {
                            // Can skip the download if the local disk has it 
                            // but it's just not in the versions file. This will
                            // be populated just before refreshing HashDbManager.
                            continue;
                        }
                    }

                    publishTask("Downloading " + path);
                    String sharedName = convertLocalDbPathToShared(path);
                    File sharedDbBaseFolder = new File(remoteFolder, REMOTE_HASH_FOLDER);
                    sharedDb = new File(sharedDbBaseFolder, sharedName);

                    if (!localDb.getParentFile().exists()) {
                        if (!localDb.getParentFile().mkdirs()) {
                            throw new SharedConfigurationException("Error creating hash set directory " + localDb.getParentFile().getAbsolutePath());
                        }
                    }

                    // If a copy of the database is loaded, close it before deleting and copying.
                    if (localDb.exists()) {
                        List<HashDbManager.HashDb> hashDbs = HashDbManager.getInstance().getAllHashSets();
                        HashDbManager.HashDb matchingDb = null;
                        for (HashDbManager.HashDb db : hashDbs) {
                            try {
                                if (localDb.getAbsolutePath().equals(db.getDatabasePath()) || localDb.getAbsolutePath().equals(db.getIndexPath())) {
                                    matchingDb = db;
                                    break;
                                }
                            } catch (TskCoreException ex) {
                                throw new SharedConfigurationException(String.format("Error getting hash set path info for %s", localDb.getParentFile().getAbsolutePath()), ex);
                            }
                        }

                        if (matchingDb != null) {
                            try {
                                HashDbManager.getInstance().removeHashDatabase(matchingDb);
                            } catch (HashDbManager.HashDbManagerException ex) {
                                throw new SharedConfigurationException(String.format("Error updating hash set info for %s", localDb.getAbsolutePath()), ex);
                            }

                        }
                        if (localDb.exists()) {
                            localDb.delete();
                        }
                    }
                    FileUtils.copyFile(sharedDb, localDb);

                    // check whether the hash db has an index file (.idx) that should also be copied.
                    // NOTE: only text hash databases (.txt, .hash, .Hash) can have index file. 
                    // it is possible that the hash db file itself is the index file
                    String fullPathToRemoteDbFile = sharedDb.getPath();
                    String fullPathToRemoteIndexFile = "";
                    String fullPathToLocalIndexFile = "";
                    if (fullPathToRemoteDbFile.endsWith(".txt") || fullPathToRemoteDbFile.toLowerCase().endsWith(".hash")) {
                        // check whether index file for this text database is present
                        // For example, if text db name is "hash_db.txt" then index file name will be "hash_db.txt-md5.idx"
                        fullPathToRemoteIndexFile = fullPathToRemoteDbFile + "-md5.idx";

                        // if index file exists, copy it to the remote location
                        File remoteDbIndexFile = new File(fullPathToRemoteIndexFile);
                        if (remoteDbIndexFile.exists()) {
                            // delete local copy of "index of the index" file if one exists
                            fullPathToLocalIndexFile = localDb.getPath() + "-md5.idx";
                            File localIndexFile = new File(fullPathToLocalIndexFile);
                            if (localIndexFile.exists()) {
                                localIndexFile.delete();
                            }
                            // copy index file to the remote folder
                            FileUtils.copyFile(remoteDbIndexFile, localIndexFile);
                        } else {
                            // index file doesn't exist at remote location
                            fullPathToRemoteIndexFile = "";
                        }
                    } else if (fullPathToRemoteDbFile.endsWith(".idx")) {
                        // hash db file itself is the index file and it has already been copied to the remote location
                        fullPathToRemoteIndexFile = fullPathToRemoteDbFile;
                        fullPathToLocalIndexFile = localDb.getPath();
                    }

                    // check whether "index of the index" file exists for this hash DB index file.
                    // NOTE: "index of the index" file may only exist for hash database index files (.idx files). 
                    // For example, hash_db.txt-md5.idx index file will have hash_db.txt-md5.idx2 "index of the index" file.
                    // "index of the index" file is optional and may not be present. 
                    if (fullPathToRemoteIndexFile.endsWith(".idx")) {
                        // check if "index of the index" file exists in remote shared config folder
                        String fullPathToRemoteIndexOfIndexFile = fullPathToRemoteIndexFile + "2"; // "index of the index" file has same file name with extension ".idx2"
                        File remoteIndexOfIndexFile = new File(fullPathToRemoteIndexOfIndexFile);
                        if (remoteIndexOfIndexFile.exists()) {

                            // delete local copy of "index of the index" file if one exists
                            String fullPathToLocalIndexOfIndexFile = fullPathToLocalIndexFile + "2"; // "index of the index" file has same file name with extension ".idx2"
                            File localIndexOfIndexFile = new File(fullPathToLocalIndexOfIndexFile);
                            if (localIndexOfIndexFile.exists()) {
                                localIndexOfIndexFile.delete();
                            }
                            // copy index of the index file to the local folder
                            FileUtils.copyFile(remoteIndexOfIndexFile, localIndexOfIndexFile);
                        }
                    }
                }
            }
        } catch (IOException | SecurityException ex) {
            throw new SharedConfigurationException(String.format("Failed to copy %s to %s", sharedDb.getAbsolutePath(), localDb.getAbsolutePath()), ex);
        }

        // Copy the settings filey
        copyToLocalFolder(HASHDB_CONFIG_FILE_NAME, HASH_SETTINGS_PATH, remoteFolder, true);
        copyToLocalFolder(HASHDB_CONFIG_FILE_NAME_LEGACY, HASH_SETTINGS_PATH, remoteFolder, true);
        copyToLocalFolder(SHARED_CONFIG_VERSIONS, moduleDirPath, remoteFolder, true);

        // Refresh HashDbManager with the new settings
        HashDbManager.getInstance().loadLastSavedConfiguration();
    }

    /**
     * Read in the hashsets settings to pull out the names of the databases.
     *
     * @return List of all hash databases
     *
     * @throws SharedConfigurationException
     */
    private static List<String> getHashFileNamesFromSettingsFile() throws SharedConfigurationException {
        List<String> results = new ArrayList<>();
        try {
            HashDbManager hashDbManager = HashDbManager.getInstance();
            hashDbManager.loadLastSavedConfiguration();
            for (HashDbManager.HashDb hashDb : hashDbManager.getAllHashSets()) {
                // Central Repository hash sets have no path and don't need to be copied
                if (hashDb.getIndexPath().isEmpty() && hashDb.getDatabasePath().isEmpty()) {
                    continue;
                }

                if (hashDb.hasIndexOnly()) {
                    results.add(hashDb.getIndexPath());
                } else {
                    results.add(hashDb.getDatabasePath());
                }
            }
        } catch (TskCoreException ex) {
            throw new SharedConfigurationException("Unable to read hash sets", ex);
        }
        return results;
    }

    /**
     * Change the database path into a form that can be used to create
     * subfolders in the shared folder.
     *
     * @param localName Database name from the XML file
     *
     * @return Path with the initial colon removed
     */
    private static String convertLocalDbPathToShared(String localName) {
        // Replace the colon
        String sharedName = localName.replace(":", "__colon__");
        return sharedName;
    }

    /**
     * Write the list of database paths and versions to a file.
     *
     * @param versionFile File to write to
     * @param versions    Map of database name -> version (current using CRCs as
     *                    versions)
     *
     * @throws SharedConfigurationException
     */
    private static void writeVersionsToFile(File versionFile, Map<String, String> versions) throws SharedConfigurationException {
        try (PrintWriter writer = new PrintWriter(versionFile.getAbsoluteFile(), "UTF-8")) {
            for (String filename : versions.keySet()) {
                writer.println(versions.get(filename) + " " + filename);
            }
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
            throw new SharedConfigurationException(String.format("Failed to write version info to %s", versionFile), ex);
        }
    }

    /**
     * Read the map of database paths to versions from a file.
     *
     * @param versionFile File containing the version information
     *
     * @return Map of database name -> version
     *
     * @throws SharedConfigurationException
     */
    private static Map<String, String> readVersionsFromFile(File versionFile) throws SharedConfigurationException {
        Map<String, String> versions = new HashMap<>();

        // If the file does not exist, return an empty map
        if (!versionFile.exists()) {
            return versions;
        }

        // Read in and store each pair
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            String currentLine = reader.readLine();
            while (null != currentLine) {
                if (!currentLine.isEmpty()) {
                    int index = currentLine.indexOf(' '); // Find the first space
                    String crc = currentLine.substring(0, index);
                    String path = currentLine.substring(index + 1);
                    versions.put(path, crc);
                }
                currentLine = reader.readLine();
            }
        } catch (FileNotFoundException ex) {
            throw new SharedConfigurationException(String.format("Failed to find version file %s", versionFile), ex);
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to read version info from %s", versionFile), ex);
        }

        return versions;
    }

    /**
     * Calculate the CRC of a file to use to determine if it has changed.
     *
     * @param filePath File to get the CRC for
     *
     * @return String containing the CRC
     *
     * @throws SharedConfigurationException
     */
    private static String calculateCRC(String filePath) throws SharedConfigurationException {
        File file = new File(filePath);
        try {
            FileInputStream fileStream = new FileInputStream(file);
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[65536];
            int bytesRead = fileStream.read(buffer);
            while (-1 != bytesRead) {
                crc.update(buffer, 0, bytesRead);
                bytesRead = fileStream.read(buffer);
            }
            return String.valueOf(crc.getValue());
        } catch (IOException ex) {
            throw new SharedConfigurationException(String.format("Failed to calculate CRC for %s", file.getAbsolutePath()), ex);
        }
    }

    /**
     * Copy the YARA settings directory from the local directory to the remote
     * directory.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws
     * org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.SharedConfigurationException
     */
    private void uploadYARASetting(File remoteFolder) throws SharedConfigurationException {
        publishTask("Uploading YARA module configuration");

        File localYara = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "yara").toFile();

        if (!localYara.exists()) {
            return;
        }

        copyLocalFolderToRemoteFolder(localYara, remoteFolder);
    }

    /**
     * Downloads the YARA settings folder from the remote directory to the local
     * one.
     *
     * @param remoteFolder Shared settings folder
     *
     * @throws
     * org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.SharedConfigurationException
     */
    private void downloadYARASettings(File remoteFolder) throws SharedConfigurationException {
        publishTask("Downloading YARA module configuration");
        File localYara = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "yara").toFile();

        copyRemoteFolderToLocalFolder(localYara, remoteFolder);
    }
}
