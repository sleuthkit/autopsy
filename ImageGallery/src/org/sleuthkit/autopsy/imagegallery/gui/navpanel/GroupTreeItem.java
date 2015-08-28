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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

/**
 * A node in the nav/hash tree. Manages inserts and removals. Has parents and
 * children. Does not have graphical properties these are configured in
 * {@link GroupTreeCell}. Each GroupTreeItem has a TreeNode which has a path
 * segment and may or may not have a group
 */
class GroupTreeItem extends TreeItem<TreeNode> implements Comparable<GroupTreeItem> {

    static GroupTreeItem getTreeItemForGroup(GroupTreeItem root, DrawableGroup grouping) {
        if (Objects.equals(root.getValue().getGroup(), grouping)) {
            return root;
        } else {
            synchronized (root.getChildren()) {
                for (TreeItem<TreeNode> child : root.getChildren()) {
                    final GroupTreeItem childGTI = (GroupTreeItem) child;

                    GroupTreeItem val = getTreeItemForGroup(childGTI, grouping);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }
        return null;
    }

    /**
     * maps a path segment to the child item of this item with that path segment
     */
    private final Map<String, GroupTreeItem> childMap = new HashMap<>();
    /**
     * the comparator if any used to sort the children of this item
     */
    private TreeNodeComparators comp;

    public GroupTreeItem(String t, DrawableGroup g, TreeNodeComparators comp) {
        super(new TreeNode(t, g));
        this.comp = comp;
    }

    /**
     * Returns the full absolute path of this level in the tree
     *
     * @return the full absolute path of this level in the tree
     */
    public String getAbsolutePath() {
        if (getParent() != null) {
            return ((GroupTreeItem) getParent()).getAbsolutePath() + getValue().getPath() + "/";
        } else {
            return getValue().getPath() + "/";
        }
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param path Full path (or subset not yet added) to add
     * @param g    Group to add
     * @param tree True if it is part of a tree (versus a list)
     */
    void insert(String path, DrawableGroup g, Boolean tree) {
        if (tree) {
            String cleanPath = StringUtils.stripStart(path, "/");

            // get the first token
            String prefix = StringUtils.substringBefore(cleanPath, "/");

            // Are we at the end of the recursion?
            if ("".equals(prefix)) {
                getValue().setGroup(g);
            } else {
                GroupTreeItem prefixTreeItem = childMap.get(prefix);
                if (prefixTreeItem == null) {
                    final GroupTreeItem newTreeItem = new GroupTreeItem(prefix, null, comp);

                    prefixTreeItem = newTreeItem;
                    childMap.put(prefix, prefixTreeItem);
                    Platform.runLater(() -> {
                        synchronized (getChildren()) {
                            getChildren().add(newTreeItem);
                        }
                    });

                }

                // recursively go into the path
                prefixTreeItem.insert(StringUtils.stripStart(cleanPath, prefix), g, tree);
            }
        } else {
            GroupTreeItem treeItem = childMap.get(path);
            if (treeItem == null) {
                final GroupTreeItem newTreeItem = new GroupTreeItem(path, g, comp);
                newTreeItem.setExpanded(true);
                childMap.put(path, newTreeItem);

                Platform.runLater(() -> {
                    synchronized (getChildren()) {
                        getChildren().add(newTreeItem);
                        if (comp != null) {
                            FXCollections.sort(getChildren(), comp);
                        }
                    }
                });

            }
        }
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param path Full path (or subset not yet added) to add
     * @param g    Group to add
     * @param tree True if it is part of a tree (versus a list)
     */
    void insert(List<String> path, DrawableGroup g, Boolean tree) {
        if (tree) {
            // Are we at the end of the recursion?
            if (path.isEmpty()) {
                getValue().setGroup(g);
            } else {
                String prefix = path.get(0);

                GroupTreeItem prefixTreeItem = childMap.get(prefix);
                if (prefixTreeItem == null) {
                    final GroupTreeItem newTreeItem = new GroupTreeItem(prefix, null, comp);

                    prefixTreeItem = newTreeItem;
                    childMap.put(prefix, prefixTreeItem);

                    Platform.runLater(() -> {
                        synchronized (getChildren()) {
                            getChildren().add(newTreeItem);
                        }
                    });

                }

                // recursively go into the path
                prefixTreeItem.insert(path.subList(1, path.size()), g, tree);
            }
        } else {
            //flat list
            GroupTreeItem treeItem = childMap.get(StringUtils.join(path, "/"));
            if (treeItem == null) {
                final GroupTreeItem newTreeItem = new GroupTreeItem(StringUtils.join(path, "/"), g, comp);
                newTreeItem.setExpanded(true);
                childMap.put(path.get(0), newTreeItem);

                Platform.runLater(() -> {
                    synchronized (getChildren()) {
                        getChildren().add(newTreeItem);
                        if (comp != null) {
                            FXCollections.sort(getChildren(), comp);
                        }
                    }
                });
            }
        }
    }

    @Override
    public int compareTo(GroupTreeItem o) {
        return comp.compare(this, o);
    }

    GroupTreeItem getTreeItemForPath(List<String> path) {
        // end of recursion
        if (path.isEmpty()) {
            return this;
        } else {
            synchronized (getChildren()) {
                String prefix = path.get(0);

                GroupTreeItem prefixTreeItem = childMap.get(prefix);
                if (prefixTreeItem == null) {
                    // @@@ ERROR;
                    return null;
                }

                // recursively go into the path
                return prefixTreeItem.getTreeItemForPath(path.subList(1, path.size()));
            }
        }
    }

    void removeFromParent() {
        final GroupTreeItem parent = (GroupTreeItem) getParent();
        if (parent != null) {
            parent.childMap.remove(getValue().getPath());

            Platform.runLater(() -> {
                synchronized (parent.getChildren()) {
                    parent.getChildren().removeAll(Collections.singleton(GroupTreeItem.this));
                }
            });

            if (parent.childMap.isEmpty()) {
                parent.removeFromParent();
            }
        }
    }

    void resortChildren(TreeNodeComparators newComp) {
        this.comp = newComp;
        synchronized (getChildren()) {
            FXCollections.sort(getChildren(), comp);
        }
        for (GroupTreeItem ti : childMap.values()) {
            ti.resortChildren(comp);
        }
    }

}
