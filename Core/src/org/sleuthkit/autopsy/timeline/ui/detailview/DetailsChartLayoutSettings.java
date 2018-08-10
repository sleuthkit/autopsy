/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.SetChangeListener;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;

/**
 *
 */
final class DetailsChartLayoutSettings {

    DetailsChartLayoutSettings(TimeLineController controller) {
        controller.getPinnedEvents().addListener((SetChangeListener.Change<? extends DetailViewEvent> change) -> {
            //if the pinned events change and aren't empty, show them
            setPinnedLaneShowing(change.getSet().isEmpty() == false);
        });

        //initialy show the pinned events if they are not empty
        if (controller.getPinnedEvents().isEmpty() == false) {
            setPinnedLaneShowing(true);
        }
    }

    /**
     * true == truncate all the labels to the greater of the size of their
     * timespan indicator or the value of truncateWidth. false == don't truncate
     * the labels, alow them to extend past the timespan indicator and off the
     * edge of the screen
     */
    private final SimpleBooleanProperty truncateAll = new SimpleBooleanProperty(false);
    /**
     * the width to truncate all labels to if truncateAll is true. adjustable
     * via slider if truncateAll is true
     */
    private final SimpleDoubleProperty truncateWidth = new SimpleDoubleProperty(200.0);
    /**
     * true == layout each event type in its own band, false == mix all the
     * events together during layout
     */
    private final SimpleBooleanProperty bandByType = new SimpleBooleanProperty(false);

    /**
     * true == enforce that no two events can share the same 'row', leading to
     * sparser but possibly clearer layout. false == put unrelated events in the
     * same 'row', creating a denser more compact layout
     */
    private final SimpleBooleanProperty oneEventPerRow = new SimpleBooleanProperty(false);

    /**
     * how much detail of the description to show in the ui
     */
    private final SimpleObjectProperty<DescriptionVisibility> descrVisibility = new SimpleObjectProperty<>(DescriptionVisibility.SHOWN);

    /**
     * is the pinned events lane showing
     */
    private final SimpleBooleanProperty pinnedLaneShowing = new SimpleBooleanProperty(false);

    SimpleBooleanProperty bandByTypeProperty() {
        return bandByType;
    }

    SimpleBooleanProperty pinnedLaneShowing() {
        return pinnedLaneShowing;
    }

    boolean isPinnedLaneShowing() {
        return pinnedLaneShowing.get();
    }

    void setPinnedLaneShowing(boolean showing) {
        pinnedLaneShowing.set(showing);
    }

    SimpleBooleanProperty oneEventPerRowProperty() {
        return oneEventPerRow;
    }

    SimpleDoubleProperty truncateWidthProperty() {
        return truncateWidth;
    }

    SimpleBooleanProperty truncateAllProperty() {
        return truncateAll;
    }

    SimpleObjectProperty< DescriptionVisibility> descrVisibilityProperty() {
        return descrVisibility;
    }

    void setBandByType(Boolean t1) {
        bandByType.set(t1);
    }

    boolean getBandByType() {
        return bandByType.get();
    }

    boolean getTruncateAll() {
        return truncateAll.get();
    }

    double getTruncateWidth() {
        return truncateWidth.get();
    }

    boolean getOneEventPerRow() {
        return oneEventPerRow.get();
    }

    DescriptionVisibility getDescrVisibility() {
        return descrVisibility.get();
    }
}
