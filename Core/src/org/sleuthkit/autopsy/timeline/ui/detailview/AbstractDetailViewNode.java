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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.SleuthkitCase;

public abstract class AbstractDetailViewNode< T extends EventBundle, S extends AbstractDetailViewNode<T, S>> extends StackPane implements DetailViewNode<AbstractDetailViewNode<T, S>> {

    static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png");
    static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS
    static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS
    static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/funnel.png"); // NON-NLS
    static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS
    static final CornerRadii CORNER_RADII = new CornerRadii(3);
    /**
     * the border to apply when this node is 'selected'
     */
    static final Border selectionBorder = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII, new BorderWidths(2)));
    private static final Logger LOGGER = Logger.getLogger(AbstractDetailViewNode.class
            .getName());

    static void configureLODButton(Button b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    private final Map<EventType, DropShadow> dropShadowMap = new HashMap<>();
    final Color evtColor;

    private final S parentNode;
    private DescriptionVisibility descrVis;

    /**
     * Pane that contains AggregateEventNodes of any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
    private final Pane subNodePane = new Pane();

    Pane getSubNodePane() {
        return subNodePane;
    }

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

    private final T eventBundle;
    private final EventDetailChart chart;
    private final SleuthkitCase sleuthkitCase;

    SleuthkitCase getSleuthkitCase() {
        return sleuthkitCase;
    }

    FilteredEventsModel getEventsModel() {
        return eventsModel;
    }
    private final FilteredEventsModel eventsModel;

    private final Button hideButton;
    private final Button plusButton;
    private final Button minusButton;

    private final SimpleObjectProperty<DescriptionLOD> descLOD = new SimpleObjectProperty<>();
    final HBox header;

    Region getSpacer() {
        return spacer;
    }

    private final Region spacer = new Region();

    private final CollapseClusterAction collapseClusterAction;
    private final ExpandClusterAction expandClusterAction;
    private final HideClusterAction hideClusterAction;

    public AbstractDetailViewNode(EventDetailChart chart, T bundle, S parentEventNode) {
        this.eventBundle = bundle;
        this.parentNode = parentEventNode;
        this.chart = chart;
        descLOD.set(bundle.getDescriptionLOD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();
        ImageView hashIV = new ImageView(HASH_PIN);
        ImageView tagIV = new ImageView(TAG);
        if (eventBundle.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventBundle.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        hideClusterAction = new HideClusterAction();
        hideButton = ActionUtils.createButton(hideClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLODButton(hideButton);

        expandClusterAction = new ExpandClusterAction();
        plusButton = ActionUtils.createButton(expandClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLODButton(plusButton);

        collapseClusterAction = new CollapseClusterAction();
        minusButton = ActionUtils.createButton(collapseClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLODButton(minusButton);

        HBox.setHgrow(spacer, Priority.ALWAYS);
        header = new HBox(getDescrLabel(), getCountLabel(), hashIV, tagIV, hideButton, minusButton, plusButton);

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
        setOnMouseClicked(new EventMouseHandler());

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

        setLayoutX(getChart().getXAxis().getDisplayPosition(new DateTime(eventBundle.getStartMillis())) - getLayoutXCompensation());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<S> getSubNodes() {
        return subNodePane.getChildrenUnmodifiable().stream()
                .map(t -> (S) t)
                .collect(Collectors.toList());
    }

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    @Override
    public void applySelectionEffect(boolean applied) {
        Platform.runLater(() -> {
            if (applied) {
                setBorder(selectionBorder);
            } else {
                setBorder(null);
            }
        });
    }

    /**
     *
     * @param showControls the value of par
     */
    void showDescriptionLoDControls(final boolean showControls) {
        DropShadow dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(10, eventType.getColor()));
        getSpanFillNode().setEffect(showControls ? dropShadow : null);
        show(minusButton, showControls);
        show(plusButton, showControls);
        show(hideButton, showControls);
    }

    /**
     * make a new filter intersecting the global filter with description and
     * type filters to restrict sub-clusters
     *
     */
    RootFilter getSubClusterFilter() {
        RootFilter subClusterFilter = eventsModel.filterProperty().get().copyOf();
        subClusterFilter.getSubFilters().addAll(
                new DescriptionFilter(getEventBundle().getDescriptionLOD(), getDescription(), DescriptionFilter.FilterMode.INCLUDE),
                new TypeFilter(getEventType()));
        return subClusterFilter;
    }

    abstract Collection<T> makeBundlesFromClusters(List<EventCluster> eventClusters);

    abstract void showSpans(final boolean showSpans);

    /**
     * @param w the maximum width the description label should have
     */
    @Override
    public void setDescriptionWidth(double w) {
        getDescrLabel().setMaxWidth(w);
    }

    abstract void installTooltip();

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    @Override
    public synchronized void applyHighlightEffect(boolean applied) {
        if (applied) {
            getDescrLabel().setStyle("-fx-font-weight: bold;"); // NON-NLS
            getSpanFillNode().setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .3), CORNER_RADII, Insets.EMPTY)));
            setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .2), CORNER_RADII, Insets.EMPTY)));
        } else {
            getDescrLabel().setStyle("-fx-font-weight: normal;"); // NON-NLS
            getSpanFillNode().setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
            setBackground(new Background(new BackgroundFill(getEventType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        }
    }

    String getDisplayedDescription() {
        return getDescrLabel().getText();
    }

    abstract Region getSpanFillNode();

    Button getPlusButton() {
        return plusButton;
    }

    Button getMinusButton() {
        return minusButton;
    }

    public final Label getDescrLabel() {
        return descrLabel;
    }

    final public Label getCountLabel() {
        return countLabel;
    }

    public S getParentNode() {
        return parentNode;
    }

    @Override
    public final T getEventBundle() {
        return eventBundle;
    }

    public final EventDetailChart getChart() {
        return chart;
    }

    public DescriptionLOD getDescLOD() {
        return descLOD.get();
    }

    /**
     * loads sub-bundles at the given Description LOD, continues
     *
     * @param requestedDescrLoD
     * @param expand
     */
    private synchronized void loadSubBundles(DescriptionLOD.RelativeDetail relativeDetail) {
        subNodePane.getChildren().clear();
        if (descLOD.get().withRelativeDetail(relativeDetail) == getEventBundle().getDescriptionLOD()) {
            descLOD.set(getEventBundle().getDescriptionLOD());
            showSpans(true);
            chart.setRequiresLayout(true);
            chart.requestChartLayout();
        } else {
            showSpans(false);

            // make new ZoomParams to query with
            final RootFilter subClusterFilter = getSubClusterFilter();
            /*
             * We need to extend end time because for the query by one second,
             * because it is treated as an open interval but we want to include
             * events at exactly the time of the last event in this cluster
             */
            final Interval subClusterSpan = new Interval(getEventBundle().getStartMillis(), getEventBundle().getEndMillis() + 1000);
            final EventTypeZoomLevel eventTypeZoomLevel = eventsModel.eventTypeZoomProperty().get();
            final ZoomParams zoomParams = new ZoomParams(subClusterSpan, eventTypeZoomLevel, subClusterFilter, getDescLOD());

            LoggedTask<List<S>> loggedTask;
            loggedTask = new LoggedTask<List<S>>(
                    NbBundle.getMessage(this.getClass(), "AggregateEventNode.loggedTask.name"), true) {
                        private Collection<T> bundles;
                        private volatile DescriptionLOD loadedDescriptionLoD = getDescLOD().withRelativeDetail(relativeDetail);
                        private DescriptionLOD next = loadedDescriptionLoD;

                        @Override
                        protected List<S> call() throws Exception {
                            do {
                                loadedDescriptionLoD = next;
                                if (loadedDescriptionLoD == getEventBundle().getDescriptionLOD()) {
                                    return Collections.emptyList();
                                }
                                bundles = loadBundles();
                                next = loadedDescriptionLoD.withRelativeDetail(relativeDetail);
                            } while (bundles.size() == 1 && nonNull(next));

                            // return list of AbstractDetailViewNodes representing sub-bundles
                            return bundles.stream()
                            .map(AbstractDetailViewNode.this::getNodeForBundle)
                            .collect(Collectors.toList());
                        }

                        private Collection<T> loadBundles() {
                            return makeBundlesFromClusters(eventsModel.getEventClusters(zoomParams.withDescrLOD(loadedDescriptionLoD)));
                        }

                        @Override
                        protected void succeeded() {
                            chart.setCursor(Cursor.WAIT);
                            try {
                                List<S> subBundleNodes = get();
                                if (subBundleNodes.isEmpty()) {
                                    showSpans(true);
                                } else {
                                    showSpans(false);
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

    final double getLayoutXCompensation() {
        return (getParentNode() != null ? getParentNode().getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    @Override
    public final void setDescriptionVisibility(DescriptionVisibility descrVis) {
        this.descrVis = descrVis;
        final int size = getEventBundle().getEventIDs().size();

        switch (this.descrVis) {
            case COUNT_ONLY:
                descrLabel.setText("");
                countLabel.setText(String.valueOf(size));
                break;
            case HIDDEN:
                countLabel.setText("");
                descrLabel.setText("");
                break;
            default:
            case SHOWN:
                String description = getEventBundle().getDescription();
                description = getParentNode() != null
                        ? "    ..." + StringUtils.substringAfter(description, getParentNode().getDescription())
                        : description;
                descrLabel.setText(description);
                countLabel.setText(((size == 1) ? "" : " (" + size + ")")); // NON-NLS
                break;
        }
    }

    abstract S getNodeForBundle(T bundle);

    /**
     * event handler used for mouse events on {@link AggregateEventNode}s
     */
    private class EventMouseHandler implements EventHandler<MouseEvent> {

        private ContextMenu contextMenu;

        @Override
        public void handle(MouseEvent t) {

            if (t.getButton() == MouseButton.PRIMARY) {
                t.consume();
                if (t.isShiftDown()) {
                    if (chart.selectedNodes.contains(AbstractDetailViewNode.this) == false) {
                        chart.selectedNodes.add(AbstractDetailViewNode.this);
                    }
                } else if (t.isShortcutDown()) {
                    chart.selectedNodes.removeAll(AbstractDetailViewNode.this);
                } else if (t.getClickCount() > 1) {
                    final DescriptionLOD next = descLOD.get().moreDetailed();
                    if (next != null) {
                        loadSubBundles(DescriptionLOD.RelativeDetail.MORE);

                    }
                } else {
                    chart.selectedNodes.setAll(AbstractDetailViewNode.this);
                }
                t.consume();
            } else if (t.isPopupTrigger()) {
                ContextMenu chartContextMenu = chart.getChartContextMenu(t);
                if (contextMenu == null) {
                    contextMenu = new ContextMenu();
                    contextMenu.setAutoHide(true);

                    contextMenu.getItems().add(ActionUtils.createMenuItem(expandClusterAction));
                    contextMenu.getItems().add(ActionUtils.createMenuItem(collapseClusterAction));
                    contextMenu.getItems().add(ActionUtils.createMenuItem(hideClusterAction));

                    contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(AbstractDetailViewNode.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }

    private class ExpandClusterAction extends Action {

        ExpandClusterAction() {
            super("Expand");

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

        CollapseClusterAction() {
            super("Collapse");

            setGraphic(new ImageView(MINUS));
            setEventHandler((ActionEvent t) -> {
                final DescriptionLOD previous = descLOD.get().lessDetailed();
                if (previous != null) {
                    loadSubBundles(DescriptionLOD.RelativeDetail.LESS);
                }
            });
            disabledProperty().bind(descLOD.isEqualTo(getEventBundle().getDescriptionLOD()));
        }
    }

    private class HideClusterAction extends Action {

        HideClusterAction() {
            super("Hide");
            setGraphic(new ImageView(HIDE));
            setEventHandler((ActionEvent t) -> {
                DescriptionFilter descriptionFilter = new DescriptionFilter(getDescLOD(), getDescription(), DescriptionFilter.FilterMode.EXCLUDE);
                chart.getBundleFilters().add(descriptionFilter);
                RootFilter rootFilter = eventsModel.getFilter();
                rootFilter.getSubFilters().add(descriptionFilter);
                chart.getController().pushFilters(rootFilter.copyOf());
                chart.setRequiresLayout(true);
                chart.requestChartLayout();
            });
        }
    }
}
