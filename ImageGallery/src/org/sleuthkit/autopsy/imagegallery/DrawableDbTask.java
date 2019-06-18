/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Worker;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;

/**
 * An abstract base class for drawables database tasks.
 */
@NbBundle.Messages({
    "DrawableDbTask.InnerTask.progress.name=progress",
    "DrawableDbTask.InnerTask.message.name=status"
})
public abstract class DrawableDbTask implements Runnable, Cancellable {

    private final SimpleObjectProperty<Worker.State> state = new SimpleObjectProperty<>(Worker.State.READY);
    private final SimpleDoubleProperty progress = new SimpleDoubleProperty(this, Bundle.DrawableDbTask_InnerTask_progress_name());
    private final SimpleStringProperty message = new SimpleStringProperty(this, Bundle.DrawableDbTask_InnerTask_message_name());

    public double getProgress() {
        return progress.get();
    }

    public final void updateProgress(Double workDone) {
        this.progress.set(workDone);
    }

    public String getMessage() {
        return message.get();
    }

    public final void updateMessage(String Status) {
        this.message.set(Status);
    }

    public SimpleDoubleProperty progressProperty() {
        return progress;
    }

    public SimpleStringProperty messageProperty() {
        return message;
    }

    public Worker.State getState() {
        return state.get();
    }

    public ReadOnlyObjectProperty<Worker.State> stateProperty() {
        return new ReadOnlyObjectWrapper<>(state.get());
    }

    @Override
    public synchronized boolean cancel() {
        updateState(Worker.State.CANCELLED);
        return true;
    }

    protected void updateState(Worker.State newState) {
        state.set(newState);
    }

    protected synchronized boolean isCancelled() {
        return getState() == Worker.State.CANCELLED;
    }
}
