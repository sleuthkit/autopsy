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

import java.util.Deque;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

/** A simple extension to SimpleListProperty to add a stack api
 *
 * TODO: this really should not extend SimpleListProperty but should delegate an
 * appropriate observable implementation while implementing the {@link Deque}
 * interface */
public class ObservableStack<T> extends SimpleListProperty<T> {

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
