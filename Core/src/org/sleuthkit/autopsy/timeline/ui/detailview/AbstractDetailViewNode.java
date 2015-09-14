/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
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
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.SleuthkitCase;

public abstract class AbstractDetailViewNode< T extends EventBundle, S extends AbstractDetailViewNode<T, S>> extends StackPane implements DetailViewNode<AbstractDetailViewNode<T, S>> {

    static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png");
    static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS
    static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS
    static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS
    static final CornerRadii CORNER_RADII = new CornerRadii(3);
    Map<EventType, DropShadow> dropShadowMap = new HashMap<>();

    static void configureLODButton(Button b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }
    /**
     * the border to apply when this node is 'selected'
     */
    static final Border selectionBorder = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII, new BorderWidths(2)));
    final Color evtColor;

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

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    final ImageView hashIV = new ImageView(HASH_PIN);
    final ImageView tagIV = new ImageView(TAG);
    private final S parentNode;
    DescriptionVisibility descrVis;

    /**
     * Pane that contains AggregateEventNodes of any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
    final Pane subNodePane = new Pane();

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
    final SleuthkitCase sleuthkitCase;
    final FilteredEventsModel eventsModel;

    final Button plusButton = new Button(null, new ImageView(PLUS)) {
        {
            configureLODButton(this);
        }

    };

    final Button minusButton = new Button(null, new ImageView(MINUS)) {
        {
            configureLODButton(this);
        }
    };
    SimpleObjectProperty<DescriptionLOD> descLOD = new SimpleObjectProperty<>();
    final HBox header;

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
    }
    final Region spacer = new Region();

    RootFilter getSubClusterFilter() {
        RootFilter combinedFilter = eventsModel.filterProperty().get().copyOf();
        //make a new filter intersecting the global filter with text(description) and type filters to restrict sub-clusters
        combinedFilter.getSubFilters().addAll(new TextFilter(getEventBundle().getDescription()),
                new TypeFilter(getEventType()));
        return combinedFilter;
    }

    abstract Collection<T> makeBundlesFromClusters(List<EventCluster> eventClusters);

    abstract void showSpans(final boolean showSpans);

    public AbstractDetailViewNode(EventDetailChart chart, T bundle, S parentEventNode) {
        this.eventBundle = bundle;
        this.parentNode = parentEventNode;
        this.chart = chart;
        descLOD.set(bundle.getDescriptionLOD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();

        if (eventBundle.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventBundle.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header = new HBox(getDescrLabel(), getCountLabel(), hashIV, tagIV, /*spacer,*/ minusButton, plusButton);

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

        plusButton.disableProperty().bind(descLOD.isEqualTo(DescriptionLOD.FULL));
        minusButton.disableProperty().bind(descLOD.isEqualTo(getEventBundle().getDescriptionLOD()));

        plusButton.setOnMouseClicked(e -> {
            final DescriptionLOD next = descLOD.get().next();
            if (next != null) {
                loadSubClusters(next);
                descLOD.set(next);
            }
        });
        minusButton.setOnMouseClicked(e -> {
            final DescriptionLOD previous = descLOD.get().previous();
            if (previous != null) {
                loadSubClusters(previous);
                descLOD.set(previous);
            }
        });

        setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));

        setLayoutX(getChart().getXAxis().getDisplayPosition(new DateTime(eventBundle.getStartMillis())) - getLayoutXCompensation());
    }

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

    public final Label getCountLabel() {
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
    private static final Logger LOGGER = Logger.getLogger(AbstractDetailViewNode.class.getName());

    /**
     * loads sub-clusters at the given Description LOD
     *
     * @param newDescriptionLOD
     */
    final synchronized void loadSubClusters(DescriptionLOD newDescriptionLOD) {
        subNodePane.getChildren().clear();
        if (newDescriptionLOD == getEventBundle().getDescriptionLOD()) {
            showSpans(true);
            getChart().setRequiresLayout(true);
            getChart().requestChartLayout();
        } else {
            showSpans(false);
            RootFilter combinedFilter = getSubClusterFilter();

            //make a new end inclusive span (to 'filter' with)
            final Interval span = new Interval(getEventBundle().getStartMillis(), getEventBundle().getEndMillis() + 1000);

            //make a task to load the subnodes
            LoggedTask<List<S>> loggedTask = new LoggedTask<List<S>>(
                    NbBundle.getMessage(this.getClass(), "AggregateEventNode.loggedTask.name"), true) {

                        @Override
                        protected List<S> call() throws Exception {
                            //query for the sub-clusters
                            List<EventCluster> aggregatedEvents = eventsModel.getAggregatedEvents(new ZoomParams(span,
                                            eventsModel.eventTypeZoomProperty().get(),
                                            combinedFilter,
                                            newDescriptionLOD));
                            
                            return makeBundlesFromClusters(aggregatedEvents).stream()
                            .map(aggEvent -> {
                                return getNodeForCluser(aggEvent);
                            }).collect(Collectors.toList()); // return list of AggregateEventNodes representing subclusters
                        }

                        @Override
                        protected void succeeded() {
                            try {
                                getChart().setCursor(Cursor.WAIT);
                                //assign subNodes and request chart layout
                                subNodePane.getChildren().setAll(get());
                                setDescriptionVisibility(descrVis);
                                getChart().setRequiresLayout(true);
                                getChart().requestChartLayout();
                                getChart().setCursor(null);
                            } catch (InterruptedException | ExecutionException ex) {
                                LOGGER.log(Level.SEVERE, "Error loading subnodes", ex);
                            }
                        }
                    };

            //start task
            getChart().getController().monitorTask(loggedTask);
        }
    }

    final double getLayoutXCompensation() {
        return (getParentNode() != null ? getParentNode().getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    @Override
    final public void setDescriptionVisibility(DescriptionVisibility descrVis) {
        this.descrVis = descrVis;
        final int size = getEventBundle().getEventIDs().size();

        switch (descrVis) {
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

    /**
     * event handler used for mouse events on {@link AggregateEventNode}s
     */
    private class EventMouseHandler implements EventHandler<MouseEvent> {

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
                    final DescriptionLOD next = descLOD.get().next();
                    if (next != null) {
                        loadSubClusters(next);
                        descLOD.set(next);
                    }
                } else {
                    chart.selectedNodes.setAll(AbstractDetailViewNode.this);
                }

            }
        }
    }

    abstract S getNodeForCluser(T cluster);

}
