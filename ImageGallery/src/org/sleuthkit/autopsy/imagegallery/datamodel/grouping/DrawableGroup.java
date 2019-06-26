/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019  Basis Technology Corp.
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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.HashSetManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a set of image/video files in a group. The UI listens to changes
 * to the group membership and updates itself accordingly.
 */
public class DrawableGroup implements Comparable<DrawableGroup> {

    private static final Logger logger = Logger.getLogger(DrawableGroup.class.getName());

    public static String getBlankGroupName() {
        return "unknown";
    }

    private final GroupKey<?> groupKey;
    private final ObservableList<Long> fileIDs = FXCollections.observableArrayList();
    private final ObservableList<Long> unmodifiableFileIDS = FXCollections.unmodifiableObservableList(fileIDs);

    //cache the number of files in this groups with hashset hits
    private final ReadOnlyLongWrapper hashSetHitsCount = new ReadOnlyLongWrapper(-1);
    //cache the number ofuncategorized files in this group
    private final ReadOnlyLongWrapper uncatCount = new ReadOnlyLongWrapper(-1);
    //cache the hash hit density for this group
    private final DoubleBinding hashDensity = hashSetHitsCount.multiply(100d).divide(Bindings.size(fileIDs));
    //cache if this group has been seen
    private final ReadOnlyBooleanWrapper seen = new ReadOnlyBooleanWrapper(false);

    private final DrawableDB drawableDb;
    private final HashSetManager hashSetManager;

    DrawableGroup(GroupKey<?> groupKey, Set<Long> filesInGroup, boolean seen, DrawableDB drawableDb, HashSetManager hashSetManager) {
        this.groupKey = groupKey;
        this.fileIDs.setAll(filesInGroup);
        this.seen.set(seen);
        this.drawableDb = drawableDb;
        this.hashSetManager = hashSetManager;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableList<Long> getFileIDs() {
        return unmodifiableFileIDS;
    }

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

    public synchronized int getSize() {
        return fileIDs.size();
    }

    public IntegerBinding sizeProperty() {
        return Bindings.size(fileIDs);
    }

    public double getHashHitDensity() {
        getHashSetHitsCount(); //initialize hashSetHitsCount
        return hashDensity.get();
    }

    public DoubleBinding hashHitDensityProperty() {
        getHashSetHitsCount(); //initialize hashSetHitsCount
        return hashDensity;
    }

    /**
     * @return the number of files in this group that have hash set hits
     */
    public synchronized long getHashSetHitsCount() {
        if (hashSetHitsCount.get() < 0) {
            hashSetHitsCount.set(fileIDs.stream()
                    .map(hashSetManager::isInAnyHashSet)
                    .filter(Boolean::booleanValue)
                    .count());
        }
        return hashSetHitsCount.get();
    }

    public ReadOnlyLongProperty hashSetHitsCountProperty() {
        getHashSetHitsCount(); //initialize hashSetHitsCount
        return hashSetHitsCount.getReadOnlyProperty();
    }

    public final synchronized long getUncategorizedCount() {
        if (uncatCount.get() < 0) {
            try {
                uncatCount.set(drawableDb.getUncategorizedCount(fileIDs));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Failed to get the uncategorized files count for DrawableGroup with GroupKey: %s ", this.groupKey), ex); //NON-NLS
            }
        }
        return uncatCount.get();
    }

    public ReadOnlyLongProperty uncatCountProperty() {
        getUncategorizedCount(); //initialize uncatCount
        return uncatCount.getReadOnlyProperty();

    }

    void setSeen(boolean isSeen) {
        this.seen.set(isSeen);
    }

    public boolean isSeen() {
        return seen.get();
    }

    public ReadOnlyBooleanProperty seenProperty() {
        return seen.getReadOnlyProperty();
    }

    @Subscribe
    public synchronized void handleCatChange(CategoryManager.CategoryChangeEvent event) {
        if (Iterables.any(event.getFileIDs(), fileIDs::contains)) {
            uncatCount.set(-1);
        }
    }

    synchronized void addFile(Long f) {
        if (fileIDs.contains(f) == false) {
            fileIDs.add(f);
        }
        // invalidate no matter what because the file could have new hash hits, etc.
        invalidateProperties(true);
    }

    synchronized void setFiles(Set<? extends Long> newFileIds) {
        fileIDs.removeIf(fileID -> newFileIds.contains(fileID) == false);
        invalidateProperties(false);
        newFileIds.stream().forEach(this::addFile);
    }

    synchronized void removeFile(Long f) {
        if (fileIDs.contains(f)) {
            fileIDs.removeAll(f);
            invalidateProperties(false);
        }
    }

    private void invalidateProperties(boolean seenChanged) {
        if (seenChanged) {
            seen.set(false);
        }
        uncatCount.set(-1);
        hashSetHitsCount.set(-1);
    }

    @Override
    public String toString() {
        return "Grouping{ keyProp=" + groupKey + '}'; //NON-NLS
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
        return Objects.equals(this.getGroupKey(), ((DrawableGroup) obj).getGroupKey());
    }

    // By default, sort by group key name
    @Override
    public int compareTo(DrawableGroup other) {
        return getGroupByValueDislpayName().compareTo(other.getGroupByValueDislpayName());
    }
}
