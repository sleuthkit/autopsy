/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
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
//            toFront();
        });

        setOnMouseExited((MouseEvent e) -> {
            showDescriptionLoDControls(false);
        });

        configureLoDButton(plusButton);
        configureLoDButton(minusButton);
        HBox buttonBar = new HBox(5, plusButton, minusButton);
        buttonBar.setMaxWidth(USE_PREF_SIZE);
        buttonBar.setAlignment(Pos.BOTTOM_LEFT);

        getChildren().addAll(clusterRegion, subNodePane, buttonBar);
    }

    void showDescriptionLoDControls(final boolean showControls) {
        show(minusButton, showControls);
        show(plusButton, showControls);
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
            chart.setRequiresLayout(true);
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
                                    .map(EventClusterNode.this::getSubNodeForBundle)
                                    .collect(Collectors.toSet()));
                            subNodePane.getChildren().addAll(subNodes);
                        }
                        descLOD.set(loadedDescriptionLoD);
                        //assign subNodes and request chart layout

                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Error loading subnodes", ex);
                    }
                    chart.setRequiresLayout(true);
                    chart.requestChartLayout();
                    chart.setCursor(null);
                }
            };

//start task
            chart.getController().monitorTask(loggedTask);
        }
    }

    EventStripeNode getSubNodeForBundle(EventStripe stripe) {
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
    protected void layoutChildren(double xOffset) {
        super.layoutChildren(); //To change body of generated methods, choose Tools | Templates.
        double chartX = chart.getXAxis().getDisplayPosition(new DateTime(getStartMillis()));
        double startX = chartX - xOffset;
        double w = chart.getXAxis().getDisplayPosition(new DateTime(getEndMillis())) - chartX;

        //position of start and end according to range of axis
        setLayoutX(startX);
        clusterRegion.setPrefWidth(w);
        chart.layoutStripes(subNodes, 0, chartX);
        autosize();
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
