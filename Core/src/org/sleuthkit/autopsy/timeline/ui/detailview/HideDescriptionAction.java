/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DefaultFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DescriptionFilter;
import static org.sleuthkit.datamodel.timeline.TimelineFilter.DescriptionFilter.FilterMode.EXCLUDE;

/**
 * An Action that hides, in the given chart, events that have the given
 * description
 */
@NbBundle.Messages(value = {"HideDescriptionAction.displayName=Hide",
    "HideDescriptionAction.displayMsg=Hide this group from the details view."})
class HideDescriptionAction extends Action {

    private static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS

    HideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
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
            final FilterState<DescriptionFilter> testFilter
                    = new DefaultFilterState<>(
                            new DescriptionFilter( description, EXCLUDE));

            FilterState<DescriptionFilter> descriptionFilter = chart.getController().getQuickHideFilters().stream()
                    .filter(testFilter::equals).findFirst()
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
