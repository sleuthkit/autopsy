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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities.ResultSetHandler;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides information for the DataSourceSummaryCountsPanel.
 */
public class DataSourceCountsSummary {

    private static final Logger logger = Logger.getLogger(DataSourceCountsSummary.class.getName());

    /**
     * Get count of regular files (not directories) in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    public static Long getCountOfFiles(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource, null,
                "Unable to get count of files, providing empty results");
    }

    /**
     * Get count of allocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    public static Long getCountOfAllocatedFiles(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.ALLOC),
                "Unable to get counts of unallocated files for datasource, providing empty results");
    }

    /**
     * Get count of unallocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    public static Long getCountOfUnallocatedFiles(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType(),
                "Unable to get counts of unallocated files for datasource, providing empty results");
    }

    /**
     * Get count of directories in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    public static Long getCountOfDirectories(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfTskFiles(currentDataSource,
                "meta_type=" + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType(),
                "Unable to get count of directories for datasource, providing empty results");
    }

    /**
     * Get count of slack files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     */
    public static Long getCountOfSlackFiles(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)
                + " AND type=" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType(),
                "Unable to get count of slack files for datasources, providing empty results");
    }

    /**
     * Retrieves counts for each artifact type in a data source.
     *
     * @param selectedDataSource The data source.
     *
     * @return A mapping of artifact type name to the counts or null if there
     *         was an error executing the query.
     */
    public static Map<String, Long> getCountsOfArtifactsByType(DataSource selectedDataSource) {
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
        return DataSourceInfoUtilities.getBaseQueryResult(query,
                getStringLongResultSetHandler(nameParam, valueParam),
                errorMessage);
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

    private DataSourceCountsSummary() {
    }
}
