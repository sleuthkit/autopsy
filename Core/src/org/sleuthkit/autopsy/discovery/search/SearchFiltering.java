/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.text.SimpleDateFormat;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.discovery.search.SearchData.FileSize;
import org.sleuthkit.autopsy.discovery.search.SearchData.Type;
import org.sleuthkit.autopsy.discovery.search.SearchData.Frequency;
import org.sleuthkit.autopsy.discovery.search.SearchData.Score;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Run various filters to return a subset of Results from the current case.
 */
public class SearchFiltering {

    /**
     * Run the given filters to get a list of matching files.
     *
     * @param filters       The filters to run.
     * @param caseDb        The case database.
     * @param centralRepoDb The central repo. Can be null as long as no filters
     *                      need it.
     * @param context       The SearchContext the search is being performed
     *                      from.
     *
     * @return List of Results from the search performed.
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    static List<Result> runQueries(List<AbstractFilter> filters, SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
        if (caseDb == null) {
            throw new DiscoveryException("Case DB parameter is null"); // NON-NLS
        }
        // Combine all the SQL queries from the filters into one query
        String combinedQuery = "";
        for (AbstractFilter filter : filters) {
            if (!filter.getWhereClause().isEmpty()) {
                if (!combinedQuery.isEmpty()) {
                    combinedQuery += " AND "; // NON-NLS
                }
                combinedQuery += "(" + filter.getWhereClause() + ")"; // NON-NLS
            }
        }

        if (combinedQuery.isEmpty()) {
            // The file search filter is required, so this should never be empty.
            throw new DiscoveryException("Selected filters do not include a case database query");
        }
        if (context.searchIsCancelled()) {
            throw new SearchCancellationException("The search was cancelled before result list could be retrieved.");
        }
        try {
            return getResultList(filters, combinedQuery, caseDb, centralRepoDb, context);
        } catch (TskCoreException ex) {
            throw new DiscoveryException("Error querying case database", ex); // NON-NLS
        }
    }

    /**
     * Private helper method for runQueries method to get the ResultFile list.
     *
     * @param filters       The filters to run.
     * @param combinedQuery The query to get results files for.
     * @param caseDb        The case database.
     * @param centralRepoDb The central repo. Can be null as long as no filters
     *                      need it.
     * @param context       The SearchContext the search is being performed
     *                      from.
     *
     * @return An ArrayList of Results returned by the query.
     *
     * @throws TskCoreException
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    private static List<Result> getResultList(List<AbstractFilter> filters, String combinedQuery, SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws TskCoreException, DiscoveryException, SearchCancellationException {
        // Get all matching abstract files
        List<Result> resultList = new ArrayList<>();
        List<AbstractFile> sqlResults = caseDb.findAllFilesWhere(combinedQuery);
        if (context.searchIsCancelled()) {
            throw new SearchCancellationException("The search was cancelled while the case database query was being performed.");
        }
        // If there are no results, return now
        if (sqlResults.isEmpty()) {
            return resultList;
        }

        // Wrap each result in a ResultFile
        for (AbstractFile abstractFile : sqlResults) {
            resultList.add(new ResultFile(abstractFile));
        }

        // Now run any non-SQL filters. 
        for (AbstractFilter filter : filters) {
            if (context.searchIsCancelled()) {
                throw new SearchCancellationException("The search was cancelled while alternate filters were being applied.");
            }
            if (filter.useAlternateFilter()) {
                resultList = filter.applyAlternateFilter(resultList, caseDb, centralRepoDb, context);
            }
            // There are no matches for the filters run so far, so return
            if (resultList.isEmpty()) {
                return resultList;
            }
        }
        return resultList;
    }

    /**
     * A filter to specify date range for artifacts, start and end times should
     * be in epoch seconds.
     */
    public static class ArtifactDateRangeFilter extends AbstractFilter {

        private final Long startDate;
        private final Long endDate;

        // Attributes to search for date
        private static List<BlackboardAttribute.ATTRIBUTE_TYPE> dateAttributes
                = Arrays.asList(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED
                );

        /**
         * Construct a new ArtifactDateRangeFilter.
         *
         * @param startDate The first date to include results for in the search.
         * @param endDate   The last date to include results for in the search.
         */
        public ArtifactDateRangeFilter(Long startDate, Long endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        /**
         * Create a SQL clause containing the date time attribute types to
         * search.
         */
        static String createAttributeTypeClause() {
            StringJoiner joiner = new StringJoiner(",");
            for (BlackboardAttribute.ATTRIBUTE_TYPE type : dateAttributes) {
                joiner.add("\'" + type.getTypeID() + "\'");
            }
            return "attribute_type_id IN (" + joiner.toString() + ")";
        }

        @Override
        public String getWhereClause() {
            return createAttributeTypeClause()
                    + " AND (value_int64 BETWEEN " + startDate + " AND " + endDate + ")";
        }

        @NbBundle.Messages({"SearchFiltering.dateRangeFilter.lable=Activity date ",
            "# {0} - startDate",
            "SearchFiltering.dateRangeFilter.after=after: {0}",
            "# {0} - endDate",
            "SearchFiltering.dateRangeFilter.before=before: {0}",
            "SearchFiltering.dateRangeFilter.and= and "})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            if (startDate > 0) {
                desc += Bundle.SearchFiltering_dateRangeFilter_after(new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(TimeUnit.SECONDS.toMillis(startDate))));
            }
            if (endDate < 10000000000L) { //arbitrary time sometime in the 23rd century to check that they specified a date and the max date isn't being used
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_dateRangeFilter_and();
                }
                desc += Bundle.SearchFiltering_dateRangeFilter_before(new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(TimeUnit.SECONDS.toMillis(endDate))));
            }
            if (!desc.isEmpty()) {
                desc = Bundle.SearchFiltering_dateRangeFilter_lable() + desc;
            }
            return desc;
        }
    }

    /**
     * A filter to specify artifact types.
     */
    public static class ArtifactTypeFilter extends AbstractFilter {

        private final Collection<ARTIFACT_TYPE> types;

        /**
         * Construct a new ArtifactTypeFilter.
         *
         * @param types The list of BlackboardArtifact types to include in
         *              results from.
         */
        public ArtifactTypeFilter(Collection<ARTIFACT_TYPE> types) {
            this.types = types;
        }

        /**
         * Get the list of artifact types specified by the filter.
         *
         * @return The list of artifact types specified by the filter.
         */
        public Collection<ARTIFACT_TYPE> getTypes() {
            return Collections.unmodifiableCollection(types);
        }

        private StringJoiner joinStandardArtifactTypes() {
            StringJoiner joiner = new StringJoiner(",");
            for (ARTIFACT_TYPE type : types) {
                joiner.add("\'" + type.getTypeID() + "\'");
            }
            return joiner;
        }

        @Override
        public String getWhereClause() {
            StringJoiner joiner = joinStandardArtifactTypes();
            return "artifact_type_id IN (" + joiner + ")";
        }

        /**
         * Used by backend domain search code to query for additional artifact
         * types.
         */
        String getWhereClause(List<ARTIFACT_TYPE> nonVisibleArtifactTypesToInclude) {
            StringJoiner joiner = joinStandardArtifactTypes();
            for (ARTIFACT_TYPE type : nonVisibleArtifactTypesToInclude) {
                joiner.add("\'" + type.getTypeID() + "\'");
            }
            return "artifact_type_id IN (" + joiner + ")";
        }

        @NbBundle.Messages({"# {0} - artifactTypes",
            "SearchFiltering.artifactTypeFilter.desc=Result type(s): {0}",
            "SearchFiltering.artifactTypeFilter.or=, "})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            for (ARTIFACT_TYPE type : types) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_artifactTypeFilter_or();
                }
                desc += type.getDisplayName();
            }
            desc = Bundle.SearchFiltering_artifactTypeFilter_desc(desc);
            return desc;
        }

    }

    /**
     * A filter for specifying the file size.
     */
    public static class SizeFilter extends AbstractFilter {

        private final List<FileSize> fileSizes;

        /**
         * Create the SizeFilter.
         *
         * @param fileSizes The file sizes that should match.
         */
        public SizeFilter(List<FileSize> fileSizes) {
            this.fileSizes = fileSizes;
        }

        @Override
        public String getWhereClause() {
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
            "SearchFiltering.SizeFilter.desc=Size(s): {0}",
            "SearchFiltering.SizeFilter.or=, "})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            for (FileSize size : fileSizes) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_SizeFilter_or();
                }
                desc += size.getSizeGroup();
            }
            desc = Bundle.SearchFiltering_SizeFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A utility class for the ParentFilter to store the search string and
     * whether it is a full path or a sub-string.
     */
    public static class ParentSearchTerm {

        private final String searchStr;
        private final boolean fullPath;
        private final boolean included;

        /**
         * Create the ParentSearchTerm object.
         *
         * @param searchStr  The string to search for in the file path.
         * @param isFullPath True if the path should exactly match the given
         *                   string, false to do a sub-string search.
         * @param isIncluded True if the results must include the path, false if
         *                   the path should be excluded from the results.
         */
        public ParentSearchTerm(String searchStr, boolean isFullPath, boolean isIncluded) {
            this.searchStr = searchStr;
            this.fullPath = isFullPath;
            this.included = isIncluded;
        }

        /**
         * Get the SQL term to search for.
         *
         * @return The SQL for a where clause to search for a matching path.
         */
        public String getSQLForTerm() {
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
            "SearchFiltering.ParentSearchTerm.fullString= (exact)",
            "SearchFiltering.ParentSearchTerm.subString= (substring)",
            "SearchFiltering.ParentSearchTerm.includeString= (include)",
            "SearchFiltering.ParentSearchTerm.excludeString= (exclude)",})
        @Override
        public String toString() {
            String returnString = getSearchStr();
            if (isFullPath()) {
                returnString += Bundle.SearchFiltering_ParentSearchTerm_fullString();
            } else {
                returnString += Bundle.SearchFiltering_ParentSearchTerm_subString();
            }
            if (isIncluded()) {
                returnString += Bundle.SearchFiltering_ParentSearchTerm_includeString();
            } else {
                returnString += Bundle.SearchFiltering_ParentSearchTerm_excludeString();
            }
            return returnString;
        }

        /**
         * Is the search string the full path of the of the parent or is it a
         * sub-string in the parent path?
         *
         * @return True if the search string is the full path of the parent,
         *         false if it is a sub-string.
         */
        public boolean isFullPath() {
            return fullPath;
        }

        /**
         * Should the search string be included in the path, or excluded from
         * the path?
         *
         * @return True if the search string should be included, false if it
         *         should be excluded.
         */
        public boolean isIncluded() {
            return included;
        }

        /**
         * Get the string being searched for by this filter.
         *
         * @return The string being searched for by this filter.
         */
        public String getSearchStr() {
            return searchStr;
        }
    }

    /**
     * A filter for specifying parent path (either full path or substring).
     */
    public static class ParentFilter extends AbstractFilter {

        private final List<ParentSearchTerm> parentSearchTerms;

        /**
         * Create the ParentFilter.
         *
         * @param parentSearchTerms Full paths or substrings to filter on.
         */
        public ParentFilter(List<ParentSearchTerm> parentSearchTerms) {
            this.parentSearchTerms = parentSearchTerms;
        }

        @Override
        public String getWhereClause() {
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
            "SearchFiltering.ParentFilter.desc=Paths matching: {0}",
            "SearchFiltering.ParentFilter.or=, ",
            "SearchFiltering.ParentFilter.exact=(exact match)",
            "SearchFiltering.ParentFilter.substring=(substring)",
            "SearchFiltering.ParentFilter.included=(included)",
            "SearchFiltering.ParentFilter.excluded=(excluded)"})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            for (ParentSearchTerm searchTerm : parentSearchTerms) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_ParentFilter_or();
                }
                if (searchTerm.isFullPath()) {
                    desc += searchTerm.getSearchStr() + Bundle.SearchFiltering_ParentFilter_exact();
                } else {
                    desc += searchTerm.getSearchStr() + Bundle.SearchFiltering_ParentFilter_substring();
                }
                if (searchTerm.isIncluded()) {
                    desc += Bundle.SearchFiltering_ParentFilter_included();
                } else {
                    desc += Bundle.SearchFiltering_ParentFilter_excluded();
                }
            }
            desc = Bundle.SearchFiltering_ParentFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying data sources.
     */
    public static class DataSourceFilter extends AbstractFilter {

        private final List<DataSource> dataSources;

        /**
         * Create the DataSourceFilter.
         *
         * @param dataSources The data sources to filter on.
         */
        public DataSourceFilter(List<DataSource> dataSources) {
            this.dataSources = dataSources;
        }

        @Override
        public String getWhereClause() {
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
            "SearchFiltering.DataSourceFilter.desc=Data source(s): {0}",
            "SearchFiltering.DataSourceFilter.or=, ",
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "SearchFiltering.DataSourceFilter.datasource={0}({1})",})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            for (DataSource ds : dataSources) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_DataSourceFilter_or();
                }
                desc += Bundle.SearchFiltering_DataSourceFilter_datasource(ds.getName(), ds.getId());
            }
            desc = Bundle.SearchFiltering_DataSourceFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying keyword list names. A file must contain a keyword
     * from one of the given lists to pass.
     */
    public static class KeywordListFilter extends AbstractFilter {

        private final List<String> listNames;

        /**
         * Create the KeywordListFilter.
         *
         * @param listNames The list of keywords for this filter.
         */
        public KeywordListFilter(List<String> listNames) {
            this.listNames = listNames;
        }

        @Override
        public String getWhereClause() {
            String keywordListPart = concatenateNamesForSQL(listNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = 9 AND attribute_type_ID = 37 "
                    + "AND (" + keywordListPart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "SearchFiltering.KeywordListFilter.desc=Keywords in list(s): {0}",})
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_KeywordListFilter_desc(concatenateSetNamesForDisplay(listNames));
        }
    }

    /**
     * A filter for specifying file types.
     */
    public static class FileTypeFilter extends AbstractFilter {

        private final List<Type> categories;

        /**
         * Create the FileTypeFilter.
         *
         * @param categories List of file types to filter on
         */
        public FileTypeFilter(List<Type> categories) {
            this.categories = categories;
        }

        /**
         * Create the FileTypeFilter.
         *
         * @param category The file type to filter on.
         */
        public FileTypeFilter(Type category) {
            this.categories = new ArrayList<>();
            this.categories.add(category);
        }

        @Override
        public String getWhereClause() {
            String queryStr = ""; // NON-NLS
            for (Type cat : categories) {
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
            "SearchFiltering.FileTypeFilter.desc=Type: {0}",
            "SearchFiltering.FileTypeFilter.or=, ",})
        @Override
        public String getDesc() {
            String desc = "";
            for (Type cat : categories) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_FileTypeFilter_or();
                }
                desc += cat.toString();
            }
            desc = Bundle.SearchFiltering_FileTypeFilter_desc(desc);
            return desc;
        }
    }

    /**
     * A filter for specifying frequency in the central repository.
     */
    public static class FrequencyFilter extends AbstractFilter {

        private final List<Frequency> frequencies;

        /**
         * Create the FrequencyFilter.
         *
         * @param frequencies List of frequencies that will pass the filter.
         */
        public FrequencyFilter(List<Frequency> frequencies) {
            this.frequencies = frequencies;
        }

        @Override
        public String getWhereClause() {
            // Since this relies on the central repository database, there is no
            // query on the case database.
            return ""; // NON-NLS
        }

        @Override
        public boolean useAlternateFilter() {
            return true;
        }

        @Override
        public List<Result> applyAlternateFilter(List<Result> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
            // Set the frequency for each file
            DiscoveryAttributes.FrequencyAttribute freqAttr = new DiscoveryAttributes.FrequencyAttribute();
            freqAttr.addAttributeToResults(currentResults, caseDb, centralRepoDb, context);

            // If the frequency matches the filter, add the file to the results
            List<Result> frequencyResults = new ArrayList<>();
            for (Result file : currentResults) {
                if (context.searchIsCancelled()) {
                    throw new SearchCancellationException("The search was cancelled while Frequency alternate filter was being applied.");
                }
                if (frequencies.contains(file.getFrequency())) {
                    frequencyResults.add(file);
                }
            }
            return frequencyResults;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "SearchFiltering.FrequencyFilter.desc=Past occurrences: {0}",
            "SearchFiltering.FrequencyFilter.or=, ",})
        @Override
        public String getDesc() {
            String desc = ""; // NON-NLS
            for (Frequency freq : frequencies) {
                if (!desc.isEmpty()) {
                    desc += Bundle.SearchFiltering_FrequencyFilter_or();
                }
                desc += freq.toString();
            }
            return Bundle.SearchFiltering_FrequencyFilter_desc(desc);
        }
    }

    /**
     * A filter for domains with known account types.
     */
    public static class KnownAccountTypeFilter extends AbstractFilter {

        @Override
        public String getWhereClause() {
            throw new UnsupportedOperationException("Not supported, this is an alternative filter.");
        }

        @Override
        public boolean useAlternateFilter() {
            return true;
        }

        @Override
        public List<Result> applyAlternateFilter(List<Result> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
            List<Result> filteredResults = new ArrayList<>();
            for (Result result : currentResults) {
                if (context.searchIsCancelled()) {
                    throw new SearchCancellationException("The search was cancelled while Known Account Type alternate filter was being applied.");
                }
                if (result instanceof ResultDomain) {
                    ResultDomain domain = (ResultDomain) result;
                    if (domain.hasKnownAccountType()) {
                        filteredResults.add(domain);
                    }
                } else {
                    filteredResults.add(result);
                }
            }
            return filteredResults;
        }

        @NbBundle.Messages({
            "SearchFiltering.KnownAccountTypeFilter.desc=Only domains with known account type"
        })
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_KnownAccountTypeFilter_desc();
        }

    }

    /**
     * A filter for previously notable content in the central repository.
     */
    public static class PreviouslyNotableFilter extends AbstractFilter {

        @Override
        public String getWhereClause() {
            throw new UnsupportedOperationException("Not supported, this is an alternative filter.");
        }

        @Override
        public boolean useAlternateFilter() {
            return true;
        }

        @Override
        public List<Result> applyAlternateFilter(List<Result> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
            DiscoveryAttributes.PreviouslyNotableAttribute previouslyNotableAttr = new DiscoveryAttributes.PreviouslyNotableAttribute();
            previouslyNotableAttr.addAttributeToResults(currentResults, caseDb, centralRepoDb, context);
            List<Result> filteredResults = new ArrayList<>();
            for (Result file : currentResults) {
                if (context.searchIsCancelled()) {
                    throw new SearchCancellationException("The search was cancelled while Previously Notable alternate filter was being applied.");
                }
                if (file.getPreviouslyNotableInCR() == SearchData.PreviouslyNotable.PREVIOUSLY_NOTABLE) {
                    filteredResults.add(file);
                }
            }
            return filteredResults;
        }

        @NbBundle.Messages({
            "SearchFiltering.PreviouslyNotableFilter.desc=Previously marked as notable in central repository"
        })
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_PreviouslyNotableFilter_desc();
        }

    }

    /**
     * A filter for specifying hash set names. A file must match one of the
     * given sets to pass.
     */
    public static class HashSetFilter extends AbstractFilter {

        private final List<String> setNames;

        /**
         * Create the HashSetFilter.
         *
         * @param setNames The hash set names for this filter.
         */
        public HashSetFilter(List<String> setNames) {
            this.setNames = setNames;
        }

        @Override
        public String getWhereClause() {
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
        public String getDesc() {
            return Bundle.FileSearchFiltering_HashSetFilter_desc(concatenateSetNamesForDisplay(setNames));
        }
    }

    /**
     * A filter for specifying interesting file set names. A file must match one
     * of the given sets to pass.
     */
    public static class InterestingFileSetFilter extends AbstractFilter {

        private final List<String> setNames;

        /**
         * Create the InterestingFileSetFilter.
         *
         * @param setNames The interesting file set names for this filter.
         */
        public InterestingFileSetFilter(List<String> setNames) {
            this.setNames = setNames;
        }

        /**
         * @SuppressWarnings("deprecation") - we need to support already
         * existing interesting file and artifact hits.
         */
        @SuppressWarnings("deprecation")
        @Override
        public String getWhereClause() {
            String intItemSetPart = concatenateNamesForSQL(setNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE (artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
                    + " OR artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID()
                    + ") AND attribute_type_ID = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " "
                    + "AND (" + intItemSetPart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "SearchFiltering.InterestingItemSetFilter.desc=Interesting item hits in set(s): {0}",})
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_InterestingItemSetFilter_desc(concatenateSetNamesForDisplay(setNames));
        }
    }

    /**
     * A filter for specifying object types detected. A file must match one of
     * the given types to pass.
     */
    public static class ObjectDetectionFilter extends AbstractFilter {

        private final List<String> typeNames;

        /**
         * Create the ObjectDetectionFilter.
         *
         * @param typeNames The type names for this filter.
         */
        public ObjectDetectionFilter(List<String> typeNames) {
            this.typeNames = typeNames;
        }

        @Override
        public String getWhereClause() {
            String objTypePart = concatenateNamesForSQL(typeNames);

            String queryStr = "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID()
                    + " AND attribute_type_ID = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID() + " "
                    + "AND (" + objTypePart + "))))";  // NON-NLS

            return queryStr;
        }

        @NbBundle.Messages({
            "# {0} - filters",
            "SearchFiltering.ObjectDetectionFilter.desc=Objects detected in set(s): {0}",})
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_ObjectDetectionFilter_desc(concatenateSetNamesForDisplay(typeNames));
        }
    }

    /**
     * A filter for specifying the score. A file must have one of the given
     * scores to pass.
     */
    public static class ScoreFilter extends AbstractFilter {

        private final List<Score> scores;

        /**
         * Create the ScoreFilter.
         *
         * @param scores The list of scores for this filter.
         */
        public ScoreFilter(List<Score> scores) {
            this.scores = scores;
        }

        /**
         * @SuppressWarnings("deprecation") - we need to support already
         * existing interesting file and artifact hits.
         */
        @SuppressWarnings("deprecation")
        @Override
        public String getWhereClause() {

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
                        + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID() + " OR artifact_type_id = "
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
            "SearchFiltering.ScoreFilter.desc=Score(s) of : {0}",})
        @Override
        public String getDesc() {
            return Bundle.SearchFiltering_ScoreFilter_desc(
                    concatenateSetNamesForDisplay(scores.stream().map(p -> p.toString()).collect(Collectors.toList())));
        }
    }

    /**
     * A filter for specifying tag names. A file must contain one of the given
     * tags to pass.
     */
    public static class TagsFilter extends AbstractFilter {

        private final List<TagName> tagNames;

        /**
         * Create the TagsFilter.
         *
         * @param tagNames The list of tag names for this filter.
         */
        public TagsFilter(List<TagName> tagNames) {
            this.tagNames = tagNames;
        }

        @Override
        public String getWhereClause() {
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
        public String getDesc() {
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
    public static class UserCreatedFilter extends AbstractFilter {

        @Override
        public String getWhereClause() {
            return "(obj_id IN (SELECT obj_id from blackboard_artifacts WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM blackboard_attributes WHERE artifact_type_id = "
                    + BlackboardArtifact.ARTIFACT_TYPE.TSK_USER_CONTENT_SUSPECTED.getTypeID() + ")))";
        }

        @NbBundle.Messages({
            "FileSearchFiltering.UserCreatedFilter.desc=that contain EXIF data",})
        @Override
        public String getDesc() {
            return Bundle.FileSearchFiltering_UserCreatedFilter_desc();
        }
    }

    /**
     * A filter for specifying that the file must have been marked as notable in
     * the CR.
     */
    public static class NotableFilter extends AbstractFilter {

        @Override
        public String getWhereClause() {
            // Since this relies on the central repository database, there is no
            // query on the case database.
            return ""; // NON-NLS
        }

        @Override
        public boolean useAlternateFilter() {
            return true;
        }

        @Override
        public List<Result> applyAlternateFilter(List<Result> currentResults, SleuthkitCase caseDb,
                CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {

            if (centralRepoDb == null) {
                throw new DiscoveryException("Can not run Previously Notable filter with null Central Repository DB"); // NON-NLS
            }

            // We have to have run some kind of SQL filter before getting to this point,
            // and should have checked afterward to see if the results were empty.
            if (currentResults.isEmpty()) {
                throw new DiscoveryException("Can not run on empty list"); // NON-NLS
            }

            // The matching files
            List<Result> notableResults = new ArrayList<>();

            try {
                CorrelationAttributeInstance.Type type = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(CorrelationAttributeInstance.FILES_TYPE_ID);

                for (Result result : currentResults) {
                    if (context.searchIsCancelled()) {
                        throw new SearchCancellationException("The search was cancelled while Notable alternate filter was being applied.");
                    }
                    ResultFile file = (ResultFile) result;
                    if (result.getType() == SearchData.Type.DOMAIN) {
                        break;
                    }
                    if (file.getFirstInstance().getMd5Hash() != null && !file.getFirstInstance().getMd5Hash().isEmpty()) {
                        // Check if this file hash is marked as notable in the CR
                        String value = file.getFirstInstance().getMd5Hash();
                        if (centralRepoDb.getCountArtifactInstancesKnownBad(type, value) > 0) {
                            notableResults.add(result);
                        }
                    }
                }
                return notableResults;
            } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                throw new DiscoveryException("Error querying central repository", ex); // NON-NLS
            }
        }

        @NbBundle.Messages({
            "FileSearchFiltering.PreviouslyNotableFilter.desc=that were previously marked as notable",})
        @Override
        public String getDesc() {
            return Bundle.FileSearchFiltering_PreviouslyNotableFilter_desc();
        }
    }

    /**
     * A filter for specifying if known files should be included.
     */
    public static class KnownFilter extends AbstractFilter {

        @Override
        public String getWhereClause() {
            return "known!=" + TskData.FileKnown.KNOWN.getFileKnownValue(); // NON-NLS
        }

        @NbBundle.Messages({
            "FileSearchFiltering.KnownFilter.desc=which are not known"})
        @Override
        public String getDesc() {
            return Bundle.FileSearchFiltering_KnownFilter_desc();
        }
    }

    /**
     * Concatenate the set names into a "," separated list.
     *
     * @param setNames The List of setNames to concatenate.
     *
     * @return The concatenated list for display.
     */
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
     * @param setNames The List of setNames to concatenate.
     *
     * @return The concatenated list to use in the SQL query.
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

    /**
     * Private constructor for SearchFiltering class.
     */
    private SearchFiltering() {
        // Class should not be instantiated
    }
}
