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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Class to represent the settings to be serialized for hash lookup.
 */
final class HashLookupSettings implements Serializable {

    private static final String SERIALIZATION_FILE_NAME = "hashLookup.settings"; //NON-NLS
    private static final String SERIALIZATION_FILE_PATH = PlatformUtil.getUserConfigDirectory() + File.separator + SERIALIZATION_FILE_NAME; //NON-NLS
    private static final String SET_ELEMENT = "hash_set"; //NON-NLS
    private static final String SET_NAME_ATTRIBUTE = "name"; //NON-NLS
    private static final String SET_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String SEARCH_DURING_INGEST_ATTRIBUTE = "use_for_ingest"; //NON-NLS
    private static final String SEND_INGEST_MESSAGES_ATTRIBUTE = "show_inbox_messages"; //NON-NLS
    private static final String PATH_ELEMENT = "hash_set_path"; //NON-NLS
    private static final String LEGACY_PATH_NUMBER_ATTRIBUTE = "number"; //NON-NLS
    private static final String CONFIG_FILE_NAME = "hashsets.xml"; //NON-NLS
    private static final String configFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + CONFIG_FILE_NAME;
    private static final Logger logger = Logger.getLogger(HashDbManager.class.getName());

    private static final long serialVersionUID = 1L;
    private final List<HashDbInfo> hashDbInfoList;

    /**
     * Constructs a settings object to be serialized for hash lookups
     *
     * @param hashDbInfoList The list of hash db info.
     */
    HashLookupSettings(List<HashDbInfo> hashDbInfoList) {
        this.hashDbInfoList = hashDbInfoList;
    }

    /**
     * Constructs a settings object to be serialized for hash lookups
     *
     * @param knownHashSets    The list known hash sets for the settings.
     * @param knownBadHashSets The list of known bad hash sets for the settings.
     */
    HashLookupSettings(List<HashDbManager.HashDb> knownHashSets, List<HashDbManager.HashDb> knownBadHashSets) throws HashLookupSettingsException {
        hashDbInfoList = new ArrayList<>();
        this.addHashesToList(knownHashSets);
        this.addHashesToList(knownBadHashSets);
    }

    /**
     * Adds each HashDb to the settings.
     *
     * @param hashSetList The list of HashDb to add to the settings
     *
     * @throws * pacannot be obtained
     */
    private void addHashesToList(List<HashDbManager.HashDb> hashSetList) throws HashLookupSettingsException {
        for (HashDbManager.HashDb hashDb : hashSetList) {
            try {
                String dbPath;
                if (hashDb.hasIndexOnly()) {
                    dbPath = hashDb.getIndexPath();
                } else {
                    dbPath = hashDb.getDatabasePath();
                }
                hashDbInfoList.add(new HashDbInfo(hashDb.getHashSetName(), hashDb.getKnownFilesType(), hashDb.getSearchDuringIngest(), hashDb.getSendIngestMessages(), dbPath));
            } catch (TskCoreException ex) {
                throw new HashLookupSettingsException("Couldn't add hash database named: " + hashDb.getHashSetName(), ex);
            }
        }
    }

    /**
     * Gets the list of hash db info that this settings contains
     *
     * @return The list of hash databse info
     */
    List<HashDbInfo> getHashDbInfo() {
        return hashDbInfoList;
    }

    /**
     * Reads the settings from the disk.
     *
     * @return The settings object representing what was read.
     *
     * @throws HashLookupSettingsException When there is a problem reading the
     *                                     settings.
     */
    static HashLookupSettings readSettings() throws HashLookupSettingsException {
        File fileSetFile = new File(SERIALIZATION_FILE_PATH);
        if (fileSetFile.exists()) {
            return readSerializedSettings();
        }
        return readXmlSettings();

    }

    /**
     * Reads the serialization settings from the disk
     *
     * @return Settings object representing what is saved in the serialization
     *         file.
     *
     * @throws HashLookupSettingsException If there's a problem importing the
     *                                     settings
     */
    private static HashLookupSettings readSerializedSettings() throws HashLookupSettingsException {
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(SERIALIZATION_FILE_PATH))) {
                HashLookupSettings filesSetsSettings = (HashLookupSettings) in.readObject();
                return filesSetsSettings;
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new HashLookupSettingsException("Could not read hash database settings.", ex);
        }
    }

    /**
     * Reads the xml settings from the disk
     *
     * @return Settings object representing what is saved in the xml file, or an
     *         empty settings if there is no xml file.
     *
     * @throws HashLookupSettingsException If there's a problem importing the
     *                                     settings
     */
    private static HashLookupSettings readXmlSettings() throws HashLookupSettingsException {
        File xmlFile = new File(configFilePath);
        if (xmlFile.exists()) {
            boolean updatedSchema = false;

            // Open the XML document that implements the configuration file.
            final Document doc = XMLUtil.loadDoc(HashDbManager.class, configFilePath);
            if (doc == null) {
                throw new HashLookupSettingsException("Could not open xml document.");
            }

            // Get the root element.
            Element root = doc.getDocumentElement();
            if (root == null) {
                throw new HashLookupSettingsException("Error loading hash sets: invalid file format.");
            }

            // Get the hash set elements.
            NodeList setsNList = root.getElementsByTagName(SET_ELEMENT);
            int numSets = setsNList.getLength();

            // Create HashDbInfo objects for each hash set element. Throws on malformed xml.
            String attributeErrorMessage = "Missing %s attribute"; //NON-NLS
            String elementErrorMessage = "Empty %s element"; //NON-NLS
            List<String> hashSetNames = new ArrayList<>();
            List<HashDbInfo> hashDbInfoList = new ArrayList<>();
            for (int i = 0; i < numSets; ++i) {
                Element setEl = (Element) setsNList.item(i);

                String hashSetName = setEl.getAttribute(SET_NAME_ATTRIBUTE);
                if (hashSetName.isEmpty()) {
                    throw new HashLookupSettingsException(String.format(attributeErrorMessage, SET_NAME_ATTRIBUTE));
                }

                // Handle configurations saved before duplicate hash set names were not permitted.
                if (hashSetNames.contains(hashSetName)) {
                    int suffix = 0;
                    String newHashSetName;
                    do {
                        ++suffix;
                        newHashSetName = hashSetName + suffix;
                    } while (hashSetNames.contains(newHashSetName));
                    logger.log(Level.INFO, "Duplicate hash set name " + hashSetName + " found. Replacing with " + newHashSetName + ".");
                    if (RuntimeProperties.runningWithGUI()) {
                        JOptionPane.showMessageDialog(null,
                                NbBundle.getMessage(HashLookupSettings.class,
                                        "HashDbManager.replacingDuplicateHashsetNameMsg",
                                        hashSetName, newHashSetName),
                                NbBundle.getMessage(HashLookupSettings.class, "HashDbManager.openHashDbErr"),
                                JOptionPane.ERROR_MESSAGE);
                        hashSetName = newHashSetName;
                    }
                }

                String knownFilesType = setEl.getAttribute(SET_TYPE_ATTRIBUTE);
                if (knownFilesType.isEmpty()) {
                    throw new HashLookupSettingsException(String.format(attributeErrorMessage, SET_TYPE_ATTRIBUTE));
                }

                // Handle legacy known files types.
                if (knownFilesType.equals("NSRL")) { //NON-NLS
                    knownFilesType = HashDbManager.HashDb.KnownFilesType.KNOWN.toString();
                    updatedSchema = true;
                }

                final String searchDuringIngest = setEl.getAttribute(SEARCH_DURING_INGEST_ATTRIBUTE);
                if (searchDuringIngest.isEmpty()) {
                    throw new HashLookupSettingsException(String.format(attributeErrorMessage, SEND_INGEST_MESSAGES_ATTRIBUTE));
                }
                Boolean searchDuringIngestFlag = Boolean.parseBoolean(searchDuringIngest);

                final String sendIngestMessages = setEl.getAttribute(SEND_INGEST_MESSAGES_ATTRIBUTE);
                if (searchDuringIngest.isEmpty()) {
                    throw new HashLookupSettingsException(String.format(attributeErrorMessage, SEND_INGEST_MESSAGES_ATTRIBUTE));
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
                        throw new HashLookupSettingsException(String.format(elementErrorMessage, PATH_ELEMENT));
                    }
                } else {
                    throw new HashLookupSettingsException(String.format(elementErrorMessage, PATH_ELEMENT));
                }
                hashDbInfoList.add(new HashDbInfo(hashSetName, HashDbManager.HashDb.KnownFilesType.valueOf(knownFilesType),
                        searchDuringIngestFlag, sendIngestMessagesFlag, dbPath));
                hashSetNames.add(hashSetName);
            }

            if (updatedSchema) {
                String backupFilePath = configFilePath + ".v1_backup"; //NON-NLS
                String messageBoxTitle = NbBundle.getMessage(HashLookupSettings.class,
                        "HashDbManager.msgBoxTitle.confFileFmtChanged");
                String baseMessage = NbBundle.getMessage(HashLookupSettings.class,
                        "HashDbManager.baseMessage.updatedFormatHashDbConfig");
                try {
                    FileUtils.copyFile(new File(configFilePath), new File(backupFilePath));
                    logger.log(Level.INFO, "Updated the schema, backup saved at: " + backupFilePath);
                    if (RuntimeProperties.runningWithGUI()) {
                        JOptionPane.showMessageDialog(null,
                                NbBundle.getMessage(HashLookupSettings.class,
                                        "HashDbManager.savedBackupOfOldConfigMsg",
                                        baseMessage, backupFilePath),
                                messageBoxTitle,
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed to save backup of old format configuration file to " + backupFilePath, ex); //NON-NLS
                    JOptionPane.showMessageDialog(null, baseMessage, messageBoxTitle, JOptionPane.INFORMATION_MESSAGE);
                }
                HashLookupSettings settings;
                settings = new HashLookupSettings(hashDbInfoList);
                HashLookupSettings.writeSettings(settings);
            }
            return new HashLookupSettings(hashDbInfoList);
        } else {
            return new HashLookupSettings(new ArrayList<>());
        }
    }

    /**
     * Writes the given settings objects to the disk at the designated location
     *
     * @param settings The settings to be written
     *
     * @return Whether or not the settings were written successfully
     */
    static boolean writeSettings(HashLookupSettings settings) {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(SERIALIZATION_FILE_PATH))) {
            out.writeObject(settings);
            return true;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not wtite hash database settings.");
            return false;
        }
    }

    /**
     * Represents the serializable information within a hash lookup in order to
     * be written to disk. Used to hand off information when loading and saving
     * hash lookups.
     */
    static final class HashDbInfo implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String hashSetName;
        private final HashDbManager.HashDb.KnownFilesType knownFilesType;
        private final boolean searchDuringIngest;
        private final boolean sendIngestMessages;
        private final String path;

        /**
         * Constructs a HashDbInfo object
         *
         * @param hashSetName        The name of the hash set
         * @param knownFilesType     The known files type
         * @param searchDuringIngest Whether or not the db is searched during
         *                           ingest
         * @param sendIngestMessages Whether or not ingest messages are sent
         * @param path               The path to the db
         */
        HashDbInfo(String hashSetName, HashDbManager.HashDb.KnownFilesType knownFilesType, boolean searchDuringIngest, boolean sendIngestMessages, String path) {
            this.hashSetName = hashSetName;
            this.knownFilesType = knownFilesType;
            this.searchDuringIngest = searchDuringIngest;
            this.sendIngestMessages = sendIngestMessages;
            this.path = path;
        }

        /**
         * Gets the hash set name.
         *
         * @return The hash set name.
         */
        String getHashSetName() {
            return hashSetName;
        }

        /**
         * Gets the known files type setting.
         *
         * @return The known files type setting.
         */
        HashDbManager.HashDb.KnownFilesType getKnownFilesType() {
            return knownFilesType;
        }

        /**
         * Gets the search during ingest setting.
         *
         * @return The search during ingest setting.
         */
        boolean getSearchDuringIngest() {
            return searchDuringIngest;
        }

        /**
         * Gets the send ingest messages setting.
         *
         * @return The send ingest messages setting.
         */
        boolean getSendIngestMessages() {
            return sendIngestMessages;
        }

        /**
         * Gets the path.
         *
         * @return The path.
         */
        String getPath() {
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
