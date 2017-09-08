/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.concurrent.Task;

/**
 * An extension of Task that allows a client to request cancellation, with out
 * the Task entering the cancelled state immediately. This allows the task to
 * continue to report progress of eg its cleanup operations. Implementations
 * should use the isCancelRequested() method to check for cancellation and call
 * cancel() before returning from the call() method.
 */
public abstract class CancellationProgressTask<X> extends Task<X> {

    private boolean cancelRequested;

    public synchronized boolean isCancelRequested() {
        return cancelRequested || isCancelled();
    }

    public synchronized boolean requestCancel() {
        this.cancelRequested = true;
        return true;
    }

    abstract public ReadOnlyBooleanProperty cancellableProperty();

    boolean isCancellable() {
        return cancellableProperty().get();
    }
}
