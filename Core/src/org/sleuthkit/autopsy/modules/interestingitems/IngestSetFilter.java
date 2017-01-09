/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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

import java.util.Set;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * Allows limiting which files ingest is run on by storing rules and allowing
 * files to be compared to them.
 *
 */
public final class IngestSetFilter {

    FilesSet currentRules;
    String rulesKey;
    private boolean processUnallocatedSpace;
    public final static String ALL_FILES_FILTER = "<All Files>";
    public final static String ALL_FILES_AND_UNALLOCATED_FILTER = "<All Files and Unallocated Space>";
    public final static String NEW_INGEST_FILTER = "<Create New>";
    private String lastSelected;
    private static final String LAST_INGEST_FILTER_FILE = "CurrentIngestFilter";
    private static final String LAST_INGEST_FILTER_PROPERTY = "LastIngestFilter";

    /**
     * Creates an IngestSetFilter for the filter specified by the key.
     *
     * @param key - The name of the filter you wish to create.
     */
    public IngestSetFilter(String key) {
        this.rulesKey = key;
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        switch (key) {
            case ALL_FILES_FILTER:
                currentRules = null;
                processUnallocatedSpace = false;
                break;
            case ALL_FILES_AND_UNALLOCATED_FILTER:
                currentRules = null;
                processUnallocatedSpace = true;
                break;
            default:
                try {
                    currentRules = manager.getInterestingFilesSets(InterestingItemDefsManager.getIngestSetFilterDefsName(), "").get(key);
                    processUnallocatedSpace = currentRules.processesUnallocatedSpace();
                } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
                    Exceptions.printStackTrace(ex);
                }
                break;
        }
    }

    /**
     * No argument constructor for IngestSetFilter, creates a filter using the
     * same filter that was selected previously.
     */
    public IngestSetFilter() {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        this.rulesKey = getLastSelected();
        switch (rulesKey) {
            case ALL_FILES_FILTER:
                currentRules = null;
                processUnallocatedSpace = false;
                break;
            case ALL_FILES_AND_UNALLOCATED_FILTER:
                currentRules = null;
                processUnallocatedSpace = true;
                break;
            default:
                try {
                    currentRules = manager.getInterestingFilesSets(InterestingItemDefsManager.getIngestSetFilterDefsName(), "").get(rulesKey);
                    processUnallocatedSpace = currentRules.processesUnallocatedSpace();
                } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
                    Exceptions.printStackTrace(ex);
                }
                break;
        }

    }

    /**
     * Get the set of available Ingest Set Filters.
     *
     * @return - the set of filter names
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.InterestingItemDefsManager.InterestingItemDefsManagerException
     */
    public static Set<String> getKeys() throws InterestingItemDefsManager.InterestingItemDefsManagerException {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        return manager.getInterestingFilesSets(InterestingItemDefsManager.getIngestSetFilterDefsName(), "").keySet();
    }

    /**
     * Returns access to the Rules currently being used by the Ingest Set Filter
     *
     * @return - the active file filter set from the InterestingItemsDefsManager
     */
    FilesSet getFileFilterSet() {
        return currentRules;
    }

    /**
     * Return whether or not the file meets any of the rules specified
     *
     * @param file
     * @return fileMatches - true if the file matches a rule, false if no rules
     * are matched
     */
    public boolean match(AbstractFile file) {
        boolean fileMatches = false;
        if (isProcessUnallocatedSpace() == false && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            fileMatches = false;
        } else if (rulesKey.equals(ALL_FILES_FILTER) || rulesKey.equals(ALL_FILES_AND_UNALLOCATED_FILTER)) {
            fileMatches = true;
        } else if (currentRules.fileIsMemberOf(file) != null) {
            fileMatches = true;
        }
        return fileMatches;
    }

    /**
     * Get the name of the Ingest Set Filter which was last used, so that when
     * running on the same set of files you will not have to reselect that set.
     *
     * @return lastSelected - the string which represents the Ingest Set Filter
     * which was last used.
     */
    public String getLastSelected() {
        if (lastSelected == null) {
            if (ModuleSettings.configExists(LAST_INGEST_FILTER_FILE)) {
                lastSelected = ModuleSettings.getConfigSetting(LAST_INGEST_FILTER_FILE, LAST_INGEST_FILTER_PROPERTY);
            } else {
                lastSelected = ALL_FILES_AND_UNALLOCATED_FILTER;
            }
        }
        return lastSelected;
    }

    /**
     * Saves the last selected IngestSetFilter, to a file so that it can be
     * loaded later.
     *
     * @return True if value was saved successfully, false if it was not.
     */
    public void setLastSelected(String lastSelectedFilter) {
        lastSelected = lastSelectedFilter;
        ModuleSettings.setConfigSetting(LAST_INGEST_FILTER_FILE, LAST_INGEST_FILTER_PROPERTY, lastSelected);
    }

    /**
     * Get whether or not unallocated space should be processed as a boolean.
     *
     * @return the processUnallocatedSpace true if unallocated space should be
     * processed false if unallocated space should not be processed
     */
    public boolean isProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }
}
