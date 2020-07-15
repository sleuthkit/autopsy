/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.discovery.FileSearchData.FileSize;
import org.sleuthkit.autopsy.discovery.FileSearchData.FileType;
import org.sleuthkit.autopsy.discovery.FileSearchData.Frequency;
import org.sleuthkit.autopsy.discovery.FileSearchData.Score;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Run various filters to return a subset of files from the current case.
 */
class FileSearchFiltering {

    /**
     * Run the given filters to get a list of matching files.
     *
     * @param filters The filters to run
     * @param caseDb  The case database
     * @param crDb    The central repo. Can be null as long as no filters need
     *                it.
     *
     * @return
     */
    static List<ResultFile> runQueries(List<FileFilter> filters, SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {
        if (caseDb == null) {
            throw new FileSearchException("Case DB parameter is null"); // NON-NLS
        }
        // Combine all the SQL queries from the filters into one query
        String combinedQuery = "";
        for (FileFilter filter : filters) {
            if (!filter.getWhereClause().isEmpty()) {
                if (!combinedQuery.isEmpty()) {
                    combinedQuery += " AND "; // NON-NLS
                }
                combinedQuery += "(" + filter.getWhereClause() + ")"; // NON-NLS
            }
        }

        if (combinedQuery.isEmpty()) {
            // The file search filter is required, so this should never be empty.
            throw new FileSearchException("Selected filters do not include a case database query");
        }
        try {
            return getResultList(filters, combinedQuery, caseDb, centralRepoDb);
        } catch (TskCoreException ex) {
            throw new FileSearchException("Error querying case database", ex); // NON-NLS
        }
    }

    /**
     * Private helper method for runQueries method to get the ResultFile list.
     *
     * @param filters       The filters to run.
     * @param combinedQuery The query to get results files for.
     * @param caseDb        The case database.
     * @param crDb          The central repo. Can be null as long as no filters
     *                      need it.
     *
     * @return An ArrayList of ResultFiles returned by the query.
     *
     * @throws TskCoreException
     * @throws FileSearchException
     */
    private static List<ResultFile> getResultList(List<FileFilter> filters, String combinedQuery, SleuthkitCase caseDb, CentralRepository centralRepoDb) throws TskCoreException, FileSearchException {
        // Get all matching abstract files
        List<ResultFile> resultList = new ArrayList<>();
        List<AbstractFile> sqlResults = caseDb.findAllFilesWhere(combinedQuery);

        // If there are no results, return now
        if (sqlResults.isEmpty()) {
            return resultList;
        }

        // Wrap each result in a ResultFile
        for (AbstractFile abstractFile : sqlResults) {
            resultList.add(new ResultFile(abstractFile));
        }

        // Now run any non-SQL filters. 
        for (FileFilter filter : filters) {
            if (filter.useAlternateFilter()) {
                resultList = filter.applyAlternateFilter(resultList, caseDb, centralRepoDb);
            }
            // There are no matches for the filters run so far, so return
            if (resultList.isEmpty()) {
                return resultList;
            }
        }
        return resultList;
    }

    /**
     * Base class for the filters.
     */
    static abstract class FileFilter {

        /**
         * Returns part of a query on the tsk_files table that can be AND-ed
         * with other pieces
         *
         * @return the SQL query or an empty string if there is no SQL query for
         *         this filter.
         */
        abstract String getWhereClause();

        /**
         * Indicates whether this filter needs to use the secondary, non-SQL
         * method applyAlternateFilter().
         *
         * @return false by default
         */
        boolean useAlternateFilter() {
            return false;
        }

        /**
         * Run a secondary filter that does not operate on tsk_files.
         *
         * @param currentResults The current list of matching files; empty if no
         *                       filters have yet been run.
         * @param caseDb         The case database
         * @param centralRepoDb  The central repo database. Can be null if the
         *                       filter does not require it.
         *
         * @return The list of files that match this filter (and any that came
         *         before it)
         *
         * @throws FileSearchException
         */
        List<ResultFile> applyAlternateFilter(List<ResultFile> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {
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
    static class SizeFilter extends FileFilter {

        private final List<FileSize> fileSizes;

        /**
         * Create the SizeFilter
         *
         * @param fileSizes the file sizes that should match
         */
        SizeFilter(List<FileSize> fileSizes) {
            this.fileSizes = fileSizes;
        }

        @Override
        String getWhereClause() {
            String queryStr = ""; // NON-NLS
            for (FileSize size : fileSizes) {
                if (!queryStr.isEmpty()) {
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
            "FileSearchFiltering.SizeFilter.desc=Size(s): {0}",
            "FileSearchFiltering.SizeFilter.or=, "})
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (FileSize size : fileSizes) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_SizeFilter_or();
                }
                desc += size.getSizeGroup();
            }
            desc = Bundle.FileSearchFiltering_SizeFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A utility class for the ParentFilter to store the search string and
     * whether it is a full path or a substring.
     */
    static class ParentSearchTerm {

        private final String searchStr;
        private final boolean fullPath;
        private final boolean included;

        /**
         * Create the ParentSearchTerm object
         *
         * @param searchStr  The string to search for in the file path
         * @param isFullPath True if the path should exactly match the given
         *                   string, false to do a substring search
         * @param isIncluded True if the results must include the path, false if
         *                   the path should be excluded from the results.
         */
        ParentSearchTerm(String searchStr, boolean isFullPath, boolean isIncluded) {
            this.searchStr = searchStr;
            this.fullPath = isFullPath;
            this.included = isIncluded;
        }

        /**
         * Get the SQL term to search for
         *
         * @return The SQL for a where clause to search for a matching path
         */
        String getSQLForTerm() {
            // TODO - these should really be prepared statements
            if (isIncluded()) {
                if (isFullPath()) {
                    return "parent_path=\'" + getSearchStr() + "\'"; // NON-NLS
                } else {
                    return "parent_path LIKE \'%" + getSearchStr() + "%\'"; // NON-NLS
                }
            } else {
                if (isFullPath()) {
                    return "parent_path!=\'" + getSearchStr() + "\'"; // NON-NLS
                } else {
                    return "parent_path NOT LIKE \'%" + getSearchStr() + "%\'"; // NON-NLS
                }
            }
        }

        @NbBundle.Messages({
            "FileSearchFiltering.ParentSearchTerm.fullString= (exact)",
            "FileSearchFiltering.ParentSearchTerm.subString= (substring)",
            "FileSearchFiltering.ParentSearchTerm.includeString= (include)",
            "FileSearchFiltering.ParentSearchTerm.excludeString= (exclude)",})
        @Override
        public String toString() {
            String returnString = getSearchStr();
            if (isFullPath()) {
                returnString += Bundle.FileSearchFiltering_ParentSearchTerm_fullString();
            } else {
                returnString += Bundle.FileSearchFiltering_ParentSearchTerm_subString();
            }
            if (isIncluded()) {
                returnString += Bundle.FileSearchFiltering_ParentSearchTerm_includeString();
            } else {
                returnString += Bundle.FileSearchFiltering_ParentSearchTerm_excludeString();
            }
            return returnString;
        }

        /**
         * @return the fullPath
         */
        boolean isFullPath() {
            return fullPath;
        }

        /**
         * @return the included
         */
        boolean isIncluded() {
            return included;
        }

        /**
         * @return the searchStr
         */
        String getSearchStr() {
            return searchStr;
        }
    }

    /**
     * A filter for specifying parent path (either full path or substring)
     */
    static class ParentFilter extends FileFilter {

        private final List<ParentSearchTerm> parentSearchTerms;

        /**
         * Create the ParentFilter
         *
         * @param parentSearchTerms Full paths or substrings to filter on
         */
        ParentFilter(List<ParentSearchTerm> parentSearchTerms) {
            this.parentSearchTerms = parentSearchTerms;
        }

        @Override
        String getWhereClause() {
            String includeQueryStr = ""; // NON-NLS
            String excludeQueryStr = "";
            for (ParentSearchTerm searchTerm : parentSearchTerms) {
                if (searchTerm.isIncluded()) {
                    if (!includeQueryStr.isEmpty()) {
                        includeQueryStr += " OR "; // NON-NLS
                    }
                    includeQueryStr += searchTerm.getSQLForTerm();
                } else {
                    if (!excludeQueryStr.isEmpty()) {
                        excludeQueryStr += " AND "; // NON-NLS
                    }
                    excludeQueryStr += searchTerm.getSQLForTerm();
                }
            }
            if (!includeQueryStr.isEmpty()) {
                includeQueryStr = "(" + includeQueryStr + ")";
            }
            if (!excludeQueryStr.isEmpty()) {
                excludeQueryStr = "(" + excludeQueryStr + ")";
            }
            if (includeQueryStr.isEmpty() || excludeQueryStr.isEmpty()) {
                return includeQueryStr + excludeQueryStr;
            } else {
                return includeQueryStr + " AND " + excludeQueryStr;
            }
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.ParentFilter.desc=Paths matching: {0}",
            "FileSearchFiltering.ParentFilter.or=, ",
            "FileSearchFiltering.ParentFilter.exact=(exact match)",
            "FileSearchFiltering.ParentFilter.substring=(substring)",
            "FileSearchFiltering.ParentFilter.included=(included)",
            "FileSearchFiltering.ParentFilter.excluded=(excluded)"})
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (ParentSearchTerm searchTerm : parentSearchTerms) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_ParentFilter_or();
                }
                if (searchTerm.isFullPath()) {
                    desc += searchTerm.getSearchStr() + Bundle.FileSearchFiltering_ParentFilter_exact();
                } else {
                    desc += searchTerm.getSearchStr() + Bundle.FileSearchFiltering_ParentFilter_substring();
                }
                if (searchTerm.isIncluded()) {
                    desc += Bundle.FileSearchFiltering_ParentFilter_included();                           
                } else {
                    desc += Bundle.FileSearchFiltering_ParentFilter_excluded();
                }
            }
            desc = Bundle.FileSearchFiltering_ParentFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying data sources
     */
    static class DataSourceFilter extends FileFilter {

        private final List<DataSource> dataSources;

        /**
         * Create the DataSourceFilter
         *
         * @param dataSources the data sources to filter on
         */
        DataSourceFilter(List<DataSource> dataSources) {
            this.dataSources = dataSources;
        }

        @Override
        String getWhereClause() {
            String queryStr = ""; // NON-NLS
            for (DataSource ds : dataSources) {
                if (!queryStr.isEmpty()) {
                    queryStr += ","; // NON-NLS
                }
                queryStr += "\'" + ds.getId() + "\'"; // NON-NLS
            }
            queryStr = "data_source_obj_id IN (" + queryStr + ")"; // NON-NLS
            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.DataSourceFilter.desc=Data source(s): {0}",
            "FileSearchFiltering.DataSourceFilter.or=, ",
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "FileSearchFiltering.DataSourceFilter.datasource={0}({1})",})
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (DataSource ds : dataSources) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_DataSourceFilter_or();
                }
                desc += Bundle.FileSearchFiltering_DataSourceFilter_datasource(ds.getName(), ds.getId());
            }
            desc = Bundle.FileSearchFiltering_DataSourceFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying keyword list names. A file must contain a keyword
     * from one of the given lists to pass.
     */
    static class KeywordListFilter extends FileFilter {

        private final List<String> listNames;

        /**
         * Create the KeywordListFilter
         *
         * @param listNames
         */
        KeywordListFilter(List<String> listNames) {
            this.listNames = listNames;
        }

        @Override
        String getWhereClause() {
            String keywordListPart = concatenateNamesForSQL(listNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = 9 AND attribute_type_ID = 37 "
                    + "AND (" + keywordListPart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.KeywordListFilter.desc=Keywords in list(s): {0}",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_KeywordListFilter_desc(concatenateSetNamesForDisplay(listNames));
        }
    }

    /**
     * A filter for specifying file types.
     */
    static class FileTypeFilter extends FileFilter {

        private final List<FileType> categories;

        /**
         * Create the FileTypeFilter
         *
         * @param categories List of file types to filter on
         */
        FileTypeFilter(List<FileType> categories) {
            this.categories = categories;
        }

        /**
         * Create the FileTypeFilter
         *
         * @param category the file type to filter on
         */
        FileTypeFilter(FileType category) {
            this.categories = new ArrayList<>();
            this.categories.add(category);
        }

        @Override
        String getWhereClause() {
            String queryStr = ""; // NON-NLS
            for (FileType cat : categories) {
                for (String type : cat.getMediaTypes()) {
                    if (!queryStr.isEmpty()) {
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
            "FileSearchFiltering.FileTypeFilter.desc=Type: {0}",
            "FileSearchFiltering.FileTypeFilter.or=, ",})
        @Override
        String getDesc() {
            String desc = "";
            for (FileType cat : categories) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_FileTypeFilter_or();
                }
                desc += cat.toString();
            }
            desc = Bundle.FileSearchFiltering_FileTypeFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying frequency in the central repository.
     */
    static class FrequencyFilter extends FileFilter {

        private final List<Frequency> frequencies;

        /**
         * Create the FrequencyFilter
         *
         * @param frequencies List of frequencies that will pass the filter
         */
        FrequencyFilter(List<Frequency> frequencies) {
            this.frequencies = frequencies;
        }

        @Override
        String getWhereClause() {
            // Since this relies on the central repository database, there is no
            // query on the case database.
            return ""; // NON-NLS
        }

        @Override
        boolean useAlternateFilter() {
            return true;
        }

        @Override
        List<ResultFile> applyAlternateFilter(List<ResultFile> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            // We have to have run some kind of SQL filter before getting to this point,
            // and should have checked afterward to see if the results were empty.
            if (currentResults.isEmpty()) {
                throw new FileSearchException("Can not run on empty list"); // NON-NLS
            }

            // Set the frequency for each file
            FileSearch.FrequencyAttribute freqAttr = new FileSearch.FrequencyAttribute();
            freqAttr.addAttributeToResultFiles(currentResults, caseDb, centralRepoDb);

            // If the frequency matches the filter, add the file to the results
            List<ResultFile> frequencyResults = new ArrayList<>();
            for (ResultFile file : currentResults) {
                if (frequencies.contains(file.getFrequency())) {
                    frequencyResults.add(file);
                }
            }
            return frequencyResults;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.FrequencyFilter.desc=Past occurrences: {0}",
            "FileSearchFiltering.FrequencyFilter.or=, ",})
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (Frequency freq : frequencies) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_FrequencyFilter_or();
                }
                desc += freq.toString();
            }
            return Bundle.FileSearchFiltering_FrequencyFilter_desc(desc);
        }
    }

    /**
     * A filter for specifying hash set names. A file must match one of the
     * given sets to pass.
     */
    static class HashSetFilter extends FileFilter {

        private final List<String> setNames;

        /**
         * Create the HashSetFilter
         *
         * @param setNames
         */
        HashSetFilter(List<String> setNames) {
            this.setNames = setNames;
        }

        @Override
        String getWhereClause() {
            String hashSetPart = concatenateNamesForSQL(setNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                    + " AND attribute_type_ID = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " "
                    + "AND (" + hashSetPart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.HashSetFilter.desc=Hash set hits in set(s): {0}",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_HashSetFilter_desc(concatenateSetNamesForDisplay(setNames));
        }
    }

    /**
     * A filter for specifying interesting file set names. A file must match one
     * of the given sets to pass.
     */
    static class InterestingFileSetFilter extends FileFilter {

        private final List<String> setNames;

        /**
         * Create the InterestingFileSetFilter
         *
         * @param setNames
         */
        InterestingFileSetFilter(List<String> setNames) {
            this.setNames = setNames;
        }

        @Override
        String getWhereClause() {
            String intItemSetPart = concatenateNamesForSQL(setNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
                    + " AND attribute_type_ID = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " "
                    + "AND (" + intItemSetPart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.InterestingItemSetFilter.desc=Interesting item hits in set(s): {0}",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_InterestingItemSetFilter_desc(concatenateSetNamesForDisplay(setNames));
        }
    }

    /**
     * A filter for specifying object types detected. A file must match one of
     * the given types to pass.
     */
    static class ObjectDetectionFilter extends FileFilter {

        private final List<String> typeNames;

        /**
         * Create the ObjectDetectionFilter
         *
         * @param typeNames
         */
        ObjectDetectionFilter(List<String> typeNames) {
            this.typeNames = typeNames;
        }

        @Override
        String getWhereClause() {
            String objTypePart = concatenateNamesForSQL(typeNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID()
                    + " AND attribute_type_ID = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID() + " "
                    + "AND (" + objTypePart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.ObjectDetectionFilter.desc=Objects detected in set(s): {0}",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_ObjectDetectionFilter_desc(concatenateSetNamesForDisplay(typeNames));
        }
    }

    /**
     * A filter for specifying the score. A file must have one of the given
     * scores to pass
     */
    static class ScoreFilter extends FileFilter {

        private final List<Score> scores;

        /**
         * Create the ObjectDetectionFilter
         *
         * @param typeNames
         */
        ScoreFilter(List<Score> scores) {
            this.scores = scores;
        }

        @Override
        String getWhereClause() {

            // Current algorithm:
            // "Notable" if the file is a match for a notable hashset or has been tagged with a notable tag.
            // "Interesting" if the file has an interesting item match or has been tagged with a non-notable tag.
            String hashsetQueryPart = "";
            String tagQueryPart = "";
            String intItemQueryPart = "";

            if (scores.contains(Score.NOTABLE)) {
                // do hashset
                hashsetQueryPart = " (known = " + TskData.FileKnown.BAD.getFileKnownValue() + ") ";
            }

            if (scores.contains(Score.INTERESTING)) {
                // Matches interesting item artifact
                intItemQueryPart = " (obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_type_id = "
                        + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() + ")) ";
            }

            if (scores.contains(Score.NOTABLE) && scores.contains(Score.INTERESTING)) {
                // Any tag will work
                tagQueryPart = "(obj_id IN (SELECT obj_id FROM content_tags))";
            } else if (scores.contains(Score.NOTABLE)) {
                // Notable tags
                tagQueryPart = "(obj_id IN (SELECT obj_id FROM content_tags WHERE tag_name_id IN (SELECT tag_name_id FROM tag_names WHERE knownStatus = "
                        + TskData.FileKnown.BAD.getFileKnownValue() + ")))";
            } else if (scores.contains(Score.INTERESTING)) {
                // Non-notable tags
                tagQueryPart = "(obj_id IN (SELECT obj_id FROM content_tags WHERE tag_name_id IN (SELECT tag_name_id FROM tag_names WHERE knownStatus != "
                        + TskData.FileKnown.BAD.getFileKnownValue() + ")))";
            }

            String queryStr = hashsetQueryPart;
            if (!intItemQueryPart.isEmpty()) {
                if (!queryStr.isEmpty()) {
                    queryStr += " OR ";
                }
                queryStr += intItemQueryPart;
            }
            if (!tagQueryPart.isEmpty()) {
                if (!queryStr.isEmpty()) {
                    queryStr += " OR ";
                }
                queryStr += tagQueryPart;
            }
            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "FileSearchFiltering.ScoreFilter.desc=Score(s) of : {0}",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_ScoreFilter_desc(
                    concatenateSetNamesForDisplay(scores.stream().map(p -> p.toString()).collect(Collectors.toList())));
        }
    }

    /**
     * A filter for specifying tag names. A file must contain one of the given
     * tags to pass.
     */
    static class TagsFilter extends FileFilter {

        private final List<TagName> tagNames;

        /**
         * Create the TagsFilter
         *
         * @param tagNames
         */
        TagsFilter(List<TagName> tagNames) {
            this.tagNames = tagNames;
        }

        @Override
        String getWhereClause() {
            String tagIDs = ""; // NON-NLS
            for (TagName tagName : tagNames) {
                if (!tagIDs.isEmpty()) {
                    tagIDs += ",";
                }
                tagIDs += tagName.getId();
            }

            String queryStr = "(obj_id IN (SELECT obj_id FROM content_tags WHERE tag_name_id IN (" + tagIDs + ")))";

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - tag names",
            "FileSearchFiltering.TagsFilter.desc=Tagged {0}",
            "FileSearchFiltering.TagsFilter.or=, ",})
        @Override
        String getDesc() {
            String desc = ""; // NON-NLS
            for (TagName name : tagNames) {
                if (!desc.isEmpty()) {
                    desc += Bundle.FileSearchFiltering_TagsFilter_or();
                }
                desc += name.getDisplayName();
            }
            return Bundle.FileSearchFiltering_TagsFilter_desc(desc);
        }
    }

    /**
     * A filter for specifying that the file must have user content suspected
     * data.
     */
    static class UserCreatedFilter extends FileFilter {

        /**
         * Create the ExifFilter
         */
        UserCreatedFilter() {
            // Nothing to save
        }

        @Override
        String getWhereClause() {
            return "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = "
                    + BlackboardArtifact.ARTIFACT_TYPE.TSK_USER_CONTENT_SUSPECTED.getTypeID() + ")))";
        }

        @NbBundle.Messages({
            "FileSearchFiltering.UserCreatedFilter.desc=that contain EXIF data",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_UserCreatedFilter_desc();
        }
    }

    /**
     * A filter for specifying that the file must have been marked as notable in
     * the CR.
     */
    static class NotableFilter extends FileFilter {

        /**
         * Create the NotableFilter
         */
        NotableFilter() {
            // Nothing to save
        }

        @Override
        String getWhereClause() {
            // Since this relies on the central repository database, there is no
            // query on the case database.
            return ""; // NON-NLS
        }

        @Override
        boolean useAlternateFilter() {
            return true;
        }

        @Override
        List<ResultFile> applyAlternateFilter(List<ResultFile> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            if (centralRepoDb == null) {
                throw new FileSearchException("Can not run Previously Notable filter with null Central Repository DB"); // NON-NLS
            }

            // We have to have run some kind of SQL filter before getting to this point,
            // and should have checked afterward to see if the results were empty.
            if (currentResults.isEmpty()) {
                throw new FileSearchException("Can not run on empty list"); // NON-NLS
            }

            // The matching files
            List<ResultFile> notableResults = new ArrayList<>();

            try {
                CorrelationAttributeInstance.Type type = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(CorrelationAttributeInstance.FILES_TYPE_ID);

                for (ResultFile file : currentResults) {
                    if (file.getFirstInstance().getMd5Hash() != null && !file.getFirstInstance().getMd5Hash().isEmpty()) {

                        // Check if this file hash is marked as notable in the CR
                        String value = file.getFirstInstance().getMd5Hash();
                        if (centralRepoDb.getCountArtifactInstancesKnownBad(type, value) > 0) {
                            notableResults.add(file);
                        }
                    }
                }
                return notableResults;
            } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                throw new FileSearchException("Error querying central repository", ex); // NON-NLS
            }
        }

        @NbBundle.Messages({
            "FileSearchFiltering.PreviouslyNotableFilter.desc=that were previously marked as notable",})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_PreviouslyNotableFilter_desc();
        }
    }

    /**
     * A filter for specifying if known files should be included.
     */
    static class KnownFilter extends FileFilter {

        @Override
        String getWhereClause() {
            return "known!=" + TskData.FileKnown.KNOWN.getFileKnownValue(); // NON-NLS
        }

        @NbBundle.Messages({
            "FileSearchFiltering.KnownFilter.desc=which are not known"})
        @Override
        String getDesc() {
            return Bundle.FileSearchFiltering_KnownFilter_desc();
        }
    }

    @NbBundle.Messages({
        "FileSearchFiltering.concatenateSetNamesForDisplay.comma=, ",})
    private static String concatenateSetNamesForDisplay(List<String> setNames) {
        String desc = ""; // NON-NLS
        for (String setName : setNames) {
            if (!desc.isEmpty()) {
                desc += Bundle.FileSearchFiltering_concatenateSetNamesForDisplay_comma();
            }
            desc += setName;
        }
        return desc;
    }

    /**
     * Concatenate the set names into an "OR" separated list. This does not do
     * any SQL-escaping.
     *
     * @param setNames
     *
     * @return the list to use in the SQL query
     */
    private static String concatenateNamesForSQL(List<String> setNames) {
        String result = ""; // NON-NLS
        for (String setName : setNames) {
            if (!result.isEmpty()) {
                result += " OR "; // NON-NLS
            }
            result += "value_text = \'" + setName + "\'";  // NON-NLS
        }
        return result;
    }

    private FileSearchFiltering() {
        // Class should not be instantiated
    }
}
