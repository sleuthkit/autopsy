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
package org.sleuthkit.autopsy.imageanalyzer;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.Immutable;

@Immutable
public class FileUpdateEvent {

    private final Set<Long> updatedFiles;

    private final DrawableAttribute changedAttribute;

    private final UpdateType updateType;

    public UpdateType getUpdateType() {
        return updateType;
    }

    public Collection<Long> getUpdatedFiles() {
        return updatedFiles;
    }

    public DrawableAttribute getChangedAttribute() {
        return changedAttribute;
    }

    public FileUpdateEvent(Collection<? extends Long> updatedFiles, DrawableAttribute changedAttribute, UpdateType updateType) {
        this.updatedFiles = new HashSet<>(updatedFiles);
        this.changedAttribute = changedAttribute;
        this.updateType = updateType;
    }

    public FileUpdateEvent(Collection<? extends Long> updatedFiles, DrawableAttribute changedAttribute) {
        this.updatedFiles = new HashSet<>(updatedFiles);
        this.changedAttribute = changedAttribute;
        this.updateType = UpdateType.FILE_UPDATED;
    }

    public FileUpdateEvent(Collection<? extends Long> updatedFiles) {
        this.updatedFiles = new HashSet<>(updatedFiles);
        changedAttribute = null;
        this.updateType = UpdateType.FILE_UPDATED;
    }

    static public enum UpdateType {

        FILE_UPDATED, FILE_REMOVED;
    }
}
