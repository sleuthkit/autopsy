/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DescriptionFilterState;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * An Action that hides, in the given chart, events that have the given
 * description
 */
@NbBundle.Messages(value = {"HideDescriptionAction.displayName=Hide",
    "HideDescriptionAction.displayMsg=Hide this group from the details view."})
class HideDescriptionAction extends Action {

    private static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS

    HideDescriptionAction(String description, TimelineLevelOfDetail descriptionLoD, DetailsChart chart) {
        super(Bundle.HideDescriptionAction_displayName());
        setLongText(Bundle.HideDescriptionAction_displayMsg());
        setGraphic(new ImageView(HIDE));

        setEventHandler(actionEvent -> {
            /*
             * See if there is already a quick-hide-filter for the given
             * description by making a test one and checking all the existing
             * filters against it. If there is not already an existing filter,
             * hook up the listeners on the test filter and add the test filter
             * as the new filter for the given description. Set the (new) filter
             * active.
             */
            final DescriptionFilterState testFilter
                    = new DescriptionFilterState(
                            new DescriptionFilter(descriptionLoD, description));

            DescriptionFilterState descriptionFilter = chart.getController().getQuickHideFilters().stream()
                    .filter(otherFilterState -> testFilter.getFilter().equals(otherFilterState.getFilter()))
                    .findFirst()
                    .orElseGet(() -> {
                        //if the selected state of the filter changes, do chart layout
                        testFilter.selectedProperty().addListener(selectedProperty -> chart.requestLayout());
                        chart.getController().getQuickHideFilters().add(testFilter);
                        return testFilter;
                    });
            descriptionFilter.setSelected(true);
        });
    }
}
