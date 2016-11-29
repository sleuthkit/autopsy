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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 *
 *
 */
public class IngestSetFilter {

    FilesSet currentRules;
    String rulesKey;
    private boolean processUnallocatedSpace;
    public final static String ALL_FILES_FILTER = "<All Files>";
    public final static String ALL_FILES_AND_UNALLOCATED_FILTER = "<All Files and Unallocated Space>";
    public final static String NEW_INGEST_FILTER= "<Create New>";

    public static Set<String> getKeys() throws InterestingItemDefsManager.InterestingItemDefsManagerException {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        return manager.getInterestingFilesSets(InterestingItemDefsManager.getFILE_FILTER_SET_DEFS_SERIALIZATION_NAME(), "").keySet();
    }

    public IngestSetFilter(String key) {
        this.rulesKey = key;
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        if (key.equals(ALL_FILES_FILTER)) {
            currentRules = null;
            processUnallocatedSpace = false;
        } else if (key.equals(ALL_FILES_AND_UNALLOCATED_FILTER)) {
            currentRules = null;
            processUnallocatedSpace = true;
        } else {
            try {
                currentRules = manager.getInterestingFilesSets(InterestingItemDefsManager.getFILE_FILTER_SET_DEFS_SERIALIZATION_NAME(), "").get(key);
                processUnallocatedSpace = currentRules.processesUnallocatedSpace();
            } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    /**
     *
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
        if (isProcessUnallocatedSpace() == false && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)){
            fileMatches = false;
        }
        else if (rulesKey.equals(ALL_FILES_FILTER) || rulesKey.equals(ALL_FILES_AND_UNALLOCATED_FILTER) ) {
                fileMatches = true;
        } else if (currentRules.fileIsMemberOf(file) != null) {
            fileMatches = true;
        }
        return fileMatches;
    }

    /**
     * @return the processUnallocatedSpace
     */
    public boolean isProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }
}
