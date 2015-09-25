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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/**
 *
 */
class EventDescriptionTreeItem extends NavTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    private final Map<String, EventDescriptionTreeItem> childMap = new ConcurrentHashMap<>();
    private final DescriptionLOD descriptionLoD;

    EventDescriptionTreeItem(EventBundle g) {
        descriptionLoD = g.getDescriptionLOD();
        setValue(new NavTreeNode(g.getEventType().getBaseType(), g.getDescription(), g.getDescriptionLOD(), g.getEventIDs().size()));
    }

    @Override
    public int getCount() {
        return getValue().getCount();
    }

    @Override
    public void insert(EventBundle g) {
        NavTreeNode value = getValue();
        if (value.getType().getBaseType().equals(g.getEventType().getBaseType())
                && g.getDescription().startsWith(value.getDescription())) {
            throw new IllegalArgumentException();
        }

        switch (descriptionLoD.getDetailLevelRelativeTo(g.getDescriptionLOD())) {
            case LESS:
                EventDescriptionTreeItem get = childMap.get(g.getDescription());
                if (get == null) {
                    EventDescriptionTreeItem eventDescriptionTreeItem = new EventDescriptionTreeItem(g);
                    childMap.put(g.getDescription(), eventDescriptionTreeItem);
                    getChildren().add(eventDescriptionTreeItem);
                } else {
                    get.insert(g);
                }
                break;
            case EQUAL:
                setValue(new NavTreeNode(value.getType().getBaseType(), value.getDescription(), value.getDescriptionLoD(), value.getCount() + g.getEventIDs().size()));
                break;
            case MORE:
                throw new IllegalArgumentException();
        }

    }

    @Override
    public void resort(Comparator<TreeItem<NavTreeNode>> comp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TreeItem<NavTreeNode> findTreeItemForEvent(EventBundle t) {

        if (getValue().getType().getBaseType() == t.getEventType().getBaseType() && getValue().getDescription().equals(t.getDescription())) {
            return this;
        }
        return null;
    }
}
