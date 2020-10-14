/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Provides convenient access to a Preferences node for user preferences with
 * default values. 
 */
final class UserPreferences {

    private static final Preferences preferences = NbPreferences.forModule(UserPreferences.class);
    private static final String INDEXING_DOC_QUEUE_SIZE = "IndexingDocumentQueueSize"; //NON-NLS
    private static final int DEFAULT_INDEXING_DOC_QUEUE_SIZE = 30; //NON-NLS

    // Prevent instantiation.
    private UserPreferences() {
    }

    /**
     * Reload all preferences from disk. This is only needed if the preferences
     * file is being directly modified on disk while Autopsy is running.
     *
     * @throws BackingStoreException
     */
    public static void reloadFromStorage() throws BackingStoreException {
        preferences.sync();
    }

    /**
     * Saves the current preferences to storage. This is only needed if the
     * preferences files are going to be copied to another location while
     * Autopsy is running.
     *
     * @throws BackingStoreException
     */
    public static void saveToStorage() throws BackingStoreException {
        preferences.flush();
    }

    public static void addChangeListener(PreferenceChangeListener listener) {
        preferences.addPreferenceChangeListener(listener);
    }

    public static void removeChangeListener(PreferenceChangeListener listener) {
        preferences.removePreferenceChangeListener(listener);
    }
    
    public static void setDocumentsQueueSize(int size) {
        preferences.putInt(INDEXING_DOC_QUEUE_SIZE, size);
    }

    public static int getDocumentsQueueSize() {
        return preferences.getInt(INDEXING_DOC_QUEUE_SIZE, DEFAULT_INDEXING_DOC_QUEUE_SIZE);
    }
}