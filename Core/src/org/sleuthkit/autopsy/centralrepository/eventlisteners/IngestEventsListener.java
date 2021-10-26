/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Listen for ingest job events and update entries in the Central Repository
 * database accordingly
 */
public class IngestEventsListener {

    private static final Logger LOGGER = Logger.getLogger(CorrelationAttributeInstance.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED);
    private static final String INGEST_EVENT_THREAD_NAME = "Ingest-Event-Listener-%d";
    private final ExecutorService jobProcessingExecutor;
    private final PropertyChangeListener pcl2 = new IngestJobEventListener();

    public IngestEventsListener() {
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INGEST_EVENT_THREAD_NAME).build());
    }

    public void shutdown() {
        ThreadUtils.shutDownTaskExecutor(jobProcessingExecutor);
    }

    /*
     * Add all of our Ingest Event Listeners to the IngestManager Instance.
     */
    public void installListeners() {
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, pcl2);
    }

    /*
     * Remove all of our Ingest Event Listeners from the IngestManager Instance.
     */
    public void uninstallListeners() {
        IngestManager.getInstance().removeIngestJobEventListener(pcl2);
    }

    private class IngestJobEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            CentralRepository dbManager;
            try {
                dbManager = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                return;
            }

            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                case DATA_SOURCE_ANALYSIS_COMPLETED: {
                    jobProcessingExecutor.submit(new AnalysisCompleteTask(dbManager, evt));
                    break;
                }
                default:
                    break;
            }
        }

    }

    private final class AnalysisCompleteTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private AnalysisCompleteTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            /*
             * Ensure the data source in the Central Repository has hash values
             * that match those in the case database.
             */
            if (!CentralRepository.isEnabled()) {
                return;
            }
            Content dataSource;
            String dataSourceName = "";
            long dataSourceObjectId = -1;
            try {
                dataSource = ((DataSourceAnalysisEvent) event).getDataSource();
                /*
                 * We only care about Images for the purpose of updating hash
                 * values.
                 */
                if (!(dataSource instanceof Image)) {
                    return;
                }

                dataSourceName = dataSource.getName();
                dataSourceObjectId = dataSource.getId();

                Case openCase = Case.getCurrentCaseThrows();

                CorrelationCase correlationCase = dbManager.getCase(openCase);
                if (null == correlationCase) {
                    correlationCase = dbManager.newCase(openCase);
                }

                CorrelationDataSource correlationDataSource = dbManager.getDataSource(correlationCase, dataSource.getId());
                if (correlationDataSource == null) {
                    // Add the data source.
                    CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource);
                } else {
                    // Sync the data source hash values if necessary.
                    if (dataSource instanceof Image) {
                        Image image = (Image) dataSource;

                        String imageMd5Hash = image.getMd5();
                        if (imageMd5Hash == null) {
                            imageMd5Hash = "";
                        }
                        String crMd5Hash = correlationDataSource.getMd5();
                        if (StringUtils.equals(imageMd5Hash, crMd5Hash) == false) {
                            correlationDataSource.setMd5(imageMd5Hash);
                        }

                        String imageSha1Hash = image.getSha1();
                        if (imageSha1Hash == null) {
                            imageSha1Hash = "";
                        }
                        String crSha1Hash = correlationDataSource.getSha1();
                        if (StringUtils.equals(imageSha1Hash, crSha1Hash) == false) {
                            correlationDataSource.setSha1(imageSha1Hash);
                        }

                        String imageSha256Hash = image.getSha256();
                        if (imageSha256Hash == null) {
                            imageSha256Hash = "";
                        }
                        String crSha256Hash = correlationDataSource.getSha256();
                        if (StringUtils.equals(imageSha256Hash, crSha256Hash) == false) {
                            correlationDataSource.setSha256(imageSha256Hash);
                        }
                    }
                }
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Unable to fetch data from the Central Repository for data source '%s' (obj_id=%d)",
                        dataSourceName, dataSourceObjectId), ex);
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "No current case opened.", ex);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Unable to fetch data from the case database for data source '%s' (obj_id=%d)",
                        dataSourceName, dataSourceObjectId), ex);
            }
        }
    }

}
