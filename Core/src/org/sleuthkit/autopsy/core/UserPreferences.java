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

import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

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
    public static final String PROCESS_TIME_OUT_HOURS = "ProcessTimeOutHours"; //NON-NLS
    private static final String TIME_OUT_MODE = "TimeOutMode"; // NON-NLS  
    
    private static final int DEFAULT_PROCESS_TIMEOUT_HR = 48;   
    public enum SelectedTimeOutMode  {
        DEFAULT,     
        NEVER,       
        CUSTOM       
    };     
    
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
    
    public static int getProcessTimeOutHrs() {
        int timeOut = preferences.getInt(PROCESS_TIME_OUT_HOURS, DEFAULT_PROCESS_TIMEOUT_HR);
        if (timeOut < 0)
            timeOut = 0;
        return timeOut;
    }
    
    public static int getDefaultProcessTimeOutHrs() {
        return DEFAULT_PROCESS_TIMEOUT_HR;
    }    

    public static void setProcessTimeOutHrs(int value) {
        if (value < 0)
            value = 0;            
        preferences.putInt(PROCESS_TIME_OUT_HOURS, value);
    }  
    
    public static SelectedTimeOutMode getTimeOutMode() {
        int ordinal = preferences.getInt(TIME_OUT_MODE, SelectedTimeOutMode.NEVER.ordinal());
        return SelectedTimeOutMode.values()[ordinal];
    }

    public static void setTimeOutMode(SelectedTimeOutMode timeOutMode) {
        preferences.putInt(TIME_OUT_MODE, timeOutMode.ordinal());
    }         
}
