/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 - 2021 Basis Technology Corp.
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_TYPE_ENUM;

/**
 * Utilities for getting information about a data source or all data sources
 * from the case database.
 */
public final class DataSourceInfoUtilities {

    public static final String COMMA_FORMAT_STR = "#,###";
    public static final DecimalFormat COMMA_FORMATTER = new DecimalFormat(COMMA_FORMAT_STR);

    /**
     * Gets a count of tsk_files for a particular datasource.
     *
     * @param skCase            The current SleuthkitCase.
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     *
     * @return The count of files or null on error.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    static Long getCountOfTskFiles(SleuthkitCase skCase, DataSource currentDataSource, String additionalWhere)
            throws TskCoreException, SQLException {
        if (currentDataSource != null) {
            return skCase.countFilesWhere(
                    "data_source_obj_id=" + currentDataSource.getId()
                    + (StringUtils.isBlank(additionalWhere) ? "" : (" AND " + additionalWhere)));
        }
        return null;
    }

    /**
     * Gets a count of regular files for a particular datasource.
     *
     * @param skCase            The current SleuthkitCase.
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     *
     * @return The count of files or null on error.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    static Long getCountOfRegularFiles(SleuthkitCase skCase, DataSource currentDataSource, String additionalWhere)
            throws TskCoreException, SQLException {
        String whereClause = "meta_type=" + TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue();

        if (StringUtils.isNotBlank(additionalWhere)) {
            whereClause += " AND " + additionalWhere;
        }

        return getCountOfTskFiles(skCase, currentDataSource, whereClause);
    }

    /**
     * Gets a count of regular non-slack files for a particular datasource.
     *
     * @param skCase            The current SleuthkitCase.
     * @param currentDataSource The datasource.
     * @param additionalWhere   Additional sql where clauses.
     *
     * @return The count of files or null on error.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    public static Long getCountOfRegNonSlackFiles(SleuthkitCase skCase, DataSource currentDataSource, String additionalWhere)
            throws TskCoreException, SQLException {
        String whereClause = "meta_type=" + TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()
                + " AND type<>" + TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType();

        if (StringUtils.isNotBlank(additionalWhere)) {
            whereClause += " AND " + additionalWhere;
        }

        return getCountOfTskFiles(skCase, currentDataSource, whereClause);
    }

    /**
     * An interface for handling a result set and returning a value.
     */
    public interface ResultSetHandler<T> {

        T process(ResultSet resultset) throws SQLException;
    }

    /**
     * Retrieves a result based on the provided query.
     *
     * @param skCase    The current SleuthkitCase.
     * @param query     The query.
     * @param processor The result set handler.
     *
     * @return The ResultSetHandler value or null if no ResultSet could be
     *         obtained.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    static <T> T getBaseQueryResult(SleuthkitCase skCase, String query, ResultSetHandler<T> processor)
            throws TskCoreException, SQLException {
        try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            return processor.process(resultSet);
        }
    }

    /**
     * Creates sql where clause that does a bitwise check to see if flag is
     * present.
     *
     * @param flag The flag for which to check.
     *
     * @return The clause.
     */
    public static String getMetaFlagsContainsStatement(TSK_FS_META_FLAG_ENUM flag) {
        return "meta_flags & " + flag.getValue() + " > 0";
    }

    /**
     * Enum for specifying the sort order for getAttributes.
     */
    public enum SortOrder {
        DESCENDING,
        ASCENDING
    }

    /**
     * Returns a list of all artifacts of the given type that have an attribute
     * of the given type sorted by given attribute type value. Artifacts that do
     * not have the given attribute will not be included in the list.
     *
     * Sorting on attributes of type byte[] and JSON is not currently supported.
     *
     * @param skCase        SleuthkitCase instance.
     * @param artifactType  Type of artifacts to sort.
     * @param dataSource    Data Source that the artifact belongs to.
     * @param attributeType Attribute type to sort by.
     * @param sortOrder     Sort order of the attributes, either ascending or
     *                      descending.
     *
     * @return A list of artifacts of type artifactType sorted by the attribute
     *         of attributeType in the given sortOrder. If no artifacts are
     *         found an empty list will be returned.
     *
     * @throws TskCoreException
     */
    public static List<BlackboardArtifact> getArtifacts(SleuthkitCase skCase, BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType, SortOrder sortOrder) throws TskCoreException {
        return getArtifacts(skCase, artifactType, dataSource, attributeType, sortOrder, 0);
    }

    /**
     * Return a list of artifacts that have been sorted by their attribute of
     * attributeType. If an artifact of the given type does not have the given
     * attribute it will not be included in the returned list.
     *
     * Sorting on attributes of type byte[] and JSON is not currently supported.
     *
     * @param skCase        SleuthkitCase instance.
     * @param artifactType  Type of artifacts to sort.
     * @param dataSource    Data Source that the artifact belongs to.
     * @param attributeType Attribute type to sort by.
     * @param sortOrder     Sort order of the attributes, either ascending or
     *                      descending.
     * @param maxCount      Maximum number of results to return. To return all
     *                      values maxCount should be 0.
     *
     * @return A list of artifacts of type artifactType sorted by the attribute
     *         of attributeType in the given sortOrder. If no artifacts are
     *         found an empty list will be returned.
     *
     * @throws TskCoreException
     */
    public static List<BlackboardArtifact> getArtifacts(SleuthkitCase skCase, BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType, SortOrder sortOrder, int maxCount) throws TskCoreException {
        if (maxCount < 0) {
            throw new IllegalArgumentException("Invalid maxCount passed to getArtifacts, value must be equal to or greater than 0");
        }

        return createListFromMap(getArtifactMap(skCase, artifactType, dataSource, attributeType, sortOrder), maxCount);
    }

    /**
     * Empty private constructor
     */
    private DataSourceInfoUtilities() {
    }

    /**
     * Create a Map of lists of artifacts sorted by the given attribute.
     *
     * @param skCase        SleuthkitCase instance.
     * @param artifactType  Type of artifacts to sort.
     * @param dataSource    Data Source that the artifact belongs to.
     * @param attributeType Attribute type to sort by.
     * @param sortOrder     Sort order of the attributes, either ascending or
     *                      descending.
     *
     * @return A Map of lists of artifacts sorted by the value of attribute
     *         given type. Artifacts that do not have an attribute of the given
     *         type will not be included.
     *
     * @throws TskCoreException
     */
    static private SortedMap<BlackboardAttribute, List<BlackboardArtifact>> getArtifactMap(SleuthkitCase skCase, BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType, SortOrder sortOrder) throws TskCoreException {
        SortedMap<BlackboardAttribute, List<BlackboardArtifact>> sortedMap = new TreeMap<>(new AttributeComparator(sortOrder));
        List<BlackboardArtifact> artifactList = skCase.getBlackboard().getArtifacts(artifactType.getTypeID(), dataSource.getId());

        for (BlackboardArtifact artifact : artifactList) {
            BlackboardAttribute attribute = artifact.getAttribute(attributeType);
            if (attribute == null) {
                continue;
            }

            List<BlackboardArtifact> mapArtifactList = sortedMap.get(attribute);
            if (mapArtifactList == null) {
                mapArtifactList = new ArrayList<>();
                sortedMap.put(attribute, mapArtifactList);
            }

            mapArtifactList.add(artifact);
        }

        return sortedMap;
    }

    /**
     * Creates the list of artifacts from the sorted map and the given count.
     *
     * @param sortedMap Sorted map of artifact lists.
     * @param maxCount  Maximum number of artifacts to return.
     *
     * @return List of artifacts, list will be empty if none were found.
     */
    static private List<BlackboardArtifact> createListFromMap(SortedMap<BlackboardAttribute, List<BlackboardArtifact>> sortedMap, int maxCount) {
        List<BlackboardArtifact> artifactList = new ArrayList<>();

        for (List<BlackboardArtifact> mapArtifactList : sortedMap.values()) {

            if (maxCount == 0 || (artifactList.size() + mapArtifactList.size()) <= maxCount) {
                artifactList.addAll(mapArtifactList);
                continue;
            }

            if (maxCount == artifactList.size()) {
                break;
            }

            for (BlackboardArtifact artifact : mapArtifactList) {
                if (artifactList.size() < maxCount) {
                    artifactList.add(artifact);
                } else {
                    break;
                }
            }
        }
        return artifactList;
    }

    /**
     * Compares the value of two BlackboardAttributes that are of the same type.
     * This comparator is specialized for data source summary and only supports
     * the basic attribute types of string, integer, long, datetime (long), and
     * double.
     *
     * Note: A runtime exception will be thrown from the compare if the
     * attributes are not of the same type or if their type is not supported.
     */
    private static class AttributeComparator implements Comparator<BlackboardAttribute> {

        private final SortOrder direction;

        AttributeComparator(SortOrder direction) {
            this.direction = direction;
        }

        @Override
        public int compare(BlackboardAttribute attribute1, BlackboardAttribute attribute2) {
            if (!attribute1.getAttributeType().equals(attribute2.getAttributeType())) {
                throw new IllegalArgumentException("Unable to compare attributes of different types");
            }

            int result = compare(attribute1.getAttributeType(), attribute1, attribute2);

            if (direction == SortOrder.DESCENDING) {
                result *= -1;
            }

            return result;
        }

        /**
         * Compared two attributes of the given type. Note, that not all
         * attribute types are supported. A runtime exception will be thrown if
         * an unsupported attribute is supplied.
         *
         * @param type       Attribute type.
         * @param attribute1 First attribute to compare.
         * @param attribute2 Second attribute to compare.
         *
         * @return Compare result.
         */
        private int compare(BlackboardAttribute.Type type, BlackboardAttribute attribute1, BlackboardAttribute attribute2) {
            switch (type.getValueType()) {
                case STRING:
                    return attribute1.getValueString().compareToIgnoreCase(attribute2.getValueString());
                case INTEGER:
                    return Integer.compare(attribute1.getValueInt(), attribute2.getValueInt());
                case LONG:
                case DATETIME:
                    return Long.compare(attribute1.getValueLong(), attribute2.getValueLong());
                case DOUBLE:
                    return Double.compare(attribute1.getValueDouble(), attribute2.getValueDouble());
                case BYTE:
                case JSON:
                default:
                    throw new IllegalArgumentException("Unable to compare attributes of type " + attribute1.getAttributeType().getTypeName());
            }
        }
    }

    /**
     * Retrieves attribute from artifact if exists. Returns null if attribute is
     * null or underlying call throws exception.
     *
     * @param artifact      The artifact.
     * @param attributeType The attribute type to retrieve from the artifact.
     *
     * @return The attribute or null if could not be received.
     */
    private static BlackboardAttribute getAttributeOrNull(BlackboardArtifact artifact, Type attributeType) {
        try {
            return artifact.getAttribute(attributeType);
        } catch (TskCoreException ex) {
            return null;
        }
    }

    /**
     * Retrieves the string value of a certain attribute type from an artifact.
     *
     * @param artifact      The artifact.
     * @param attributeType The attribute type.
     *
     * @return The 'getValueString()' value or null if the attribute or String
     *         could not be retrieved.
     */
    public static String getStringOrNull(BlackboardArtifact artifact, Type attributeType) {
        BlackboardAttribute attr = getAttributeOrNull(artifact, attributeType);
        return (attr == null) ? null : attr.getValueString();
    }

    /**
     * Retrieves the long value of a certain attribute type from an artifact.
     *
     * @param artifact      The artifact.
     * @param attributeType The attribute type.
     *
     * @return The 'getValueLong()' value or null if the attribute could not be
     *         retrieved.
     */
    public static Long getLongOrNull(BlackboardArtifact artifact, Type attributeType) {
        BlackboardAttribute attr = getAttributeOrNull(artifact, attributeType);
        return (attr == null) ? null : attr.getValueLong();
    }

    /**
     * Retrieves the int value of a certain attribute type from an artifact.
     *
     * @param artifact      The artifact.
     * @param attributeType The attribute type.
     *
     * @return The 'getValueInt()' value or null if the attribute could not be
     *         retrieved.
     */
    public static Integer getIntOrNull(BlackboardArtifact artifact, Type attributeType) {
        BlackboardAttribute attr = getAttributeOrNull(artifact, attributeType);
        return (attr == null) ? null : attr.getValueInt();
    }

    /**
     * Retrieves the long value of a certain attribute type from an artifact and
     * converts to date (seconds since epoch).
     *
     * @param artifact      The artifact.
     * @param attributeType The attribute type.
     *
     * @return The date determined from the 'getValueLong()' as seconds from
     *         epoch or null if the attribute could not be retrieved or is 0.
     */
    public static Date getDateOrNull(BlackboardArtifact artifact, Type attributeType) {
        Long longVal = getLongOrNull(artifact, attributeType);
        return (longVal == null || longVal == 0) ? null : new Date(longVal * 1000);
    }

    /**
     * Returns the long value or zero if longVal is null.
     *
     * @param longVal The long value.
     *
     * @return The long value or 0 if provided value is null.
     */
    public static long getLongOrZero(Long longVal) {
        return longVal == null ? 0 : longVal;
    }

    /**
     * Returns string value of long with comma separators. If null returns a
     * string of '0'.
     *
     * @param longVal The long value.
     *
     * @return The string value of the long.
     */
    public static String getStringOrZero(Long longVal) {
        return longVal == null ? "0" : COMMA_FORMATTER.format(longVal);
    }
}
