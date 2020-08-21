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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.util.Arrays;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
    
    enum SortOrder {
        DECENDING,
        ASCENDING
    }
    
    /**
     * Return a list of artifacts that have been sorted by their attribute
     * of attributeType.  
     * 
     * Sorting on attributes of type byte[] and JSON is not currently
     * supported.
     *
     * @param skCase        SleuthkitCase instance.
     * @param artifactType  Type of artifacts to sort.
     * @param dataSource    Data Source that the artifact belongs to.
     * @param attributeType Attribute type to sort by.
     * @param sortOrder     Sort order of the attributes, either ascending or
     *                      descending.
     * @param maxCount      Maximum number of results to return. To return all
     *                      values maxCount should be -1.
     *
     * @return A list of artifacts of type artifactType sorted by the attribute
     *         of attributeType in the given sortOrder. If no artifacts are
     *         found an empty list will be returned.
     *
     * @throws TskCoreException
     */
    static List<BlackboardArtifact> getArtifacts(SleuthkitCase skCase, BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType, SortOrder sortOrder, int maxCount) throws TskCoreException {
        if (maxCount < 1 && maxCount != -1) {
            throw new IllegalArgumentException("Invalid maxCount passed to getArtifacts, value must be at greater 0");
        }

        TreeMap<BlackboardAttribute, BlackboardArtifact> sortedMap = new TreeMap<>(new AttributeComparator(sortOrder));
        List<BlackboardArtifact> artifactList = skCase.getBlackboard().getArtifacts(artifactType.getTypeID(), dataSource.getId());

        for (BlackboardArtifact artifact : artifactList) {
            BlackboardAttribute attribute = artifact.getAttribute(attributeType);
            if (attribute != null) {
                sortedMap.put(attribute, artifact);
            }
        }

        artifactList = new ArrayList<>();

        for (BlackboardArtifact artifact : sortedMap.values()) {
            artifactList.add(artifact);
            if (maxCount != -1 && artifactList.size() == maxCount - 1) {
                break;
            }
        } 
        return artifactList;
    }

    /**
     * Empty private constructor
     */
    private DataSourceInfoUtilities() {
    }
    
    /**
     * Compares the value of two BlackboardAttributes that are of the same type.
     * This comparator is specialized for data source summary and only supports
     * the basic attribute types of string, integer, long, datetime (long), and
     * double.
     */
    private static class AttributeComparator implements Comparator<BlackboardAttribute> {

        private final SortOrder direction;

        AttributeComparator(SortOrder direction) {
            this.direction = direction;
        }

        @Override
        public int compare(BlackboardAttribute attribute1, BlackboardAttribute attribute2) {
            if (attribute1.getAttributeType() != attribute2.getAttributeType()) {
                throw new IllegalArgumentException("Unable to compare attributes of different types");
            }

            int result;
            switch (attribute1.getValueType()) {
                case STRING:
                    result = attribute1.getValueString().compareTo(attribute2.getValueString());
                    break;
                case INTEGER:
                    result = Integer.compare(attribute1.getValueInt(), attribute2.getValueInt());
                    break;
                case LONG:
                case DATETIME:
                    result = Long.compare(attribute1.getValueLong(), attribute2.getValueLong());
                    break;
                case DOUBLE:
                    result = Double.compare(attribute1.getValueDouble(), attribute2.getValueDouble());
                    break;
                case BYTE:
                case JSON:
                default:
                    throw new IllegalArgumentException("Unable to compare attributes of type " + attribute1.getAttributeType().getTypeName());
            }

            if (direction == SortOrder.DECENDING) {
                result *= -1;
            }

            return result;
        }
    }
}
