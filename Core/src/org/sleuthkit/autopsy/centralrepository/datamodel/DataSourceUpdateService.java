/*
 * Central Repository
 *
 * Copyright 2018 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

@ServiceProvider(service = AutopsyService.class)
public class DataSourceUpdateService implements AutopsyService {

    @Override
    @NbBundle.Messages({"DataSourceUpdateService.serviceName.text=Update Central Repository Data Sources"})
    public String getServiceName() {
        return Bundle.DataSourceUpdateService_serviceName_text();
    }

    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        if (EamDb.isEnabled()) {
            try {
                EamDb centralRepository = EamDb.getInstance();
                CorrelationCase correlationCase = centralRepository.getCase(context.getCase());
                for (CorrelationDataSource correlationDataSource : centralRepository.getDataSources()) {
                    //ResultSet.getLong has a value of 0 when the value is null
                    if (correlationDataSource.getCaseID() == correlationCase.getID() && correlationDataSource.getCaseDataSourceID() == 0) {
                        for (Content dataSource : context.getCase().getDataSources()) {
                            if (((DataSource) dataSource).getDeviceId().equals(correlationDataSource.getDeviceID())) {
                                centralRepository.addDataSourceObjectId(correlationDataSource.getID(), dataSource.getId());
                                break;
                            }
                        }

                    }
                }
            } catch (EamDbException | TskCoreException ex) {
                throw new AutopsyServiceException("Unabe to update datasources in central repository", ex);
            }
        }
    }

}
