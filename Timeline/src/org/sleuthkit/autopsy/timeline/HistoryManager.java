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

import javafx.beans.property.ReadOnlyObjectWrapper;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coreutils.ObservableStack;

/**
 *
 */
public class HistoryManager<T> {

    /** list based stack to hold history, 'top' is at index 0; */
    @GuardedBy("this")
    private final ObservableStack<T> historyStack = new ObservableStack<>();

    @GuardedBy("this")
    private final ObservableStack<T> forwardStack = new ObservableStack<>();

    private final ReadOnlyObjectWrapper<T> currentState = new ReadOnlyObjectWrapper<>();

    public ReadOnlyObjectWrapper<T> currentState() {
        return currentState;
    }

    public T getCurrentState() {
        return currentState.get();
    }

    synchronized public ObservableStack<T> getHistoryStack() {
        return historyStack;
    }

    synchronized public ObservableStack<T> getForwardStack() {
        return forwardStack;
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

        final T peek = historyStack.peek();

        if (peek != null && peek.equals(currentState.get()) == false) {
            forwardStack.push(currentState.get());
            currentState.set(peek);
        } else if (peek != null && peek.equals(currentState)) {
            historyStack.pop();
            return retreat();
        }
        return peek;
    }

    synchronized public void advance(T newState) {

        if (currentState.equals(newState) == false) {
            historyStack.push(currentState.get());
            currentState.set(newState);
            if (newState.equals(forwardStack.peek())) {
                forwardStack.pop();
            } else {
                forwardStack.clear();
            }
        }
    }

}
