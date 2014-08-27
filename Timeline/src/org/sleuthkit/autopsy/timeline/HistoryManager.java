/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.coreutils.ObservableStack;

/**
 *
 */
@ThreadSafe
public class HistoryManager<T> {

    @GuardedBy("this")
    private final ObservableStack<T> historyStack = new ObservableStack<>();

    @GuardedBy("this")
    private final ObservableStack<T> forwardStack = new ObservableStack<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<T> currentState = new ReadOnlyObjectWrapper<>();

    @GuardedBy("this")
    private final ReadOnlyBooleanWrapper canAdvance = new ReadOnlyBooleanWrapper();

    @GuardedBy("this")
    private final ReadOnlyBooleanWrapper canRetreat = new ReadOnlyBooleanWrapper();

    synchronized public boolean canAdvance() {
        return canAdvance.get();
    }

    synchronized public boolean canRetreat() {
        return canRetreat.get();
    }

    synchronized public ReadOnlyObjectProperty<T> currentState() {
        return currentState.getReadOnlyProperty();
    }

    synchronized public T getCurrentState() {
        return currentState.get();
    }

    synchronized public ReadOnlyBooleanProperty getCanAdvance() {
        return canAdvance.getReadOnlyProperty();
    }

    synchronized public ReadOnlyBooleanProperty getCanRetreat() {
        return canRetreat.getReadOnlyProperty();
    }

    public HistoryManager(T initialState) {
        this();
        currentState.set(initialState);
    }

    public HistoryManager() {
        canAdvance.bind(forwardStack.emptyProperty().not());
        canRetreat.bind(historyStack.emptyProperty().not());
    }

    synchronized public T advance() {
        final T peek = forwardStack.peek();

        if (peek != null && peek.equals(currentState.get()) == false) {
            historyStack.push(currentState.get());
            currentState.set(peek);
            forwardStack.pop();
        }
        return peek;
    }

    synchronized public T retreat() {
        final T peek = historyStack.pop();

        if (peek != null && peek.equals(currentState.get()) == false) {
            forwardStack.push(currentState.get());
            currentState.set(peek);
        } else if (peek != null && peek.equals(currentState.get())) {
            return retreat();
        }
        return peek;
    }

    synchronized public void advance(T newState) {
        if (currentState.equals(newState) == false) {
            if (currentState.get() != null) {
                historyStack.push(currentState.get());
            }
            currentState.set(newState);
            if (newState.equals(forwardStack.peek())) {
                forwardStack.pop();
            } else {
                forwardStack.clear();
            }
        }
    }

}
