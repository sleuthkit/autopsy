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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import java.util.Collection;
import java.util.Collections;
import javax.annotation.concurrent.Immutable;

/**
 * Event broadcast to various UI componenets when one or more files' category
 * has been changed
 */
@Immutable
public class CategoryChangeEvent {

    private final Collection<Long> fileIDs;
    private final Category newCategory;

    public CategoryChangeEvent(Collection<Long> fileIDs, Category newCategory) {
        this.fileIDs = fileIDs;
        this.newCategory = newCategory;
    }

    public Category getNewCategory() {
        return newCategory;
    }

    /**
     * @return the fileIDs of the files whose categories have changed
     */
    public Collection<Long> getFileIDs() {
        return Collections.unmodifiableCollection(fileIDs);
    }
}
