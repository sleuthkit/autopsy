/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.ReportProgressPanel;

/**
 * Writes progress and status messages to the application log.
 */
public class ReportProgressLogger implements ReportProgressPanel {

    private static final Logger logger = Logger.getLogger(ReportProgressLogger.class.getName());
    private volatile ReportProgressPanel.ReportStatus status;

    public ReportProgressLogger() {
        status = ReportStatus.QUEUING;
    }

    /**
     * Gets the current status of the generation of the report.
     *
     * @return The report generation status as a ReportStatus enum.
     */
    @Override
    public ReportStatus getStatus() {
        return status;
    }

    /**
     * Logs that the report has been started.
     */
    @Override
    public void start() {
        status = ReportStatus.RUNNING;
        logger.log(Level.INFO, "Report started");
    }

    /**
     * NO-OP.
     *
     * @param max The maximum value.
     */
    @Override
    public void setMaximumProgress(int max) {
    }

    /**
     * NO-OP.
     */
    @Override
    public void increment() {
    }

    /**
     * NO-OP.
     *
     * @param value The value to be set.
     */
    @Override
    public void setProgress(int value) {
    }

    /**
     * NO-OP.
     *
     * @param indeterminate True if the progress bar should be set to
     * indeterminate.
     */
    @Override
    public void setIndeterminate(boolean indeterminate) {
    }

    /**
     * Logs the status message.
     *
     * @param statusMessage String to use as label text.
     */
    @Override
    public void updateStatusLabel(String statusMessage) {
        if (status != ReportStatus.CANCELED) {
            logger.log(Level.INFO, statusMessage);
        }
    }

    /**
     * Logs the final status of the report generation.
     *
     * @param reportStatus The final status, must be COMPLETE or ERROR.
     * @param statusMessage String to use as label or error text.
     */
    @Override
    public void complete(ReportStatus reportStatus, String statusMessage) {
        if (!statusMessage.isEmpty()) {
            logger.log(Level.INFO, statusMessage);
        }
        if (status != ReportStatus.CANCELED) {
            switch (reportStatus) {
                case COMPLETE: {
                    status = ReportStatus.COMPLETE;
                    logger.log(Level.INFO, "Report completed");
                    break;
                }
                case ERROR: {
                    status = ReportStatus.ERROR;
                    logger.log(Level.INFO, "Report completed with errors");
                    break;
                }
                default: {
                    break;
                }
            }
        }
    }
    
    /**
     * Logs the final status of the report generation.
     *
     * @param reportStatus The final status, must be COMPLETE or ERROR.
     */
    @Override
    public void complete(ReportStatus reportStatus) {
        complete(reportStatus, "");
    }

    /**
     * Logs that the generation of the report was cancelled.
     */
    void cancel() {
        switch (status) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            case ERROR:
                break;
            default:
                status = ReportStatus.CANCELED;
                logger.log(Level.INFO, "Report cancelled");
                break;
        }
    }

    /**
     * Logs the final status of the report generation.
     *
     * @deprecated Use {@link #complete(ReportStatus)}
     */
    @Deprecated
    @Override
    public void complete() {
        complete(ReportStatus.COMPLETE);
    }

}
