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
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;

/**
 *
 */
@ThreadSafe
public class UndoRedoManager {

    @GuardedBy("this")
    private final ObservableStack<Command> undoStack = new ObservableStack<>();

    @GuardedBy("this")
    private final ObservableStack<Command> redoStack = new ObservableStack<>();

    private final ImageGalleryController controller;

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

    public UndoRedoManager(ImageGalleryController controller) {
        this.controller = controller;
    }

    synchronized public void clear() {
        redoStack.clear();
        undoStack.clear();
    }

    /**
     * Flip the top redo command over to the undo stack
     *
     * @return the redone command or null if there are no redos available
     */
    synchronized public Optional<Command> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        } else {
            Command pop = redoStack.pop();
            undoStack.push(pop);
            pop.apply(controller);
            return Optional.of(pop);
        }

    }

    /**
     * Flip the top undo command over to the redo stack
     *
     * @return the undone command or null if there there are no undos available.
     */
    synchronized public Optional<Command> undo() {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        } else {
            final Command pop = undoStack.pop();
            redoStack.push(pop);
            pop.undo(controller);
            return Optional.of(pop);
        }
    }

    /**
     * push a new command onto the undo stack and clear the redo stack
     *
     * @param command the command to add to the undo stack
     */
    synchronized public void addToUndo(@Nonnull Command command) {
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
}
