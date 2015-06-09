/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TreeItem;

/**
 *
 */
enum TreeNodeComparators implements Comparator<TreeItem<TreeNode>>, NonNullCompareable {

    ALPHABETICAL("Group Name") {
                @Override
        public int nonNullCompare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2) {

                    return o1.getValue().getGroup().groupKey.getValue().toString().compareTo(o2.getValue().getGroup().groupKey.getValue().toString());
                }
            },
    HIT_COUNT("Hit Count") {
                @Override
                public int nonNullCompare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2) {

                    return -Long.compare(o1.getValue().getGroup().getHashSetHitsCount(), o2.getValue().getGroup().getHashSetHitsCount());
                }
            },
    FILE_COUNT("Group Size") {
                @Override
                public int nonNullCompare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2) {

                    return -Integer.compare(o1.getValue().getGroup().getSize(), o2.getValue().getGroup().getSize());
                }
            },
    HIT_FILE_RATIO("Hit Density") {
                @Override
                public int nonNullCompare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2) {

                    return -Double.compare(o1.getValue().getGroup().getHashSetHitsCount() / (double) o1.getValue().getGroup().getSize(),
                            o2.getValue().getGroup().getHashSetHitsCount() / (double) o2.getValue().getGroup().getSize());
                }
            };

    @Override
    public int compare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2) {
        if (o1.getValue().getGroup() == null) {
            if (o1.getValue().getGroup() == null) {
                return 0;
            }
            return 1;
        } else if (o2.getValue().getGroup() == null) {
            return -1;
        }
        return nonNullCompare(o1, o2);
    }
    final private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    private TreeNodeComparators(String displayName) {
        this.displayName = displayName;
    }

    public static class ComparatorListCell extends ListCell<TreeNodeComparators> {

        @Override
        protected void updateItem(TreeNodeComparators t, boolean bln) {
            super.updateItem(t, bln);
            if (t != null) {
                setText(t.getDisplayName());
            }
        }
    }
}

interface NonNullCompareable {

    int nonNullCompare(TreeItem<TreeNode> o1, TreeItem<TreeNode> o2);
}
