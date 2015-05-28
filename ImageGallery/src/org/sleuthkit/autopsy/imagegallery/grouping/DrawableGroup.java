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
package org.sleuthkit.autopsy.imagegallery.grouping;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;

/**
 * Represents a set of image/video files in a group. The UI listens to changes
 * to the group membership and updates itself accordingly.
 */
public class DrawableGroup implements Comparable<DrawableGroup> {

    private static final Logger LOGGER = Logger.getLogger(DrawableGroup.class.getName());

    /**
     * the string to use when the groupkey is 'empty'
     */
    public static final String UNKNOWN = "unknown";

    private final ObservableList<Long> fileIDs = FXCollections.observableArrayList();

    //cache the number of files in this groups with hashset hits
    private int filesWithHashSetHitsCount = -1;

    synchronized public ObservableList<Long> fileIds() {
        return fileIDs;
    }

    final public GroupKey<?> groupKey;

    DrawableGroup(GroupKey<?> groupKey, List<Long> filesInGroup) {
        this.groupKey = groupKey;
        fileIDs.setAll(filesInGroup);
    }

    synchronized public int getSize() {
        return fileIDs.size();
    }

    public double getHashHitDensity() {
        return getFilesWithHashSetHitsCount() / (double) getSize();
    }

    /**
     * Call to indicate that an image has been added or removed from the group,
     * so the hash counts may not longer be accurate.
     */
    synchronized public void invalidateHashSetHitsCount() {
        filesWithHashSetHitsCount = -1;
    }

    synchronized public int getFilesWithHashSetHitsCount() {
        //TODO: use the drawable db for this ? -jm
        if (filesWithHashSetHitsCount < 0) {
            filesWithHashSetHitsCount = 0;
            for (Long fileID : fileIds()) {

                try {
                    if (ImageGalleryController.getDefault().getDatabase().isInHashSet(fileID)) {
                        filesWithHashSetHitsCount++;
                    }
                } catch (IllegalStateException | NullPointerException ex) {
                    LOGGER.log(Level.WARNING, "could not access case during getFilesWithHashSetHitsCount()");
                    break;
                }
            }
        }
        return filesWithHashSetHitsCount;
    }

    @Override
    public String toString() {
        return "Grouping{ keyProp=" + groupKey + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.groupKey);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return Objects.equals(this.groupKey,
                ((DrawableGroup) obj).groupKey);
    }

    synchronized public void addFile(Long f) {
        invalidateHashSetHitsCount();
        if (fileIDs.contains(f) == false) {
            fileIDs.add(f);
        }
    }

    synchronized public void removeFile(Long f) {
        invalidateHashSetHitsCount();
        fileIDs.removeAll(f);
    }

    // By default, sort by group key name
    @Override
    public int compareTo(DrawableGroup other) {
        return this.groupKey.getValueDisplayName().compareTo(other.groupKey.getValueDisplayName());
    }
}
