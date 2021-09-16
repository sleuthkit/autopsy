/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.annotations.Beta;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An Autopsy Data Model item with an underlying Sleuth Kit Data Model object
 * that implements the Sleuth Kit Data Model's Content interface.
 *
 * @param <T> The type of the underlying Sleuth Kit Data Model object.
 */
@Beta
public class TskContentItem<T extends Content> {

    private final T content;

    /**
     * Constructs an Autopsy Data Model item with an underlying Sleuth Kit Data
     * Model object that implements the Sleuth Kit Data Model's Content
     * interface.
     *
     * @param content The Sleuth Kit Data Model object.
     *
     */
    @Beta
    TskContentItem(T content) {
        this.content = content;
    }

    /**
     * Gets the underlying Sleuth Kit Data Model object.
     *
     * @return The Sleuth Kit Data Model object.
     */
    @Beta
    public T getTskContent() {
        return content;
    }

    @Beta
    public Content getSourceContent() throws TskCoreException {
        return this.content.getParent();
    }
}
