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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides methods to query for data source overview details.
 */
public class ContainerSummary implements DefaultArtifactUpdateGovernor {

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE.getTypeID()
    ));

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    public ContainerSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public ContainerSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean isRefreshRequired(ModuleContentEvent evt) {
        return true;
    }

    @Override
    public boolean isRefreshRequired(AbstractFile file) {
        return true;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Gets the size of unallocated files in a particular datasource.
     *
     * @param currentDataSource The data source.
     *
     * @return The size or null if the query could not be executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getSizeOfUnallocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        if (currentDataSource == null) {
            return null;
        }

        final String valueParam = "value";
        final String countParam = "count";
        String query = "SELECT SUM(size) AS " + valueParam + ", COUNT(*) AS " + countParam
                + " FROM tsk_files"
                + " WHERE " + DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType()
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                + " AND name<>''"
                + " AND data_source_obj_id=" + currentDataSource.getId();

        DataSourceInfoUtilities.ResultSetHandler<Long> handler = (resultSet) -> {
            if (resultSet.next()) {
                // ensure that there is an unallocated count result that is attached to this data source
                long resultCount = resultSet.getLong(valueParam);
                return (resultCount > 0) ? resultSet.getLong(valueParam) : null;
            } else {
                return null;
            }
        };

        return DataSourceInfoUtilities.getBaseQueryResult(provider.get(), query, handler);
    }

    /**
     * Retrieves the concatenation of operating system attributes for a
     * particular data source.
     *
     * @param dataSource The data source.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public String getOperatingSystems(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

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
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public String getDataSourceType(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        if (dataSource == null) {
            return null;
        }

        return getConcattedAttrValue(dataSource.getId(),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());
    }

    /**
     * Generates a string which is a concatenation of the value received from
     * the result set.
     *
     * @param query              The query.
     * @param valueParam         The parameter for the value in the result set.
     * @param separator          The string separator used in concatenation.
     *
     * @return The concatenated string or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    private String getConcattedStringsResult(String query, String valueParam, String separator)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        DataSourceInfoUtilities.ResultSetHandler<String> handler = (resultSet) -> {
            String toRet = "";
            boolean first = true;
            while (resultSet.next()) {
                if (first) {
                    first = false;
                } else {
                    toRet += separator;
                }
                toRet += resultSet.getString(valueParam);
            }

            return toRet;
        };

        return DataSourceInfoUtilities.getBaseQueryResult(provider.get(), query, handler);
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
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    private String getConcattedAttrValue(long dataSourceId, int artifactTypeId, int attributeTypeId)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        final String valueParam = "concatted_attribute_value";
        String query = "SELECT attr.value_text AS " + valueParam
                + " FROM blackboard_artifacts bba "
                + " INNER JOIN blackboard_attributes attr ON bba.artifact_id = attr.artifact_id "
                + " WHERE bba.data_source_obj_id = " + dataSourceId
                + " AND bba.artifact_type_id = " + artifactTypeId
                + " AND attr.attribute_type_id = " + attributeTypeId;

        String separator = ", ";
        return getConcattedStringsResult(query, valueParam, separator);
    }
}
