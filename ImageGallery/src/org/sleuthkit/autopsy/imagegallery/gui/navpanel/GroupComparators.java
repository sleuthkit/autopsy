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
package org.sleuthkit.autopsy.imagegallery.gui.navpanel;

import java.util.Comparator;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

@NbBundle.Messages({"GroupComparators.uncategorizedCount=Uncategorized Count",
    "GroupComparators.groupName=Group Name",
    "GroupComparators.hitCount=Hit Count",
    "GroupComparators.groupSize=Group Size",
    "GroupComparators.hitDensity=Hit Density"})
final class GroupComparators<T extends Comparable<T>> implements Comparator<DrawableGroup> {

    static final GroupComparators<Long> UNCATEGORIZED_COUNT =
            new GroupComparators<>(Bundle.GroupComparators_uncategorizedCount(), DrawableGroup::getUncategorizedCount, String::valueOf, false);

    static final GroupComparators<String> ALPHABETICAL =
            new GroupComparators<>(Bundle.GroupComparators_groupName(), DrawableGroup::getGroupByValueDislpayName, String::valueOf, false);

    static final GroupComparators<Long> HIT_COUNT =
            new GroupComparators<>(Bundle.GroupComparators_hitCount(), DrawableGroup::getHashSetHitsCount, String::valueOf, true);

    static final GroupComparators<Integer> FILE_COUNT =
            new GroupComparators<>(Bundle.GroupComparators_groupSize(), DrawableGroup::getSize, String::valueOf, true);

    static final GroupComparators<Double> HIT_FILE_RATIO =
            new GroupComparators<>(Bundle.GroupComparators_hitDensity(), DrawableGroup::getHashHitDensity, density -> String.format("%.2f", density) + "%", true); //NON-NLS

    private final static ObservableList<GroupComparators<?>> values = FXCollections.observableArrayList(UNCATEGORIZED_COUNT, ALPHABETICAL, HIT_COUNT, FILE_COUNT, HIT_FILE_RATIO);

    public static ObservableList<GroupComparators<?>> getValues() {
        return FXCollections.unmodifiableObservableList(values);
    }

    private final Function<DrawableGroup, T> extractor;
    private final Function<T, String> valueFormatter;
    private final boolean orderReveresed;

     boolean isOrderReveresed() {
        return orderReveresed;
    }
    private final String displayName;

    private GroupComparators(String displayName, Function<DrawableGroup, T> extractor, Function<T, String> formatter, boolean defaultOrderReversed) {
        this.displayName = displayName;
        this.extractor = extractor;
        this.orderReveresed = defaultOrderReversed;
        this.valueFormatter = formatter;
    }

    @Override
    public int compare(DrawableGroup o1, DrawableGroup o2) {
        int compareTo = extractor.apply(o1).compareTo(extractor.apply(o2));
        return orderReveresed ? -compareTo : compareTo;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    String getFormattedValueOfGroup(DrawableGroup group) {
        return valueFormatter.apply(extractor.apply(group));
    }
}
