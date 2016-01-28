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

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.function.Function;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

/**
 *
 */
final class GroupComparators<T extends Comparable<T>> implements Comparator<DrawableGroup> {

    static final GroupComparators<Long> UNCATEGORIZED_COUNT =
            new GroupComparators<>(NbBundle.getMessage(GroupComparators.class, "GroupComparators.uncategorizedCount.txt"), DrawableGroup::getUncategorizedCount, String::valueOf, false);

    static final GroupComparators<String> ALPHABETICAL =
            new GroupComparators<>(NbBundle.getMessage(GroupComparators.class, "GroupComparators.groupName.txt"), DrawableGroup::getGroupByValueDislpayName, String::valueOf, false);

    static final GroupComparators<Long> HIT_COUNT =
            new GroupComparators<>(NbBundle.getMessage(GroupComparators.class, "GroupComparators.hitCount.txt"), DrawableGroup::getHashSetHitsCount, String::valueOf, true);

    static final GroupComparators<Integer> FILE_COUNT =
            new GroupComparators<>(NbBundle.getMessage(GroupComparators.class, "GroupComparators.groupSize.txt"), DrawableGroup::getSize, String::valueOf, true);

    static final GroupComparators<Double> HIT_FILE_RATIO =
            new GroupComparators<>(NbBundle.getMessage(GroupComparators.class, "GroupComparators.hitDensity.txt"), DrawableGroup::getHashHitDensity, density -> String.format("%.2f", density) + "%", true); // NON-NLS

    private final static ImmutableList<GroupComparators<?>> values = ImmutableList.of(UNCATEGORIZED_COUNT, ALPHABETICAL, HIT_COUNT, FILE_COUNT, HIT_FILE_RATIO);

    public static ImmutableList<GroupComparators<?>> getValues() {
        return values;
    }

    private final Function<DrawableGroup, T> extractor;
    private final Function<T, String> valueFormatter;
    private final boolean orderReveresed;
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
