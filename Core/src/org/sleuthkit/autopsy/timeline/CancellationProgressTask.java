/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.concurrent.Task;

/**
 * An extension of Task that allows a client to request cancellation, with out
 * the Task entering the cancelled state immediately. This allows the task to
 * continue to report progress of eg its cleanup operations. Implementations
 * should use the {@link #isCancelRequested() } method to check for cancelation
 * and call cancel() before returning form the call() method.
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
