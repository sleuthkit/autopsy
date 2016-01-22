/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel.grouping;

import com.google.common.collect.Iterables;
import com.google.common.eventbus.Subscribe;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;

/**
 * Represents a set of image/video files in a group. The UI listens to changes
 * to the group membership and updates itself accordingly.
 */
public class DrawableGroup implements Comparable<DrawableGroup> {

    private static final Logger LOGGER = Logger.getLogger(DrawableGroup.class.getName());

    public static String getBlankGroupName() {
        return "unknown";
    }

    private final ObservableList<Long> fileIDs = FXCollections.observableArrayList();
    private final ObservableList<Long> unmodifiableFileIDS = FXCollections.unmodifiableObservableList(fileIDs);

    //cache the number of files in this groups with hashset hits
    private final ReadOnlyLongWrapper hashSetHitsCount = new ReadOnlyLongWrapper(-1);

    public ReadOnlyLongProperty hashSetHitsCountProperty() {
        return hashSetHitsCount.getReadOnlyProperty();
    }
    private final ReadOnlyBooleanWrapper seen = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyLongWrapper uncatCount = new ReadOnlyLongWrapper(-1);

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    synchronized public ObservableList<Long> fileIds() {
        return unmodifiableFileIDS;
    }

    final public GroupKey<?> groupKey;

    public GroupKey<?> getGroupKey() {
        return groupKey;
    }

    public DrawableAttribute<?> getGroupByAttribute() {
        return groupKey.getAttribute();
    }

    public Object getGroupByValue() {
        return groupKey.getValue();
    }

    public String getGroupByValueDislpayName() {
        return groupKey.getValueDisplayName();
    }

    DrawableGroup(GroupKey<?> groupKey, Set<Long> filesInGroup, boolean seen) {
        this.groupKey = groupKey;
        this.fileIDs.setAll(filesInGroup);
        fileIDs.addListener((Observable observable) -> {
            hashSetHitsCount.set(-1);
            DrawableGroup.this.seen.set(false);
        });
        this.seen.set(seen);
        getUncategorizedCount();
    }

    synchronized public int getSize() {
        return fileIDs.size();
    }

    public IntegerBinding sizeProperty() {
        return Bindings.size(fileIDs);
    }

    public double getHashHitDensity() {
        return hashSetHitsCountProperty().divide((double) getSize()).get();
    }

    synchronized private void invalidateUncatCount() {
        uncatCount.set(-1);
    }

    /**
     * @return the number of files in this group that have hash set hits
     */
    synchronized public long getHashSetHitsCount() {
        if (hashSetHitsCount.get() < 0) {
            try {
                hashSetHitsCount.set(fileIDs.stream()
                        .map(fileID -> ImageGalleryController.getDefault().getHashSetManager().isInAnyHashSet(fileID))
                        .filter(Boolean::booleanValue)
                        .count());
            } catch (IllegalStateException | NullPointerException ex) {
                LOGGER.log(Level.WARNING, "could not access case during getFilesWithHashSetHitsCount()");
            }
        }

        return hashSetHitsCount.get();
    }

    final synchronized public long getUncategorizedCount() {
        if (uncatCount.get() < 0) {
            try {
                uncatCount.set(ImageGalleryController.getDefault().getDatabase().getCategoryCount(Category.ZERO, fileIDs));

            } catch (IllegalStateException | NullPointerException ex) {
                LOGGER.log(Level.WARNING, "could not access case during getFilesWithHashSetHitsCount()");
            }
        }

        return uncatCount.get();
    }

    public ReadOnlyLongProperty uncatCountProperty() {
        return uncatCount.getReadOnlyProperty();
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

    synchronized void addFile(Long f) {
        invalidateUncatCount();
        if (fileIDs.contains(f) == false) {
            fileIDs.add(f);
        }
    }

    synchronized void setFiles(Set<? extends Long> newFileIds) {
        fileIDs.removeIf((Long t) -> newFileIds.contains(t) == false);

        for (Long f : newFileIds) {
            if (fileIDs.contains(f) == false) {
                fileIDs.add(f);
                seen.set(false);
            }
        }
    }

    synchronized void removeFile(Long f) {
        fileIDs.removeAll(f);
        invalidateUncatCount();
    }

    // By default, sort by group key name
    @Override
    public int compareTo(DrawableGroup other) {
        return this.groupKey.getValueDisplayName().compareTo(other.groupKey.getValueDisplayName());
    }

    void setSeen(boolean isSeen) {
        this.seen.set(isSeen);
    }

    public ReadOnlyBooleanWrapper seenProperty() {
        return seen;
    }

    public boolean isSeen() {
        return seen.get();
    }
    @Subscribe
    synchronized public void handleCatChange(CategoryManager.CategoryChangeEvent event) {
        if (Iterables.any(event.getFileIDs(), fileIDs::contains)) {
            invalidateUncatCount();
        }
    }
}
