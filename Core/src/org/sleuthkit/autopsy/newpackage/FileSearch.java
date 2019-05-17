/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import java.util.Comparator;
import java.util.List;

import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileSize;
import org.sleuthkit.autopsy.newpackage.FileSearchData.Frequency;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
/**
 *
 */
class FileSearch {
    
    
    static void runFileSearch(List<FileSearchFiltering.SubFilter> filters, 
            AttributeType attrType, FileGroup.GroupSortingAlgorithm groupSortingType, 
            Comparator<ResultFile> fileSortingMethod, 
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        List<ResultFile> resultFiles = FileSearchFiltering.runFilters(filters, caseDb, centralRepoDb);

        SearchResults searchResults = new SearchResults(groupSortingType, attrType, fileSortingMethod);
        for (ResultFile file : resultFiles) {
            searchResults.add(file);
        }
        
        searchResults.finish();
        
        //searchResults.print();
        
        //return searchResults.getTranferrableVersion();
    }

    static Comparator<ResultFile> getFileNameComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                if (file1 == null) {
                    return 1;
                }
                return file1.getAbstractFile().getName().compareToIgnoreCase(file2.getAbstractFile().getName());
            }
        };
    }
    
    abstract static class AttributeType {
        abstract Object getGroupIdentifier(ResultFile file);
        abstract String getGroupName(ResultFile file);
        abstract Comparator<ResultFile> getDefaultFileComparator();
    }
    
    static class FileSizeAttribute extends AttributeType {
        
        @Override
        Object getGroupIdentifier(ResultFile file) {
            return FileSize.fromSize(file.getAbstractFile().getSize());
        }
        
        @Override
        String getGroupName(ResultFile file) {
            return FileSize.fromSize(file.getAbstractFile().getSize()).toString();
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    return -1 * Long.compare(file1.getAbstractFile().getSize(), file2.getAbstractFile().getSize()); // Large to small
                }
            };
        }
    }
    
    static class FrequencyAttribute extends AttributeType {
        
        @Override
        Object getGroupIdentifier(ResultFile file) {
            return file.getFrequency();
        }
        
        @Override
        String getGroupName(ResultFile file) {
            return file.getFrequency().toString();
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    return Long.compare(file1.getFrequency().getRanking(), file2.getFrequency().getRanking());
                }
            };
        }
    }
    
    private FileSearch() {
        // Class should not be instantiated
    }
}
