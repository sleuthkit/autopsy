/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MetaTypeCondition;

/**
 * Provides access to collections of FilesSet definitions persisted to disk.
 * Clients receive copies of the most recent FilesSet definitions for
 * Interesting Items or File Ingest Filters via synchronized methods, allowing
 * the definitions to be safely published to multiple threads.
 */
public final class FilesSetsManager extends Observable {

    @NbBundle.Messages({"FilesSetsManager.allFilesAndDirectories=All Files and Directories",
        "FilesSetsManager.allFilesDirectoriesAndUnallocated=All Files, Directories, and Unallocated Space"})
    private static final List<String> ILLEGAL_FILE_NAME_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", "/", ":", "*", "?", "\"", "<", ">")));
    private static final List<String> ILLEGAL_FILE_PATH_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", ":", "*", "?", "\"", "<", ">")));
    private static final String LEGACY_FILES_SET_DEFS_FILE_NAME = "InterestingFilesSetDefs.xml"; //NON-NLS
    private static final String INTERESTING_FILES_SET_DEFS_NAME = "InterestingFileSets.settings";
    private static final String FILE_INGEST_FILTER_DEFS_NAME = "FileIngestFilterDefs.settings";
    private static final Object FILE_INGEST_FILTER_LOCK = new Object();
    private static final Object INTERESTING_FILES_SET_LOCK = new Object();
    private static FilesSetsManager instance;
    private static final FilesSet FILES_DIRS_INGEST_FILTER = new FilesSet(
            Bundle.FilesSetsManager_allFilesAndDirectories(), Bundle.FilesSetsManager_allFilesAndDirectories(), false, true, new HashMap<String, Rule>() {
        {
            put(Bundle.FilesSetsManager_allFilesAndDirectories(),
                    new Rule(Bundle.FilesSetsManager_allFilesAndDirectories(), null,
                            new MetaTypeCondition(MetaTypeCondition.Type.ALL), null, null, null, null));
        }
    });
    private static final FilesSet FILES_DIRS_UNALLOC_INGEST_FILTER = new FilesSet(
            Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(), Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(),
            false, false, new HashMap<String, Rule>() {
        {
            put(Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(),
                    new Rule(Bundle.FilesSetsManager_allFilesDirectoriesAndUnallocated(), null,
                            new MetaTypeCondition(MetaTypeCondition.Type.ALL), null, null, null, null));
        }
    });

    /**
     * Gets the FilesSet definitions manager singleton.
     */
    public synchronized static FilesSetsManager getInstance() {
        if (instance == null) {
            instance = new FilesSetsManager();
        }
        return instance;
    }

    /**
     * Gets the set of chars deemed to be illegal in file names (Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFileNameChars() {
        return FilesSetsManager.ILLEGAL_FILE_NAME_CHARS;
    }

    /**
     * Gets the set of chars deemed to be illegal in file path
     * (SleuthKit/Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFilePathChars() {
        return FilesSetsManager.ILLEGAL_FILE_PATH_CHARS;
    }

    /**
     * Get a list of default FileIngestFilters.
     *
     * @return a list of FilesSets which cover default options.
     */
    public static List<FilesSet> getStandardFileIngestFilters() {
        return Arrays.asList(FILES_DIRS_UNALLOC_INGEST_FILTER, FILES_DIRS_INGEST_FILTER);
    }
    
    /**
     * Gets a copy of the current ingest file set definitions.
     *
     * The defaults are not included so that they will not show up in the
     * editor.
     *
     * @return A map of FilesSet names to file ingest sets, possibly empty.
     */
    public Map<String, FilesSet> getCustomFileIngestFilters() throws FilesSetsManagerException {
        synchronized (FILE_INGEST_FILTER_LOCK) {
            return FileSetsDefinitions.readSerializedDefinitions(FILE_INGEST_FILTER_DEFS_NAME);
        }
    }

    /**
     * Get the filter that should be used as the default value, if no filter is
     * specified.
     *
     * @return FILES_DIRS_UNALLOC_INGEST_FILTER
     */
    public static FilesSet getDefaultFilter() {
        return FILES_DIRS_UNALLOC_INGEST_FILTER;
    }
    
    /**
     * Sets the current interesting file sets definitions, replacing any
     * previous definitions.
     *
     * @param filesSets A mapping of file ingest filters names to files sets,
     *                  used to enforce unique files set names.
     */
    void setCustomFileIngestFilters(Map<String, FilesSet> filesSets) throws FilesSetsManagerException {
        synchronized (FILE_INGEST_FILTER_LOCK) {
            FileSetsDefinitions.writeDefinitionsFile(FILE_INGEST_FILTER_DEFS_NAME, filesSets);
        }
    }

    
    /**
     * Gets a copy of the current interesting files set definitions.
     *
     * @return A map of interesting files set names to interesting file sets,
     *         possibly empty.
     */
    public Map<String, FilesSet> getInterestingFilesSets() throws FilesSetsManagerException {
        synchronized (INTERESTING_FILES_SET_LOCK) {
            return InterestingItemsFilesSetSettings.readDefinitionsFile(INTERESTING_FILES_SET_DEFS_NAME, LEGACY_FILES_SET_DEFS_FILE_NAME);
        }
    }


    /**
     * Sets the current interesting file sets definitions, replacing any
     * previous definitions.
     *
     * @param filesSets A mapping of interesting files set names to files sets,
     *                  used to enforce unique files set names.
     */
    void setInterestingFilesSets(Map<String, FilesSet> filesSets) throws FilesSetsManagerException {
        synchronized (INTERESTING_FILES_SET_LOCK) {
            InterestingItemsFilesSetSettings.writeDefinitionsFile(INTERESTING_FILES_SET_DEFS_NAME, filesSets);
            this.setChanged();
            this.notifyObservers();
        }
    }

    

    public static class FilesSetsManagerException extends Exception {

        FilesSetsManagerException() {

        }

        FilesSetsManagerException(String message) {
            super(message);
        }

        FilesSetsManagerException(String message, Throwable cause) {
            super(message, cause);
        }

        FilesSetsManagerException(Throwable cause) {
            super(cause);
        }
    }
}
