/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.configureActionButton;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 *
 */
final public class EventClusterNode extends MultiEventNodeBase<EventCluster, EventStripe, EventStripeNode> {

    private static final Logger LOGGER = Logger.getLogger(EventClusterNode.class.getName());
    private static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    private static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);

    private final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));

    Button plusButton;
    Button minusButton;

    @Override
    void installActionButtons() {
        super.installActionButtons();
        if (plusButton == null) {
            plusButton = ActionUtils.createButton(new ExpandClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
            minusButton = ActionUtils.createButton(new CollapseClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
            controlsHBox.getChildren().addAll(minusButton, plusButton);

            configureActionButton(plusButton);
            configureActionButton(minusButton);
        }
    }

    EventClusterNode(DetailsChartLane<?> chartLane, EventCluster eventCluster, EventStripeNode parentNode) {
        super(chartLane, eventCluster, parentNode);

        subNodePane.setBorder(clusterBorder);
        subNodePane.setBackground(defaultBackground);
        subNodePane.setMinWidth(1);
        subNodePane.setMaxWidth(USE_PREF_SIZE);
        setMinHeight(24);
        setAlignment(Pos.CENTER_LEFT);

        setCursor(Cursor.HAND);
        getChildren().addAll(subNodePane, infoHBox);

        if (parentNode == null) {
            setDescriptionVisibiltiyImpl(DescriptionVisibility.SHOWN);
        }
    }

    @Override
    void setDescriptionVisibiltiyImpl(DescriptionVisibility descrVis) {
        final int size = getEventCluster().getSize();
        switch (descrVis) {
            case HIDDEN:
                countLabel.setText("");
                descrLabel.setText("");
                break;
            case COUNT_ONLY:
                descrLabel.setText("");
                countLabel.setText(String.valueOf(size));
                break;
            case SHOWN:
            default:
                String count;
                String description;
                if (parentNode == null) {
                    description = tlEvent.getDescription();
                    count = "(" + size + ")";
                } else {
                    description = "";
                    count = String.valueOf(size);
                }

                descrLabel.setText(description);
                countLabel.setText(count);
                break;
        }
    }

    /**
     * loads sub-bundles at the given Description LOD, continues
     *
     * @param requestedDescrLoD
     * @param expand
     */
    @NbBundle.Messages(value = "EventStripeNode.loggedTask.name=Load sub clusters")
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private synchronized void loadSubBundles(DescriptionLoD.RelativeDetail relativeDetail) {
        getChartLane().setCursor(Cursor.WAIT);


        /*
         * make new ZoomParams to query with
         *
         * We need to extend end time because for the query by one second,
         * because it is treated as an open interval but we want to include
         * events at exactly the time of the last event in this cluster
         */
        final RootFilter subClusterFilter = getSubClusterFilter();
        final Interval subClusterSpan = new Interval(getStartMillis(), getEndMillis() + 1000);
        final EventTypeZoomLevel eventTypeZoomLevel = eventsModel.eventTypeZoomProperty().get();
        final ZoomParams zoomParams = new ZoomParams(subClusterSpan, eventTypeZoomLevel, subClusterFilter, getDescriptionLoD());

        Task<List<EventStripe>> loggedTask = new LoggedTask<List<EventStripe>>(Bundle.EventStripeNode_loggedTask_name(), false) {

            private volatile DescriptionLoD loadedDescriptionLoD = getDescriptionLoD().withRelativeDetail(relativeDetail);

            @Override
            protected List<EventStripe> call() throws Exception {
                List<EventStripe> bundles;
                DescriptionLoD next = loadedDescriptionLoD;
                do {
                    loadedDescriptionLoD = next;
                    if (loadedDescriptionLoD == getEventBundle().getDescriptionLoD()) {
                        return Collections.emptyList();
                    }
                    bundles = eventsModel.getEventStripes(zoomParams.withDescrLOD(loadedDescriptionLoD));

                    next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                } while (bundles.size() == 1 && nonNull(next));

                // return list of EventStripes representing sub-bundles
                return bundles.stream()
                        .map(eventStripe -> eventStripe.withParent(getEventCluster()))
                        .collect(Collectors.toList());
            }

            @Override
            protected void succeeded() {
                try {
                    List<EventStripe> bundles = get();

                    //clear the existing subnodes
                    List<TimeLineEvent> transform = subNodes.stream().flatMap(new StripeFlattener()).collect(Collectors.toList());
//                    ((EventDetailsChart) getChart()).getEventStripes().removeAll(transform);
                    subNodes.clear();
                    if (bundles.isEmpty()) {
                        getChildren().setAll(subNodePane, infoHBox);
                        descLOD.set(getEventBundle().getDescriptionLoD());
                    } else {
//                        ((EventDetailsChart) getChart()).getEventStripes().addAll(bundles);
                        subNodes.addAll(Lists.transform(bundles, EventClusterNode.this::createChildNode));
                        getChildren().setAll(new VBox(infoHBox, subNodePane));
                        descLOD.set(loadedDescriptionLoD);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Error loading subnodes", ex); //NON-NLS
                }
                getChartLane().requestChartLayout();
                getChartLane().setCursor(null);
            }
        };

        new Thread(loggedTask).start();
        //start task
        getChartLane().getController().monitorTask(loggedTask);
    }

    @Override
    EventNodeBase<?> createChildNode(EventStripe stripe) {
        if (stripe.getEventIDs().size() == 1) {
            return new SingleEventNode(getChartLane(), getChartLane().getController().getEventsModel().getEventById(Iterables.getOnlyElement(stripe.getEventIDs())), this);
        } else {
            return new EventStripeNode(getChartLane(), stripe, this);
        }
    }

    EventCluster getEventCluster() {
        return getEventBundle();
    }

    @Override
    protected void layoutChildren() {
        double chartX = getChartLane().getXAxis().getDisplayPosition(new DateTime(getStartMillis()));
        double w = getChartLane().getXAxis().getDisplayPosition(new DateTime(getEndMillis())) - chartX;
        subNodePane.setPrefWidth(Math.max(1, w));
        super.layoutChildren();
    }

    /**
     * make a new filter intersecting the global filter with description and
     * type filters to restrict sub-clusters
     *
     */
    RootFilter getSubClusterFilter() {
        RootFilter subClusterFilter = eventsModel.filterProperty().get().copyOf();
        subClusterFilter.getSubFilters().addAll(
                new DescriptionFilter(getEventBundle().getDescriptionLoD(), getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        return subClusterFilter;
    }

    @Override
    Collection<? extends Action> getActions() {
        return Arrays.asList(new ExpandClusterAction(),
                new CollapseClusterAction());
    }

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> new ExpandClusterAction().handle(null);
    }

    class ExpandClusterAction extends Action {

        @NbBundle.Messages({"ExpandClusterAction.text=Expand"})
        ExpandClusterAction() {
            super(Bundle.ExpandClusterAction_text());

            setGraphic(new ImageView(PLUS));
            setEventHandler(actionEvent -> {
                if (descLOD.get().moreDetailed() != null) {
                    loadSubBundles(DescriptionLoD.RelativeDetail.MORE);
                }
            });
            disabledProperty().bind(descLOD.isEqualTo(DescriptionLoD.FULL));
        }
    }

    class CollapseClusterAction extends Action {

        @NbBundle.Messages({"CollapseClusterAction.text=Collapse"})
        CollapseClusterAction() {
            super(Bundle.CollapseClusterAction_text());

            setGraphic(new ImageView(MINUS));
            setEventHandler(actionEvent -> {
                if (descLOD.get().lessDetailed() != null) {
                    loadSubBundles(DescriptionLoD.RelativeDetail.LESS);
                }
            });
            disabledProperty().bind(Bindings.createBooleanBinding(() -> nonNull(getEventCluster()) && descLOD.get() == getEventCluster().getDescriptionLoD(), descLOD));
        }
    }

}
