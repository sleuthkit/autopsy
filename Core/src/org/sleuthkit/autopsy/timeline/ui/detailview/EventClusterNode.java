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
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventBundleNodeBase.CORNER_RADII_3;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 *
 */
public class EventClusterNode extends EventBundleNodeBase<EventCluster, EventStripe, EventStripeNode> {

    private static final Logger LOGGER = Logger.getLogger(EventClusterNode.class.getName());
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);
    private final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));

    public EventClusterNode(EventDetailChart chart, EventCluster eventCluster, EventStripeNode parentNode) {
        super(chart, eventCluster, parentNode);

 
        setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .2), CORNER_RADII_3, Insets.EMPTY)));
        setBorder(clusterBorder);

        setCursor(Cursor.HAND);
        setOnMouseClicked(new MouseClickHandler());

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            /*
             * defer tooltip creation till needed, this had a surprisingly large
             * impact on speed of loading the chart
             */
            installTooltip();
//            showDescriptionLoDControls(true);
            toFront();
        });

        setOnMouseExited((MouseEvent e) -> {
//            showDescriptionLoDControls(false);
        });

    }

    @Override
    final void installTooltip() {

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
                getSubNodes().stream().anyMatch(subNode ->
                        bundle.equals(subNode.getEventStripe()))
        );
        subNodePane.getChildren().clear();
        if (descLOD.get().withRelativeDetail(relativeDetail) == getEventBundle().getDescriptionLoD()) {
            descLOD.set(getEventBundle().getDescriptionLoD());
//            clustersHBox.setVisible(true);
            chart.setRequiresLayout(true);
            chart.requestChartLayout();
        } else {
//            clustersHBox.setVisible(false);

            // make new ZoomParams to query with
            final RootFilter subClusterFilter = null;//getSubClusterFilter();
            /*
             * We need to extend end time because for the query by one second,
             * because it is treated as an open interval but we want to include
             * events at exactly the time of the last event in this cluster
             */
            final Interval subClusterSpan = new Interval(getEventBundle().getStartMillis(), getEventBundle().getEndMillis() + 1000);
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
                                //                                .map(cluster -> cluster.withParent(getEventBundle()))
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
//                            clustersHBox.setVisible(true);
                        } else {
//                            clustersHBox.setVisible(false);
                            chart.getEventBundles().addAll(bundles);
                            subNodePane.getChildren().setAll(bundles.stream()
                                    .map(EventClusterNode.this::getSubNodeForBundle)
                                    .collect(Collectors.toSet()));
                        }
                        descLOD.set(loadedDescriptionLoD);
                        //assign subNodes and request chart layout

                        chart.setRequiresLayout(true);
                        chart.requestChartLayout();
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Error loading subnodes", ex);
                    }
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
}
