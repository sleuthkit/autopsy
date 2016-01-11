/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 *
 */
@ThreadSafe
public class UndoRedoManager {

    @GuardedBy("this")
    private final ObservableStack<UndoableCommand> undoStack = new ObservableStack<>();

    @GuardedBy("this")
    private final ObservableStack<UndoableCommand> redoStack = new ObservableStack<>();

    synchronized public int getRedosAvaialable() {
        return redoStack.getSize();
    }

    synchronized public int getUndosAvailable() {
        return redoStack.getSize();
    }

    synchronized public ReadOnlyIntegerProperty redosAvailableProporty() {
        return redoStack.sizeProperty();
    }

    synchronized public ReadOnlyIntegerProperty undosAvailableProperty() {
        return undoStack.sizeProperty();
    }

    synchronized public void clear() {
        redoStack.clear();
        undoStack.clear();
    }

    /**
     * Flip the top redo command over to the undo stack, after applying it
     *
     * @return the redone command or null if there are no redos available
     */
    synchronized Optional<UndoableCommand> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        } else {
            UndoableCommand pop = redoStack.pop();
            undoStack.push(pop);
            pop.run();
            return Optional.of(pop);
        }

    }

    /**
     * Flip the top undo command over to the redo stack, after undoing it
     *
     * @return the undone command or null if there there are no undos available.
     */
    synchronized Optional<UndoableCommand> undo() {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        } else {
            final UndoableCommand pop = undoStack.pop();
            redoStack.push(pop);
            pop.undo();
            return Optional.of(pop);
        }
    }

    /**
     * push a new command onto the undo stack and clear the redo stack.
     *
     * Note: this method does not actually apply/execute the given command, only
     * record it in the undo stack.
     *
     * @param command the command to add to the undo stack
     */
    synchronized void addToUndo(@Nonnull UndoableCommand command) {
        Objects.requireNonNull(command);
        undoStack.push(command);
        redoStack.clear();
    }

    /**
     * A simple extension to SimpleListProperty to add a stack api
     *
     * TODO: this really should not extend SimpleListProperty but should
     * delegate to an appropriate observable implementation while implementing
     * the {@link Deque} interface
     */
    private static class ObservableStack<T> extends SimpleListProperty<T> {

        ObservableStack() {
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

    /**
     * Encapulates an operation and its inverse.
     *
     */
    public static interface UndoableCommand extends Runnable {

        /**
         * Execute this command
         */
        @Override
        void run();

        /**
         * Undo this command
         */
        void undo();
    }
}
