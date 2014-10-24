/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.events.AggregateEvent;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/** Represents an {@link AggregateEvent} in a {@link EventDetailChart}. */
public class AggregateEventNode extends StackPane {

    private final static Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png");
    private final static Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png");

    private static final CornerRadii CORNER_RADII = new CornerRadii(3);

    /** the border to apply when this node is 'selected' */
    private static final Border selectionBorder = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII, new BorderWidths(2)));

    /** The event this AggregateEventNode represents visually */
    private final AggregateEvent event;

    private final AggregateEventNode parentEventNode;

    /** the region that represents the time span of this node's event */
    private final Region spanRegion = new Region();

    /** The label used to display this node's event's description */
    private final Label descrLabel = new Label();

    /** The label used to display this node's event count */
    private final Label countLabel = new Label();

    /** The IamgeView used to show the icon for this node's event's type */
    private final ImageView eventTypeImageView = new ImageView();

    /** Pane that contains AggregateEventNodes of any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart */
    private final Pane subNodePane = new Pane();

    /** the context menu that with the slider that controls subnode/event
     * display
     *
     * //TODO: move more of the control of subnodes/events here and out
     * of EventDetail Chart */
    private final SimpleObjectProperty<ContextMenu> contextMenu = new SimpleObjectProperty<>();

    /** the Background used to fill the spanRegion, this varies epending on the
     * selected/highlighted state of this node in its parent EventDetailChart */
    private Background spanFill;

    private final Button plusButton = new Button(null, new ImageView(PLUS)) {
        {
            setMinSize(16, 16);
            setMaxSize(16, 16);
            setPrefSize(16, 16);
        }
    };
    private final Button minusButton = new Button(null, new ImageView(MINUS)) {
        {
            setMinSize(16, 16);
            setMaxSize(16, 16);
            setPrefSize(16, 16);
        }
    };
    private final EventDetailChart chart;

    private SimpleObjectProperty<DescriptionLOD> descLOD = new SimpleObjectProperty<>();
    private DescriptionVisibility descrVis;

    public AggregateEventNode(final AggregateEvent event, AggregateEventNode parentEventNode, EventDetailChart chart) {
        this.event = event;
        descLOD.set(event.getLOD());
        this.parentEventNode = parentEventNode;
        this.chart = chart;
        final Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        final HBox hBox = new HBox(descrLabel, countLabel, region, minusButton, plusButton);
        hBox.setPrefWidth(USE_COMPUTED_SIZE);
        hBox.setMinWidth(USE_PREF_SIZE);
        hBox.setPadding(new Insets(2, 5, 2, 5));
        hBox.setAlignment(Pos.CENTER_LEFT);

        minusButton.setVisible(false);
        plusButton.setVisible(false);
        minusButton.setManaged(false);
        plusButton.setManaged(false);
        final BorderPane borderPane = new BorderPane(subNodePane, hBox, null, null, null);
        BorderPane.setAlignment(subNodePane, Pos.TOP_LEFT);
        borderPane.setPrefWidth(USE_COMPUTED_SIZE);

        getChildren().addAll(spanRegion, borderPane);

        setAlignment(Pos.TOP_LEFT);
        setMinHeight(24);
        minWidthProperty().bind(spanRegion.widthProperty());
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);

        //set up subnode pane sizing contraints
        subNodePane.setPrefHeight(USE_COMPUTED_SIZE);
        subNodePane.setMinHeight(USE_PREF_SIZE);
        subNodePane.setMinWidth(USE_PREF_SIZE);
        subNodePane.setMaxHeight(USE_PREF_SIZE);
        subNodePane.setMaxWidth(USE_PREF_SIZE);
        subNodePane.setPickOnBounds(false);

        //setup description label
        eventTypeImageView.setImage(event.getType().getFXImage());
        descrLabel.setGraphic(eventTypeImageView);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);

        descrLabel.setMouseTransparent(true);
        setDescriptionVisibility(chart.getDescrVisibility().get());

        //setup backgrounds
        final Color evtColor = event.getType().getColor();
        spanFill = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY));
        setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        setCursor(Cursor.HAND);
        spanRegion.setStyle("-fx-border-width:2 0 2 2; -fx-border-radius: 2; -fx-border-color: " + ColorUtilities.getRGBCode(evtColor) + ";");
        spanRegion.setBackground(spanFill);

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            //defer tooltip creation till needed, this had a surprisingly large impact on speed of loading the chart
            installTooltip();
            spanRegion.setEffect(new DropShadow(10, evtColor));
            minusButton.setVisible(true);
            plusButton.setVisible(true);
            minusButton.setManaged(true);
            plusButton.setManaged(true);
            toFront();

        });

        setOnMouseExited((MouseEvent e) -> {
            spanRegion.setEffect(null);
            minusButton.setVisible(false);
            plusButton.setVisible(false);
            minusButton.setManaged(false);
            plusButton.setManaged(false);

        });

        setOnMouseClicked(new EventMouseHandler());

        plusButton.disableProperty().bind(descLOD.isEqualTo(DescriptionLOD.FULL));
        minusButton.disableProperty().bind(descLOD.isEqualTo(event.getLOD()));

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
    }

    private void installTooltip() {
        Tooltip.install(AggregateEventNode.this, new Tooltip(getEvent().getEventIDs().size() + " " + getEvent().getType() + " events\n"
                + getEvent().getDescription()
                + "\nbetween " + getEvent().getSpan().getStart().toString(TimeLineController.getZonedFormatter())
                + "\nand      " + getEvent().getSpan().getEnd().toString(TimeLineController.getZonedFormatter())));
    }

    public Pane getSubNodePane() {
        return subNodePane;
    }

    public AggregateEvent getEvent() {
        return event;
    }

    /**
     * sets the width of the {@link Region} with border and background used to
     * indicate the temporal span of this aggregate event
     *
     * @param w
     */
    public void setSpanWidth(double w) {
        spanRegion.setPrefWidth(w);
        spanRegion.setMaxWidth(w);
        spanRegion.setMinWidth(Math.max(2, w));
    }

    /**
     *
     * @param w the maximum width the description label should have
     */
    public void setDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    /** @param descrVis the level of description that should be displayed */
    final void setDescriptionVisibility(DescriptionVisibility descrVis) {
        this.descrVis = descrVis;
        final int size = event.getEventIDs().size();

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
                String description = event.getDescription();
                description = parentEventNode != null
                        ? "    ..." + StringUtils.substringAfter(description, parentEventNode.getEvent().getDescription())
                        : description;
                descrLabel.setText(description);
                countLabel.setText(((size == 1) ? "" : " (" + size + ")"));
                break;
        }
    }

    /** apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    void applySelectionEffect(final boolean applied) {
        Platform.runLater(() -> {
            if (applied) {
                setBorder(selectionBorder);
            } else {
                setBorder(null);
            }
        });
    }

    /** apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    void applyHighlightEffect(boolean applied) {

        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;");
            spanFill = new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .3), CORNER_RADII, Insets.EMPTY));
            spanRegion.setBackground(spanFill);
            setBackground(new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .2), CORNER_RADII, Insets.EMPTY)));
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;");
            spanFill = new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY));
            spanRegion.setBackground(spanFill);
            setBackground(new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        }
    }

    String getDisplayedDescription() {
        return descrLabel.getText();
    }

    double getLayoutXCompensation() {
        return (parentEventNode != null ? parentEventNode.getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    /**
     * @return the contextMenu
     */
    public ContextMenu getContextMenu() {
        return contextMenu.get();
    }

    /**
     * @param contextMenu the contextMenu to set
     */
    public void setContextMenu(ContextMenu contextMenu) {
        this.contextMenu.set(contextMenu);
    }

    /**
     * loads sub-clusters at the given Description LOD
     *
     * @param newLOD
     */
    private void loadSubClusters(DescriptionLOD newLOD) {
        getSubNodePane().getChildren().clear();
        if (newLOD == event.getLOD()) {
            getSubNodePane().getChildren().clear();
            chart.setRequiresLayout(true);
            chart.requestChartLayout();
        } else {
            //make a new filter intersecting the global filter with text(description) and type filters to restrict sub-clusters
            final Filter combinedFilter = Filter.intersect(new Filter[]{new TextFilter(event.getDescription()),
                new TypeFilter(event.getType()),
                chart.getFilteredEvents().filter().get()});

            //make a new end inclusive span (to 'filter' with)
            final Interval span = event.getSpan().withEndMillis(event.getSpan().getEndMillis() + 1000);

            //make a task to load the subnodes
            LoggedTask<List<AggregateEventNode>> loggedTask = new LoggedTask<List<AggregateEventNode>>("Load sub events", true) {

                @Override
                protected List<AggregateEventNode> call() throws Exception {
                    //query for the sub-clusters
                    List<AggregateEvent> aggregatedEvents = chart.getFilteredEvents().getAggregatedEvents(new ZoomParams(span,
                            chart.getFilteredEvents().eventTypeZoom().get(),
                            combinedFilter,
                            newLOD));
                    //for each sub cluster make an AggregateEventNode to visually represent it, and set x-position
                    return aggregatedEvents.stream().map((AggregateEvent t) -> {
                        AggregateEventNode subNode = new AggregateEventNode(t, AggregateEventNode.this, chart);
                        subNode.setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(t.getSpan().getStartMillis())) - getLayoutXCompensation());
                        return subNode;
                    }).collect(Collectors.toList()); // return list of AggregateEventNodes representing subclusters
                }

                @Override
                protected void succeeded() {
                    try {
                        chart.setCursor(Cursor.WAIT);
                        //assign subNodes and request chart layout
                        getSubNodePane().getChildren().setAll(get());
                        setDescriptionVisibility(descrVis);
                        chart.setRequiresLayout(true);
                        chart.requestChartLayout();
                        chart.setCursor(null);
                    } catch (InterruptedException | ExecutionException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            };

            //start task
            chart.getController().monitorTask(loggedTask);
        }
    }

    /** event handler used for mouse events on {@link AggregateEventNode}s */
    private class EventMouseHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.PRIMARY) {
                t.consume();
                if (t.isShiftDown()) {
                    if (chart.selectedNodes.contains(AggregateEventNode.this) == false) {
                        chart.selectedNodes.add(AggregateEventNode.this);
                    }
                } else if (t.isShortcutDown()) {
                    chart.selectedNodes.removeAll(AggregateEventNode.this);
                } else if (t.getClickCount() > 1) {
                    final DescriptionLOD next = descLOD.get().next();
                    if (next != null) {
                        loadSubClusters(next);
                        descLOD.set(next);
                    }
                } else {
                    chart.selectedNodes.setAll(AggregateEventNode.this);
                }
            }
        }
    }
}
