/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template abstractFile, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;


import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileSize;
import org.sleuthkit.autopsy.newpackage.FileSearchData.Frequency;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 *
 */
class FileSearchFiltering {
    
    /**
     * 
     * @param filters
     * @param caseDb
     * @param crDb     The central repo. Can be null as long as no filters need it.
     * @return 
     */
    static List<ResultFile> runFilters(List<SubFilter> filters, SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        
        if (caseDb == null) {
            throw new FileSearchException("Case DB parameter is null");
        }
        
        System.out.println("Running filters: ");
        for (SubFilter filter : filters) {
            System.out.println("  " + filter.getDesc());
        }
        System.out.println("\n");
        
        // TODO - It'd be nice to restrict to files?
        String combinedQuery = "";
        for (SubFilter subFilter : filters) {
            if ( ! subFilter.getSQL().isEmpty()) {
                if ( ! combinedQuery.isEmpty()) {
                    combinedQuery += " AND ";
                }
                combinedQuery += "(" + subFilter.getSQL() + ")";
            }
        }
        
        try {
            // Get all matching abstract files
            System.out.println("SQL query: " + combinedQuery + "\n");
            List<AbstractFile> sqlResults = caseDb.findAllFilesWhere(combinedQuery);
            
            // Wrap each result in a ResultFile
            List<ResultFile> resultList = new ArrayList<>();
            for (AbstractFile abstractFile : sqlResults) {
                resultList.add(new ResultFile(abstractFile));
            }
            
            // Now run any non-SQL filters
            for (SubFilter subFilter : filters) {
                if (subFilter.useAlternameFilter()) {
                    resultList = subFilter.getMatchingFiles(resultList, caseDb, centralRepoDb);
                }
            }
            
            return resultList;
        } catch (TskCoreException ex) {
            throw new FileSearchException("Error querying case database", ex);
        }
    }
    
    static abstract class SubFilter {
        // Should be part of a query on the tsk_files table that can be AND-ed with other pieces
        abstract String getSQL();
        
        boolean useAlternameFilter() {
            return false;
        }
        
        List<ResultFile> getMatchingFiles (List<ResultFile> currentResults, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            return new ArrayList<>();
        }
        
        abstract String getDesc();
    }
    
    static class SizeSubFilter extends SubFilter {
        private final List<FileSize> fileSizes;
        
        SizeSubFilter(List<FileSize> fileSizes) {
            this.fileSizes = fileSizes;
        }
        
        @Override
        String getSQL() {
            String queryStr = "";
            for (FileSize size : fileSizes) {
                if (! queryStr.isEmpty()) {
                    queryStr += " OR ";
                } 
                if (size.getMaxBytes() != FileSize.NO_MAXIMUM) {
                    queryStr += "(size > \'" + size.getMinBytes() + "\' AND size <= \'" + size.getMaxBytes() + "\')";
                } else {
                    queryStr += "(size >= \'" + size.getMinBytes() + "\')";
                }
            }
            return queryStr;
        }
        
        @Override
        String getDesc() {
            String desc = "";
            for (FileSize size : fileSizes) {
                if ( ! desc.isEmpty()) {
                    desc += " or ";
                }
                desc += "(" + size.getMinBytes() + " to " + size.getMaxBytes() + ")";
            }
            desc = "Files with size in range(s) " + desc;
            return desc;
        }
    }
        
    static class FrequencySubFilter extends SubFilter {
        
        private final List<Frequency> frequencies;
        
        FrequencySubFilter(List<Frequency> frequencies) {
            this.frequencies = frequencies;
        }
        
        @Override
        boolean useAlternameFilter() {
            return true;
        }
        
        @Override
        List<ResultFile> getMatchingFiles (List<ResultFile> currentResults, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            
            if (centralRepoDb == null) {
                throw new FileSearchException("Can not run Frequency filter with null Central Repository DB");
            }
            
            // For the moment, we have to have run some kind of SQL filter before getting to this point.
            // TBD what we should do if this is the only filter.
            if (currentResults.isEmpty()) {
                return currentResults;
            }
            
            // We'll make this more efficient later - for now, check the frequency of each file individually
            List<ResultFile> frequencyResults = new ArrayList<>();
            for (ResultFile file : currentResults) {
                try {
                    if (file.getAbstractFile().getMd5Hash() != null && ! file.getAbstractFile().getMd5Hash().isEmpty()) {
                        CorrelationAttributeInstance.Type attributeType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
                        long count = centralRepoDb.getCountUniqueCaseDataSourceTuplesHavingTypeValue(attributeType, file.getAbstractFile().getMd5Hash());
                        
                        // Thresholds TBD, and should probably go in the enum. For now use these for testing.
                        if (count <= 1) {
                            file.setFrequency(Frequency.UNIQUE);
                        } else if (count < 5) {
                            file.setFrequency(Frequency.RARE);
                        } else {
                            file.setFrequency(Frequency.COMMON);
                        }
                    }
                    
                    if (frequencies.contains(file.getFrequency())) {
                        frequencyResults.add(file);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            return frequencyResults;
        }
        
        @Override
        String getSQL() {
            return "";
        }
        
        @Override
        String getDesc() {
            String desc = "";
            for (Frequency freq : frequencies) {
                if ( ! desc.isEmpty()) {
                    desc += " or ";
                }
                desc += freq.name();
            }
            return "Files with frequency " + desc;
        }
    }

    
    private FileSearchFiltering() {
        // Class should not be instantiated
    }    
}
