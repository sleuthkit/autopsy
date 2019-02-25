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

import java.util.Objects;
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
 * objects of type T. exposes current state and availability of
 * advance/retreat operations via methods and JFX Property objects. Null is not
 * a valid state, and will only be the current state before the first call to
 * advance.
 *
 * @param T the type of objects used to represent the
 *            current/historical/future states
 */
@ThreadSafe
public class History<T> {

    // Stack of things that were previously shown before an 'advance' was done
    @GuardedBy("this")
    private final ObservableStack<T> historyStack = new ObservableStack<>();

    // stack of things that were previously shown before a 'retreat' (i.e. a back) was done
    @GuardedBy("this")
    private final ObservableStack<T> forwardStack = new ObservableStack<>();

    // what is currently being shown
    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<T> currentState = new ReadOnlyObjectWrapper<>();

    // Is the forward stack empty?
    @GuardedBy("this")
    private final ReadOnlyBooleanWrapper canAdvance = new ReadOnlyBooleanWrapper();

    // is the historyStack empty?
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

    public History(T initialState) {
        this();
        currentState.set(initialState);
    }

    public History() {
        canAdvance.bind(forwardStack.emptyProperty().not());
        canRetreat.bind(historyStack.emptyProperty().not());
    }

    synchronized public void reset(T newState) {
        forwardStack.clear();
        historyStack.clear();
        currentState.set(newState);
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
        final T pop = historyStack.pop();

        if (pop != null && pop.equals(currentState.get()) == false) {
            forwardStack.push(currentState.get());
            currentState.set(pop);
            return pop;
        } else if (pop != null && pop.equals(currentState.get())) {
            return retreat();
        }
        return pop;
    }

    /**
     * put the current state in the history and advance to the given state. It
     * is a no-op if the current state is equal to the given state as determined
     * by invoking the equals method. Throws away any forward states.
     *
     * @param newState the new state to advance to
     *
     * @throws IllegalArgumentException if newState == null
     */
    synchronized public void advance(T newState) throws IllegalArgumentException {
        if (newState != null && Objects.equals(currentState.get(), newState) == false) {
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

    synchronized public void clear() {
        historyStack.clear();
        forwardStack.clear();
        currentState.set(null);
    }

    /**
     * A simple extension to SimpleListProperty to add a stack api
     *
     * TODO: this really should not extend SimpleListProperty but should
     * delegate to an appropriate observable implementation while implementing
     * the Deque interface
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
