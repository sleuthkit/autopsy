/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.progress;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Worker;

/**
 *
 */
public abstract class ProgressAdapterBase implements ProgressAdapter {

    @Override
    public double getProgress() {
        return progress.get();
    }

    public final void updateProgress(Double workDone) {
        this.progress.set(workDone);
    }

    @Override
    public String getMessage() {
        return message.get();
    }

    public final void updateMessage(String Status) {
        this.message.set(Status);
    }

    SimpleObjectProperty<Worker.State> state = new SimpleObjectProperty(Worker.State.READY);

    SimpleDoubleProperty progress = new SimpleDoubleProperty(this, "pregress");

    SimpleStringProperty message = new SimpleStringProperty(this, "status");

    @Override
    public SimpleDoubleProperty progressProperty() {
        return progress;
    }

    @Override
    public SimpleStringProperty messageProperty() {
        return message;
    }

    @Override
    public Worker.State getState() {
        return state.get();
    }

    protected void updateState(Worker.State newState) {
        state.set(newState);
    }

    @Override
    public ReadOnlyObjectProperty<Worker.State> stateProperty() {
        return new ReadOnlyObjectWrapper<>(state.get());
    }

    public ProgressAdapterBase() {
    }
}
