/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventStripeNode.configureLoDButton;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventStripeNode.show;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 *
 */
final public class EventClusterNode extends EventBundleNodeBase<EventCluster, EventStripe, EventStripeNode> {

    private static final Logger LOGGER = Logger.getLogger(EventClusterNode.class.getName());
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);
    private final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));

    private final Region clusterRegion = new Region();

    final Button plusButton = ActionUtils.createButton(new ExpandClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
    final Button minusButton = ActionUtils.createButton(new CollapseClusterAction(), ActionUtils.ActionTextBehavior.HIDE);

    public EventClusterNode(EventDetailChart chart, EventCluster eventCluster, EventStripeNode parentNode) {
        super(chart, eventCluster, parentNode);
       
        clusterRegion.setBorder(clusterBorder);
        clusterRegion.setBackground(defaultBackground);
        clusterRegion.setMaxHeight(USE_COMPUTED_SIZE);
        clusterRegion.setMinHeight(24);
//        clusterRegion.prefHeightProperty().bind(subNodePane.prefHeightProperty().add(24));
        clusterRegion.setMaxWidth(USE_PREF_SIZE);
        clusterRegion.setMinWidth(1);

        setMinHeight(24);
        setCursor(Cursor.HAND);
        setOnMouseClicked(new MouseClickHandler());

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            /*
             * defer tooltip creation till needed, this had a surprisingly large
             * impact on speed of loading the chart
             */
            installTooltip();
            showDescriptionLoDControls(true);
            chart.requestChartLayout();
        });
        setOnMouseExited((MouseEvent event) -> {
            showDescriptionLoDControls(false);
            chart.requestChartLayout();
        });
        configureLoDButton(plusButton);
        configureLoDButton(minusButton);

        setAlignment(Pos.CENTER_LEFT);
        HBox buttonBar = new HBox(5, minusButton, plusButton);
        buttonBar.setMaxWidth(USE_PREF_SIZE);
        buttonBar.setAlignment(Pos.BOTTOM_LEFT);
        Label label = new Label(Long.toString(getEventBundle().getCount()));
        label.setPadding(new Insets(0, 3, 0, 5));
        StackPane stackPane = new StackPane(clusterRegion, label, subNodePane);
        stackPane.setAlignment(Pos.CENTER_LEFT);
        setAlignment(stackPane, Pos.TOP_LEFT);
        VBox vBox = new VBox(stackPane, buttonBar);
        getChildren().addAll(vBox);
    }

    void showDescriptionLoDControls(final boolean showControls) {
        show(plusButton, showControls);
        show(minusButton, showControls);
    }

    @Override
    void installTooltip() {

    }

    @Override
    void applySelectionEffect(boolean selected) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void applyHighlightEffect(boolean applied) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    void setSpanWidths(List<Double> spanWidths) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    void setDescriptionWidth(double max) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void setDescriptionVisibility(DescriptionVisibility get) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * event handler used for mouse events on {@link EventStripeNode}s
     */
    private class MouseClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu contextMenu;

        @Override
        public void handle(MouseEvent t) {

            if (t.getButton() == MouseButton.PRIMARY) {
                t.consume();
                if (t.isShiftDown()) {
                    if (chart.selectedNodes.contains(EventClusterNode.this) == false) {
                        chart.selectedNodes.add(EventClusterNode.this);
                    }
                } else if (t.isShortcutDown()) {
                    chart.selectedNodes.removeAll(EventClusterNode.this);
                } else if (t.getClickCount() > 1) {
                    final DescriptionLoD next = descLOD.get().moreDetailed();
                    if (next != null) {
                        loadSubBundles(DescriptionLoD.RelativeDetail.MORE);

                    }
                } else {
                    chart.selectedNodes.setAll(EventClusterNode.this);
                }
                t.consume();
            } else if (t.getButton() == MouseButton.SECONDARY) {
                ContextMenu chartContextMenu = chart.getChartContextMenu(t);
                if (contextMenu == null) {
                    contextMenu = new ContextMenu();
                    contextMenu.setAutoHide(true);

                    contextMenu.getItems().add(ActionUtils.createMenuItem(new ExpandClusterAction()));
                    contextMenu.getItems().add(ActionUtils.createMenuItem(new CollapseClusterAction()));

                    contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(EventClusterNode.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }

    /**
     * loads sub-bundles at the given Description LOD, continues
     *
     * @param requestedDescrLoD
     * @param expand
     */
    @NbBundle.Messages(value = "EventStripeNode.loggedTask.name=Load sub clusters")
    private synchronized void loadSubBundles(DescriptionLoD.RelativeDetail relativeDetail) {
        chart.getEventBundles().removeIf(bundle ->
                subNodes.stream().anyMatch(subNode ->
                        bundle.equals(subNode.getEventStripe()))
        );
        subNodePane.getChildren().clear();
        subNodes.clear();
        if (descLOD.get().withRelativeDetail(relativeDetail) == getEventBundle().getDescriptionLoD()) {
            descLOD.set(getEventBundle().getDescriptionLoD());
            chart.requestChartLayout();
        } else {
            /*
             * make new ZoomParams to query with
             *
             *
             * We need to extend end time because for the query by one second,
             * because it is treated as an open interval but we want to include
             * events at exactly the time of the last event in this cluster
             */
            final RootFilter subClusterFilter = getSubClusterFilter();
            final Interval subClusterSpan = new Interval(getStartMillis(), getEndMillis() + 1000);
            final EventTypeZoomLevel eventTypeZoomLevel = eventsModel.eventTypeZoomProperty().get();
            final ZoomParams zoomParams = new ZoomParams(subClusterSpan, eventTypeZoomLevel, subClusterFilter, getDescriptionLoD());

            Task<Collection<EventStripe>> loggedTask = new Task<Collection<EventStripe>>() {

                private volatile DescriptionLoD loadedDescriptionLoD = getDescriptionLoD().withRelativeDetail(relativeDetail);

                {
                    updateTitle(Bundle.EventStripeNode_loggedTask_name());
                }

                @Override
                protected Collection<EventStripe> call() throws Exception {
                    Collection<EventStripe> bundles;
                    DescriptionLoD next = loadedDescriptionLoD;
                    do {
                        loadedDescriptionLoD = next;
                        if (loadedDescriptionLoD == getEventBundle().getDescriptionLoD()) {
                            return Collections.emptySet();
                        }
                        bundles = eventsModel.getEventClusters(zoomParams.withDescrLOD(loadedDescriptionLoD)).stream()
                                .collect(Collectors.toMap((eventCluster) -> eventCluster.getDescription(), //key
                                                (eventCluster) -> new EventStripe(eventCluster, getEventCluster()), //value
                                                EventStripe::merge) //merge method
                                ).values();
                        next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                    } while (bundles.size() == 1 && nonNull(next));

                    // return list of AbstractEventStripeNodes representing sub-bundles
                    return bundles;

                }

                @Override
                protected void succeeded() {
                    chart.setCursor(Cursor.WAIT);
                    try {
                        Collection<EventStripe> bundles = get();

                        if (bundles.isEmpty()) {
                        } else {
                            chart.getEventBundles().addAll(bundles);
                            subNodes.addAll(bundles.stream()
                                    .map(EventClusterNode.this::createStripeNode)
                                    .sorted(Comparator.comparing(EventStripeNode::getStartMillis))
                                    .collect(Collectors.toList()));
                            subNodePane.getChildren().addAll(subNodes);
                        }
                        descLOD.set(loadedDescriptionLoD);
                        //assign subNodes and request chart layout

                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Error loading subnodes", ex);
                    }
                    chart.requestLayout();
                    chart.setCursor(null);
                }
            };

//start task
            chart.getController().monitorTask(loggedTask);
        }
    }

    private EventStripeNode createStripeNode(EventStripe stripe) {
        return new EventStripeNode(chart, stripe, this);
    }

    private static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    private static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N

    private class ExpandClusterAction extends Action {

        @NbBundle.Messages("ExpandClusterAction.text=Expand")
        ExpandClusterAction() {
            super(Bundle.ExpandClusterAction_text());

            setGraphic(new ImageView(PLUS));
            setEventHandler((ActionEvent t) -> {
                final DescriptionLoD next = descLOD.get().moreDetailed();
                if (next != null) {
                    loadSubBundles(DescriptionLoD.RelativeDetail.MORE);

                }
            });
            disabledProperty().bind(descLOD.isEqualTo(DescriptionLoD.FULL));
        }
    }

    private class CollapseClusterAction extends Action {

        @NbBundle.Messages("CollapseClusterAction.text=Collapse")
        CollapseClusterAction() {
            super(Bundle.CollapseClusterAction_text());

            setGraphic(new ImageView(MINUS));
            setEventHandler((ActionEvent t) -> {
                final DescriptionLoD previous = descLOD.get().lessDetailed();
                if (previous != null) {
                    loadSubBundles(DescriptionLoD.RelativeDetail.LESS);
                }
            });
            disabledProperty().bind(Bindings.createBooleanBinding(() -> nonNull(getEventCluster()) && descLOD.get() == getEventCluster().getDescriptionLoD(), descLOD));
        }
    }

    EventCluster getEventCluster() {
        return getEventBundle();
    }

    @Override
    double layoutChildren(double xOffset) {
        double chartX = super.layoutChildren(xOffset); //To change body of generated methods, choose Tools | Templates.
        double w = chart.getXAxis().getDisplayPosition(new DateTime(getEndMillis())) - chartX;
        clusterRegion.setPrefWidth(w);
        return chartX;
    }

    /**
     * make a new filter intersecting the global filter with description and
     * type filters to restrict sub-clusters
     *
     */
    RootFilter getSubClusterFilter() {
        RootFilter subClusterFilter = eventsModel.filterProperty().get().copyOf();
        subClusterFilter.getSubFilters().addAll(
                new DescriptionFilter(getDescriptionLoD(), getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        return subClusterFilter;
    }
}
