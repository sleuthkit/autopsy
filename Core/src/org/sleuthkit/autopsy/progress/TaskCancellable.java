/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp. Contact: carrier <at> sleuthkit
 * <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sleuthkit.autopsy.progress;

import java.util.concurrent.Future;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;

/**
 * Pluggable cancellation behavior for use in progress indicators (such as the
 * application frame progress indicator) that support cancelling a task using an
 * implementation of org.openide.util.Cancellable. Encapsulates a Future<?> to
 * be cancelled and sets the cancelling flag and message of the progress
 * indicator.
 */
public class TaskCancellable implements Cancellable {

    private final ProgressIndicator progress;
    private Future<?> future;

    /**
     * Constructs a pluggable cancellation behavior for use in progress
     * indicators (such as the application frame progress indicator) that
     * support cancelling a task using an implementation of
     * org.openide.util.Cancellable. Encapsulates a Future<?> to be cancelled
     * and sets the cancelling flag and message of the progress indicator.
     *
     * @param progress
     */
    public TaskCancellable(ProgressIndicator progress) {
        this.progress = progress;
    }

    /**
     * Sets the Future<?> used to cancel the associated task.
     *
     * @param future The future for the associated task.
     */
    public synchronized void setFuture(Future<?> future) {
        this.future = future;
    }

    @Override
    @NbBundle.Messages({
        "TaskCanceller.progress.cancellingMessage=Cancelling..."
    })
    public synchronized boolean cancel() {
        progress.setCancelling(Bundle.TaskCanceller_progress_cancellingMessage());
        return future.cancel(true);
    }
    
}
