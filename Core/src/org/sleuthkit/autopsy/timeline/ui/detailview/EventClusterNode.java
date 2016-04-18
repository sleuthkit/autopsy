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
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
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

    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);

    private final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));

    Button plusButton;
    Button minusButton;

    @Override
    void installActionButtons() {
        super.installActionButtons();
        if (plusButton == null) {
            plusButton = ActionUtils.createButton(new ExpandClusterAction(this), ActionUtils.ActionTextBehavior.HIDE);
            minusButton = ActionUtils.createButton(new CollapseClusterAction(this), ActionUtils.ActionTextBehavior.HIDE);
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
            setDescriptionVisibility(DescriptionVisibility.SHOWN);
        }
    }

    @Override
    void showFullDescription(final int size) {
        if (getParentNode().isPresent()) {
            showCountOnly(size);
        } else {
            super.showFullDescription(size);
        }
    }

    /**
     * loads sub-bundles at the given Description LOD, continues
     *
     * @param requestedDescrLoD
     * @param expand
     */
    @NbBundle.Messages(value = "EventClusterNode.loggedTask.name=Load sub events")
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private synchronized void loadSubStripes(DescriptionLoD.RelativeDetail relativeDetail) {
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

        Task<List<EventStripe>> loggedTask = new LoggedTask<List<EventStripe>>(Bundle.EventClusterNode_loggedTask_name(), false) {

            private volatile DescriptionLoD loadedDescriptionLoD = getDescriptionLoD().withRelativeDetail(relativeDetail);

            @Override
            protected List<EventStripe> call() throws Exception {
                List<EventStripe> stripes;
                DescriptionLoD next = loadedDescriptionLoD;
                do {
                    loadedDescriptionLoD = next;
                    if (loadedDescriptionLoD == getEvent().getDescriptionLoD()) {
                        return Collections.emptyList();
                    }
                    stripes = eventsModel.getEventStripes(zoomParams.withDescrLOD(loadedDescriptionLoD));

                    next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                } while (stripes.size() == 1 && nonNull(next));

                // return list of EventStripes representing sub-bundles
                return stripes.stream()
                        .map(eventStripe -> eventStripe.withParent(getEvent()))
                        .collect(Collectors.toList());
            }

            @Override
            protected void succeeded() {
                try {
                    List<EventStripe> newSubStripes = get();

                    //clear the existing subnodes
                    List<TimeLineEvent> oldSubEvents = subNodes.stream().flatMap(new StripeFlattener()).collect(Collectors.toList());
                    getChartLane().getParentChart().getAllNestedEvents().removeAll(oldSubEvents);
                    subNodes.clear();
                    if (newSubStripes.isEmpty()) {
                        getChildren().setAll(subNodePane, infoHBox);
                        setDescriptionLOD(getEvent().getDescriptionLoD());
                    } else {
                        subNodes.addAll(Lists.transform(newSubStripes, EventClusterNode.this::createChildNode));
                        List<TimeLineEvent> newSubEvents = subNodes.stream().flatMap(new StripeFlattener()).collect(Collectors.toList());
                        getChartLane().getParentChart().getAllNestedEvents().addAll(newSubEvents);
                        getChildren().setAll(new VBox(infoHBox, subNodePane));
                        setDescriptionLOD(loadedDescriptionLoD);
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
            return new SingleEventNode(getChartLane(), getChartLane().getController().getEventsModel().getEventById(Iterables.getOnlyElement(stripe.getEventIDs())).withParent(stripe), this);
        } else {
            return new EventStripeNode(getChartLane(), stripe, this);
        }
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
                new DescriptionFilter(getEvent().getDescriptionLoD(), getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        return subClusterFilter;
    }

    @Override
    Iterable<? extends Action> getActions() {
        return Iterables.concat(
                super.getActions(),
                Arrays.asList(new ExpandClusterAction(this), new CollapseClusterAction(this))
        );
    }

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> new ExpandClusterAction(this).handle(null);
    }

    static private class ExpandClusterAction extends Action {

        private static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N

        @NbBundle.Messages({"ExpandClusterAction.text=Expand"})
        ExpandClusterAction(EventClusterNode node) {
            super(Bundle.ExpandClusterAction_text());

            setGraphic(new ImageView(PLUS));
            setEventHandler(actionEvent -> {
                if (node.getDescriptionLoD().moreDetailed() != null) {
                    node.loadSubStripes(DescriptionLoD.RelativeDetail.MORE);
                }
            });
            disabledProperty().bind(node.descriptionLoDProperty().isEqualTo(DescriptionLoD.FULL));
        }
    }

    static private class CollapseClusterAction extends Action {

        private static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N

        @NbBundle.Messages({"CollapseClusterAction.text=Collapse"})
        CollapseClusterAction(EventClusterNode node) {
            super(Bundle.CollapseClusterAction_text());

            setGraphic(new ImageView(MINUS));
            setEventHandler(actionEvent -> {
                if (node.getDescriptionLoD().lessDetailed() != null) {
                    node.loadSubStripes(DescriptionLoD.RelativeDetail.LESS);
                }
            });

            disabledProperty().bind(node.descriptionLoDProperty().isEqualTo(node.getEvent().getDescriptionLoD()));
        }
    }
}
