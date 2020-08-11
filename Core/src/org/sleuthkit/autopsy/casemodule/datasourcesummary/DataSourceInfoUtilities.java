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

import java.io.File;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
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
        return (resultSet) -> {
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

    /**
     * Describes a result of a program run on a datasource.
     */
    static class TopProgramsResult {

        private final String programName;
        private final String programPath;
        private final Long runTimes;
        private final Date lastRun;

        /**
         * Main constructor.
         *
         * @param programName The name of the program.
         * @param programPath The path of the program.
         * @param runTimes    The number of runs.
         */
        TopProgramsResult(String programName, String programPath, Long runTimes, Date lastRun) {
            this.programName = programName;
            this.programPath = programPath;
            this.runTimes = runTimes;
            this.lastRun = lastRun;
        }

        /**
         * @return The name of the program
         */
        String getProgramName() {
            return programName;
        }

        /**
         * @return The path of the program.
         */
        String getProgramPath() {
            return programPath;
        }

        /**
         * @return The number of run times or null if not present.
         */
        Long getRunTimes() {
            return runTimes;
        }

        /**
         * @return The last time the program was run or null if not present.
         */
        public Date getLastRun() {
            return lastRun;
        }
    }

    /**
     * A SQL join type.
     */
    private enum JoinType {
        LEFT,
        RIGHT,
        INNER,
        OUTER
    }

    /**
     * A blackboard attribute value column.
     */
    private enum AttributeColumn {
        value_text,
        value_int32,
        value_int64
    }

    /**
     * The suffix joined to a key name for use as an identifier of a query.
     */
    private static final String QUERY_SUFFIX = "_query";

    /**
     * Creates a sql statement querying the blackboard attributes table for a
     * particular attribute type and returning a specified value. That query
     * also joins with the blackboard artifact table.
     *
     * @param joinType        The type of join statement to create.
     * @param attributeColumn The blackboard attribute column that should be
     *                        returned.
     * @param attrType        The attribute type to query for.
     * @param keyName         The aliased name of the attribute to return. This
     *                        is also used to calculate the alias of the query
     *                        same as getFullKey.
     * @param bbaName         The blackboard artifact table alias.
     *
     * @return The generated sql statement.
     */
    private static String getAttributeJoin(JoinType joinType, AttributeColumn attributeColumn, ATTRIBUTE_TYPE attrType, String keyName, String bbaName) {
        String queryName = keyName + QUERY_SUFFIX;
        String innerQueryName = "inner_attribute_" + queryName;

        return "\n" + joinType + " JOIN (\n"
                + "    SELECT \n"
                + "        " + innerQueryName + ".artifact_id,\n"
                + "        " + innerQueryName + "." + attributeColumn + " AS " + keyName + "\n"
                + "    FROM blackboard_attributes " + innerQueryName + "\n"
                + "    WHERE " + innerQueryName + ".attribute_type_id = " + attrType.getTypeID() + " -- " + attrType.name() + "\n"
                + ") " + queryName + " ON " + queryName + ".artifact_id = " + bbaName + ".artifact_id\n";
    }

    /**
     * Given a column key, creates the full name for the column key.
     *
     * @param key The column key.
     *
     * @return The full identifier for the column key.
     */
    private static String getFullKey(String key) {
        return key + QUERY_SUFFIX + "." + key;
    }

    /**
     * Constructs a SQL 'where' statement from a list of clauses and puts
     * parenthesis around each clause.
     *
     * @param clauses The clauses
     *
     * @return The generated 'where' statement.
     */
    private static String getWhereString(List<String> clauses) {
        if (clauses.isEmpty()) {
            return "";
        }

        List<String> parenthesized = clauses.stream()
                .map(c -> "(" + c + ")")
                .collect(Collectors.toList());

        return "\nWHERE " + String.join("\n    AND ", parenthesized) + "\n";
    }

    /**
     * Generates a [column] LIKE sql clause.
     *
     * @param column     The column identifier.
     * @param likeString The string that will be used as column comparison.
     * @param isLike     if false, the statement becomes NOT LIKE.
     *
     * @return The generated statement.
     */
    private static String getLikeClause(String column, String likeString, boolean isLike) {
        return column + (isLike ? "" : " NOT") + " LIKE '" + likeString + "'";
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

        // ntosboot should be ignored
        final String ntosBootIdentifier = "NTOSBOOT";
        // programs in windows directory to be ignored
        final String windowsDir = "/WINDOWS%";

        final String nameParam = "name";
        final String pathParam = "path";
        final String runCountParam = "run_count";
        final String lastRunParam = "last_run";

        String bbaQuery = "bba";

        final String query = "SELECT\n"
                + "    " + getFullKey(nameParam) + " AS " + nameParam + ",\n"
                + "    " + getFullKey(pathParam) + " AS " + pathParam + ",\n"
                + "    MAX(" + getFullKey(runCountParam) + ") AS " + runCountParam + ",\n"
                + "    MAX(" + getFullKey(lastRunParam) + ") AS " + lastRunParam + "\n"
                + "FROM blackboard_artifacts " + bbaQuery + "\n"
                + getAttributeJoin(JoinType.INNER, AttributeColumn.value_text, ATTRIBUTE_TYPE.TSK_PROG_NAME, nameParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_text, ATTRIBUTE_TYPE.TSK_PATH, pathParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_int32, ATTRIBUTE_TYPE.TSK_COUNT, runCountParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_int64, ATTRIBUTE_TYPE.TSK_DATETIME, lastRunParam, bbaQuery)
                + getWhereString(Arrays.asList(
                        bbaQuery + ".artifact_type_id = " + ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID(),
                        bbaQuery + ".data_source_obj_id = " + dataSource.getId(),
                        // exclude ntosBootIdentifier from results
                        getLikeClause(getFullKey(nameParam), ntosBootIdentifier, false),
                        // exclude windows directory items from results
                        getFullKey(pathParam) + " IS NULL OR " + getLikeClause(getFullKey(pathParam), windowsDir, false)
                ))
                + "GROUP BY " + getFullKey(nameParam) + ", " + getFullKey(pathParam) + "\n"
                + "ORDER BY \n"
                + "    MAX(" + getFullKey(runCountParam) + ") DESC,\n"
                + "    MAX(" + getFullKey(lastRunParam) + ") DESC,\n"
                + "    " + getFullKey(nameParam) + " ASC";

        final String errorMessage = "Unable to get top program results; returning null.";

        ResultSetHandler<List<TopProgramsResult>> handler = (resultSet) -> {
            List<TopProgramsResult> progResults = new ArrayList<>();

            boolean quitAtCount = false;

            while (resultSet.next() && (!quitAtCount || progResults.size() < count)) {
                try {
                    long lastRunEpoch = resultSet.getLong(lastRunParam);
                    Date lastRun = (resultSet.wasNull()) ? null : new Date(lastRunEpoch * 1000);

                    Long runCount = resultSet.getLong(runCountParam);
                    if (resultSet.wasNull()) {
                        runCount = null;
                    }

                    if (lastRun != null || runCount != null) {
                        quitAtCount = true;
                    }

                    progResults.add(new TopProgramsResult(
                            resultSet.getString(nameParam),
                            resultSet.getString(pathParam),
                            runCount,
                            lastRun));

                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to get a top program result from the result set.", ex);
                }
            }

            return progResults;
        };

        return getBaseQueryResult(query, handler, errorMessage);
    }

    /**
     * Functions that determine the folder name of a list of path elements. If
     * not matched, function returns null.
     */
    private static final List<Function<List<String>, String>> SHORT_FOLDER_MATCHERS = Arrays.asList(
            // handle Program Files and Program Files (x86) - if true, return the next folder
            (pathList) -> {
                if (pathList.size() < 2) {
                    return null;
                }

                String rootParent = pathList.get(0).toUpperCase();
                if ("PROGRAM FILES".equals(rootParent) || "PROGRAM FILES (X86)".equals(rootParent)) {
                    return pathList.get(1);
                } else {
                    return null;
                }
            },
            // if there is a folder named "APPLICATION DATA" or "APPDATA"
            (pathList) -> {
                for (String pathEl : pathList) {
                    String uppered = pathEl.toUpperCase();
                    if ("APPLICATION DATA".equals(uppered) || "APPDATA".equals(uppered)) {
                        return "AppData";
                    }
                }
                return null;
            }
    );

    /**
     * Determines a short folder name if any. Otherwise, returns empty string.
     *
     * @param strPath The string path.
     *
     * @return The short folder name or empty string if not found.
     */
    static String getShortFolderName(String strPath, String applicationName) {
        if (strPath == null) {
            return "";
        }

        List<String> pathEls = new ArrayList<>(Arrays.asList(applicationName));

        File file = new File(strPath);
        while (file != null && StringUtils.isNotBlank(file.getName())) {
            pathEls.add(file.getName());
            file = file.getParentFile();
        }

        Collections.reverse(pathEls);

        for (Function<List<String>, String> matchEntry : SHORT_FOLDER_MATCHERS) {
            String result = matchEntry.apply(pathEls);
            if (StringUtils.isNotBlank(result)) {
                return result;
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
