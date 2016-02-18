/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
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
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.Event;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.show;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
public abstract class EventNodeBase<Type extends Event> extends StackPane {

    static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N NON-NLS
    static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N
    static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N
    static final Image PIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--plus.png"); // NON-NLS //NOI18N
    static final Image UNPIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--minus.png"); // NON-NLS //NOI18N

    static final Map<EventType, Effect> dropShadowMap = new ConcurrentHashMap<>();

    private final Tooltip tooltip = new Tooltip(Bundle.EventBundleNodeBase_toolTip_loading());

    final Type ievent;

    final EventNodeBase<?> parentNode;
    final Label descrLabel = new Label();
    final SimpleObjectProperty<DescriptionLoD> descLOD = new SimpleObjectProperty<>();
    final SimpleObjectProperty<DescriptionVisibility> descVisibility = new SimpleObjectProperty<>();

    static void configureActionButton(ButtonBase b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    private Timeline timeline;
    final DetailsChart chart;

    public EventNodeBase(Type ievent, EventNodeBase<?> parent, DetailsChart chart) {
        this.chart = chart;
        this.ievent = ievent;
        this.parentNode = parent;

        descVisibility.addListener(observable -> setDescriptionVisibiltiyImpl(descVisibility.get()));
//        descVisibility.set(DescriptionVisibility.SHOWN); //trigger listener for initial value

        //set up mouse hover effect and tooltip
        setOnMouseEntered(mouseEntered -> {

            Tooltip.uninstall(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            showHoverControls(true);
            toFront();
        });
        setOnMouseExited(mouseExited -> {
            showHoverControls(false);
            if (parentNode != null) {
                parentNode.showHoverControls(true);
            } else {
                Tooltip.install(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            }
        });
        setOnMouseClicked(new ClickHandler());
    }

    public Type getEvent() {
        return ievent;
    }

    protected void layoutChildren() {
        super.layoutChildren();
    }

    abstract public TimeLineChart<DateTime> getChart();

    void showHoverControls(final boolean showControls) {
        Effect dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(-10, eventType.getColor()));
        setEffect(showControls ? dropShadow : null);
        installTooltip();
        enableTooltip(showControls);
        if (parentNode != null) {
            parentNode.showHoverControls(false);
        }
    }

    abstract void installTooltip();

    void enableTooltip(boolean toolTipEnabled) {
        if (toolTipEnabled) {
            Tooltip.install(this, tooltip);
        } else {
            Tooltip.uninstall(this, tooltip);
        }
    }

    EventType getEventType() {
        return ievent.getEventType();
    }

    long getStartMillis() {
        return ievent.getStartMillis();
    }

    final double getLayoutXCompensation() {
        return parentNode != null
                ? getChart().getXAxis().getDisplayPosition(new DateTime(parentNode.getStartMillis()))
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

    abstract void requestChartLayout();

    void setDescriptionVisibility(DescriptionVisibility get) {
        descVisibility.set(get);
    }

    abstract void setDescriptionVisibiltiyImpl(DescriptionVisibility get);

    abstract void setMaxDescriptionWidth(double descriptionWidth);

    abstract public List<EventNodeBase<?>> getSubNodes();

    abstract void applyHighlightEffect(boolean b);

    void applyHighlightEffect() {
        applyHighlightEffect(true);
    }

    void clearHighlightEffect() {
        applyHighlightEffect(false);
    }

    abstract Collection<Long> getEventIDs();

    void applySelectionEffect(Boolean selected) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    static class PinEventAction extends Action {

        @NbBundle.Messages({"PinEventAction.text=Pin"})
        PinEventAction(TimeLineController controller, Set<Long> eventIds) {
            super(Bundle.PinEventAction_text());
            setEventHandler(actionEvent -> controller.pinEvents(eventIds));
            setGraphic(new ImageView(PIN));
        }
    }

    static class UnPinEventAction extends Action {

        @NbBundle.Messages({"UnPinEventAction.text=Unpin"})
        UnPinEventAction(TimeLineController controller, Set<Long> eventIds) {
            super(Bundle.UnPinEventAction_text());
            setEventHandler(actionEvent -> controller.unPinEvents(eventIds));
            setGraphic(new ImageView(UNPIN));
        }
    }

    /**
     * event handler used for mouse events on {@link EventNodeBase}s
     */
    class ClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu contextMenu;

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.PRIMARY) {
                if (t.getClickCount() > 1) {
                    getDoubleClickHandler().handle(t);
                } else if (t.isShiftDown()) {
                    chart.getSelectedNodes().add(EventNodeBase.this);
                } else if (t.isShortcutDown()) {
                    chart.getSelectedNodes().removeAll(EventNodeBase.this);
                } else {
                    chart.getSelectedNodes().setAll(EventNodeBase.this);
                }
                t.consume();
            } else if (t.getButton() == MouseButton.SECONDARY) {
                ContextMenu chartContextMenu = chart.getChartContextMenu(t);
                if (contextMenu == null) {
                    contextMenu = new ContextMenu();
                    contextMenu.setAutoHide(true);

                    contextMenu.getItems().addAll(ActionUtils.createContextMenu(getActions()).getItems());

                    contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(EventNodeBase.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }

    }

    abstract EventHandler<MouseEvent> getDoubleClickHandler();

    abstract Collection<? extends Action> getActions();

}
