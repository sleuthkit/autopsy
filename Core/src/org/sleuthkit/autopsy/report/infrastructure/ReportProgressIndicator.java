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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.report.ReportProgressPanel;

/**
 * An adapter that adapts the ReportProgressPanel interface to the
 * ProgressIndicator interface so that a given progress indicator can be used
 * where a ReportProgressPanel is expected.
 */
public class ReportProgressIndicator extends ReportProgressPanel {

    private static final long serialVersionUID = 1L;
    private final ProgressIndicator progressIndicator;
    private int workUnitsCompleted;

    /**
     * Constructs an adapter that adapts the ReportProgressPanel interface to
     * the ProgressIndicator interface so that a given progress indicator can be
     * used where a ReportProgressPanel is expected.
     *
     * @param progressIndicator The progress indicator to be adapted.
     */
    public ReportProgressIndicator(ProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
    }

    @Override
    @NbBundle.Messages({
        "ReportProgressIndicator.startMessage=Report generation started"
    })
    public void start() {
        workUnitsCompleted = 0;
        setStatus(ReportStatus.RUNNING);
        progressIndicator.start(Bundle.ReportProgressIndicator_startMessage());
    }

    @Override
    @NbBundle.Messages({
        "ReportProgressIndicator.switchToDeterminateMessage=Report generation progress switched to determinate"
    })
    public void setMaximumProgress(int max) {
        progressIndicator.switchToDeterminate(Bundle.ReportProgressIndicator_switchToDeterminateMessage(), 0, max);
    }

    @Override
    public void increment() {
        ++workUnitsCompleted;
        progressIndicator.progress(workUnitsCompleted);
    }

    @Override
    public void setProgress(int value) {
        workUnitsCompleted = value;
        progressIndicator.progress(workUnitsCompleted);
    }

    @Override
    @NbBundle.Messages({
        "ReportProgressIndicator.switchToIndeterminateMessage=Report generation progress switched to indeterminate"
    })
    public void setIndeterminate(boolean indeterminate) {
        progressIndicator.switchToIndeterminate(Bundle.ReportProgressIndicator_switchToIndeterminateMessage());
    }

    @Override
    public void updateStatusLabel(String statusMessage) {
        if (getStatus() != ReportStatus.CANCELED) {
            progressIndicator.progress(statusMessage);
        }
    }

    @Override
    @NbBundle.Messages({
        "ReportProgressIndicator.completedMessage=Report generation completed",
        "ReportProgressIndicator.completedWithErrorsMessage=Report generation completed with errors",})
    public void complete(ReportStatus reportStatus) {
        if (getStatus() != ReportStatus.CANCELED) {
            switch (reportStatus) {
                case COMPLETE: {
                    progressIndicator.progress(Bundle.ReportProgressIndicator_completedMessage());
                    break;
                }
                case ERROR: {
                    progressIndicator.progress(Bundle.ReportProgressIndicator_completedWithErrorsMessage());
                    break;
                }
                default: {
                    break;
                }
            }
        }
        progressIndicator.finish();
    }

    @Override
    public void complete(ReportStatus reportStatus, String statusMessage) {
        if (getStatus() != ReportStatus.CANCELED) {
            progressIndicator.progress(statusMessage);
        }
        complete(reportStatus);
    }

    @Override
    @NbBundle.Messages({
        "ReportProgressIndicator.cancelledMessage=Report generation cancelled",})
    public void cancel() {
        switch (getStatus()) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            case ERROR:
                break;
            default:
                setStatus(ReportStatus.CANCELED);
                progressIndicator.progress(Bundle.ReportProgressIndicator_cancelledMessage());
                break;
        }
    }

}
