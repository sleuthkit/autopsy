/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Collects metrics for an auto ingest cluster.
 */
final class AutoIngestMetricsCollector {
    
    private static final Logger LOGGER = Logger.getLogger(AutoIngestMetricsCollector.class.getName());
    private CoordinationService coordinationService;
    
    /**
     * Creates an instance of the AutoIngestMetricsCollector.
     * 
     * @throws AutoIngestMetricsCollector.AutoIngestMetricsCollectorException 
     */
    AutoIngestMetricsCollector() throws AutoIngestMetricsCollectorException {
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestMetricsCollectorException("Failed to get coordination service", ex); //NON-NLS
        }
    }
    
    /**
     * Gets a new metrics snapshot from the coordination service for an auto
     * ingest cluster.
     *
     * @return The metrics snapshot.
     */
    MetricsSnapshot queryCoordinationServiceForMetrics() {
        try {
            MetricsSnapshot newMetricsSnapshot = new MetricsSnapshot();
            List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
            for (String node : nodeList) {
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, node));
                    if (nodeData.getVersion() < 1) {
                        /*
                         * Ignore version '0' nodes that have not been
                         * "upgraded" since they don't carry enough data.
                         */
                        continue;
                    }
                    AutoIngestJob job = new AutoIngestJob(nodeData);
                    AutoIngestJob.ProcessingStatus processingStatus = nodeData.getProcessingStatus();
                    switch (processingStatus) {
                        case PENDING:
                        case PROCESSING:
                        case DELETED:
                            /*
                             * These are not jobs we care about for metrics, so
                             * we will ignore them.
                             */
                            break;
                        case COMPLETED:
                            newMetricsSnapshot.addCompletedJobDate(job.getCompletedDate());
                            break;
                        default:
                            LOGGER.log(Level.SEVERE, "Unknown AutoIngestJobData.ProcessingStatus");
                            break;
                    }
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Unexpected interrupt while retrieving coordination service node data for '%s'", node), ex);
                } catch (AutoIngestJobNodeData.InvalidDataException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Unable to use node data for '%s'", node), ex);
                } catch (AutoIngestJob.AutoIngestJobException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to create a job for '%s'", node), ex);
                }
            }
            
            return newMetricsSnapshot;
            
        } catch (CoordinationService.CoordinationServiceException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get node list from coordination service", ex);
            return new MetricsSnapshot();
        }
    }
    
    /**
     * A snapshot of metrics for an auto ingest cluster.
     */
    static final class MetricsSnapshot {
        
        private final List<Long> completedJobDates = new ArrayList<>();
        
        /**
         * Gets a list of completed job dates, formatted in milliseconds.
         * 
         * @return The completed job dates, formatted in milliseconds.
         */
        List<Long> getCompletedJobDates() {
            return new ArrayList<>(completedJobDates);
        }
        
        /**
         * Adds a new date to the list of completed job dates.
         * 
         * @param date The date to be added.
         */
        void addCompletedJobDate(java.util.Date date) {
            completedJobDates.add(date.getTime());
        }
    }
    
    /**
     * Exception type thrown when there is an error completing an auto ingest
     * metrics collector operation.
     */
    static final class AutoIngestMetricsCollectorException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest metrics collector operation.
         *
         * @param message The exception message.
         */
        private AutoIngestMetricsCollectorException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest metrics collector operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestMetricsCollectorException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}