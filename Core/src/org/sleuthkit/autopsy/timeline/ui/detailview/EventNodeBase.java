
/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-19 Basis Technology Corp.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.events.TagsAddedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsDeletedEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimelineChart;
import org.sleuthkit.autopsy.timeline.ui.ContextMenuProvider;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getColor;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getImagePath;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.show;
import static org.sleuthkit.autopsy.timeline.ui.detailview.MultiEventNodeBase.CORNER_RADII_3;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 *
 */
public abstract class EventNodeBase<Type extends DetailViewEvent> extends StackPane implements ContextMenuProvider {

    private static final Logger LOGGER = Logger.getLogger(EventNodeBase.class.getName());

    private static final Image HASH_HIT = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N NON-NLS
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N
    private static final Image PIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--plus.png"); // NON-NLS //NOI18N
    private static final Image UNPIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--minus.png"); // NON-NLS //NOI18N

    private static final Map<TimelineEventType, Effect> dropShadowMap = new ConcurrentHashMap<>();

    static void configureActionButton(ButtonBase b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }

    private final Type tlEvent;

    private final EventNodeBase<?> parentNode;

    final DetailsChartLane<?> chartLane;
    final Background highlightedBackground;
    final Background defaultBackground;
    final Color evtColor;

    final Label countLabel = new Label();
    final Label descrLabel = new Label();
    final ImageView hashIV = new ImageView(HASH_HIT);
    final ImageView tagIV = new ImageView(TAG);
    final ImageView eventTypeImageView = new ImageView();

    final Tooltip tooltip = new Tooltip(Bundle.EventBundleNodeBase_toolTip_loading());

    final HBox controlsHBox = new HBox(5);
    final HBox infoHBox = new HBox(5, eventTypeImageView, hashIV, tagIV, descrLabel, countLabel, controlsHBox);
    final SleuthkitCase sleuthkitCase;
    final EventsModel eventsModel;
    private Timeline timeline;
    private Button pinButton;
    private final Border SELECTION_BORDER;

    EventNodeBase(Type tlEvent, EventNodeBase<?> parent, DetailsChartLane<?> chartLane) {
        this.chartLane = chartLane;
        this.tlEvent = tlEvent;
        this.parentNode = parent;

        sleuthkitCase = chartLane.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chartLane.getController().getEventsModel();
        eventTypeImageView.setImage(new Image(getImagePath(getEventType())));

        if (tlEvent.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }

        if (tlEvent.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        if (chartLane.getController().getEventsModel().getEventTypeZoom() == TimelineEventType.HierarchyLevel.CATEGORY) {
            evtColor = getColor(getEventType());
        } else {
            evtColor = getColor(getEventType().getCategory());
        }
        SELECTION_BORDER = new Border(new BorderStroke(evtColor.darker().desaturate(), BorderStrokeStyle.SOLID, CORNER_RADII_3, new BorderWidths(2)));

        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        highlightedBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1.1, 1.1, .3), CORNER_RADII_3, Insets.EMPTY));
        setBackground(defaultBackground);

        Tooltip.install(this, this.tooltip);

        //set up mouse hover effect and tooltip
        setOnMouseEntered(mouseEntered -> {
            Tooltip.uninstall(chartLane, AbstractTimelineChart.getDefaultTooltip());
            showHoverControls(true);
            toFront();
        });

        setOnMouseExited(mouseExited -> {
            showHoverControls(false);
            if (parentNode != null) {
                parentNode.showHoverControls(true);
            } else {
                Tooltip.install(chartLane, AbstractTimelineChart.getDefaultTooltip());
            }
        });
        setOnMouseClicked(new ClickHandler());
        show(controlsHBox, false);
    }

    public Type getEvent() {
        return tlEvent;
    }

    @Override
    public TimeLineController getController() {
        return chartLane.getController();
    }

    public Optional<EventNodeBase<?>> getParentNode() {
        return Optional.ofNullable(parentNode);
    }

    DetailsChartLane<?> getChartLane() {
        return chartLane;
    }

    /**
     * @param w the maximum width the description label should have
     */
    public void setMaxDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    public abstract List<EventNodeBase<?>> getSubNodes();

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    public void applySelectionEffect(boolean applied) {
        setBorder(applied ? SELECTION_BORDER : null);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
    }

    /**
     * Install whatever buttons are visible on hover for this node. likes
     * tooltips, this had a surprisingly large impact on speed of loading the
     * chart
     */
    void installActionButtons() {
        if (pinButton == null) {
            pinButton = new Button();
            controlsHBox.getChildren().add(pinButton);
            configureActionButton(pinButton);
        }
    }

    final void showHoverControls(final boolean showControls) {
        Effect dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(-10, getColor(eventType)));
        setEffect(showControls ? dropShadow : null);
        installTooltip();
        enableTooltip(showControls);
        installActionButtons();

        TimeLineController controller = getChartLane().getController();

        if (controller.getPinnedEvents().contains(tlEvent)) {
            pinButton.setOnAction(actionEvent -> {
                new UnPinEventAction(controller, tlEvent).handle(actionEvent);
                showHoverControls(true);
            });
            pinButton.setGraphic(new ImageView(UNPIN));
        } else {
            pinButton.setOnAction(actionEvent -> {
                new PinEventAction(controller, tlEvent).handle(actionEvent);
                showHoverControls(true);
            });
            pinButton.setGraphic(new ImageView(PIN));
        }

        show(controlsHBox, showControls);
        if (parentNode != null) {
            parentNode.showHoverControls(false);
        }
    }

    /**
     * defer tooltip content creation until needed, this had a surprisingly large
     * impact on speed of loading the chart
     */
    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventNodeBase.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}",
        "EventNodeBase.toolTip.loading2=loading tooltip",
        "# {0} - hash set count string",
        "EventNodeBase.toolTip.hashSetHits=\n\nHash Set Hits\n{0}",
        "# {0} - tag count string",
        "EventNodeBase.toolTip.tags=\n\nTags\n{0}"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void installTooltip() {
        if (tooltip.getText().equalsIgnoreCase(Bundle.EventBundleNodeBase_toolTip_loading())) {
            final Task<String> tooltTipTask = new Task<String>() {
                {
                    updateTitle(Bundle.EventNodeBase_toolTip_loading2());
                }

                @Override
                protected String call() throws Exception {
                    return Bundle.EventNodeBase_tooltip_text(getEventIDs().size(), getEventType(), getDescription(),
                            TimeLineController.getZonedFormatter().print(getStartMillis()),
                            TimeLineController.getZonedFormatter().print(getEndMillis() + 1000));
                }

                @Override
                protected void done() {
                    super.succeeded();
                    try {
                        tooltip.setText(get());
                        tooltip.setGraphic(null);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Tooltip generation failed.", ex); //NON-NLS
                    }
                }
            };
            new Thread(tooltTipTask).start();
            chartLane.getController().monitorTask(tooltTipTask);
        }
    }

    void enableTooltip(boolean toolTipEnabled) {
        if (toolTipEnabled) {
            Tooltip.install(this, tooltip);
        } else {
            Tooltip.uninstall(this, tooltip);
        }
    }

    final TimelineEventType getEventType() {
        return tlEvent.getEventType();
    }

    long getStartMillis() {
        return tlEvent.getStartMillis();
    }

    final long getEndMillis() {
        return tlEvent.getEndMillis();
    }

    final double getLayoutXCompensation() {
        return parentNode != null
                ? getChartLane().getXAxis().getDisplayPosition(new DateTime(parentNode.getStartMillis()))
                : 0;
    }

    abstract String getDescription();

    void animateTo(double xLeft, double yTop) {
        if (timeline != null) {
            timeline.stop();
            Platform.runLater(this::requestChartLayout);
        }
        timeline = new Timeline(new KeyFrame(Duration.millis(100),
                new KeyValue(layoutXProperty(), xLeft),
                new KeyValue(layoutYProperty(), yTop))
        );
        timeline.setOnFinished(finished -> Platform.runLater(this::requestChartLayout));
        timeline.play();
    }

    void requestChartLayout() {
        getChartLane().requestChartLayout();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void setDescriptionVisibility(DescriptionVisibility descrVis) {
        final int size = getEvent().getSize();
        switch (descrVis) {
            case HIDDEN:
                hideDescription();
                break;
            case COUNT_ONLY:
                showCountOnly(size);
                break;
            case SHOWN:
            default:
                showFullDescription(size);
                break;
        }
    }

    void showCountOnly(final int size) {
        descrLabel.setText("");
        countLabel.setText(String.valueOf(size));
    }

    void hideDescription() {
        countLabel.setText("");
        descrLabel.setText("");
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    synchronized void applyHighlightEffect(boolean applied) {
        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;"); // NON-NLS
            setBackground(highlightedBackground);
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;"); // NON-NLS
            setBackground(defaultBackground);
        }
    }

    void applyHighlightEffect() {
        applyHighlightEffect(true);
    }

    void clearHighlightEffect() {
        applyHighlightEffect(false);
    }

    abstract Collection<Long> getEventIDs();

    abstract EventHandler<MouseEvent> getDoubleClickHandler();

    Iterable<? extends Action> getActions() {
        if (getController().getPinnedEvents().contains(getEvent())) {
            return Arrays.asList(new UnPinEventAction(getController(), getEvent()));
        } else {
            return Arrays.asList(new PinEventAction(getController(), getEvent()));
        }
    }

    @Deprecated
    @Override
    final public void clearContextMenu() {
    }

    public ContextMenu getContextMenu(MouseEvent mouseEvent) {
        ContextMenu chartContextMenu = chartLane.getContextMenu(mouseEvent);

        ContextMenu contextMenu = ActionUtils.createContextMenu(Lists.newArrayList(getActions()));
        contextMenu.getItems().add(new SeparatorMenuItem());
        contextMenu.getItems().addAll(chartContextMenu.getItems());
        contextMenu.setAutoHide(true);
        return contextMenu;
    }

    void showFullDescription(final int size) {
        countLabel.setText((size == 1) ? "" : " (" + size + ")"); // NON-NLS
        String description = getParentNode().map(pNode
                -> "    ..." + StringUtils.substringAfter(getEvent().getDescription(), parentNode.getDescription()))
                .orElseGet(getEvent()::getDescription);

        descrLabel.setText(description);
    }

    @Subscribe
    public void handleTimeLineTagEvent(TagsAddedEvent event) {
        if (false == Sets.intersection(getEvent().getEventIDs(), event.getUpdatedEventIDs()).isEmpty()) {
            Platform.runLater(() -> {
                show(tagIV, true);
            });
        }
    }

    /**
     * TODO: this method implementation is wrong and just a place holder
     */
    @Subscribe
    public void handleTimeLineTagEvent(TagsDeletedEvent event) {
        Sets.SetView<Long> difference = Sets.difference(getEvent().getEventIDs(), event.getUpdatedEventIDs());

        if (false == difference.isEmpty()) {
            Platform.runLater(() -> {
                show(tagIV, true);
            });
        }
    }

    private static class PinEventAction extends Action {

        @NbBundle.Messages({"PinEventAction.text=Pin"})
        PinEventAction(TimeLineController controller, DetailViewEvent event) {
            super(Bundle.PinEventAction_text());
            setEventHandler(actionEvent -> controller.pinEvent(event));
            setGraphic(new ImageView(PIN));
        }
    }

    private static class UnPinEventAction extends Action {

        @NbBundle.Messages({"UnPinEventAction.text=Unpin"})
        UnPinEventAction(TimeLineController controller, DetailViewEvent event) {
            super(Bundle.UnPinEventAction_text());
            setEventHandler(actionEvent -> controller.unPinEvent(event));
            setGraphic(new ImageView(UNPIN));
        }
    }

    /**
     * event handler used for mouse events on {@link EventNodeBase}s
     */
    private class ClickHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.PRIMARY) {
                if (t.getClickCount() > 1) {
                    getDoubleClickHandler().handle(t);
                } else if (t.isShiftDown()) {
                    chartLane.getSelectedNodes().add(EventNodeBase.this);
                } else if (t.isShortcutDown()) {
                    chartLane.getSelectedNodes().removeAll(EventNodeBase.this);
                } else {
                    chartLane.getSelectedNodes().setAll(EventNodeBase.this);
                }
                t.consume();
            } else if (t.isPopupTrigger() && t.isStillSincePress()) {
                getContextMenu(t).show(EventNodeBase.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }
}
