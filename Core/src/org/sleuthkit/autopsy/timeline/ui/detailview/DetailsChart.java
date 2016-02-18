/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
interface DetailsChart extends TimeLineChart<DateTime> {

    static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS
    static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS

    public void requestTimelineChartLayout();

    public Node asNode();

    public ObservableList<EventStripe> getEventStripes();

    public ObservableList<EventNodeBase<?>> getSelectedNodes();

    double layoutEventBundleNodes(final Collection<? extends EventNodeBase<?>> nodes, final double minY);

    @NbBundle.Messages({"HideDescriptionAction.displayName=Hide",
        "HideDescriptionAction.displayMsg=Hide this group from the details view."})
    static class HideDescriptionAction extends Action {

        HideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.HideDescriptionAction_displayName());
            setLongText(Bundle.HideDescriptionAction_displayMsg());
            setGraphic(new ImageView(HIDE));
            setEventHandler((ActionEvent t) -> {
                final DescriptionFilter testFilter = new DescriptionFilter(
                        descriptionLoD,
                        description,
                        DescriptionFilter.FilterMode.EXCLUDE);

                DescriptionFilter descriptionFilter = chart.getController().getQuickHideFilters().stream()
                        .filter(testFilter::equals)
                        .findFirst().orElseGet(() -> {
                            testFilter.selectedProperty().addListener(observable -> chart.requestTimelineChartLayout());
                            chart.getController().getQuickHideFilters().add(testFilter);
                            return testFilter;
                        });
                descriptionFilter.setSelected(true);
            });
        }
    }

    @NbBundle.Messages({"UnhideDescriptionAction.displayName=Unhide"})
    static class UnhideDescriptionAction extends Action {

        UnhideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.UnhideDescriptionAction_displayName());
            setGraphic(new ImageView(SHOW));
            setEventHandler((ActionEvent t) ->
                    chart.getController().getQuickHideFilters().stream()
                    .filter(descriptionFilter -> descriptionFilter.getDescriptionLoD().equals(descriptionLoD)
                            && descriptionFilter.getDescription().equals(description))
                    .forEach(descriptionfilter -> descriptionfilter.setSelected(false))
            );
        }
    }
}
