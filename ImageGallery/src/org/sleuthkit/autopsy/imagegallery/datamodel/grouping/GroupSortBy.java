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

import java.util.Arrays;
import java.util.Comparator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javax.swing.SortOrder;
import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;

/**
 * enum of possible properties to sort groups by. This is the model for the drop
 * down in Toolbar as well as each enum value having the stategy
 * ({@link  Comparator}) for sorting the groups
 */
public enum GroupSortBy implements ComparatorProvider {

    /**
     * sort the groups by the number of files in each sort the groups by the
     * number of files in each
     */
    FILE_COUNT("Group Size", true, "folder-open-image.png") {
                @Override
                public Comparator<DrawableGroup> getGrpComparator(final SortOrder sortOrder) {
                    return applySortOrder(sortOrder, Comparator.comparingInt(DrawableGroup::getSize));
                }

                @Override
                public <A extends Comparable<A>> Comparator<A> getValueComparator(final DrawableAttribute<A> attr, final SortOrder sortOrder) {
                    return getDefaultValueComparator(attr, sortOrder);
                }
            },
    /**
     * sort the groups by the natural order of the grouping value ( eg group
     * them by path alphabetically )
     */
    GROUP_BY_VALUE("Group Name", true, "folder-rename.png") {
                @Override
                public Comparator<DrawableGroup> getGrpComparator(final SortOrder sortOrder) {
                    return applySortOrder(sortOrder, Comparator.comparing(t -> t.getGroupByValueDislpayName()));
                }

                @Override
                public <A extends Comparable<A>> Comparator<A> getValueComparator(final DrawableAttribute<A> attr, final SortOrder sortOrder) {
                    return applySortOrder(sortOrder, Comparator.<A>naturalOrder());
                }
            },
    /**
     * don't sort the groups just use what ever order they come in (ingest
     * order)
     */
    NONE("None", false, "prohibition.png") {
                @Override
                public Comparator<DrawableGroup> getGrpComparator(SortOrder sortOrder) {
                    return new NoOpComparator<>();
                }

                @Override
                public <A extends Comparable<A>> Comparator<A> getValueComparator(DrawableAttribute<A> attr, final SortOrder sortOrder) {
                    return new NoOpComparator<>();
                }
            },
    /**
     * sort the groups by some priority metric to be determined and implemented
     */
    PRIORITY("Priority", false, "hashset_hits.png") {
                @Override
                public Comparator<DrawableGroup> getGrpComparator(SortOrder sortOrder) {
                    return Comparator.nullsLast(Comparator.comparingDouble(DrawableGroup::getHashHitDensity).thenComparingInt(DrawableGroup::getSize).reversed());
                }

                @Override
                public <A extends Comparable<A>> Comparator<A> getValueComparator(DrawableAttribute<A> attr, SortOrder sortOrder) {
                    return getDefaultValueComparator(attr, sortOrder);
                }
            };

    /**
     * get a list of the values of this enum
     *
     * @return
     */
    public static ObservableList<GroupSortBy> getValues() {
        return FXCollections.observableArrayList(Arrays.asList(values()));

    }

    final private String displayName;

    private Image icon;

    private final String imageName;

    private final Boolean sortOrderEnabled;

    private GroupSortBy(String displayName, Boolean sortOrderEnabled, String imagePath) {
        this.displayName = displayName;
        this.sortOrderEnabled = sortOrderEnabled;
        this.imageName = imagePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Image getIcon() {
        if (icon == null) {
            if (StringUtils.isBlank(imageName) == false) {
                this.icon = new Image("org/sleuthkit/autopsy/imagegallery/images/" + imageName, true);
            }
        }
        return icon;
    }

    public Boolean isSortOrderEnabled() {
        return sortOrderEnabled;
    }

    private static <T> Comparator<T> applySortOrder(final SortOrder sortOrder, Comparator<T> comparator) {
        switch (sortOrder) {
            case ASCENDING:
                return comparator;
            case DESCENDING:
                return comparator.reversed();
            case UNSORTED:
            default:
                return new NoOpComparator<>();
        }
    }

    private static class NoOpComparator<A> implements Comparator<A> {

        @Override
        public int compare(A o1, A o2) {
            return 0;
        }
    }

}

/**
 * * implementers of this interface must provide a method to compare
 * ({@link Comparable}) values and Groupings based on an
 * {@link DrawableAttribute} and a {@link SortOrder}
 */
interface ComparatorProvider {

    <A extends Comparable<A>> Comparator<A> getValueComparator(DrawableAttribute<A> attr, SortOrder sortOrder);

    Comparator<DrawableGroup> getGrpComparator(SortOrder sortOrder);

    default <A extends Comparable<A>> Comparator<A> getDefaultValueComparator(DrawableAttribute<A> attr, SortOrder sortOrder) {
        return (A v1, A v2) -> {
            DrawableGroup g1 = ImageGalleryController.getDefault().getGroupManager().getGroupForKey(new GroupKey<>(attr, v1));
            DrawableGroup g2 = ImageGalleryController.getDefault().getGroupManager().getGroupForKey(new GroupKey<>(attr, v2));

            return getGrpComparator(sortOrder).compare(g1, g2);
        };
    }
}
