/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.core;

import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;


/**
 * Provides convenient access to a Preferences node for user preferences with
 * default values.
 */
public final class UserPreferences {

    private static final Preferences preferences = NbPreferences.forModule(UserPreferences.class);
    public static final String KEEP_PREFERRED_VIEWER = "KeepPreferredViewer"; // NON-NLS    
    public static final String HIDE_KNOWN_FILES_IN_DATA_SOURCES_TREE = "HideKnownFilesInDataSourcesTree"; //NON-NLS 
    public static final String HIDE_KNOWN_FILES_IN_VIEWS_TREE = "HideKnownFilesInViewsTree"; //NON-NLS 
    public static final String DISPLAY_TIMES_IN_LOCAL_TIME = "DisplayTimesInLocalTime"; //NON-NLS
    public static final String NUMBER_OF_FILE_INGEST_THREADS = "NumberOfFileIngestThreads"; //NON-NLS
    public static final String EXTERNAL_DATABASE_HOSTNAME_OR_IP = "ExternalDatabaseHostnameOrIp"; //NON-NLS
    public static final String EXTERNAL_DATABASE_PORTNUMBER = "ExternalDatabasePortNumber"; //NON-NLS
    public static final String EXTERNAL_DATABASE_NAME = "ExternalDatabaseName"; //NON-NLS
    public static final String EXTERNAL_DATABASE_USER = "ExternalDatabaseUsername"; //NON-NLS
    public static final String EXTERNAL_DATABASE_PASSWORD = "ExternalDatabasePassword"; //NON-NLS
    public static final String EXTERNAL_DATABASE_TYPE = "ExternalDatabaseType"; //NON-NLS    
    public static final String INDEXING_SERVER_HOST = "IndexingServerHost"; //NON-NLS
    public static final String INDEXING_SERVER_PORT = "IndexingServerPort"; //NON-NLS
    private static final String MESSAGE_SERVICE_PASSWORD = "MessageServicePassword"; //NON-NLS
    private static final String MESSAGE_SERVICE_USER = "MessageServiceUser"; //NON-NLS
    private static final String MESSAGE_SERVICE_HOST = "MessageServiceHost"; //NON-NLS
    private static final String MESSAGE_SERVICE_PORT = "MessageServicePort"; //NON-NLS

    // Prevent instantiation.
    private UserPreferences() {
    }

    public static void addChangeListener(PreferenceChangeListener listener) {
        preferences.addPreferenceChangeListener(listener);
    }

    public static void removeChangeListener(PreferenceChangeListener listener) {
        preferences.removePreferenceChangeListener(listener);
    }

    public static boolean keepPreferredContentViewer() {
        return preferences.getBoolean(KEEP_PREFERRED_VIEWER, false);
    }

    public static void setKeepPreferredContentViewer(boolean value) {
        preferences.putBoolean(KEEP_PREFERRED_VIEWER, value);
    }

    public static boolean hideKnownFilesInDataSourcesTree() {
        return preferences.getBoolean(HIDE_KNOWN_FILES_IN_DATA_SOURCES_TREE, false);
    }

    public static void setHideKnownFilesInDataSourcesTree(boolean value) {
        preferences.putBoolean(HIDE_KNOWN_FILES_IN_DATA_SOURCES_TREE, value);
    }

    public static boolean hideKnownFilesInViewsTree() {
        return preferences.getBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, true);
    }

    public static void setHideKnownFilesInViewsTree(boolean value) {
        preferences.putBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, value);
    }

    public static boolean displayTimesInLocalTime() {
        return preferences.getBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, true);
    }

    public static void setDisplayTimesInLocalTime(boolean value) {
        preferences.putBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, value);
    }

    public static int numberOfFileIngestThreads() {
        return preferences.getInt(NUMBER_OF_FILE_INGEST_THREADS, 2);
    }

    public static void setNumberOfFileIngestThreads(int value) {
        preferences.putInt(NUMBER_OF_FILE_INGEST_THREADS, value);
    }

    public static CaseDbConnectionInfo getDatabaseConnectionInfo() {
        DbType dbType;
        try {
            dbType = DbType.valueOf(preferences.get(EXTERNAL_DATABASE_TYPE, "UNKOWN"));
        } catch (Exception ex) {
            dbType = DbType.UNKNOWN;
        }
        return new CaseDbConnectionInfo(
                preferences.get(EXTERNAL_DATABASE_HOSTNAME_OR_IP, ""),
                preferences.get(EXTERNAL_DATABASE_PORTNUMBER, ""),
                preferences.get(EXTERNAL_DATABASE_USER, ""),
                preferences.get(EXTERNAL_DATABASE_PASSWORD, ""),
                dbType);
    }

    public static void setDatabaseConnectionInfo(CaseDbConnectionInfo connectionInfo) {
        preferences.put(EXTERNAL_DATABASE_HOSTNAME_OR_IP, connectionInfo.getHost());
        preferences.put(EXTERNAL_DATABASE_PORTNUMBER, connectionInfo.getPort());
        preferences.put(EXTERNAL_DATABASE_USER, connectionInfo.getUserName());
        preferences.put(EXTERNAL_DATABASE_PASSWORD, connectionInfo.getPassword());
        preferences.put(EXTERNAL_DATABASE_TYPE, connectionInfo.getDbType().toString());
    }

    public static String getIndexingServerHost() {
        return preferences.get(INDEXING_SERVER_HOST, "");
    }
    
    public static void setIndexingServerHost(String hostName) {
        preferences.put(INDEXING_SERVER_HOST, hostName);
    }
    
    public static String getIndexingServerPort() {
        return preferences.get(INDEXING_SERVER_PORT, "");
    }
    
    public static void setIndexingServerPort(int port) {
        preferences.putInt(INDEXING_SERVER_PORT, port);
    }

    /**
     * Persists message service connection info.
     *
     * @param info An object encapsulating the message service info.
     */
    public static void setMessageServiceConnectionInfo(MessageServiceConnectionInfo info) {
        preferences.put(MESSAGE_SERVICE_USER, info.getUserName());
        preferences.put(MESSAGE_SERVICE_PASSWORD, info.getPassword());
        preferences.put(MESSAGE_SERVICE_HOST, info.getHost());
        preferences.put(MESSAGE_SERVICE_PORT, info.getPort());
    }

    /**
     * Reads persisted message service connection info.
     *
     * @return An object encapsulating the message service info.
     */
    public static MessageServiceConnectionInfo getMessageServiceConnectionInfo() {
        return new MessageServiceConnectionInfo(preferences.get(MESSAGE_SERVICE_USER, ""),
                preferences.get(MESSAGE_SERVICE_PASSWORD, ""),
                preferences.get(MESSAGE_SERVICE_HOST, ""),
                preferences.get(MESSAGE_SERVICE_PORT, ""));
    }

}
