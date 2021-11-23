/*
 * Central Repository
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.eventlisteners.CaseEventListener;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The Autopsy application service for the central repository.
 */
@ServiceProvider(service = AutopsyService.class)
public class CentralRepositoryService implements AutopsyService {

    private CaseEventListener caseEventListener = new CaseEventListener();

    @Override
    @NbBundle.Messages({
        "CentralRepositoryService.serviceName=Central Repository Service"
    })
    public String getServiceName() {
        return Bundle.CentralRepositoryService_serviceName();
    }

    @NbBundle.Messages({
        "CentralRepositoryService.progressMsg.updatingSchema=Checking for schema updates...",
        "CentralRepositoryService.progressMsg.startingListener=Starting events listener..."
    })
    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        if (!CentralRepository.isEnabled()) {
            return;
        }

        ProgressIndicator progress = context.getProgressIndicator();
        progress.progress(Bundle.CentralRepositoryService_progressMsg_updatingSchema());
        updateSchema();
        if (context.cancelRequested()) {
            return;
        }

        dataUpgradeForVersion1dot2(context.getCase());
        if (context.cancelRequested()) {
            return;
        }

        progress.progress(Bundle.CentralRepositoryService_progressMsg_startingListener());
        caseEventListener = new CaseEventListener();
        caseEventListener.startUp();
    }

    @NbBundle.Messages({
        "CentralRepositoryService.progressMsg.waitingForListeners=Finishing adding data to central repository database...."
    })
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        ProgressIndicator progress = context.getProgressIndicator();
        progress.progress(Bundle.CentralRepositoryService_progressMsg_waitingForListeners());
        if (caseEventListener != null) {
            caseEventListener.shutdown();
        }
    }

    /**
     * Updates the central repository database schema to the latest version.
     *
     * @throws AutopsyServiceException The exception is thrown if there is an
     *                                 error updating the database schema.
     */
    private void updateSchema() throws AutopsyServiceException {
        try {
            CentralRepoDbManager.upgradeDatabase();
        } catch (CentralRepoException ex) {
            throw new AutopsyServiceException("Failed to update the Central Repository schema", ex);
        }
    }

    /**
     * Adds missing data source object IDs from data sources in this case to the
     * corresponding records in the central repository database. This is a data
     * update to go with the v1.2 schema update.
     *
     * @throws AutopsyServiceException The exception is thrown if there is an
     *                                 error updating the database.
     */
    private void dataUpgradeForVersion1dot2(Case currentCase) throws AutopsyServiceException {
        try {
            /*
             * If the case is in the central repository, there may be missing
             * data source object IDs in the data_sources.datasource_obj_id
             * column that was added in the version 1.2 schema update.
             */
            CentralRepository centralRepository = CentralRepository.getInstance();
            CorrelationCase correlationCase = centralRepository.getCase(currentCase);
            if (correlationCase != null) {
                for (CorrelationDataSource correlationDataSource : centralRepository.getDataSources()) {
                    /*
                     * ResultSet.getLong returns zero when the value in the
                     * result set is NULL.
                     */
                    if (correlationDataSource.getCaseID() == correlationCase.getID() && correlationDataSource.getDataSourceObjectID() == 0) {
                        for (Content dataSource : currentCase.getDataSources()) {
                            if (((DataSource) dataSource).getDeviceId().equals(correlationDataSource.getDeviceID()) && dataSource.getName().equals(correlationDataSource.getName())) {
                                centralRepository.addDataSourceObjectId(correlationDataSource.getID(), dataSource.getId());
                                break;
                            }
                        }
                    }
                }
            }
        } catch (CentralRepoException | TskCoreException ex) {
            throw new AutopsyServiceException("Failed to update data sources in the Central Repository for schema v1.2", ex);
        }
    }

}
