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

/**
 *
 */
final class TreeNodeComparator<T extends Comparable<T>> implements Comparator<TreeNode> {

    static final TreeNodeComparator<Long> UNCATEGORIZED_COUNT =
            new TreeNodeComparator<>("Uncategorized Count", TreeNode::getUncategorizedCount, String::valueOf, false);

    static final TreeNodeComparator<String> ALPHABETICAL =
            new TreeNodeComparator<>("Group Name", TreeNode::getGroupByValueDislpayName, String::valueOf, false);

    static final TreeNodeComparator<Long> HIT_COUNT =
            new TreeNodeComparator<>("Hit Count", TreeNode::getHashSetHitsCount, String::valueOf, true);

    static final TreeNodeComparator<Integer> FILE_COUNT =
            new TreeNodeComparator<>("Group Size", TreeNode::getSize, String::valueOf, true);

    static final TreeNodeComparator<Double> HIT_FILE_RATIO =
            new TreeNodeComparator<>("Hit Density", (treeNode) -> treeNode.getHashHitDensity(), density -> String.format("%.2f", density), true);

    private final static ImmutableList<TreeNodeComparator<?>> values = ImmutableList.of(UNCATEGORIZED_COUNT, ALPHABETICAL, HIT_COUNT, FILE_COUNT, HIT_FILE_RATIO);

    public static ImmutableList<TreeNodeComparator<?>> getValues() {
        return values;
    }

    private final Function<TreeNode, T> extractor;
    private final Function<T, String> valueFormatter;
    private final boolean orderReveresed;
    private final String displayName;

    private TreeNodeComparator(String displayName, Function<TreeNode, T> extractor, Function<T, String> formatter, boolean defaultOrderReversed) {
        this.displayName = displayName;
        this.extractor = extractor;
        this.orderReveresed = defaultOrderReversed;
        this.valueFormatter = formatter;
    }

    @Override
    public int compare(TreeNode o1, TreeNode o2) {
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

    String getFormattedValueOfTreeNode(TreeNode group) {
        return valueFormatter.apply(extractor.apply(group));
    }

}
