/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.interestingitems;

import java.util.Set;
import org.openide.util.Exceptions;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 *
 */
public class FilesFilter {

    FilesSet currentRules;
    boolean processUnallocatedSpace;

    public static Set<String> getKeys() throws InterestingItemDefsManager.InterestingItemDefsManagerException {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        return manager.getInterestingFilesSets(InterestingItemDefsManager.FILE_FILTER_SET_DEFS_SERIALIZATION_NAME, "").keySet();
    }

    public FilesFilter() {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        try {
            for (FilesSet fs : manager.getInterestingFilesSets(InterestingItemDefsManager.FILE_FILTER_SET_DEFS_SERIALIZATION_NAME, "").values()) {
                currentRules = fs;
                break;
            }
        } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
            Exceptions.printStackTrace(ex);
        }
        processUnallocatedSpace = true;
    }

    public FilesFilter(String key) {
        InterestingItemDefsManager manager = InterestingItemDefsManager.getInstance();
        try {
            currentRules = manager.getInterestingFilesSets(InterestingItemDefsManager.FILE_FILTER_SET_DEFS_SERIALIZATION_NAME, "").get(key);
        } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
            Exceptions.printStackTrace(ex);
        }
        processUnallocatedSpace = true;
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

        if (currentRules.fileIsMemberOf(file) != null) {
            fileMatches = true;
        }
        return fileMatches;
    }
}
