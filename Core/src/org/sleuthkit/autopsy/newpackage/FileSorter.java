/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
class FileSorter implements Comparator<ResultFile> {
    
    List<Comparator<ResultFile>> comparators = new ArrayList<>();
    
    FileSorter(SortingMethod method) {
        
        // Set up the comparators that should run on the files
        switch (method) {
            case BY_DATA_SOURCE:
                comparators.add(getDataSourceComparator());
                comparators.add(getDefaultComparator());
                break;
            case BY_FILE_SIZE:
                comparators.add(getFileSizeComparator());
                comparators.add(getDefaultComparator());
                break;
            case BY_FILE_TYPE:
                comparators.add(getFileTypeComparator());
                comparators.add(getDefaultComparator());
                break;
            case BY_FREQUENCY:
                break;
            case BY_KEYWORD_LIST_NAMES:
                break;
            case BY_PARENT_PATH:
                break;
            case BY_FILE_NAME:
                comparators.add(getFileNameComparator());
                comparators.add(getDefaultComparator());
            default:
                comparators.add(getDefaultComparator());
                break;
        }
    }
    
    @Override
    public int compare(ResultFile file1, ResultFile file2) {
        
        return 0;
    }
    
    private static Comparator<ResultFile> getDataSourceComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                // Sort large to small
                return Long.compare(file1.getAbstractFile().getDataSourceObjectId(), file2.getAbstractFile().getDataSourceObjectId());
            }
        };
    }    
    
    private static Comparator<ResultFile> getFileTypeComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                if (file1.getFileType() != file2.getFileType()) {
                    // Primary sort on the file type enum
                    return Integer.compare(file1.getFileType().getRanking(), file2.getFileType().getRanking());
                }
                // Secondary sort on the MIME type
                return compareStrings(file1.getAbstractFile().getMIMEType(), file2.getAbstractFile().getMIMEType());
            }
        };
    }   
    
    private static Comparator<ResultFile> getFileSizeComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                // Sort large to small
                return -1 * Long.compare(file1.getAbstractFile().getSize(), file2.getAbstractFile().getSize());
            }
        };
    }
    
    private static Comparator<ResultFile> getFileNameComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                return compareStrings(file1.getAbstractFile().getName(), file2.getAbstractFile().getName());
            }
        };
    }
    
    private static Comparator<ResultFile> getDefaultComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                // For now, compare file names and then object ID (to ensure a consistent sort)
                int result = getFileNameComparator().compare(file1, file2);
                if (result == 0) {
                    return Long.compare(file1.getAbstractFile().getId(), file2.getAbstractFile().getId());
                }
                return result;
            }
        };
    }
    
    /**
     * Compare two strings alphabetically. Nulls are allowed.
     * 
     * @param s1
     * @param s2
     * 
     * @return 
     */
    private static int compareStrings(String s1, String s2) {
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }
        return s1.compareTo(s2);
    }

    enum SortingMethod {
        BY_DATA_SOURCE,
        BY_FILE_NAME,
        BY_FILE_SIZE,
        BY_FILE_TYPE,
        BY_FREQUENCY,
        BY_KEYWORD_LIST_NAMES,
        BY_PARENT_PATH;
    }
}
