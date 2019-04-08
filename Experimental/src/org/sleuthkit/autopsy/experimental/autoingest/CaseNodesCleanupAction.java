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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.progress.AppFrameProgressBar;
import org.sleuthkit.autopsy.progress.TaskCancellable;

/**
 * An action class that kicks off a cancellable case nodes cleanup task that
 * runs in a background thread and reports progress using an application frame
 * progress bar.
 */
final class CaseNodesCleanupAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    @Override
    @NbBundle.Messages({
        "CaseNodesCleanupAction.progressDisplayName=Cleanup Case Znodes"
    })
    public void actionPerformed(ActionEvent event) {
        final AppFrameProgressBar progress = new AppFrameProgressBar(Bundle.CaseNodesCleanupAction_progressDisplayName());        
        final TaskCancellable taskCanceller = new TaskCancellable(progress);
        progress.setCancellationBehavior(taskCanceller);
        final Runnable task = new CaseNodesCleanupTask(progress);
        final FutureTask<Void> future = new FutureTask<>(task, null);
        taskCanceller.setFuture(future);
        new Thread(future).start();
    }

    @Override
    public CaseNodesCleanupAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
