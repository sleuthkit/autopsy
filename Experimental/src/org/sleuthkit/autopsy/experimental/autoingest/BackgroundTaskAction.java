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
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.event.ActionEvent;
import java.util.concurrent.FutureTask;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.progress.AppFrameProgressBar;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.progress.TaskCancellable;

/**
 * A base class for action classes that kick off a cancellable task that runs in
 * a background thread and reports progress using an application frame progress
 * bar.
 */
abstract class BackgroundTaskAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final String progressDisplayName;

    /**
     * Constructs the base class part of action classes that kick off a
     * cancellable task that runs in a background thread and reports progress
     * using an application frame progress bar.
     *
     * @param name                The name of the action.
     * @param progressDisplayName The display name for the progress bar.
     */
    BackgroundTaskAction(String name, String progressDisplayName) {
        super(name);
        this.progressDisplayName = progressDisplayName;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        final AppFrameProgressBar progress = new AppFrameProgressBar(progressDisplayName);
        final TaskCancellable taskCanceller = new TaskCancellable(progress);
        progress.setCancellationBehavior(taskCanceller);
        final Runnable task = new CaseNodesCleanupTask(progress);
        final FutureTask<Void> future = new FutureTask<>(task, null);
        taskCanceller.setFuture(future);
        new Thread(future).start();
    }

    abstract Runnable getTask(ProgressIndicator progress);
    
    @Override
    public BackgroundTaskAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
