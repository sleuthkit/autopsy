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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.Observable;
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
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
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
import static org.sleuthkit.autopsy.timeline.ui.detailview.Bundle.EventStripeNode_loggedTask_name;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
final public class EventStripeNode extends StackPane {

    private static final Logger LOGGER = Logger.getLogger(EventStripeNode.class.getName());
    private static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N
    private static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    private static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N

    private static final CornerRadii CORNER_RADII = new CornerRadii(3);

    /**
     * the border to apply when this node is selected
     */
    private static final Border SELECTION_BORDER = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII, new BorderWidths(2)));

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

    private final HBox rangesHBox = new HBox();
    private final EventStripe eventStripe;
    private Tooltip tooltip;
    private final Map<EventType, DropShadow> dropShadowMap = new HashMap<>();
    final Color evtColor;

    private final EventStripeNode parentNode;
    private DescriptionVisibility descrVis;

    /**
     * Pane that contains AggregateEventNodes of any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
    private final Pane subNodePane = new Pane();

    /**
     * The ImageView used to show the icon for this node's event's type
     */
    private final ImageView eventTypeImageView = new ImageView();

    /**
     * The label used to display this node's event's description
     */
    final Label descrLabel = new Label();

    /**
     * The label used to display this node's event count
     */
    final Label countLabel = new Label();

    private final EventDetailChart chart;
    private final SleuthkitCase sleuthkitCase;

    private final FilteredEventsModel eventsModel;

    private final Button plusButton;
    private final Button minusButton;

    private final SimpleObjectProperty<DescriptionLOD> descLOD = new SimpleObjectProperty<>();
    final HBox header;

    private final CollapseClusterAction collapseClusterAction;
    private final ExpandClusterAction expandClusterAction;

    public EventStripeNode(EventDetailChart chart, EventStripe eventStripe, EventStripeNode parentEventNode) {
        this.eventStripe = eventStripe;
        this.parentNode = parentEventNode;
        this.chart = chart;
        descLOD.set(eventStripe.getDescriptionLOD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();
        ImageView hashIV = new ImageView(HASH_PIN);
        ImageView tagIV = new ImageView(TAG);
        if (eventStripe.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventStripe.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        expandClusterAction = new ExpandClusterAction();
        plusButton = ActionUtils.createButton(expandClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLoDButton(plusButton);

        collapseClusterAction = new CollapseClusterAction();
        minusButton = ActionUtils.createButton(collapseClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLoDButton(minusButton);

        header = new HBox(5, descrLabel, countLabel, hashIV, tagIV, minusButton, plusButton);

        header.setMinWidth(USE_PREF_SIZE);
        header.setPadding(new Insets(2, 5, 2, 5));
        header.setAlignment(Pos.CENTER_LEFT);
        //setup description label
        evtColor = getEventType().getColor();

        eventTypeImageView.setImage(getEventType().getFXImage());
        descrLabel.setGraphic(eventTypeImageView);
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

        setAlignment(Pos.TOP_LEFT);
        setMinHeight(24);
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setOnMouseClicked(new MouseHandler());

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            //defer tooltip creation till needed, this had a surprisingly large impact on speed of loading the chart
            installTooltip();
            showDescriptionLoDControls(true);
            toFront();
        });

        setOnMouseExited((MouseEvent e) -> {
            showDescriptionLoDControls(false);
        });
        setCursor(Cursor.HAND);

        setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));

        setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(eventStripe.getStartMillis())) - getLayoutXCompensation());

        minWidthProperty().bind(rangesHBox.widthProperty());
        final VBox internalVBox = new VBox(header, subNodePane);
        internalVBox.setAlignment(Pos.CENTER_LEFT);

        for (Range<Long> range : eventStripe.getRanges()) {
            Region rangeRegion = new Region();
            rangeRegion.setStyle("-fx-border-width:2 1 2 1; -fx-border-radius: 1; -fx-border-color: " + ColorUtilities.getRGBCode(evtColor.deriveColor(0, 1, 1, .3)) + ";"); // NON-NLS
            rangeRegion.setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .2), CORNER_RADII, Insets.EMPTY)));
            rangesHBox.getChildren().addAll(rangeRegion, new Region());
        }
        rangesHBox.getChildren().remove(rangesHBox.getChildren().size() - 1);
        rangesHBox.setMaxWidth(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        getChildren().addAll(rangesHBox, internalVBox);
    }

    /**
     *
     * @param showControls the value of par
     */
    void showDescriptionLoDControls(final boolean showControls) {
        DropShadow dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(10, eventType.getColor()));
        rangesHBox.setEffect(showControls ? dropShadow : null);
        show(minusButton, showControls);
        show(plusButton, showControls);
    }

    public void setSpanWidths(List<Double> spanWidths) {
        for (int i = 0; i < spanWidths.size(); i++) {
            Region spanRegion = (Region) rangesHBox.getChildren().get(i);
            Double w = spanWidths.get(i);
            spanRegion.setPrefWidth(w);
            spanRegion.setMaxWidth(w);
            spanRegion.setMinWidth(Math.max(2, w));
        }
    }

    EventStripe getStripe() {
        return eventStripe;
    }

    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventStripeNode.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}"})
    synchronized void installTooltip() {
        if (tooltip == null) {
            setCursor(Cursor.WAIT);
            final Task<String> tooltTipTask = new Task<String>() {

                @Override
                protected String call() throws Exception {
                    HashMap<String, Long> hashSetCounts = new HashMap<>();
                    if (!getStripe().getEventIDsWithHashHits().isEmpty()) {
                        hashSetCounts = new HashMap<>();
                        try {
                            for (TimeLineEvent tle : eventsModel.getEventsById(getStripe().getEventIDsWithHashHits())) {
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

            tooltTipTask.stateProperty().addListener((Observable observable) -> {
                if (tooltTipTask.isDone()) {
                    setCursor(null);
                }
            });

            chart.getController().monitorTask(tooltTipTask);
        }
    }

    EventStripeNode getNodeForBundle(EventStripe cluster
    ) {
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
                new DescriptionFilter(eventStripe.getDescriptionLOD(), eventStripe.getDescription()),
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
            rangesHBox.setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .3), CORNER_RADII, Insets.EMPTY)));
            setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .2), CORNER_RADII, Insets.EMPTY)));
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;"); // NON-NLS
            rangesHBox.setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
            setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        }
    }

    private DescriptionLOD getDescriptionLoD() {
        return descLOD.get();
    }

    /**
     * loads sub-bundles at the given Description LOD, continues
     *
     * @param requestedDescrLoD
     * @param expand
     */
    @NbBundle.Messages(value = "EventStripeNode.loggedTask.name=Load sub clusters")
    private synchronized void loadSubBundles(DescriptionLOD.RelativeDetail relativeDetail) {
        subNodePane.getChildren().clear();
        if (descLOD.get().withRelativeDetail(relativeDetail) == eventStripe.getDescriptionLOD()) {
            descLOD.set(eventStripe.getDescriptionLOD());
            rangesHBox.setVisible(true);
            chart.setRequiresLayout(true);
            chart.requestChartLayout();
        } else {
            rangesHBox.setVisible(false);

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

            LoggedTask<Set<EventStripeNode>> loggedTask = new LoggedTask<Set<EventStripeNode>>(
                    EventStripeNode_loggedTask_name(), true) {
                        private Collection<EventStripe> bundles;
                        private volatile DescriptionLOD loadedDescriptionLoD = getDescriptionLoD().withRelativeDetail(relativeDetail);
                        private DescriptionLOD next = loadedDescriptionLoD;

                        @Override
                        protected Set<EventStripeNode> call() throws Exception {
                            do {
                                loadedDescriptionLoD = next;
                                if (loadedDescriptionLoD == eventStripe.getDescriptionLOD()) {
                                    return Collections.emptySet();
                                }
                                bundles = eventsModel.getEventClusters(zoomParams.withDescrLOD(loadedDescriptionLoD)).stream()
                                .collect(Collectors.toMap(
                                                EventCluster::getDescription, //key
                                                EventStripe::new, //value
                                                EventStripe::merge) //merge method
                                ).values();
                                next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                            } while (bundles.size() == 1 && nonNull(next));

                            // return list of AbstractEventStripeNodes representing sub-bundles
                            return bundles.stream()
                            .map(EventStripeNode.this::getNodeForBundle)
                            .collect(Collectors.toSet());
                        }

                        @Override
                        protected void succeeded() {
                            chart.setCursor(Cursor.WAIT);
                            try {
                                Set<EventStripeNode> subBundleNodes = get();
                                if (subBundleNodes.isEmpty()) {
                                    rangesHBox.setVisible(true);
                                } else {
                                    rangesHBox.setVisible(false);
                                }
                                descLOD.set(loadedDescriptionLoD);
                                //assign subNodes and request chart layout
                                subNodePane.getChildren().setAll(subBundleNodes);
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

    public EventStripe getEventStripe() {
        return eventStripe;
    }

    Set<Long> getEventsIDs() {
        return eventStripe.getEventIDs();

    }

    /**
     * event handler used for mouse events on {@link EventStripeNode}s
     */
    private class MouseHandler implements EventHandler<MouseEvent> {

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
                    final DescriptionLOD next = descLOD.get().moreDetailed();
                    if (next != null) {
                        loadSubBundles(DescriptionLOD.RelativeDetail.MORE);

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

                    contextMenu.getItems().add(ActionUtils.createMenuItem(expandClusterAction));
                    contextMenu.getItems().add(ActionUtils.createMenuItem(collapseClusterAction));

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
                final DescriptionLOD next = descLOD.get().moreDetailed();
                if (next != null) {
                    loadSubBundles(DescriptionLOD.RelativeDetail.MORE);

                }
            });
            disabledProperty().bind(descLOD.isEqualTo(DescriptionLOD.FULL));
        }
    }

    private class CollapseClusterAction extends Action {

        @NbBundle.Messages("CollapseClusterAction.text=Collapse")
        CollapseClusterAction() {
            super(Bundle.CollapseClusterAction_text());

            setGraphic(new ImageView(MINUS));
            setEventHandler((ActionEvent t) -> {
                final DescriptionLOD previous = descLOD.get().lessDetailed();
                if (previous != null) {
                    loadSubBundles(DescriptionLOD.RelativeDetail.LESS);
                }
            });
            disabledProperty().bind(descLOD.isEqualTo(eventStripe.getDescriptionLOD()));
        }
    }
}
