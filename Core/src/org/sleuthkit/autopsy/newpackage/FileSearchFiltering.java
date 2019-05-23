/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.newpackage;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileSize;
import org.sleuthkit.autopsy.newpackage.FileSearchData.Frequency;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;

/**
 * Run various filters to return a subset of files from the current case.
 */
class FileSearchFiltering {
    
    /**
     * Run the given filters to get a list of matching files.
     * 
     * @param filters  The filters to run
     * @param caseDb   The case database
     * @param crDb     The central repo. Can be null as long as no filters need it.
     * @return 
     */
    static List<ResultFile> runQueries(List<SubFilter> filters, SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        
        if (caseDb == null) {
            throw new FileSearchException("Case DB parameter is null"); // NON-NLS
        }
        
        // Debug - print out the current filters. Could perhaps be an info statement.
        System.out.println("Running filters: ");
        for (SubFilter filter : filters) {
            System.out.println("  " + filter.getDesc());
        }
        System.out.println("\n");
        
        // Combine all the SQL queries from the filters into one query
        // TODO - maybe exclude directories and other non-file objects?
        String combinedQuery = "";
        for (SubFilter subFilter : filters) {
            if ( ! subFilter.getSQL().isEmpty()) {
                if ( ! combinedQuery.isEmpty()) {
                    combinedQuery += " AND "; // NON-NLS
                }
                combinedQuery += "(" + subFilter.getSQL() + ")"; // NON-NLS
            }
        }
        
        try {
            // Get all matching abstract files
            List<ResultFile> resultList = new ArrayList<>();
            
            // Debug - print the SQL. could also be info
            System.out.println("SQL query: " + combinedQuery + "\n");
            
            if ( ! combinedQuery.isEmpty()) {
                List<AbstractFile> sqlResults = caseDb.findAllFilesWhere(combinedQuery);
            
                // If there are no results, return now
                if (sqlResults.isEmpty()) {
                    return resultList;
                }
                
                // Wrap each result in a ResultFile
                for (AbstractFile abstractFile : sqlResults) {
                    resultList.add(new ResultFile(abstractFile));
                }
            }
            
            // Now run any non-SQL filters. Note that resultList could be empty at this point if we had no SQL queries -
            // getMatchingFiles() will interpret this as no filters have been run up to this point
            // and act accordingly.
            for (SubFilter subFilter : filters) {
                if (subFilter.useAlternameFilter()) { // TODO typo
                    resultList = subFilter.getMatchingFiles(resultList, caseDb, centralRepoDb); // TODO rename
                }
                
                // There are no matches for the filters run so far, so return
                if (resultList.isEmpty()) {
                    return resultList;
                }
            }
            
            return resultList;
        } catch (TskCoreException ex) {
            throw new FileSearchException("Error querying case database", ex); // NON-NLS
        }
    }
    
    /**
     * Base class for the filters.
     */
    static abstract class SubFilter {
        /**
         * Returns part of a query on the tsk_files table that can be AND-ed with other pieces
         * @return the SQL query or an empty string if there is no SQL query for this filter.
         */
        abstract String getSQL();
        
        /**
         * Indicates whether this filter needs to use the secondary, non-SQL method getMatchingFiles().
         * @return false by default
         */
        boolean useAlternameFilter() {
            return false;
        }
        
        /**
         * Run a secondary filter that does not operate on tsk_files.
         * If currentResults is empty, the assumption is that this is the first filter being run, not that
         * there are no matches.
         * 
         * @param currentResults The current list of matching files; empty if no filters have yet been run.
         * @param caseDb         The case database
         * @param centralRepoDb  The central repo database. Can be null if the filter does not require it.
         * 
         * @return The list of files that match this filter (and any that came before it)
         * 
         * @throws FileSearchException 
         */
        List<ResultFile> getMatchingFiles (List<ResultFile> currentResults, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            return new ArrayList<>();
        }
        
        /**
         * Get a description of the selected filter.
         * 
         * @return A description of the filter
         */
        abstract String getDesc();
    }
    
    /**
     * A filter for specifying the file size
     */
    static class SizeSubFilter extends SubFilter {
        private final List<FileSize> fileSizes;
        
        /**
         * Create the SizeSubFilter
         * 
         * @param fileSizes the file sizes that should match
         */
        SizeSubFilter(List<FileSize> fileSizes) {
            this.fileSizes = fileSizes;
        }
        
        @Override
        String getSQL() {
            String queryStr = ""; // NON-NLS
            for (FileSize size : fileSizes) {
                if (! queryStr.isEmpty()) {
                    queryStr += " OR "; // NON-NLS
                } 
                if (size.getMaxBytes() != FileSize.NO_MAXIMUM) {
                    queryStr += "(size > \'" + size.getMinBytes() + "\' AND size <= \'" + size.getMaxBytes() + "\')"; // NON-NLS
                } else {
                    queryStr += "(size >= \'" + size.getMinBytes() + "\')"; // NON-NLS
                }
            }
            return queryStr;
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.SizeSubFilter.desc=Files with size in range(s): {0}",
            "FileSearchFiltering.SizeSubFilter.or= or ",
            "# {0} - Minimum bytes",
            "# {1} - Maximum bytes",
            "FileSearchFiltering.SizeSubFilter.range=({0} to {1})",
        })
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (FileSize size : fileSizes) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_SizeSubFilter_or();
                }
                desc += Bundle.FileSearchFiltering_SizeSubFilter_range(size.getMinBytes(), size.getMaxBytes());
            }
            desc = Bundle.FileSearchFiltering_SizeSubFilter_desc(desc);
            return desc;
        }
    }
    
    /**
     * A utility class for the ParentSubFilter to store the search string
     * and whether it is a full path or a substring.
     */
    static class ParentSearchTerm {
        private final String searchStr;
        private final boolean isFullPath;
        
        /**
         * Create the ParentSearchTerm object
         * 
         * @param searchStr  The string to search for in the file path
         * @param isFullPath True if the path should exactly match the given 
         *                   string, false to do a substring search
         */
        ParentSearchTerm(String searchStr, boolean isFullPath) {
            this.searchStr = searchStr;
            this.isFullPath = isFullPath;
        }
        
        /**
         * Get the SQL term to search for
         * 
         * @return The SQL for a where clause to search for a matching path
         */
        String getSQLForTerm() {
            // TODO - escape these
            if (isFullPath) {
                return "parent_path=\'" + searchStr + "\'"; // NON-NLS
            } else {
                return "parent_path LIKE \'%" + searchStr + "%\'"; // NON-NLS
            }
        }
    }
    
    /**
     * A filter for specifying parent path (either full path or substring)
     */    
    static class ParentSubFilter extends SubFilter {
        private final List<ParentSearchTerm> parentSearchTerms;
        
        /**
         * Create the ParentSubFilter
         * 
         * @param parentSearchTerms Full paths or substrings to filter on
         */
        ParentSubFilter(List<ParentSearchTerm> parentSearchTerms) {
            this.parentSearchTerms = parentSearchTerms;
        }
        
        @Override
        String getSQL() {
            String queryStr = ""; // NON-NLS
            for (ParentSearchTerm searchTerm : parentSearchTerms) {
                if (! queryStr.isEmpty()) {
                    queryStr += " OR "; // NON-NLS
                } 
                queryStr += searchTerm.getSQLForTerm();
            }
            return queryStr;
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.ParentSubFilter.desc=Files with paths matching: {0}",
            "FileSearchFiltering.ParentSubFilter.or= or ",
            "FileSearchFiltering.ParentSubFilter.exact=(exact match)",
            "FileSearchFiltering.ParentSubFilter.substring=(substring)",
        })
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (ParentSearchTerm searchTerm : parentSearchTerms) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_ParentSubFilter_or();
                }
                if (searchTerm.isFullPath) {
                    desc += searchTerm.searchStr + Bundle.FileSearchFiltering_ParentSubFilter_exact();
                } else {
                    desc += searchTerm.searchStr + Bundle.FileSearchFiltering_ParentSubFilter_substring();
                }
            }
            desc = Bundle.FileSearchFiltering_ParentSubFilter_desc(desc);
            return desc;
        }
    }
    
    /**
     * A filter for specifying data sources
     */ 
    static class DataSourceSubFilter extends SubFilter {
        private final List<DataSource> dataSources;
        
        /**
         * Create the DataSourceSubFilter
         * 
         * @param dataSources the data sources to filter on
         */
        DataSourceSubFilter(List<DataSource> dataSources) {
            this.dataSources = dataSources;
        }
        
        @Override
        String getSQL() {
            String queryStr = ""; // NON-NLS
            for (DataSource ds : dataSources) {
                if (! queryStr.isEmpty()) {
                    queryStr += ","; // NON-NLS
                } 
                queryStr += "\'" + ds.getId() + "\'"; // NON-NLS
            }
            queryStr = "data_source_obj_id IN (" + queryStr + ")"; // NON-NLS
            return queryStr;
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.DataSourceSubFilter.desc=Files in data source(s): {0}",
            "FileSearchFiltering.DataSourceSubFilter.or= or ",
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "FileSearchFiltering.DataSourceSubFilter.datasource={0}({1})",
        })
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (DataSource ds : dataSources) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_DataSourceSubFilter_or();
                }
                desc += Bundle.FileSearchFiltering_DataSourceSubFilter_datasource(ds.getName(), ds.getId());
            }
            desc = Bundle.FileSearchFiltering_DataSourceSubFilter_desc(desc);
            return desc;
        }
    }
    
    /**
     * A filter for specifying keyword list names.
     * A file must contain a keyword from one of the given lists to pass.
     */
    static class KeywordListSubFilter extends SubFilter {
        private final List<String> listNames;
        
        /**
         * Create the KeywordListSubFilter
         * @param listNames 
         */
        KeywordListSubFilter(List<String> listNames) {
            this.listNames = listNames;
        }
        
        @Override
        String getSQL() {
            String keywordListPart = ""; // NON-NLS
            for (String listName : listNames) {
                if (! keywordListPart.isEmpty()) {
                    keywordListPart += " OR "; // NON-NLS
                } 
                keywordListPart += "value_text = \'" + listName + "\'"; // TODO - escape this part  // NON-NLS
            }
            
            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN " + 
                    "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = 9 AND attribute_type_ID = 37 " + 
                    "AND (" + keywordListPart + "))))";  // NON-NLS
            
            return queryStr;
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.KeywordListSubFilter.desc=Files with keywords in list(s): {0}",
            "FileSearchFiltering.KeywordListSubFilter.comma=, ",
        })
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (String listName : listNames) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_KeywordListSubFilter_comma();
                }
                desc += listName;
            }
            desc = Bundle.FileSearchFiltering_KeywordListSubFilter_desc(desc);
            return desc;
        }
    }    
    
    /**
     * A filter for specifying file types.
     */    
    static class FileTypeSubFilter extends SubFilter {
        private final List<FileTypeUtils.FileTypeCategory> categories;
        
        /**
         * Create the FileTypeSubFilter
         * @param categories List of file types to filter on
         */
        FileTypeSubFilter(List<FileTypeUtils.FileTypeCategory> categories) {
            this.categories = categories;
        }
        
        @Override
        String getSQL() {
            String queryStr = ""; // NON-NLS
            for (FileTypeUtils.FileTypeCategory cat : categories) {
                for (String type : cat.getMediaTypes()) {
                    if (! queryStr.isEmpty()) {
                        queryStr += ","; // NON-NLS
                    } 
                    queryStr += "\'" + type + "\'"; // NON-NLS
                }
            }
            queryStr = "mime_type IN (" + queryStr + ")"; // NON-NLS
            return queryStr;
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.FileTypeSubFilter.desc=Files with type: {0}",
            "FileSearchFiltering.FileTypeSubFilter.or= or ",
        })
        @Override
        String getDesc() {
            String desc = "";
            for (FileTypeUtils.FileTypeCategory cat : categories) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_FileTypeSubFilter_or();
                }
                desc += cat.getDisplayName();
            }
            desc = Bundle.FileSearchFiltering_FileTypeSubFilter_desc(desc);
            return desc;
        }
    } 
        
    /**
     * A filter for specifying frequency in the central repository.
     */
    static class FrequencySubFilter extends SubFilter {
        
        private final List<Frequency> frequencies;
        
        /**
         * Create the FrequencySubFilter
         * 
         * @param frequencies List of frequencies that will pass the filter
         */
        FrequencySubFilter(List<Frequency> frequencies) {
            this.frequencies = frequencies;
        }
        
        @Override
        String getSQL() {
            // Since this relies on the central repository database, there is no
            // query on the case database.
            return ""; // NON-NLS
        }
        
        @Override
        boolean useAlternameFilter() {
            return true;
        }
        
        @Override
        List<ResultFile> getMatchingFiles (List<ResultFile> currentResults, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            
            if (centralRepoDb == null) {
                throw new FileSearchException("Can not run Frequency filter with null Central Repository DB"); // NON-NLS
            }
            
            // For the moment, we have to have run some kind of SQL filter before getting to this point.
            // TODO what we should do if this is the only filter?
            if ( ! currentResults.isEmpty()) {
            
                // We can try to make this more efficient later - for now, check the frequency of each file individually
                List<ResultFile> frequencyResults = new ArrayList<>();
                for (ResultFile file : currentResults) {
                    try {
                        if (file.getAbstractFile().getMd5Hash() != null && ! file.getAbstractFile().getMd5Hash().isEmpty()) {
                            CorrelationAttributeInstance.Type attributeType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
                            long count = centralRepoDb.getCountUniqueCaseDataSourceTuplesHavingTypeValue(attributeType, file.getAbstractFile().getMd5Hash());
                            file.setFrequency(Frequency.fromCount(count));
                        }

                        if (frequencies.contains(file.getFrequency())) {
                            frequencyResults.add(file);
                        }
                    } catch (EamDbException | CorrelationAttributeNormalizationException ex) {
                        throw new FileSearchException("Error querying central repository", ex); // NON-NLS
                    }
                }
                return frequencyResults;
            } else {
                // We need to load all files with the specified frequency - this will be essentially the same
                // as a common properties search. TODO - do we want to do this or throw an error?
                return currentResults;
            }
        }
        
        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.FrequencySubFilter.desc=Files with frequency: {0}",
            "FileSearchFiltering.FrequencySubFilter.or= or ",
        })
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (Frequency freq : frequencies) {
                if ( ! desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_FrequencySubFilter_or();
                }
                desc += freq.name();
            }
            return Bundle.FileSearchFiltering_FrequencySubFilter_desc(this);
        }
    }

    
    private FileSearchFiltering() {
        // Class should not be instantiated
    }    
}
