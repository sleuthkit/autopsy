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
package org.sleuthkit.autopsy.casemodule;

import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Acts as a bridge between the Sleuthkit Java bindings classes and Autopsy by
 * implementing the SleuthkitCase$ErrorObserver interface. All errors are
 * written to the Autopsy logs. If a GUI is running, errors are also batched up
 * and reported periodically to the user via the notification area in the lower
 * right hand corner of the main application window.
 */
class SleuthkitErrorReporter implements SleuthkitCase.ErrorObserver {

    private static final Logger LOGGER = Logger.getLogger(SleuthkitErrorReporter.class.getName());
    private final int milliSecondsBetweenReports;
    private final String message;
    private long newProblems;
    private long totalProblems;
    private long lastReportedDate;

    /**
     * Create a new IntervalErrorReprotData instance and subscribe for TSK error
     * notifications for the current case.
     *
     * @param secondsBetweenReports Minimum number of seconds between reports.
     *                              It will not warn more frequently than this.
     * @param message               The message that will be shown when warning
     *                              the user.
     */
    SleuthkitErrorReporter(int secondsBetweenReports, String message) {
        this.newProblems = 0;
        this.totalProblems = 0;
        this.lastReportedDate = 0; // arm the first warning by choosing zero
        this.milliSecondsBetweenReports = secondsBetweenReports * 1000;  // convert to milliseconds
        this.message = message;
    }

    /**
     * Call this to add problems to the class. When the time threshold is met
     * (or if this is the first problem encountered), a warning will be shown to
     * the user.
     *
     * @param context      The context in which the error occurred.
     * @param errorMessage A description of the error that occurred.
     */
    @Override
    public void receiveError(String context, String errorMessage) {
        LOGGER.log(Level.SEVERE, String.format("%s error in the SleuthKit layer: %s", context, errorMessage));
        this.newProblems += 1;
        this.totalProblems += newProblems;
        long currentTimeStamp = System.currentTimeMillis();
        if ((currentTimeStamp - lastReportedDate) > milliSecondsBetweenReports) {
            this.lastReportedDate = currentTimeStamp;
            MessageNotifyUtil.Notify.error(message, context + ", " + errorMessage + " "
                    + this.newProblems + " "
                    + NbBundle.getMessage(SleuthkitErrorReporter.class, "IntervalErrorReport.NewIssues")
                    + " " + this.totalProblems + " "
                    + NbBundle.getMessage(SleuthkitErrorReporter.class, "IntervalErrorReport.TotalIssues")
                    + ".");
            this.newProblems = 0;
        }
    }
}
