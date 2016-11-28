/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
public class FilesFilter {

    FilesSet currentRules;
    String rulesKey;
    private boolean processUnallocatedSpace;
    public final static String ALL_FILES_FILTER = "<All Files>";
    public final static String ALL_FILES_AND_UNALLOCATED_FILTER = "<All Files and Unallocated Space>";

    public static Set<String> getKeys() throws InterestingItemDefsManager.InterestingItemDefsManagerException {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        return manager.getInterestingFilesSets(InterestingItemDefsManager.getFILE_FILTER_SET_DEFS_SERIALIZATION_NAME(), "").keySet();
    }

    public FilesFilter(String key) {
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
