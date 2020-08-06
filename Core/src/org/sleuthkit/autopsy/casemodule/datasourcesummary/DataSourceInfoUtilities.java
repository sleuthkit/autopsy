/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 - 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.commons.lang3.tuple.Pair;
import java.util.function.Function;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;

/**
 * Utilities for getting information about a data source or all data sources
 * from the case database.
 */
final class DataSourceInfoUtilities {

    private static final Logger logger = Logger.getLogger(DataSourceInfoUtilities.class.getName());

    /**
     * Gets a count of files for a particular datasource where it is not a
     * virtual directory and has a name.
     *
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     * @param onError           The message to log on error.
     *
     * @return The count of files or null on error.
     */
    private static Long getCountOfFiles(DataSource currentDataSource, String additionalWhere, String onError) {
        if (currentDataSource != null) {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                return skCase.countFilesWhere(
                        "dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND name<>''"
                        + " AND data_source_obj_id=" + currentDataSource.getId()
                        + " AND " + additionalWhere);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, onError, ex);
                //unable to get count of files for the specified types cell will be displayed as empty
            }
        }
        return null;
    }

    /**
     * Get count of files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    static Long getCountOfFiles(DataSource currentDataSource) {
        return getCountOfFiles(currentDataSource,
                "type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType(),
                "Unable to get count of files, providing empty results");
    }

    /**
     * Get count of unallocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    static Long getCountOfUnallocatedFiles(DataSource currentDataSource) {
        return getCountOfFiles(currentDataSource,
                "type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                + " AND dir_flags=" + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue(),
                "Unable to get counts of unallocated files for datasource, providing empty results");
    }

    /**
     * Get count of directories in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    static Long getCountOfDirectories(DataSource currentDataSource) {
        return getCountOfFiles(currentDataSource,
                "type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                + " AND meta_type=" + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue(),
                "Unable to get count of directories for datasource, providing empty results");
    }

    /**
     * Get count of slack files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    static Long getCountOfSlackFiles(DataSource currentDataSource) {
        return getCountOfFiles(currentDataSource,
                "type=" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType(),
                "Unable to get count of slack files for datasources, providing empty results");
    }

    /**
     * An interface for handling a result set and returning a value.
     */
    private interface ResultSetHandler<T> {

        T process(ResultSet resultset) throws SQLException;
    }

    /**
     * Retrieves a result based on the provided query.
     *
     * @param query        The query.
     * @param processor    The result set handler.
     * @param errorMessage The error message to display if there is an error
     *                     retrieving the resultset.
     *
     * @return The ResultSetHandler value or null if no ResultSet could be
     *         obtained.
     */
    private static <T> T getBaseQueryResult(String query, ResultSetHandler<T> processor, String errorMessage) {
        try (SleuthkitCase.CaseDbQuery dbQuery = Case.getCurrentCaseThrows().getSleuthkitCase().executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            try {
                return processor.process(resultSet);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, errorMessage, ex);
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, errorMessage, ex);
        }
        return null;
    }

    /**
     * Gets the size of unallocated files in a particular datasource.
     *
     * @param currentDataSource The data source.
     *
     * @return The size or null if the query could not be executed.
     */
    static Long getSizeOfUnallocatedFiles(DataSource currentDataSource) {
        if (currentDataSource == null) {
            return null;
        }

        final String valueParam = "value";
        final String countParam = "count";
        String query = "SELECT SUM(size) AS " + valueParam + ", COUNT(*) AS " + countParam
                + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                + " AND dir_flags=" + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                + " AND name<>''"
                + " AND data_source_obj_id=" + currentDataSource.getId();

        ResultSetHandler<Long> handler = (resultSet) -> {
            if (resultSet.next()) {
                // ensure that there is an unallocated count result that is attached to this data source
                long resultCount = resultSet.getLong(valueParam);
                return (resultCount > 0) ? resultSet.getLong(valueParam) : null;
            } else {
                return null;
            }
        };
        String errorMessage = "Unable to get size of unallocated files; returning null.";

        return getBaseQueryResult(query, handler, errorMessage);
    }

    /**
     * Generates a result set handler that will return a map of string to long.
     *
     * @param keyParam   The named parameter in the result set representing the
     *                   key.
     * @param valueParam The named parameter in the result set representing the
     *                   value.
     *
     * @return The result set handler to generate the map of string to long.
     */
    private static ResultSetHandler<LinkedHashMap<String, Long>> getStringLongResultSetHandler(String keyParam, String valueParam) {
        ResultSetHandler<LinkedHashMap<String, Long>> handler = (resultSet) -> {
            LinkedHashMap<String, Long> toRet = new LinkedHashMap<>();
            while (resultSet.next()) {
                try {
                    toRet.put(resultSet.getString(keyParam), resultSet.getLong(valueParam));
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to get a result pair from the result set.", ex);
                }
            }

            return toRet;
        };

        return handler;
    }

    /**
     * Retrieves counts for each artifact type in a data source.
     *
     * @param selectedDataSource The data source.
     *
     * @return A mapping of artifact type name to the counts or null if there
     *         was an error executing the query.
     */
    static Map<String, Long> getCountsOfArtifactsByType(DataSource selectedDataSource) {
        if (selectedDataSource == null) {
            return Collections.emptyMap();
        }

        final String nameParam = "name";
        final String valueParam = "value";
        String query
                = "SELECT bbt.display_name AS " + nameParam + ", COUNT(*) AS " + valueParam
                + " FROM blackboard_artifacts bba "
                + " INNER JOIN blackboard_artifact_types bbt ON bba.artifact_type_id = bbt.artifact_type_id"
                + " WHERE bba.data_source_obj_id =" + selectedDataSource.getId()
                + " GROUP BY bbt.display_name";

        String errorMessage = "Unable to get artifact type counts; returning null.";
        return getBaseQueryResult(query, getStringLongResultSetHandler(nameParam, valueParam), errorMessage);
    }

    static class TopProgramsResult {

        private final String programName;
        private final String programPath;
        private final long runTimes;

        public TopProgramsResult(String programName, String programPath, long runTimes) {
            this.programName = programName;
            this.programPath = programPath;
            this.runTimes = runTimes;
        }

        public String getProgramName() {
            return programName;
        }

        public String getProgramPath() {
            return programPath;
        }

        public long getRunTimes() {
            return runTimes;
        }
    }

    /**
     * Retrieves a list of the top programs used on the data source. Currently
     * determines this based off of which prefetch results return the highest
     * count.
     *
     * @param dataSource The data source.
     * @param count      The number of programs to return.
     *
     * @return
     */
    static List<TopProgramsResult> getTopPrograms(DataSource dataSource, int count) {
        if (dataSource == null || count <= 0) {
            return Collections.emptyList();
        }

        final String progNameParam = "prog_name";
        final String progPathParam = "prog_path";
        final String runTimesParam = "run_times";
        final String prefetchIdentifier = "Windows Prefetch Extractor";

        final String query = "SELECT\n"
                + "    prog_name_query." + progNameParam + ",\n"
                + "    prog_path_query." + progPathParam + ",\n"
                + "    MAX(attr.value_int32) AS " + runTimesParam + "\n"
                + "FROM blackboard_artifacts bba\n"
                + "INNER JOIN blackboard_attributes attr ON bba.artifact_id = attr.artifact_id\n"
                + "INNER JOIN (\n"
                + "    SELECT \n"
                + "        bba1.artifact_id AS artifact_id,\n"
                + "        attr1.value_text AS " + progNameParam + "\n"
                + "    FROM blackboard_artifacts bba1\n"
                + "    INNER JOIN blackboard_attributes attr1 ON bba1.artifact_id = attr1.artifact_id\n"
                + "    WHERE bba1.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID() + "\n"
                + "    AND attr1.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID() + "\n"
                + ") prog_name_query ON bba.artifact_id = prog_name_query.artifact_id\n"
                + "LEFT JOIN (\n"
                + "    SELECT \n"
                + "        bba2.artifact_id AS artifact_id,\n"
                + "        attr2.value_text AS " + progPathParam + "\n"
                + "    FROM blackboard_artifacts bba2\n"
                + "    INNER JOIN blackboard_attributes attr2 ON bba2.artifact_id = attr2.artifact_id\n"
                + "    WHERE bba2.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID() + "\n"
                + "    AND attr2.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID() + "\n"
                + ") prog_path_query ON bba.artifact_id = prog_path_query.artifact_id\n"
                + "WHERE attr.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNT.getTypeID() + "\n"
                + "AND bba.data_source_obj_id = " + dataSource.getId() + "\n"
                + "AND attr.source = '" + prefetchIdentifier + "'\n"
                + "GROUP BY prog_name_query." + progNameParam + ", prog_path_query." + progPathParam + "\n"
                + "ORDER BY MAX(attr.value_int32) DESC\n"
                + "LIMIT " + count + " OFFSET 0";

        final String errorMessage = "Unable to get top program results; returning null.";

        ResultSetHandler<List<TopProgramsResult>> handler = (resultSet) -> {
            List<TopProgramsResult> progResults = new ArrayList<>();

            while (resultSet.next()) {
                try {
                    progResults.add(new TopProgramsResult(
                            resultSet.getString(progNameParam),
                            resultSet.getString(progPathParam),
                            resultSet.getLong(runTimesParam)));
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to get a top program result from the result set.", ex);
                }
            }

            return progResults;
        };

        return getBaseQueryResult(query, handler, errorMessage);
    }

    private static List<Pair<Pattern, Function<Matcher, String>>> SHORT_FOLDER_MATCHERS = Arrays.asList(
            // Windows if in /Windows
            Pair.of(Pattern.compile("^([A-Z]:)?[\\\\\\/]?windows", Pattern.CASE_INSENSITIVE), 
                    (match) -> "Windows"),
            
            // program name if /Program Files/program or /Program Files (x86)/program
            Pair.of(Pattern.compile("^([A-Z]:)?[\\\\\\/]?Program Files( \\(x86\\))?[\\\\\\/](?<programName>.+?)([\\\\\\/]|$)", 
                    Pattern.CASE_INSENSITIVE), (match) -> match.group("programName")),
            
            // match for an AppData folder
            Pair.of(Pattern.compile("(^|[\\\\\\/])AppData([\\\\\\/]|$)", Pattern.CASE_INSENSITIVE), 
                    (match) -> "AppData")
    );

    static String getShortFolderName(String path) {
        if (path == null) {
            return "";
        }
        
        for (Pair<Pattern, Function<Matcher, String>> matchEntry : SHORT_FOLDER_MATCHERS) {
            Pattern p = matchEntry.getLeft();
            Function<Matcher, String> resultsProvider = matchEntry.getRight();
            Matcher match = p.matcher(path);
            if (match.find()) {
                return resultsProvider.apply(match);
            }
        }
        
        return "";
    }

    /**
     * Generates a string which is a concatenation of the value received from
     * the result set.
     *
     * @param query              The query.
     * @param valueParam         The parameter for the value in the result set.
     * @param separator          The string separator used in concatenation.
     * @param errorMessage       The error message if the result set could not
     *                           be received.
     * @param singleErrorMessage The error message if a single result could not
     *                           be obtained.
     *
     * @return The concatenated string or null if the query could not be
     *         executed.
     */
    private static String getConcattedStringsResult(String query, String valueParam, String separator, String errorMessage, String singleErrorMessage) {
        ResultSetHandler<String> handler = (resultSet) -> {
            String toRet = "";
            boolean first = true;
            while (resultSet.next()) {
                try {
                    if (first) {
                        first = false;
                    } else {
                        toRet += separator;
                    }
                    toRet += resultSet.getString(valueParam);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, singleErrorMessage, ex);
                }
            }

            return toRet;
        };

        return getBaseQueryResult(query, handler, errorMessage);
    }

    /**
     * Generates a concatenated string value based on the text value of a
     * particular attribute in a particular artifact for a specific data source.
     *
     * @param dataSourceId    The data source id.
     * @param artifactTypeId  The artifact type id.
     * @param attributeTypeId The attribute type id.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     */
    private static String getConcattedAttrValue(long dataSourceId, int artifactTypeId, int attributeTypeId) {
        final String valueParam = "concatted_attribute_value";
        String query = "SELECT attr.value_text AS " + valueParam
                + " FROM blackboard_artifacts bba "
                + " INNER JOIN blackboard_attributes attr ON bba.artifact_id = attr.artifact_id "
                + " WHERE bba.data_source_obj_id = " + dataSourceId
                + " AND bba.artifact_type_id = " + artifactTypeId
                + " AND attr.attribute_type_id = " + attributeTypeId;

        String errorMessage = "Unable to execute query to retrieve concatted attribute values.";
        String singleErrorMessage = "There was an error retrieving one of the results.  That result will be omitted from concatted value.";
        String separator = ", ";
        return getConcattedStringsResult(query, valueParam, separator, errorMessage, singleErrorMessage);
    }

    /**
     * Retrieves the concatenation of operating system attributes for a
     * particular data source.
     *
     * @param dataSource The data source.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     */
    static String getOperatingSystems(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        return getConcattedAttrValue(dataSource.getId(),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID());
    }

    /**
     * Retrieves the concatenation of data source usage for a particular data
     * source.
     *
     * @param dataSource The data source.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     */
    static String getDataSourceType(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        return getConcattedAttrValue(dataSource.getId(),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());
    }

    /**
     * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
     * associated with each data source in the current case.
     *
     * @return Collection which maps datasource id to a String which displays a
     *         comma seperated list of values of data source usage types
     *         expected to be in the datasource
     */
    static Map<Long, String> getDataSourceTypes() {
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<BlackboardArtifact> listOfArtifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE);
            Map<Long, String> typeMap = new HashMap<>();
            for (BlackboardArtifact typeArtifact : listOfArtifacts) {
                BlackboardAttribute descriptionAttr = typeArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION));
                if (typeArtifact.getDataSource() != null && descriptionAttr != null) {
                    long dsId = typeArtifact.getDataSource().getId();
                    String type = typeMap.get(typeArtifact.getDataSource().getId());
                    if (type == null) {
                        type = descriptionAttr.getValueString();
                    } else {
                        type = type + ", " + descriptionAttr.getValueString();
                    }
                    typeMap.put(dsId, type);
                }
            }
            return typeMap;
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get types of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of files in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         files in the datasource, will only contain entries for
     *         datasources which have at least 1 file
     */
    static Map<Long, Long> getCountsOfFiles() {
        try {
            final String countFilesQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countFilesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of artifacts in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         artifacts in the datasource, will only contain entries for
     *         datasources which have at least 1 artifact
     */
    static Map<Long, Long> getCountsOfArtifacts() {
        try {
            final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countArtifactsQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of artifacts for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of tags which have been applied in each
     * data source in the current case. Not necessarily the same as the number
     * of items tagged, as an item can have any number of tags.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         tags which have been applied in the datasource, will only contain
     *         entries for datasources which have at least 1 item tagged.
     */
    static Map<Long, Long> getCountsOfTags() {
        try {
            final String countFileTagsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM content_tags as content_tags, tsk_files as tsk_files"
                    + " WHERE content_tags.obj_id = tsk_files.obj_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            //new hashmap so it can be modifiable
            Map<Long, Long> tagCountMap = new HashMap<>(getValuesMap(countFileTagsQuery));
            final String countArtifactTagsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM blackboard_artifact_tags as artifact_tags,  blackboard_artifacts AS arts"
                    + " WHERE artifact_tags.artifact_id = arts.artifact_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            //combine the results from the count artifact tags query into the copy of the mapped results from the count file tags query
            getValuesMap(countArtifactTagsQuery).forEach((key, value) -> tagCountMap.merge(key, value, (value1, value2) -> value1 + value2));
            return tagCountMap;
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of tags for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get the number of files in the case database for the current data source
     * which have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types which we are finding the
     *                          number of occurences of
     *
     * @return a Long value which represents the number of occurrences of the
     *         specified mime types in the current case for the specified data
     *         source, null if no count was retrieved
     */
    static Long getCountOfFilesForMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes) {
        if (currentDataSource != null) {
            try {
                String inClause = String.join("', '", setOfMimeTypes);
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                return skCase.countFilesWhere("data_source_obj_id=" + currentDataSource.getId()
                        + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                        + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND mime_type IN ('" + inClause + "')"
                        + " AND name<>''");
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get count of files for specified mime types", ex);
                //unable to get count of files for the specified mimetypes cell will be displayed as empty
            }
        }
        return null;
    }

    /**
     * Helper method to execute a select query with a
     * DataSourceSingleValueCallback.
     *
     * @param query the portion of the query which should follow the SELECT
     *
     * @return a map of datasource object ID to a value of type Long
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     */
    private static Map<Long, Long> getValuesMap(String query) throws TskCoreException, NoCurrentCaseException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        DataSourceSingleValueCallback callback = new DataSourceSingleValueCallback();
        skCase.getCaseDbAccessManager().select(query, callback);
        return callback.getMapOfValues();
    }

    /**
     * Empty private constructor
     */
    private DataSourceInfoUtilities() {
    }
}
