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
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.contentutils.ContainerSummary;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting org.sleuthkit.autopsy.contentutils.ContainerSummary functionality into 
 * a DefaultArtifactUpdateGovernor used by Container tab.
 */
public class ContainerSummaryGetter implements DefaultArtifactUpdateGovernor {

    /**
     * Main constructor.
     */
    public ContainerSummaryGetter() {
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
        return ContainerSummary.getArtifactTypeIdsForRefresh();
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
        try {
            return ContainerSummary.getSizeOfUnallocatedFiles(currentDataSource);
        } catch (NoCurrentCaseException ex) {
            throw new SleuthkitCaseProviderException("No currently open case.", ex);
        }    
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
        try {
            return ContainerSummary.getOperatingSystems(dataSource);
        } catch (NoCurrentCaseException ex) {
            throw new SleuthkitCaseProviderException("No currently open case.", ex);
        }  
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
        try {
            return ContainerSummary.getDataSourceType(dataSource);
        } catch (NoCurrentCaseException ex) {
            throw new SleuthkitCaseProviderException("No currently open case.", ex);
        }  
    }
    
    /**
     * Retrieves a container data model object containing data about
     * the data source.
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
        try {
            return ContainerSummary.getContainerDetails(dataSource);
        } catch (NoCurrentCaseException ex) {
            throw new SleuthkitCaseProviderException("No currently open case.", ex);
        }
    }
}
