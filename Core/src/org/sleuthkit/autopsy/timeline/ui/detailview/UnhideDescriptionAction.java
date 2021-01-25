/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DescriptionFilter;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * An Action that un-hides, in the given chart, events with the given
 * description.
 */
@NbBundle.Messages(value = {"UnhideDescriptionAction.displayName=Unhide"})
class UnhideDescriptionAction extends Action {

    private static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS

    UnhideDescriptionAction(String description, TimelineLevelOfDetail descriptionLoD, DetailsChart chart) {
        super(Bundle.UnhideDescriptionAction_displayName());
        setGraphic(new ImageView(SHOW));

        setEventHandler(actionEvent -> {
            /**
             * Find any quick-hide-filters for the given description by making a
             * test one and checking all the existing filters against it.
             * Disable them.
             */
            final DescriptionFilter testFilter = new DescriptionFilter(descriptionLoD, description);
            chart.getController().getQuickHideFilters().stream()
                    .filter(otherFilterState -> testFilter.equals(otherFilterState.getFilter()))
                    .forEach(descriptionfilter -> descriptionfilter.setSelected(false));
        });
    }
}
