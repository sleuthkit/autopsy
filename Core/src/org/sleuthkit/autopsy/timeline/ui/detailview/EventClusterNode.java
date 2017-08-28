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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.collections.ObservableList;
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
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.configureActionButton;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 * A Node to represent an EventCluster in a DetailsChart
 */
final class EventClusterNode extends MultiEventNodeBase<EventCluster, EventStripe, EventStripeNode> {
    
    private static final Logger LOGGER = Logger.getLogger(EventClusterNode.class.getName());

    /**
     * The border widths for event clusters (t, r,b l)
     */
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);

    /**
     * The border for this cluster, derived by from the event type color and the
     * CLUSTER_BORDER_WIDTHS
     */
    private final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));

    /**
     * The button to expand this cluster, created lazily.
     */
    private Button plusButton;
    /**
     * The button to collapse this cluster, created lazily.
     */
    private Button minusButton;

    /**
     * Constructor
     *
     * @param chartLane    the DetailsChartLane this node belongs to
     * @param eventCluster the EventCluster represented by this node
     * @param parentNode   the EventStripeNode that is the parent of this node.
     */
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

    /**
     * Get a new button configured to expand this cluster when pressed.
     *
     * @return a new button configured to expand this cluster when pressed.
     */
    Button getNewExpandButton() {
        return ActionUtils.createButton(new ExpandClusterAction(this), ActionUtils.ActionTextBehavior.HIDE);
    }

    /**
     * Get a new button configured to collapse this cluster when pressed.
     *
     * @return a new button configured to collapse this cluster when pressed.
     */
    Button getNewCollapseButton() {
        return ActionUtils.createButton(new CollapseClusterAction(this), ActionUtils.ActionTextBehavior.HIDE);
    }
    
    @Override
    void installActionButtons() {
        super.installActionButtons();
        if (plusButton == null) {
            plusButton = getNewExpandButton();
            minusButton = getNewCollapseButton();
            controlsHBox.getChildren().addAll(minusButton, plusButton);
            
            configureActionButton(plusButton);
            configureActionButton(minusButton);
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
     * Load sub-stripes of this cluster at a description level of detail
     * determined by the given RelativeDetail
     *
     * @param relativeDetail the relative detail level to load.
     */
    @NbBundle.Messages(value = "EventClusterNode.loggedTask.name=Load sub events")
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private synchronized void loadSubStripes(DescriptionLoD.RelativeDetail relativeDetail) {
        getChartLane().setCursor(Cursor.WAIT);

        /*
         * make new ZoomParams to query with
         *
         * We need to extend end time for the query by one second, because it is
         * treated as an open interval but we want to include events at exactly
         * the time of the last event in this cluster. Restrict the sub stripes
         * to the type and description of this cluster by intersecting a new
         * filter with the existing root filter.
         */
        final RootFilter subClusterFilter = eventsModel.filterProperty().get().copyOf();
        subClusterFilter.getSubFilters().addAll(
                new DescriptionFilter(getEvent().getDescriptionLoD(), getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        final Interval subClusterSpan = new Interval(getStartMillis(), getEndMillis() + 1000);
        final EventTypeZoomLevel eventTypeZoomLevel = eventsModel.eventTypeZoomProperty().get();
        final ZoomParams zoomParams = new ZoomParams(subClusterSpan, eventTypeZoomLevel, subClusterFilter, getDescriptionLoD());

        /*
         * task to load sub-stripes in a background thread
         */
        Task<List<EventStripe>> loggedTask;
        loggedTask = new LoggedTask<List<EventStripe>>(Bundle.EventClusterNode_loggedTask_name(), false) {
            
            private volatile DescriptionLoD loadedDescriptionLoD = getDescriptionLoD().withRelativeDetail(relativeDetail);
            
            @Override
            protected List<EventStripe> call() throws Exception {
                //newly loaded substripes                
                List<EventStripe> stripes;
                //next LoD in diraction of given relativeDetail
                DescriptionLoD next = loadedDescriptionLoD;
                do {
                    
                    loadedDescriptionLoD = next;
                    if (loadedDescriptionLoD == getEvent().getDescriptionLoD()) {
                        //if we are back at the level of detail of the original cluster, return empty list to inidicate.
                        return Collections.emptyList();
                    }

                    //query for stripes at the desired level of detail
                    stripes = eventsModel.getEventStripes(zoomParams.withDescrLOD(loadedDescriptionLoD));
                    //setup next for subsequent go through the "do" loop
                    next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                } while (stripes.size() == 1 && nonNull(next)); //keep going while there was only on stripe and we havne't reached the end of the LoD continuum.

                // return list of EventStripes with parents set to this cluster
                return stripes.stream()
                        .map(eventStripe -> eventStripe.withParent(getEvent()))
                        .collect(Collectors.toList());
            }
            
            @Override
            protected void succeeded() {
                ObservableList<TimeLineEvent> chartNestedEvents = getChartLane().getParentChart().getAllNestedEvents();

                //clear the existing subnodes/events
                chartNestedEvents.removeAll(StripeFlattener.flatten(subNodes));
                subNodes.clear();
                
                try {
                    setDescriptionLOD(loadedDescriptionLoD);
                    List<EventStripe> newSubStripes = get();
                    if (newSubStripes.isEmpty()) {
                        //restore original display
                        getChildren().setAll(subNodePane, infoHBox);
                    } else {
                        //display new sub stripes
                        subNodes.addAll(Lists.transform(newSubStripes, EventClusterNode.this::createChildNode)); //map stripes to nodes
                        chartNestedEvents.addAll(StripeFlattener.flatten(subNodes));
                        getChildren().setAll(new VBox(infoHBox, subNodePane));
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Error loading subnodes", ex); //NON-NLS
                }
                
                getChartLane().requestChartLayout();
                getChartLane().setCursor(null);
            }
        };

        //start task
        new Thread(loggedTask).start();
        getChartLane().getController().monitorTask(loggedTask);
    }
    
    @Override
    EventNodeBase<?> createChildNode(EventStripe stripe) {
        ImmutableSet<Long> eventIDs = stripe.getEventIDs();
        if (eventIDs.size() == 1) {
            //If the stripe is a single event, make a single event node rather than a stripe node.
            SingleEvent singleEvent = getController().getEventsModel().getEventById(Iterables.getOnlyElement(eventIDs)).withParent(stripe);
            return new SingleEventNode(getChartLane(), singleEvent, this);
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

    /**
     * An action that expands the given cluster by breaking out the sub stripes
     * at the next description level of detail.
     */
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

            //disabled if the given node is already at full description level of detail
            disabledProperty().bind(node.descriptionLoDProperty().isEqualTo(DescriptionLoD.FULL));
        }
    }

    /**
     * An action that collapses the given cluster removing any sub stripes at
     * more detailed level of detail.
     */
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

            //disabled if node is at clusters level of detail
            disabledProperty().bind(node.descriptionLoDProperty().isEqualTo(node.getEvent().getDescriptionLoD()));
        }
    }
}
