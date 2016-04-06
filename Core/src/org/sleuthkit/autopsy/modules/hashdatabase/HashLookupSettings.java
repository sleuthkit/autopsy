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
import javax.swing.JOptionPane;
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

    private static final long serialVersionUID = 1L;
    private final List<HashDbInfo> hashDbInfoList;

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

    public static HashLookupSettings readSettings() throws HashLookupSettingsException {
        File fileSetFile = new File(SERIALIZATION_FILE_PATH);
        if (fileSetFile.exists()) {
            try {
                try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(SERIALIZATION_FILE_PATH))) {
                    HashLookupSettings filesSetsSettings = (HashLookupSettings) in.readObject();
                    return filesSetsSettings;
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new HashLookupSettingsException("Could not read hash database settings.", ex);
            }
        }
        return null;
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
