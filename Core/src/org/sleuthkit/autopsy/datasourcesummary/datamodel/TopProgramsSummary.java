/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate Top Programs Summary queries.
 */
public class TopProgramsSummary implements DefaultArtifactUpdateGovernor {

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID()
    ));

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
    private static String getAttributeJoin(JoinType joinType, AttributeColumn attributeColumn, BlackboardAttribute.ATTRIBUTE_TYPE attrType, String keyName, String bbaName) {
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

    private final SleuthkitCaseProvider provider;

    public TopProgramsSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    public TopProgramsSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Retrieves a list of the top programs used on the data source. Currently
     * determines this based off of which prefetch results return the highest
     * count.
     *
     * @param dataSource The data source.
     * @param count      The number of programs to return.
     *
     * @return The top results objects found.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public List<TopProgramsResult> getTopPrograms(DataSource dataSource, int count)
            throws SleuthkitCaseProviderException, TskCoreException, SQLException {
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
                + getAttributeJoin(JoinType.INNER, AttributeColumn.value_text, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, nameParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_text, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, pathParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_int32, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNT, runCountParam, bbaQuery)
                + getAttributeJoin(JoinType.LEFT, AttributeColumn.value_int64, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, lastRunParam, bbaQuery)
                + getWhereString(Arrays.asList(
                        bbaQuery + ".artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID(),
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

        DataSourceInfoUtilities.ResultSetHandler<List<TopProgramsResult>> handler = (resultSet) -> {
            List<TopProgramsResult> progResults = new ArrayList<>();

            boolean quitAtCount = false;

            while (resultSet.next() && (!quitAtCount || progResults.size() < count)) {
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
            }

            return progResults;
        };

        try (SleuthkitCase.CaseDbQuery dbQuery = provider.get().executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            return handler.process(resultSet);
        }
    }

    /**
     * Determines a short folder name if any. Otherwise, returns empty string.
     *
     * @param strPath         The string path.
     * @param applicationName The application name.
     *
     * @return The short folder name or empty string if not found.
     */
    public String getShortFolderName(String strPath, String applicationName) {
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
     * Describes a result of a program run on a datasource.
     */
    public static class TopProgramsResult {

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
        public String getProgramName() {
            return programName;
        }

        /**
         * @return The path of the program.
         */
        public String getProgramPath() {
            return programPath;
        }

        /**
         * @return The number of run times or null if not present.
         */
        public Long getRunTimes() {
            return runTimes;
        }

        /**
         * @return The last time the program was run or null if not present.
         */
        public Date getLastRun() {
            return lastRun;
        }
    }
}
