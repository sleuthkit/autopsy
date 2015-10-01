/*

 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import com.google.common.collect.Range;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.nonNull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
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
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node used in {@link EventDetailChart} to represent an EventStripe.
 */
final public class EventStripeNode extends StackPane {

    private static final Logger LOGGER = Logger.getLogger(EventStripeNode.class.getName());
    private static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N
    private static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    private static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N

    private static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    private static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(2, 1, 2, 1);
    private final static Map<EventType, DropShadow> dropShadowMap = new ConcurrentHashMap<>();
    private static final Border SELECTION_BORDER = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII_3, new BorderWidths(2)));

    static void configureLoDButton(Button b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }

    private final SimpleObjectProperty<DescriptionLoD> descLOD = new SimpleObjectProperty<>();
    private DescriptionVisibility descrVis;
    private Tooltip tooltip;

    /**
     * Pane that contains EventStripeNodes for any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
    private final Pane subNodePane = new Pane();
    private final HBox clustersHBox = new HBox();
    private final ImageView eventTypeImageView = new ImageView();
    private final Label descrLabel = new Label("", eventTypeImageView);
    private final Label countLabel = new Label();
    private final Button plusButton = ActionUtils.createButton(new ExpandClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
    private final Button minusButton = ActionUtils.createButton(new CollapseClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
    private final ImageView hashIV = new ImageView(HASH_PIN);
    private final ImageView tagIV = new ImageView(TAG);
    private final HBox infoHBox = new HBox(5, descrLabel, countLabel, hashIV, tagIV, minusButton, plusButton);

    private final Background highlightedBackground;
    private final Background defaultBackground;
    private final EventDetailChart chart;
    private final SleuthkitCase sleuthkitCase;
    private final EventStripe eventStripe;
    private final EventStripeNode parentNode;
    private final FilteredEventsModel eventsModel;
    private final Button hideButton;

    public EventStripeNode(EventDetailChart chart, EventStripe eventStripe, EventStripeNode parentEventNode) {
        this.eventStripe = eventStripe;
        this.parentNode = parentEventNode;
        this.chart = chart;
        descLOD.set(eventStripe.getDescriptionLoD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();
        final Color evtColor = getEventType().getColor();
        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        highlightedBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1.1, 1.1, .3), CORNER_RADII_3, Insets.EMPTY));

        setBackground(defaultBackground);

        setAlignment(Pos.TOP_LEFT);
        setMinHeight(24);
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        minWidthProperty().bind(clustersHBox.widthProperty());
        setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(eventStripe.getStartMillis())) - getLayoutXCompensation());

        if (eventStripe.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventStripe.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        EventDetailChart.HideDescriptionAction hideClusterAction = chart.new HideDescriptionAction(getDescription(), eventStripe.getDescriptionLoD());
        hideButton = ActionUtils.createButton(hideClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLoDButton(hideButton);
        configureLoDButton(plusButton);
        configureLoDButton(minusButton);

        //initialize info hbox
        infoHBox.getChildren().add(4, hideButton);

        infoHBox.setMinWidth(USE_PREF_SIZE);
        infoHBox.setPadding(new Insets(2, 5, 2, 5));
        infoHBox.setAlignment(Pos.CENTER_LEFT);
        //setup description label

        eventTypeImageView.setImage(getEventType().getFXImage());
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setMouseTransparent(true);

        //set up subnode pane sizing contraints
        subNodePane.setPrefHeight(USE_COMPUTED_SIZE);
        subNodePane.setMinHeight(USE_PREF_SIZE);
        subNodePane.setMinWidth(USE_PREF_SIZE);
        subNodePane.setMaxHeight(USE_PREF_SIZE);
        subNodePane.setMaxWidth(USE_PREF_SIZE);
        subNodePane.setPickOnBounds(false);

        Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));
        for (Range<Long> range : eventStripe.getRanges()) {
            Region clusterRegion = new Region();
            clusterRegion.setBorder(clusterBorder);
            clusterRegion.setBackground(highlightedBackground);
            clustersHBox.getChildren().addAll(clusterRegion, new Region());
        }
        clustersHBox.getChildren().remove(clustersHBox.getChildren().size() - 1);
        clustersHBox.setMaxWidth(USE_PREF_SIZE);

        final VBox internalVBox = new VBox(infoHBox, subNodePane);
        internalVBox.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(clustersHBox, internalVBox);

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
            toFront();
        });

        setOnMouseExited((MouseEvent e) -> {
            showDescriptionLoDControls(false);
        });
    }

    void showDescriptionLoDControls(final boolean showControls) {
        DropShadow dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(10, eventType.getColor()));
        clustersHBox.setEffect(showControls ? dropShadow : null);
        show(minusButton, showControls);
        show(plusButton, showControls);
        show(hideButton, showControls);
    }

    public void setSpanWidths(List<Double> spanWidths) {
        for (int i = 0; i < spanWidths.size(); i++) {
            Region spanRegion = (Region) clustersHBox.getChildren().get(i);

            Double w = spanWidths.get(i);
            spanRegion.setPrefWidth(w);
            spanRegion.setMaxWidth(w);
            spanRegion.setMinWidth(Math.max(2, w));
        }
    }

    public EventStripe getEventStripe() {
        return eventStripe;
    }

    Collection<EventStripe> makeBundlesFromClusters(List<EventCluster> eventClusters) {
        return eventClusters.stream().collect(
                Collectors.toMap(
                        EventCluster::getDescription, //key
                        EventStripe::new, //value
                        EventStripe::merge)//merge method
        ).values();
    }

    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventStripeNode.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}"})
    synchronized void installTooltip() {
        if (tooltip == null) {
            final Task<String> tooltTipTask = new Task<String>() {

                @Override
                protected String call() throws Exception {
                    HashMap<String, Long> hashSetCounts = new HashMap<>();
                    if (!eventStripe.getEventIDsWithHashHits().isEmpty()) {
                        hashSetCounts = new HashMap<>();
                        try {
                            for (TimeLineEvent tle : eventsModel.getEventsById(eventStripe.getEventIDsWithHashHits())) {
                                Set<String> hashSetNames = sleuthkitCase.getAbstractFileById(tle.getFileID()).getHashSetNames();
                                for (String hashSetName : hashSetNames) {
                                    hashSetCounts.merge(hashSetName, 1L, Long::sum);
                                }
                            }
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Error getting hashset hit info for event.", ex);
                        }
                    }

                    Map<String, Long> tagCounts = new HashMap<>();
                    if (getEventStripe().getEventIDsWithTags().isEmpty() == false) {
                        tagCounts.putAll(eventsModel.getTagCountsByTagName(getEventStripe().getEventIDsWithTags()));
                    }

                    String hashSetCountsString = hashSetCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));
                    String tagCountsString = tagCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));
                    return Bundle.EventStripeNode_tooltip_text(getEventStripe().getEventIDs().size(), getEventStripe().getEventType(), getEventStripe().getDescription(),
                            TimeLineController.getZonedFormatter().print(getEventStripe().getStartMillis()),
                            TimeLineController.getZonedFormatter().print(getEventStripe().getEndMillis() + 1000))
                            + (hashSetCountsString.isEmpty() ? "" : "\n\nHash Set Hits\n" + hashSetCountsString)
                            + (tagCountsString.isEmpty() ? "" : "\n\nTags\n" + tagCountsString);
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    try {
                        tooltip = new Tooltip(get());
                        Tooltip.install(EventStripeNode.this, tooltip);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Tooltip generation failed.", ex);
                        Tooltip.uninstall(EventStripeNode.this, tooltip);
                        tooltip = null;
                    }
                }
            };

            chart.getController().monitorTask(tooltTipTask);
        }
    }

    EventStripeNode getNodeForBundle(EventStripe cluster) {
        return new EventStripeNode(chart, cluster, this);
    }

    EventType getEventType() {
        return eventStripe.getEventType();
    }

    String getDescription() {
        return eventStripe.getDescription();
    }

    long getStartMillis() {
        return eventStripe.getStartMillis();
    }

    @SuppressWarnings("unchecked")
    public List<EventStripeNode> getSubNodes() {
        return subNodePane.getChildrenUnmodifiable().stream()
                .map(t -> (EventStripeNode) t)
                .collect(Collectors.toList());
    }

    /**
     * make a new filter intersecting the global filter with description and
     * type filters to restrict sub-clusters
     *
     */
    RootFilter getSubClusterFilter() {
        RootFilter subClusterFilter = eventsModel.filterProperty().get().copyOf();
        subClusterFilter.getSubFilters().addAll(
                new DescriptionFilter(eventStripe.getDescriptionLoD(), eventStripe.getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        return subClusterFilter;
    }

    /**
     * @param w the maximum width the description label should have
     */
    public void setDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    public void applySelectionEffect(boolean applied) {
        setBorder(applied ? SELECTION_BORDER : null);
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    public synchronized void applyHighlightEffect(boolean applied) {
        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;"); // NON-NLS
            setBackground(highlightedBackground);
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;"); // NON-NLS
            setBackground(defaultBackground);
        }
    }

    private DescriptionLoD getDescriptionLoD() {
        return descLOD.get();
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
        if (descLOD.get().withRelativeDetail(relativeDetail) == eventStripe.getDescriptionLoD()) {
            descLOD.set(eventStripe.getDescriptionLoD());
            clustersHBox.setVisible(true);
            chart.setRequiresLayout(true);
            chart.requestChartLayout();
        } else {
            clustersHBox.setVisible(false);

            // make new ZoomParams to query with
            final RootFilter subClusterFilter = getSubClusterFilter();
            /*
             * We need to extend end time because for the query by one second,
             * because it is treated as an open interval but we want to include
             * events at exactly the time of the last event in this cluster
             */
            final Interval subClusterSpan = new Interval(eventStripe.getStartMillis(), eventStripe.getEndMillis() + 1000);
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
                        if (loadedDescriptionLoD == eventStripe.getDescriptionLoD()) {
                            return Collections.emptySet();
                        }
                        bundles = eventsModel.getEventClusters(zoomParams.withDescrLOD(loadedDescriptionLoD)).stream()
                                .map(cluster -> cluster.withParent(getEventStripe()))
                                .collect(Collectors.toMap(
                                                EventCluster::getDescription, //key
                                                EventStripe::new, //value
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
                            clustersHBox.setVisible(true);
                        } else {
                            clustersHBox.setVisible(false);
                            chart.getEventBundles().addAll(bundles);
                            subNodePane.getChildren().setAll(bundles.stream()
                                    .map(EventStripeNode.this::getNodeForBundle)
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

    private double getLayoutXCompensation() {
        return (parentNode != null ? parentNode.getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    public void setDescriptionVisibility(DescriptionVisibility descrVis) {
        this.descrVis = descrVis;
        final int size = eventStripe.getEventIDs().size();

        switch (this.descrVis) {
            case HIDDEN:
                countLabel.setText("");
                descrLabel.setText("");
                break;
            case COUNT_ONLY:
                descrLabel.setText("");
                countLabel.setText(String.valueOf(size));
                break;
            default:
            case SHOWN:
                String description = eventStripe.getDescription();
                description = parentNode != null
                        ? "    ..." + StringUtils.substringAfter(description, parentNode.getDescription())
                        : description;
                descrLabel.setText(description);
                countLabel.setText(((size == 1) ? "" : " (" + size + ")")); // NON-NLS
                break;
        }
    }

    Set<Long> getEventsIDs() {
        return eventStripe.getEventIDs();
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
                    if (chart.selectedNodes.contains(EventStripeNode.this) == false) {
                        chart.selectedNodes.add(EventStripeNode.this);
                    }
                } else if (t.isShortcutDown()) {
                    chart.selectedNodes.removeAll(EventStripeNode.this);
                } else if (t.getClickCount() > 1) {
                    final DescriptionLoD next = descLOD.get().moreDetailed();
                    if (next != null) {
                        loadSubBundles(DescriptionLoD.RelativeDetail.MORE);

                    }
                } else {
                    chart.selectedNodes.setAll(EventStripeNode.this);
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
                contextMenu.show(EventStripeNode.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }

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
            disabledProperty().bind(Bindings.createBooleanBinding(() -> nonNull(eventStripe) && descLOD.get() == eventStripe.getDescriptionLoD(), descLOD));
        }
    }
}
