/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.ContainerSummary functionality into a
 * DefaultArtifactUpdateGovernor used by Container tab.
 */
public class ContainerSummaryGetter implements DefaultArtifactUpdateGovernor {

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE.getTypeID()
    ));

    private final ContainerSummary containerSummary;

    /**
     * Main constructor.
     */
    public ContainerSummaryGetter() {
        containerSummary = new ContainerSummary();
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
        return Collections.unmodifiableSet(ARTIFACT_UPDATE_TYPE_IDS);
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
        return containerSummary.getSizeOfUnallocatedFiles(currentDataSource);
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
        return containerSummary.getOperatingSystems(dataSource);
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
        return containerSummary.getDataSourceType(dataSource);
    }

    /**
     * Retrieves a container data model object containing data about the data
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
    public ContainerSummary.ContainerDetails getContainerDetails(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return containerSummary.getContainerDetails(dataSource);
    }
}
