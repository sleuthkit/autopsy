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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_TYPE_ENUM;

/**
 * Utilities for getting information about a data source or all data sources
 * from the case database.
 */
final class DataSourceInfoUtilities {

    private static final Logger logger = Logger.getLogger(DataSourceInfoUtilities.class.getName());

    /**
     * Gets a count of tsk_files for a particular datasource where dir_type is
     * not a virtual directory and has a name.
     *
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     * @param onError           The message to log on error.
     *
     * @return The count of files or null on error.
     */
    static Long getCountOfTskFiles(DataSource currentDataSource, String additionalWhere, String onError) {
        if (currentDataSource != null) {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                return skCase.countFilesWhere(
                        "data_source_obj_id=" + currentDataSource.getId()
                        + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND name<>''"
                        + (StringUtils.isBlank(additionalWhere) ? "" : (" AND " + additionalWhere)));
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, onError, ex);
                //unable to get count of files for the specified types cell will be displayed as empty
            }
        }
        return null;
    }

    /**
     * Gets a count of regular files for a particular datasource where the
     * dir_type and type are not a virtual directory and has a name.
     *
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     * @param onError           The message to log on error.
     *
     * @return The count of files or null on error.
     */
    static Long getCountOfRegularFiles(DataSource currentDataSource, String additionalWhere, String onError) {
        String whereClause = "meta_type=" + TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType();

        if (StringUtils.isNotBlank(additionalWhere)) {
            whereClause += " AND " + additionalWhere;
        }

        return getCountOfTskFiles(currentDataSource, whereClause, onError);
    }

    /**
     * An interface for handling a result set and returning a value.
     */
    interface ResultSetHandler<T> {

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
    static <T> T getBaseQueryResult(String query, ResultSetHandler<T> processor, String errorMessage) {
        return getBaseQueryResult(SleuthkitCaseProvider.DEFAULT, query, processor, errorMessage);
    }

    /**
     * Retrieves a result based on the provided query.
     *
     * @param provider     The means of obtaining a SleuthkitCase.
     * @param query        The query.
     * @param processor    The result set handler.
     * @param errorMessage The error message to display if there is an error
     *                     retrieving the resultset.
     *
     * @return The ResultSetHandler value or null if no ResultSet could be
     *         obtained.
     */
    static <T> T getBaseQueryResult(SleuthkitCaseProvider provider, String query, ResultSetHandler<T> processor, String errorMessage) {
        try (SleuthkitCase.CaseDbQuery dbQuery = provider.get().executeQuery(query)) {
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
     * Creates sql where clause that does a bitwise check to see if flag is
     * present.
     *
     * @param flag The flag for which to check.
     *
     * @return The clause.
     */
    static String getMetaFlagsContainsStatement(TSK_FS_META_FLAG_ENUM flag) {
        return "meta_flags & " + flag.getValue() + " > 0";
    }

    /**
     * Empty private constructor
     */
    private DataSourceInfoUtilities() {
    }
}
