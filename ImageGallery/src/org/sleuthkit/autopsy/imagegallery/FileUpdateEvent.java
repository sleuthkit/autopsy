/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.util.Collection;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;

/** represents a change in the database for one or more files. */
@Immutable
public class FileUpdateEvent {

    /** the obj_ids of affected files */
    private final Set<Long> fileIDs;

    /** the attribute that was modified */
    private final DrawableAttribute<?> changedAttribute;

    /** the type of update ( updated/removed) */
    private final UpdateType updateType;

    public UpdateType getUpdateType() {
        return updateType;
    }

    public Collection<Long> getFileIDs() {
        return fileIDs;
    }

    public DrawableAttribute<?> getChangedAttribute() {
        return changedAttribute;
    }

    public static FileUpdateEvent newRemovedEvent(Collection<? extends Long> updatedFiles) {
        return new FileUpdateEvent(updatedFiles, UpdateType.REMOVE, null);
    }

    /**
     *
     * @param updatedFiles     the files that have been added or changed in the
     *                         database
     * @param changedAttribute the attribute that was changed for the files, or
     *                         null if this represents new files
     *
     * @return a new FileUpdateEvent
     */
    public static FileUpdateEvent newUpdateEvent(Collection<? extends Long> updatedFiles, DrawableAttribute<?> changedAttribute) {
        return new FileUpdateEvent(updatedFiles, UpdateType.UPDATE, changedAttribute);
    }

    private FileUpdateEvent(Collection<? extends Long> updatedFiles, UpdateType updateType, DrawableAttribute<?> changedAttribute) {
        this.fileIDs = new HashSet<>(updatedFiles);
        this.updateType = updateType;
        this.changedAttribute = changedAttribute;
    }

    static public enum UpdateType {

        /** files have been added or updated in the db */
        UPDATE,
        /** files have been removed
         * from the db */
        REMOVE;
    }

    /** Interface for listening to FileUpdateEvents */
    public static interface FileUpdateListener extends EventListener {

        public void handleFileUpdate(FileUpdateEvent evt);
    }
}
