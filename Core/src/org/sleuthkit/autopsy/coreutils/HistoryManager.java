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
package org.sleuthkit.autopsy.coreutils;

import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A basic history implementation. Keeps a history (and forward) stack of state
 * objects of type <T>. exposes current state and availability of
 * advance/retreat operations via methods and JFX {@link  Property}s
 *
 * @param <T> the type of objects used to represent the
 * current/historical/future states
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

    synchronized public T getCurrentState() {
        return currentState.get();
    }

    synchronized public boolean canAdvance() {
        return canAdvance.get();
    }

    synchronized public boolean canRetreat() {
        return canRetreat.get();
    }

    synchronized public ReadOnlyObjectProperty<T> currentState() {
        return currentState.getReadOnlyProperty();
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

    /**
     * advance through the forward states by one, and put the current state in
     * the history. Is a no-op if there are no forward states.
     *
     * @return the state advanced to, or null if there were no forward states.
     */
    synchronized public T advance() {
        final T peek = forwardStack.peek();

        if (peek != null && peek.equals(currentState.get()) == false) {
            historyStack.push(currentState.get());
            currentState.set(peek);
            forwardStack.pop();
        }
        return peek;
    }

    /**
     * retreat through the history states by one, and add the current state to
     * the forward states. Is a no-op if there are no history states.
     *
     * @return the state retreated to, or null if there were no history states.
     */
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

    /**
     * put the current state in the history and advance to the given state. It
     * is a no-op if the current state is equal to the given state as determined
     * by invoking the equals method. Throws away any forward states.
     *
     * @param newState the new state to advance to
     * @throws IllegalArgumentException if the newState is null
     */
    synchronized public void advance(T newState) throws IllegalArgumentException {
        if (newState == null) {
            throw new IllegalArgumentException("newState must be non-null");
        }
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

    public void clear() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * A simple extension to SimpleListProperty to add a stack api
     *
     * TODO: this really should not extend SimpleListProperty but should
     * delegate to an appropriate observable implementation while implementing
     * the {@link Deque} interface
     */
    private static class ObservableStack<T> extends SimpleListProperty<T> {

        public ObservableStack() {
            super(FXCollections.<T>synchronizedObservableList(FXCollections.<T>observableArrayList()));
        }

        public void push(T item) {
            synchronized (this) {
                add(0, item);
            }
        }

        public T pop() {
            synchronized (this) {
                if (isEmpty()) {
                    return null;
                } else {
                    return remove(0);
                }
            }
        }

        public T peek() {
            synchronized (this) {
                if (isEmpty()) {
                    return null;
                } else {
                    return get(0);
                }
            }
        }
    }
}
