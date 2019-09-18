/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

/**
 * This interface is necessary in order to not break backwards compatibility 
 * of GeneralReportModule interface. See JIRA-5354.
 */
public interface ReportProgressPanel {
    
     /**
     * Used by a report generation module to communicate report generation
     * status to this panel and its listeners.
     */
    public enum ReportStatus {

        QUEUING,
        RUNNING,
        COMPLETE,
        CANCELED,
        ERROR
    }   
    
    /**
     * Gets the current status of the generation of the report.
     *
     * @return The report generation status as a ReportStatus enum.
     */
    public ReportStatus getStatus();

    /**
     * Starts the progress bar component of this panel.
     */
    public void start();

    /**
     * Sets the maximum value of the progress bar component of this panel.
     *
     * @param max The maximum value.
     */
    public void setMaximumProgress(int max);

    /**
     * Increments the current value of the progress bar component of this panel
     * by one unit.
     */
    public void increment();

    /**
     * Sets the current value of the progress bar component of this panel.
     *
     * @param value The value to be set.
     */
    public void setProgress(int value);

    /**
     * Changes the the progress bar component of this panel to be determinate or
     * indeterminate.
     *
     * @param indeterminate True if the progress bar should be set to
     *                      indeterminate.
     */
    public void setIndeterminate(boolean indeterminate);

    /**
     * Changes the status message label component of this panel to show a given
     * processing status message. For example, updateStatusLabel("Now processing
     * files...") sets the label text to "Now processing files..."
     *
     * @param statusMessage String to use as label text.
     */
    public void updateStatusLabel(String statusMessage);

    /**
     * Makes the components of this panel indicate the final status of
     * generation of the report.
     *
     * @param reportStatus The final status, must be COMPLETE or ERROR.
     */
    public void complete(ReportStatus reportStatus);

    /**
     * Makes the components of this panel indicate the generation of the report
     * is completed.
     *
     * @deprecated Use {@link #complete(ReportStatus)}
     */
    @Deprecated
    public void complete();    
}
