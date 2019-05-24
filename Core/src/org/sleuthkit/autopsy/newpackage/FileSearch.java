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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileSize;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileType;
import org.sleuthkit.autopsy.newpackage.FileSearchData.Frequency;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main class to perform the file search.
 */
class FileSearch {
    
    private final static Logger logger = Logger.getLogger(FileSearch.class.getName());
    
    /**
     * Run the file search.
     * 
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the groups
     * @param attributesNeededForGroupingOrSorting  Any attributes that will used for grouping or sorting
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if not needed.
     * 
     * @return A LinkedHashMap grouped and sorted according to the parameters
     * 
     * @throws FileSearchException 
     */
    static LinkedHashMap<String, List<AbstractFile>> runFileSearch(
            List<FileSearchFiltering.SubFilter> filters, 
            AttributeType groupAttributeType, 
            FileGroup.GroupSortingAlgorithm groupSortingType, 
            Comparator<ResultFile> fileSortingMethod, 
            List<AttributeType> attributesNeededForGroupingOrSorting,
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        
        // Run the queries for each filter
        List<ResultFile> resultFiles = FileSearchFiltering.runQueries(filters, caseDb, centralRepoDb);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, resultFiles, caseDb, centralRepoDb);
        
        // Collect everything in the search results
        // TODO move add into Searchresults
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        for (ResultFile file : resultFiles) {
            searchResults.add(file);
        }

        // Return a version of the results in general Java objects
        return searchResults.toLinkedHashMap();
    }
    
    /**
     * Add any attributes corresponding to the attribute list to the given result files.
     * For example, specifying the KeywordListAttribute will populate the list of
     * keyword set names in the ResultFile objects.
     * 
     * @param attrs         The attributes to add to the list of result files
     * @param resultFiles   The result files
     * @param caseDb        The case database
     * @param centralRepoDb The central repository database. Can be null if not needed.
     * 
     * @throws FileSearchException 
     */
    static void addAttributes(List<AttributeType> attrs, List<ResultFile> resultFiles, SleuthkitCase caseDb, EamDb centralRepoDb)
            throws FileSearchException {
        for (AttributeType attr : attrs) {
            attr.addAttributeToResultFiles(resultFiles, caseDb, centralRepoDb);
        }
    }

    /**
     * Get a comparator for sorting on file name
     * 
     * @return comparator for case-insensitive sort on the abstract file name field 
     */
    static Comparator<ResultFile> getFileNameComparator() {
        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
                return file1.getAbstractFile().getName().compareToIgnoreCase(file2.getAbstractFile().getName());
            }
        };
    }
    
    /**
     * Base class for the grouping attributes.
     */
    abstract static class AttributeType {
        /**
         * For a given file, return the key for the group it belongs to 
         * for this attribute type.
         * 
         * @param file the result file to be grouped
         * 
         * @return the key for the group this file goes in
         */
        abstract GroupKey getGroupKey(ResultFile file);
        
        /**
         * Get the file comparator based on this attribute.
         * 
         * @return the file comparator based on this attribute
         */
        abstract Comparator<ResultFile> getDefaultFileComparator();
        
        /**
         * Add any extra data to the ResultFile object from this attribute.
         * 
         * @param files         The list of files to enhance
         * @param caseDb        The case database
         * @param centralRepoDb The central repository database. Can be null if not needed.
         * 
         * @throws FileSearchException 
         */
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
            // Default is to do nothing
        }
    }
    
    /**
     * The key used for grouping for each attribute type.
     */
    abstract static class GroupKey implements Comparable<GroupKey> {
        
        /**
         * Get the string version of the group key for display. Each
         * display name should correspond to a unique GroupKey object.
         * 
         * @return The display name for this key
         */
        abstract String getDisplayName();
        
        /**
         * Subclasses must implement equals().
         * 
         * @param otherKey
         * 
         * @return true if the keys are equal, false otherwise
         */
        @Override
        abstract public boolean equals(Object otherKey);

        /**
         * Subclasses must implement hashCode().
         * 
         * @return the hash code 
         */
        @Override
        abstract public int hashCode();
        
        /**
         * It should not happen with the current setup, but we need to cover the case where 
         * two different GroupKey subclasses are compared against each other.
         * Use a lexicographic comparison on the class names.
         * 
         * @param otherGroupKey The other group key
         * 
         * @return result of alphabetical comparison on the class name
         */
        int compareClassNames(GroupKey otherGroupKey) {
            return this.getClass().getName().compareTo(otherGroupKey.getClass().getName());
        }
    }
    
    /**
     * Attribute for grouping/sorting by file size
     */
    static class FileSizeAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FileSizeGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    // Sort large to small
                    if (file1.getAbstractFile().getSize() != file2.getAbstractFile().getSize()) {
                        return -1 * Long.compare(file1.getAbstractFile().getSize(), file2.getAbstractFile().getSize());
                    }
                    
                    // Secondary sort on file name
                    return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                }
            };
        }
    }
    
    static class FileSizeGroupKey extends GroupKey {
        private final FileSize fileSize;
                
        FileSizeGroupKey(ResultFile file) {
            fileSize = FileSize.fromSize(file.getAbstractFile().getSize());
        }
        
        @Override
        String getDisplayName() {
            return fileSize.name();
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileSizeGroupKey) {
                FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey)otherGroupKey;
                return Integer.compare(fileSize.getRanking(), otherFileSizeGroupKey.fileSize.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof FileSizeGroupKey)) {
                return false;
            }
            
            FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey)otherKey;
            return fileSize.equals(otherFileSizeGroupKey.fileSize);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileSize.getRanking());
        }
    }
    
    /**
     * Attribute for grouping/sorting by parent path
     */
    static class ParentPathAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new ParentPathGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    // Handle missing paths
                    if (file1.getAbstractFile().getParentPath() == null) {
                        if (file2.getAbstractFile().getParentPath() == null) {
                            // Secondary sort on file name
                            return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                        } else {
                            return 1;
                        }
                    } else if (file2.getAbstractFile().getParentPath() == null) {
                        return -1;
                    } 
                    
                    // Secondary sort on file name if the parent paths are the same
                    if (file1.getAbstractFile().getParentPath().equals(file2.getAbstractFile().getParentPath())) {
                        return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                    }
                    
                    // Case insensitive comparison on the parent path
                    return file1.getAbstractFile().getParentPath().compareToIgnoreCase(file2.getAbstractFile().getParentPath());
                }
            };
        }
    }
    
    static class ParentPathGroupKey extends GroupKey {
        private final String parentPath;
                
        ParentPathGroupKey(ResultFile file) {
            if (file.getAbstractFile().getParentPath() != null) {
                parentPath = file.getAbstractFile().getParentPath();
            } else {
                parentPath = ""; // NON-NLS
            }
        }
        
        @Override
        String getDisplayName() {
            return parentPath;
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof ParentPathGroupKey) {
                ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey)otherGroupKey;
                return parentPath.compareTo(otherParentPathGroupKey.parentPath);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }        
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof ParentPathGroupKey)) {
                return false;
            }
            
            ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey)otherKey;
            return parentPath.equals(otherParentPathGroupKey.parentPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentPath);
        }
    }    
    
    /**
     * Attribute for grouping/sorting by data source
     */
    static class DataSourceAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new DataSourceGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    // Primary sort on data source object ID, small to large
                    if (file1.getAbstractFile().getDataSourceObjectId() != file2.getAbstractFile().getDataSourceObjectId()) {
                        return Long.compare(file1.getAbstractFile().getDataSourceObjectId(), file2.getAbstractFile().getDataSourceObjectId());
                    }
                    
                    // Secondary sort on file name
                    return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                }
            };
        }
    }    
    
    static class DataSourceGroupKey extends GroupKey {
        private final long dataSourceID;
        private String displayName;
        
        @NbBundle.Messages({
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "FileSearch.DataSourceGroupKey.datasourceAndID={0}(ID: {1})",
            "# {0} - Data source ID",
            "FileSearch.DataSourceGroupKey.idOnly=Data source (ID: {0})",
        })        
        DataSourceGroupKey(ResultFile file) {
            dataSourceID = file.getAbstractFile().getDataSourceObjectId();
            
            try {
                // The data source should be cached so this won't actually be a database query.
                Content ds = file.getAbstractFile().getDataSource();
                displayName = Bundle.FileSearch_DataSourceGroupKey_datasourceAndID(ds.getName(), ds.getId());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error looking up data source with ID " + dataSourceID, ex); // NON-NLS
                displayName = Bundle.FileSearch_DataSourceGroupKey_idOnly(dataSourceID);
            }
        }
        
        @Override
        String getDisplayName() {
            return displayName;
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof DataSourceGroupKey) {
                DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey)otherGroupKey;
                return Long.compare(dataSourceID, otherDataSourceGroupKey.dataSourceID);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }            
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof DataSourceGroupKey)) {
                return false;
            }
            
            DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey)otherKey;
            return dataSourceID == otherDataSourceGroupKey.dataSourceID;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataSourceID);
        }
    }       

    /**
     * Attribute for grouping/sorting by file type
     */
    static class FileTypeAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FileTypeGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    if (file1.getFileType() != file2.getFileType()) {
                        // Primary sort on the file type enum
                        return Integer.compare(file1.getFileType().getRanking(), file2.getFileType().getRanking());
                    } else {
                        String mimeType1 = file1.getAbstractFile().getMIMEType();
                        String mimeType2 = file2.getAbstractFile().getMIMEType();

                        // Handle missing MIME types
                        if (mimeType1 == null) {
                            if (mimeType2 == null) {
                                // Tertiary sort on file name
                                return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                            } else {
                                return 1;
                            }
                        } else if (mimeType2 == null) {
                            return -1;
                        } 

                        // Secondary sort on MIME type
                        if ( ! StringUtils.equals(mimeType1, mimeType2)) {
                            return mimeType1.compareToIgnoreCase(mimeType2);
                        }
                        
                        // Tertiary sort on file name
                        return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                    }
                }
            };
        }
        
        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            for (ResultFile file : files) {
                if (file.getFileType().equals(FileType.OTHER)) {
                    file.setFileType(FileType.fromMIMEtype(file.getAbstractFile().getMIMEType()));
                }
            }
        }
    }    

    static class FileTypeGroupKey extends GroupKey {
        private final FileType fileType;
                
        FileTypeGroupKey(ResultFile file) {
            fileType = file.getFileType();
        }
        
        @Override
        String getDisplayName() {
            return fileType.getDisplayName();
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileTypeGroupKey) {
                FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey)otherGroupKey;
                return Integer.compare(fileType.getRanking(), otherFileTypeGroupKey.fileType.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        } 
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof FileTypeGroupKey)) {
                return false;
            }
            
            FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey)otherKey;
            return fileType.equals(otherFileTypeGroupKey.fileType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileType.getRanking());
        }    
    }
    
    /**
     * Attribute for grouping/sorting by keyword lists
     */
    static class KeywordListAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new KeywordListGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    
                    // TODO fix sort
                    // Force "no keyword hits" to the bottom
                    if (! file1.hasKeywords()) {
                        if (! file2.hasKeywords()) {
                            // Secondary sort on file name
                            return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                        }
                        return 1;
                    }else if (! file2.hasKeywords()) {
                        return -1;
                    }
                    
                    if (file1.getKeywordListNames().equals(file2.getKeywordListNames())) {
                        // Secondary sort on file name
                        return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                    }
                    return -1;
                    //return file1.getKeywordListNames().compareToIgnoreCase(file2.getKeywordListNames());
                }
            };
        }
        
        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            
            // Concatenate the object IDs in the list of files
            String objIdList = ""; // NON-NLS
            for (ResultFile file : files) {
                if ( ! objIdList.isEmpty()) {
                    objIdList += ","; // NON-NLS
                }
                objIdList += "\'" + file.getAbstractFile().getId() + "\'"; // NON-NLS
            }
            
            // Get pairs of (object ID, keyword list name) for all files in the list of files that have
            // keyword list hits.
            String selectQuery = "blackboard_artifacts.obj_id AS object_id, blackboard_attributes.value_text AS keyword_list_name " +
                            "FROM blackboard_artifacts " +
                            "INNER JOIN blackboard_attributes ON blackboard_artifacts.artifact_id=blackboard_attributes.artifact_id " +
                            "WHERE blackboard_attributes.artifact_type_id=\'" + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + "\' " +
                            "AND blackboard_attributes.attribute_type_id=\'" + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + "\' " +
                            "AND blackboard_artifacts.obj_id IN (" + objIdList + ") "; // NON-NLS
            
            SetKeywordListNamesCallback callback = new SetKeywordListNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up keyword list attributes", ex);
            }
        }
        
        /**
         * Callback to process the results of the CaseDbAccessManager select query. Will add
         * the keyword list names to the list of ResultFile objects.
         */
        private static class SetKeywordListNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SetKeywordListNamesCallback.class.getName());
            List<ResultFile> resultFiles;
            
            /**
             * Create the callback.
             * 
             * @param resultFiles List of files to add keyword list names to
             */
            SetKeywordListNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }
            
            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getAbstractFile().getId(), file);
                    }
                    
                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String keywordListName = rs.getString("keyword_list_name"); // NON-NLS

                            tempMap.get(objId).addKeywordListName(keywordListName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or keyword_list_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get keyword list names", ex); // NON-NLS
                }
            }   
        }
    }   
    
    static class KeywordListGroupKey extends GroupKey {
        private final List<String> keywordListNames;
        private final String keywordListNamesString;
                
        @NbBundle.Messages({
            "FileSearch.KeywordListGroupKey.noKeywords=None",
        })
        KeywordListGroupKey(ResultFile file) {
            keywordListNames = file.getKeywordListNames();
            
            if (keywordListNames.isEmpty()) {
                keywordListNamesString = Bundle.FileSearch_KeywordListGroupKey_noKeywords();
            } else {
                keywordListNamesString = String.join(",", keywordListNames);
            }
        }
        
         
        @Override
        String getDisplayName() {
            return keywordListNamesString;
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof KeywordListGroupKey) {
                KeywordListGroupKey otherKeywordListNamesGroupKey = (KeywordListGroupKey)otherGroupKey;
                return keywordListNamesString.compareTo(otherKeywordListNamesGroupKey.keywordListNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        } 
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof KeywordListGroupKey)) {
                return false;
            }
            // TODO put no kw group last
            KeywordListGroupKey otherKeywordListGroupKey = (KeywordListGroupKey)otherKey;
            return keywordListNamesString.equals(otherKeywordListGroupKey.keywordListNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keywordListNamesString);
        }    
    }    
    
    /**
     * Attribute for grouping/sorting by frequency in the central repository
     */
    static class FrequencyAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FrequencyGroupKey(file);
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            return new Comparator<ResultFile>() {
                @Override
                public int compare(ResultFile file1, ResultFile file2) {
                    if (file1.getFrequency() != file2.getFrequency()) {
                        return Long.compare(file1.getFrequency().getRanking(), file2.getFrequency().getRanking());
                    } 
                    
                    // Secondary sort on file name
                    return file1.getAbstractFile().getName().compareToIgnoreCase(file1.getAbstractFile().getName());
                }
            };
        }
        
        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, 
                EamDb centralRepoDb) throws FileSearchException {
            
            if (centralRepoDb == null) {
                return; // TODO - this should be an error once we don't always run this
            }
            
            // We'll make this more efficient later - for now, add the frequency of each file individually
            for (ResultFile file : files) {
                if (file.getFrequency() == Frequency.UNKNOWN) {
                    try {
                        if (file.getAbstractFile().getMd5Hash() != null && ! file.getAbstractFile().getMd5Hash().isEmpty()) {
                            CorrelationAttributeInstance.Type attributeType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
                            long count = centralRepoDb.getCountUniqueCaseDataSourceTuplesHavingTypeValue(attributeType, file.getAbstractFile().getMd5Hash());
                            file.setFrequency(Frequency.fromCount(count));
                        }
                    } catch (EamDbException | CorrelationAttributeNormalizationException ex) {
                        throw new FileSearchException("Error looking up central repository frequency for file with ID " 
                                + file.getAbstractFile().getId(), ex);
                    }
                }
            }
        }        
    }
    
    static class FrequencyGroupKey extends GroupKey {
        private final Frequency frequency;
                
        FrequencyGroupKey(ResultFile file) {
            frequency = file.getFrequency();
        }
        
        @Override
        String getDisplayName() {
            return frequency.name();
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FrequencyGroupKey) {
                FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey)otherGroupKey;
                return Integer.compare(frequency.getRanking(), otherFrequencyGroupKey.frequency.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        } 
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof FrequencyGroupKey)) {
                return false;
            }
            
            FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey)otherKey;
            return frequency.equals(otherFrequencyGroupKey.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequency.getRanking());
        }    
    }    
    
    /**
     * Default attribute used to make one group
     */
    static class NoGroupingAttribute extends AttributeType {
        
        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new NoGroupingGroupKey();
        }
        
        @Override
        Comparator<ResultFile> getDefaultFileComparator() {
            // Default to sort by file name
            return FileSearch.getFileNameComparator();
        }
    }
    
    static class NoGroupingGroupKey extends GroupKey {
                
        NoGroupingGroupKey() {
            // Nothing to save - all files will get the same GroupKey
        }
        
        @NbBundle.Messages({
            "FileSearch.NoGroupingGroupKey.allFiles=All Files",
        })
        @Override
        String getDisplayName() {
            return Bundle.FileSearch_NoGroupingGroupKey_allFiles();
        }
        
        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof NoGroupingGroupKey) {
                return 0;
            } else {
                return compareClassNames(otherGroupKey);
            }
        }        
        
        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this){
                return true;
            }
            
            if (!(otherKey instanceof NoGroupingGroupKey)) {
                return false;
            }
            
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }    
    }      
    
    private FileSearch() {
        // Class should not be instantiated
    }
}
