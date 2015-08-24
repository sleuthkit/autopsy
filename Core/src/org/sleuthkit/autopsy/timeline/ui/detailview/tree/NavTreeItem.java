/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview.tree;

import java.util.Comparator;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.AggregateEvent;

/**
 * A node in the nav tree. Manages inserts and resorts. Has parents and
 * children. Does not have graphical properties these are configured in
 * {@link EventTreeCell}. Each GroupTreeItem has a NavTreeNode which has a type,
 * description , and count
 */
abstract class NavTreeItem extends TreeItem<NavTreeNode> {

    abstract void insert(AggregateEvent g);

    abstract int getCount();

    abstract void resort(Comparator<TreeItem<NavTreeNode>> comp);

    abstract TreeItem<NavTreeNode> findTreeItemForEvent(AggregateEvent t);

}
