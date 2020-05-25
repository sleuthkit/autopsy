/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-17 Basis Technology Corp.
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

import java.util.Comparator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;

/**
 * Pseudo enum of possible properties to sort groups by.
 */
@NbBundle.Messages({
    "GroupSortBy.groupSize=Group Size",
    "GroupSortBy.groupName=Group Name",
    "GroupSortBy.none=None",
    "GroupSortBy.priority=Priority"})
public class GroupSortBy implements Comparator<DrawableGroup> {

    /**
     * sort the groups by the number of files in each
     */
    public final static GroupSortBy FILE_COUNT
            = new GroupSortBy(Bundle.GroupSortBy_groupSize(), "folder-open-image.png",
                    Comparator.comparing(DrawableGroup::getSize));

    /**
     * sort the groups by the natural order of the grouping value ( eg group
     * them by path alphabetically )
     */
    public final static GroupSortBy GROUP_BY_VALUE
            = new GroupSortBy(Bundle.GroupSortBy_groupName(), "folder-rename.png",
                    Comparator.comparing(DrawableGroup::getGroupByValueDislpayName));

    /**
     * don't sort the groups just use what ever order they come in (ingest
     * order)
     */
    public final static GroupSortBy NONE
            = new GroupSortBy(Bundle.GroupSortBy_none(), "prohibition.png",
                    new AllEqualComparator<>());

    /**
     * sort the groups by some priority metric to be determined and implemented
     */
    public final static GroupSortBy PRIORITY
            = new GroupSortBy(Bundle.GroupSortBy_priority(), "hashset_hits.png",
                    Comparator.comparing(DrawableGroup::getHashHitDensity).reversed());

    private final static ObservableList<GroupSortBy> values = FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(PRIORITY, NONE, GROUP_BY_VALUE, FILE_COUNT));

    /**
     * get a list of the values of this enum
     *
     * @return
     */
    public static ObservableList<GroupSortBy> getValues() {
        return values;
    }

    final private String displayName;

    private Image icon;

    private final String imageName;

    private final Comparator<DrawableGroup> delegate;

    private GroupSortBy(String displayName, String imagePath, Comparator<DrawableGroup> internalComparator) {
        this.displayName = displayName;
        this.imageName = imagePath;
        this.delegate = internalComparator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Image getIcon() {
        if (icon == null) {
            if (StringUtils.isBlank(imageName) == false) {
                this.icon = new Image("org/sleuthkit/autopsy/imagegallery/images/" + imageName, true); //NON-NLS
            }
        }
        return icon;
    }

    @Override
    public int compare(DrawableGroup o1, DrawableGroup o2) {
        return delegate.compare(o1, o2);
    }

    static class AllEqualComparator<A> implements Comparator<A> {

        @Override
        public int compare(A o1, A o2) {
            return 0;
        }
    }
}
