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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Serialization settings for file extension mismatch. Contains static methods
 * for reading and writing settings, and instances of this class are what is
 * written to the serialized file.
 */
class FileExtMismatchSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private HashMap<String, Set<String>> mimeTypeToExtsMap;
    private static final Logger logger = Logger.getLogger(FileExtMismatchSettings.class.getName());
    private static final String SIG_EL = "signature"; //NON-NLS
    private static final String EXT_EL = "ext";     //NON-NLS
    private static final String SIG_MIMETYPE_ATTR = "mimetype"; //NON-NLS

    private static final String DEFAULT_CONFIG_FILE_NAME = "mismatch_config.xml";   //NON-NLS
    private static final String FILTER_CONFIG_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + DEFAULT_CONFIG_FILE_NAME;
    private static final String DEFAULT_SERIALIZED_FILE_NAME = "mismatch_config.settings";
    private static final String DEFAULT_SERIALIZED_FILE_PATH = PlatformUtil.getUserConfigDirectory() + File.separator + DEFAULT_SERIALIZED_FILE_NAME;

    static {
        try {
            PlatformUtil.extractResourceToUserConfigDir(FileExtMismatchSettings.class, DEFAULT_CONFIG_FILE_NAME, false);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default mismatch configuration to user dir ", ex); //NON-NLS
        }
    }

    /**
     * Makes a settings object based on given mime type map
     *
     * @param mimeTypeToExtsMap
     */
    FileExtMismatchSettings(HashMap<String, Set<String>> mimeTypeToExtsMap) {
        this.mimeTypeToExtsMap = mimeTypeToExtsMap;
    }

    /**
     * @return the mime type to extension map
     */
    HashMap<String, Set<String>> getMimeTypeToExtsMap() {
        return mimeTypeToExtsMap;
    }

    /**
     * Sets the signature to extension map for this settings.
     */
    void setMimeTypeToExtsMap(HashMap<String, Set<String>> mimeTypeToExtsMap) {
        this.mimeTypeToExtsMap = mimeTypeToExtsMap;
    }

    /**
     * Reads the file extension mismatch settings.
     *
     * @return Loaded settings (empty if there are no settings to load).
     */
    static synchronized FileExtMismatchSettings readSettings() throws FileExtMismatchSettingsException {
        File serializedFile = new File(DEFAULT_SERIALIZED_FILE_PATH);
        //Tries reading the serialized file first, as this is the prioritized settings.
        if (serializedFile.exists()) {
            return readSerializedSettings();
        }
        return readXmlSettings();
    }

    private static FileExtMismatchSettings readSerializedSettings() throws FileExtMismatchSettingsException {
        File serializedFile = new File(DEFAULT_SERIALIZED_FILE_PATH);
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedFile))) {
                FileExtMismatchSettings fileExtMismatchSettings = (FileExtMismatchSettings) in.readObject();
                return fileExtMismatchSettings;
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new FileExtMismatchSettingsException("Couldn't read serialized settings.", ex);
        }
    }

    private static FileExtMismatchSettings readXmlSettings() throws FileExtMismatchSettingsException {
        HashMap<String, Set<String>> sigTypeToExtMap = new HashMap<>();
        //Next tries to read the xml file if the serialized file did not exist
        File xmlFile = new File(FILTER_CONFIG_FILE);
        if (xmlFile.exists()) {
            try {
                final Document doc = XMLUtil.loadDoc(FileExtMismatchSettings.class, FILTER_CONFIG_FILE);
                if (doc == null) {
                    throw new FileExtMismatchSettingsException("Error loading config file: invalid file format (could not load doc).");
                }

                Element root = doc.getDocumentElement();
                if (root == null) {
                    throw new FileExtMismatchSettingsException("Error loading config file: invalid file format (bad root)."); //NON-NLS
                }

                NodeList sigNList = root.getElementsByTagName(SIG_EL);
                final int numSigs = sigNList.getLength();

                if (numSigs == 0) {
                    throw new FileExtMismatchSettingsException("Error loading config file: invalid file format (no signature)."); //NON-NLS
                }

                for (int sigIndex = 0; sigIndex < numSigs; ++sigIndex) {
                    Element sigEl = (Element) sigNList.item(sigIndex);
                    final String mimetype = sigEl.getAttribute(SIG_MIMETYPE_ATTR);

                    NodeList extNList = sigEl.getElementsByTagName(EXT_EL);
                    final int numExts = extNList.getLength();

                    if (numExts != 0) {
                        Set<String> extStrings = new HashSet<>();
                        for (int extIndex = 0; extIndex < numExts; ++extIndex) {
                            Element extEl = (Element) extNList.item(extIndex);
                            extStrings.add(extEl.getTextContent());
                        }
                        sigTypeToExtMap.put(mimetype, extStrings);
                    } else {
                        sigTypeToExtMap.put(mimetype, null); //ok to have an empty type (the ingest module will not use it)
                    }
                }

            } catch (Exception e) {
                throw new FileExtMismatchSettingsException("Error loading config file.", e); //NON-NLS
            }
        }
        return new FileExtMismatchSettings(sigTypeToExtMap);
    }

    /**
     * Save settings to disk.
     *
     * @param settings The settings to save to disk
     *
     * @return Loaded hash map or null on error or null if data does not exist
     */
    static synchronized void writeSettings(FileExtMismatchSettings settings) throws FileExtMismatchSettingsException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(DEFAULT_SERIALIZED_FILE_PATH))) {
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new FileExtMismatchSettingsException(String.format("Failed to write settings to %s", DEFAULT_SERIALIZED_FILE_PATH), ex);
        }
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined file types manager.
     */
    static class FileExtMismatchSettingsException extends Exception {

        private static final long serialVersionUID = 1L;

        FileExtMismatchSettingsException(String message) {
            super(message);
        }

        FileExtMismatchSettingsException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}
