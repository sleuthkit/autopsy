/*
 * Central Repository
 *
 * Copyright 2018-2019 Basis Technology Corp.
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

import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class which updates the data sources in the central repository to include the
 * object ID which ties them to the current case, as well as the hash values.
 */
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
                
                //if the case isn't in the central repository yet there won't be data sources in it to update
                if (correlationCase == null) {
                    return;
                }
                
                for (CorrelationDataSource correlationDataSource : centralRepository.getDataSources()) {
                    //ResultSet.getLong has a value of 0 when the value is null
                    if (correlationDataSource.getCaseID() == correlationCase.getID()) {
                        for (Content dataSource : context.getCase().getDataSources()) {
                            if (((DataSource) dataSource).getDeviceId().equals(correlationDataSource.getDeviceID()) && dataSource.getName().equals(correlationDataSource.getName())) {
                                // Add the object ID to the data source if it doesn't exist.
                                if (correlationDataSource.getDataSourceObjectID() == 0) {
                                    centralRepository.addDataSourceObjectId(correlationDataSource.getID(), dataSource.getId());
                                }

                                // Sync the data source hash values if necessary.
                                if (dataSource instanceof Image) {
                                    Image image = (Image) dataSource;
                                    String imageMd5Hash = image.getMd5();
                                    String imageSha1Hash = image.getSha1();
                                    String imageSha256Hash = image.getSha256();
                                    
                                    String crMd5Hash = correlationDataSource.getMd5();
                                    String crSha1Hash = correlationDataSource.getSha1();
                                    String crSha256Hash = correlationDataSource.getSha256();
                                    
                                    if (StringUtils.equals(imageMd5Hash, crMd5Hash) == false || StringUtils.equals(imageSha1Hash, crSha1Hash) == false
                                            || StringUtils.equals(imageSha256Hash, crSha256Hash) == false) {
                                        correlationDataSource.setMd5(imageMd5Hash);
                                        correlationDataSource.setSha1(imageSha1Hash);
                                        correlationDataSource.setSha256(imageSha256Hash);
                                        centralRepository.updateDataSource(correlationDataSource);
                                    }
                                }

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
