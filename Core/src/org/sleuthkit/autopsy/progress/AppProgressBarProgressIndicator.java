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
 * right hand corner of the application main frame, i.e., a NetBean
 * ProgressHandle.
 */
public final class AppProgressBarProgressIndicator implements ProgressIndicator {

    private final ProgressHandle progressHandle;

    /**
     * Constructs a progress indicator that displays progress using a progress
     * bar in the lower right hand corner of the application main frame, i.e., a
     * NetBean ProgressHandle.
     */
    public AppProgressBarProgressIndicator(String displayName, Cancellable cancellationAction) {
        this.progressHandle = ProgressHandle.createHandle(displayName, cancellationAction);
    }

    @Override
    public void start(String message, int totalWorkUnits) {
        progressHandle.start(totalWorkUnits);
        progressHandle.progress(message);
    }

    @Override
    public void start(String message) {
        progressHandle.start();
        progressHandle.progress(message);
    }

    @Override
    public void switchToIndeterminate(String message) {
        progressHandle.switchToIndeterminate();
        progressHandle.progress(message);
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
        progressHandle.switchToDeterminate(totalWorkUnits);
        progressHandle.progress(message, workUnitsCompleted);
    }

    @Override
    public void progress(String message) {
        progressHandle.progress(message);
    }

    @Override
    public void progress(int workUnitsCompleted) {
        progressHandle.progress(workUnitsCompleted);
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        progressHandle.progress(message, workUnitsCompleted);
    }

    @Override
    public void finish() {
        progressHandle.finish();
    }

}
