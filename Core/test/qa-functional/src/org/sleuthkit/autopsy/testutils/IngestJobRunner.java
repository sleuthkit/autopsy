/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.testutils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.datamodel.Content;

/**
 * A utility that runs an ingest job, blocking until the job is completed.
 */
public final class IngestRunner {

    /**
     * Runs an ingest job, blocking until the job is completed.
     *
     * @param dataSources The data sources for the ingest job.
     * @param settings    The settings for the ingst job
     *
     * @return A list of ingest module start up error messages, empty if the job
     *         was started sucessfully.
     *
     * @throws InterruptedException If interrupted while wiagin for the ingest
     *                              job to complete.
     */
    public static List<IngestModuleError> runIngestJob(Collection<Content> dataSources, IngestJobSettings settings) throws InterruptedException {
        Object ingestMonitor = new Object();
        IngestJobCompletiontListener completiontListener = new IngestJobCompletiontListener(ingestMonitor);
        IngestManager ingestManager = IngestManager.getInstance();
        ingestManager.addIngestJobEventListener(completiontListener);
        try {
            synchronized (ingestMonitor) {
                IngestJobStartResult jobStartResult = ingestManager.beginIngestJob(dataSources, settings);
                if (jobStartResult.getModuleErrors().isEmpty()) {
                    ingestMonitor.wait();
                    return Collections.emptyList();
                } else {
                    return jobStartResult.getModuleErrors();
                }
            }
        } finally {
            ingestManager.removeIngestJobEventListener(completiontListener);
        }
    }

    /**
     * IngestRunner instances cannot be instatiated.
     */
    private IngestRunner() {
    }

    /**
     * An ingest job event listener that allows IngestRunner.runIngestJob to
     * block until the specified ingest job is completed.
     */
    private static final class IngestJobCompletiontListener implements PropertyChangeListener {

        private final Object ingestMonitor;

        /**
         * Constructs an ingest job event listener that allows
         * IngestRunner.runIngestJob to block until the specified ingest job is
         * completed.
         *
         * @param ingestMonitor A Java object to notify when the ingest job is
         *                      omcpleted.
         */
        IngestJobCompletiontListener(Object ingestMonitor) {
            this.ingestMonitor = ingestMonitor;
        }

        /**
         * Listens for local ingest job completed or cancelled events and
         * notifies the ingest monitor when such an event occurs.
         *
         * @param event
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) event).getSourceType()) {
                String eventType = event.getPropertyName();
                if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString()) || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    synchronized (ingestMonitor) {
                        ingestMonitor.notify();
                    }
                }
            }
        }
    }
    
}
