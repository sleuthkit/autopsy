/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 9
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.progress;

import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;

/**
 * A progress indicator that displays progress using a progress bar in the lower
 * right hand corner of the application main frame, i.e., a NetBeans
 * ProgressHandle.
 */
public final class AppFrameProgressBar implements ProgressIndicator {

    private final String displayName;
    private Cancellable cancellationBehavior;
    private ProgressHandle progressHandle;
    private volatile boolean cancelling;

    /**
     * Constructs a progress indicator that displays progress using a progress
     * bar in the lower right hand corner of the application main frame, i.e., a
     * NetBeans ProgressHandle.
     *
     * @param displayName The display name for the progress bar (a fixed name
     *                    that appears above the current progress message).
     */
    public AppFrameProgressBar(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Sets the cancellation behavior that should happen when a user clicks on
     * the "x" button of the progress bar.
     *
     * @param cancellationBehavior A org.openide.util.Cancellable that
     *                             implements the desired cancellation behavior.
     */
    public void setCancellationBehavior(Cancellable cancellationBehavior) {
        this.cancellationBehavior = cancellationBehavior;
    }

    @Override
    public void start(String message, int totalWorkUnits) {
        cancelling = false;
        this.progressHandle = ProgressHandle.createHandle(displayName, cancellationBehavior);
        progressHandle.start(totalWorkUnits);
        progressHandle.progress(message);
    }

    @Override
    public void start(String message) {
        cancelling = false;
        this.progressHandle = ProgressHandle.createHandle(displayName, cancellationBehavior);
        progressHandle.start();
        progressHandle.progress(message);
    }

    @Override
    public void switchToIndeterminate(String message) {
        if (!cancelling) {
            progressHandle.switchToIndeterminate();
            progressHandle.progress(message);
        }
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
        if (!cancelling) {
            progressHandle.switchToDeterminate(totalWorkUnits);
            progressHandle.progress(message, workUnitsCompleted);
        }
    }

    @Override
    public void progress(String message) {
        if (!cancelling) {
            progressHandle.progress(message);
        }
    }

    @Override
    public void progress(int workUnitsCompleted) {
        if (!cancelling) {
            progressHandle.progress(workUnitsCompleted);
        }
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        if (!cancelling) {
            progressHandle.progress(message, workUnitsCompleted);
        }
    }

    @Override
    public void setCancelling(String cancellingMessage) {
        cancelling = true;
        progressHandle.switchToIndeterminate();
        progressHandle.progress(cancellingMessage);
    }    
    
    @Override
    public void finish() {
        progressHandle.finish();
    }

}
