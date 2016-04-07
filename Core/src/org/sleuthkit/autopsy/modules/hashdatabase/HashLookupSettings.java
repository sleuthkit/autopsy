/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Class to represent the settings to be serialized for hash databases
 */
class HashLookupSettings implements Serializable {

    private static final String SERIALIZATION_FILE_NAME = "hashLookup.settings";
    private static final String SERIALIZATION_FILE_PATH = PlatformUtil.getUserConfigDirectory() + File.separator + SERIALIZATION_FILE_NAME;
    private static final String SET_ELEMENT = "hash_set"; //NON-NLS
    private static final String SET_NAME_ATTRIBUTE = "name"; //NON-NLS
    private static final String SET_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String SEARCH_DURING_INGEST_ATTRIBUTE = "use_for_ingest"; //NON-NLS
    private static final String SEND_INGEST_MESSAGES_ATTRIBUTE = "show_inbox_messages"; //NON-NLS
    private static final String PATH_ELEMENT = "hash_set_path"; //NON-NLS
    private static final String LEGACY_PATH_NUMBER_ATTRIBUTE = "number"; //NON-NLS
    private static final String CONFIG_FILE_NAME = "hashsets.xml"; //NON-NLS
    private static final String DB_SERIALIZATION_FILE_NAME = "hashDbs.settings";
    private static final String HASH_DATABASE_FILE_EXTENSON = "kdb"; //NON-NLS
    private static final String configFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + CONFIG_FILE_NAME;

    private static final long serialVersionUID = 1L;
    private final List<HashDbInfo> hashDbInfoList;

    public HashLookupSettings(List<HashDbInfo> hashDbInfoList) {
        this.hashDbInfoList = hashDbInfoList;
    }

    /**
     * Constructs a settings object to be serialized for hash dbs
     *
     * @param knownHashSets
     * @param knownBadHashSets
     */
    HashLookupSettings(List<HashDbManager.HashDb> knownHashSets, List<HashDbManager.HashDb> knownBadHashSets) throws TskCoreException {
        hashDbInfoList = new ArrayList<>();
        for (HashDbManager.HashDb hashDb : knownHashSets) {
            if (hashDb.hasIndexOnly()) {
                hashDbInfoList.add(new HashDbInfo(hashDb.getHashSetName(), hashDb.getKnownFilesType(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), hashDb.getIndexPath()));
            } else {
                hashDbInfoList.add(new HashDbInfo(hashDb.getHashSetName(), hashDb.getKnownFilesType(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), hashDb.getDatabasePath()));
            }
        }

        for (HashDbManager.HashDb hashDb : knownBadHashSets) {
            if (hashDb.hasIndexOnly()) {
                hashDbInfoList.add(new HashDbInfo(hashDb.getHashSetName(), hashDb.getKnownFilesType(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), hashDb.getIndexPath()));
            } else {
                hashDbInfoList.add(new HashDbInfo(hashDb.getHashSetName(), hashDb.getKnownFilesType(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), hashDb.getDatabasePath()));
            }
        }
    }

    /**
     * @return the hashDbInfoList
     */
    public List<HashDbInfo> getHashDbInfoList() {
        return hashDbInfoList;
    }

    private static String getValidFilePath(String hashSetName, String configuredPath) {
        // Check the configured path.
        File database = new File(configuredPath);
        if (database.exists()) {
            return configuredPath;
        }

        // Give the user an opportunity to find the desired file.
        String newPath = null;
        if (JOptionPane.showConfirmDialog(null,
                NbBundle.getMessage(HashLookupSettings.class, "HashDbManager.dlgMsg.dbNotFoundAtLoc",
                        hashSetName, configuredPath),
                NbBundle.getMessage(HashLookupSettings.class, "HashDbManager.dlgTitle.MissingDb"),
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

    private static String searchForFile() {
        String filePath = null;
        JFileChooser fc = new JFileChooser();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[]{"txt", "idx", "hash", "Hash", "kdb"}; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                NbBundle.getMessage(HashLookupSettings.class, "HashDbManager.fileNameExtensionFilter.title"), EXTENSION);
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

    public static HashLookupSettings readSettings() throws HashLookupSettingsException {
        File fileSetFile = new File(SERIALIZATION_FILE_PATH);
        File f = new File(configFilePath);
        if (fileSetFile.exists()) {
            try {
                try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(SERIALIZATION_FILE_PATH))) {
                    HashLookupSettings filesSetsSettings = (HashLookupSettings) in.readObject();
                    return filesSetsSettings;
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new HashLookupSettingsException("Could not read hash database settings.", ex);
            }
        } else {
            if (f.exists()) {
                boolean updatedSchema = false;

                // Open the XML document that implements the configuration file.
                final Document doc = XMLUtil.loadDoc(HashDbManager.class, configFilePath);
                if (doc == null) {
                    return null;
                }

                // Get the root element.
                Element root = doc.getDocumentElement();
                if (root == null) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Error loading hash sets: invalid file format."); //NON-NLS
                    return null;
                }

                // Get the hash set elements.
                NodeList setsNList = root.getElementsByTagName(SET_ELEMENT);
                int numSets = setsNList.getLength();
                if (numSets == 0) {
                    Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "No element hash_set exists."); //NON-NLS
                }

            // Create HashDb objects for each hash set element. Skip to the next hash database if the definition of
                // a particular hash database is not well-formed.
                String attributeErrorMessage = " attribute was not set for hash_set at index {0}, cannot make instance of HashDb class"; //NON-NLS
                String elementErrorMessage = " element was not set for hash_set at index {0}, cannot make instance of HashDb class"; //NON-NLS
                List<String> hashSetNames = new ArrayList<>();
                List<HashDbInfo> hashDbInfoList = new ArrayList<>();
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
                                NbBundle.getMessage(HashLookupSettings.class,
                                        "HashDbManager.replacingDuplicateHashsetNameMsg",
                                        hashSetName, newHashSetName),
                                NbBundle.getMessage(HashLookupSettings.class, "HashDbManager.openHashDbErr"),
                                JOptionPane.ERROR_MESSAGE);
                        hashSetName = newHashSetName;
                    }

                    String knownFilesType = setEl.getAttribute(SET_TYPE_ATTRIBUTE);
                    if (knownFilesType.isEmpty()) {
                        Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SET_TYPE_ATTRIBUTE + attributeErrorMessage, i);
                        continue;
                    }

                    // Handle legacy known files types.
                    if (knownFilesType.equals("NSRL")) { //NON-NLS
                        knownFilesType = HashDbManager.HashDb.KnownFilesType.KNOWN.toString();
                        updatedSchema = true;
                    }

                    final String searchDuringIngest = setEl.getAttribute(SEARCH_DURING_INGEST_ATTRIBUTE);
                    if (searchDuringIngest.isEmpty()) {
                        Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, SEARCH_DURING_INGEST_ATTRIBUTE + attributeErrorMessage, i);
                        continue;
                    }
                    Boolean searchDuringIngestFlag = Boolean.parseBoolean(searchDuringIngest);

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
                        hashDbInfoList.add(new HashDbInfo(hashSetName, HashDbManager.HashDb.KnownFilesType.valueOf(knownFilesType),
                                searchDuringIngestFlag, sendIngestMessagesFlag, dbPath));
                        hashSetNames.add(hashSetName);
                    } else {
                        Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "No valid path for hash_set at index {0}, cannot make instance of HashDb class", i); //NON-NLS
                    }
                }

                if (updatedSchema) {
                    String backupFilePath = configFilePath + ".v1_backup"; //NON-NLS
                    String messageBoxTitle = NbBundle.getMessage(HashLookupSettings.class,
                            "HashDbManager.msgBoxTitle.confFileFmtChanged");
                    String baseMessage = NbBundle.getMessage(HashLookupSettings.class,
                            "HashDbManager.baseMessage.updatedFormatHashDbConfig");
                    try {
                        FileUtils.copyFile(new File(configFilePath), new File(backupFilePath));
                        JOptionPane.showMessageDialog(null,
                                NbBundle.getMessage(HashLookupSettings.class,
                                        "HashDbManager.savedBackupOfOldConfigMsg",
                                        baseMessage, backupFilePath),
                                messageBoxTitle,
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        Logger.getLogger(HashDbManager.class.getName()).log(Level.WARNING, "Failed to save backup of old format configuration file to " + backupFilePath, ex); //NON-NLS
                        JOptionPane.showMessageDialog(null, baseMessage, messageBoxTitle, JOptionPane.INFORMATION_MESSAGE);
                    }
                    HashLookupSettings settings;
                    settings = new HashLookupSettings(hashDbInfoList);
                    HashLookupSettings.writeSettings(settings);
                }
                return new HashLookupSettings(hashDbInfoList);
            }
            else {
                return null;
            }
        }
    }

    static boolean writeSettings(HashLookupSettings settings) {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(SERIALIZATION_FILE_PATH))) {
            out.writeObject(settings);
            return true;
        } catch (Exception ex) {
            Logger.getLogger(HashDbManager.class.getName()).log(Level.SEVERE, "Could not wtite hash database settings.");
            return false;
        }
    }

    static final class HashDbInfo implements Serializable {

        private static final long serialVersionUID = 1L;
        private String hashSetName;
        private HashDbManager.HashDb.KnownFilesType knownFilesType;
        private boolean searchDuringIngest;
        private boolean sendIngestMessages;
        private String path;

        public HashDbInfo(String hashSetName, HashDbManager.HashDb.KnownFilesType knownFilesType, boolean searchDuringIngest, boolean sendIngestMessages, String path) {
            this.hashSetName = hashSetName;
            this.knownFilesType = knownFilesType;
            this.searchDuringIngest = searchDuringIngest;
            this.sendIngestMessages = sendIngestMessages;
            this.path = path;
        }

        /**
         * @return the hashSetName
         */
        public String getHashSetName() {
            return hashSetName;
        }

        /**
         * @return the knownFilesType
         */
        public HashDbManager.HashDb.KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        /**
         * @return the searchDuringIngest
         */
        public boolean getSearchDuringIngest() {
            return searchDuringIngest;
        }

        /**
         * @return the sendIngestMessages
         */
        public boolean getSendIngestMessages() {
            return sendIngestMessages;
        }

        /**
         * @return the path
         */
        public String getPath() {
            return path;
        }
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined file types manager.
     */
    static class HashLookupSettingsException extends Exception {

        private static final long serialVersionUID = 1L;

        HashLookupSettingsException(String message) {
            super(message);
        }

        HashLookupSettingsException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}
