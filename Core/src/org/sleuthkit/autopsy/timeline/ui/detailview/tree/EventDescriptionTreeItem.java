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
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;

/**
 *
 */
class EventDescriptionTreeItem extends NavTreeItem {

    EventDescriptionTreeItem(EventBundle g) {
        setValue(new NavTreeNode(g.getEventType().getBaseType(), g.getDescription(), g.getEventIDs().size()));
    }

    @Override
    public int getCount() {
        return getValue().getCount();
    }

    @Override
    public void insert(EventBundle g) {
        NavTreeNode value = getValue();
        if ((value.getType().getBaseType().equals(g.getEventType().getBaseType()) == false) || ((value.getDescription().equals(g.getDescription()) == false))) {
            throw new IllegalArgumentException();
        }

        setValue(new NavTreeNode(value.getType().getBaseType(), value.getDescription(), value.getCount() + g.getEventIDs().size()));
    }

    @Override
    public void resort(Comparator<TreeItem<NavTreeNode>> comp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TreeItem<NavTreeNode> findTreeItemForEvent(EventBundle t) {
//        if (getValue().getType().getBaseType() == t.getEventType().getBaseType() && t.getDescription().startsWith(getValue().getDescription())) {
//            TreeItem<NavTreeNode> treeItem = new TreeItem<>(new NavTreeNode(t.getEventType(), t.getDescription(), t.getEventIDs().size()));
//            getChildren().add(treeItem);
//            return treeItem;
//        }
        if (getValue().getType().getBaseType() == t.getEventType().getBaseType() && getValue().getDescription().equals(t.getDescription())) {
            return this;
        }
        return null;
    }
}
