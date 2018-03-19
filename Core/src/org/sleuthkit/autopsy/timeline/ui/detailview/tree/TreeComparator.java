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
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.TimeLineEvent;

@NbBundle.Messages({"TreeComparator.Description.displayName=Description",
    "TreeComparator.Count.displayName=Count",
    "TreeComparator.Type.displayName=Type"})
enum TreeComparator implements Comparator<TreeItem<TimeLineEvent>> {

    Description(Bundle.TreeComparator_Description_displayName()) {
                @Override
        public int compare(TreeItem<TimeLineEvent> o1, TreeItem<TimeLineEvent> o2) {
                    return o1.getValue().getDescription().compareTo(o2.getValue().getDescription());
                }
            },
    Count(Bundle.TreeComparator_Count_displayName()) {
                @Override
        public int compare(TreeItem<TimeLineEvent> o1, TreeItem<TimeLineEvent> o2) {
                    return Long.compare(o2.getValue().getSize(), o1.getValue().getSize());
                }
            },
    Type(Bundle.TreeComparator_Type_displayName()) {
                @Override
        public int compare(TreeItem<TimeLineEvent> o1, TreeItem<TimeLineEvent> o2) {
                    return EventType.getComparator().compare(o1.getValue().getEventType(), o2.getValue().getEventType());
                }
            };

    private final String displayName;

    private TreeComparator(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
