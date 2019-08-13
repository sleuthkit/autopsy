/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.textclassifier;

import opennlp.tools.util.ObjectStream;

import java.io.IOException;
import java.util.List;

/**
 * A very simple implementation of the ObjectStream interface that is backed by
 * a list. This is intended to make tests easier. I don't see a need for it in
 * production at this time.
 *
 * @param <T> For the document classification task, this is DocumentSample.
 */
public class ListObjectStream<T> implements ObjectStream<T> {

    private List<T> items;
    private int i;

    public ListObjectStream(List<T> items) {
        this.items = items;
        this.i = 0;
    }

    /**
     * Returns the next object. Calling this method repeatedly until it returns
     * null will return each object from the underlying source exactly once.
     *
     * @return the next object or null to signal that the stream is exhausted
     *
     * @throws java.io.IOException if there is an error during reading
     */
    @Override
    public T read() throws IOException {
        if (i < items.size()) {
            T item = items.get(i);
            i++;
            return item;
        } else {
            return null;
        }
    }

    /**
     * Repositions the stream at the beginning and the previously seen object
     * sequence will be repeated exactly. This method can be used to re-read the
     * stream if multiple passes over the objects are required.
     *
     * The implementation of this method is optional.
     *
     * @throws IOException if there is an error during reseting the stream
     */
    @Override
    public void reset() throws IOException, UnsupportedOperationException {
        i = 0;
    }

    /**
     * Closes the <code>ObjectStream</code> and releases all allocated
     * resources. After close was called its not allowed to call read or reset.
     *
     * @throws IOException if there is an error during closing the stream
     */
    @Override
    public void close() throws IOException {
        items = null;
        i = 0;
    }

    public int size() {
        return items.size();
    }
}
