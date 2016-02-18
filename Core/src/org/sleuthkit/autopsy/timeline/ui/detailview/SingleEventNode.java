/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.configureActionButton;
import static org.sleuthkit.autopsy.timeline.ui.detailview.MultiEventNodeBase.CORNER_RADII_3;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 *
 */
final class SingleEventNode extends EventNodeBase<SingleEvent> {

    private final DetailsChart chart;
    final Background defaultBackground;
    private final Color evtColor;

    final ImageView hashIV = new ImageView(HASH_PIN);
    final ImageView tagIV = new ImageView(TAG);
    final HBox infoHBox = new HBox(5, descrLabel, hashIV, tagIV);

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(0, 0, 0, 2);
    private final ImageView eventTypeImageView = new ImageView();
    private Button pinButton;

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> {
        };
    }

    @Override
    Collection<? extends Action> getActions() {
        TimeLineController controller = getChart().getController();
        if (controller.getPinnedEventIDs().contains(ievent.getEventID())) {
            return Arrays.asList(new UnPinEventAction(controller, ievent.getEventIDs()));
        } else {
            return Arrays.asList(new PinEventAction(controller, ievent.getEventIDs()));
        }
    }

    SingleEventNode(DetailsChart chart, SingleEvent event, MultiEventNodeBase<?, ?, ?> parent) {
        super(event, parent, chart);
        this.chart = chart;
        this.descrLabel.setText(event.getFullDescription());
        eventTypeImageView.setImage(getEventType().getFXImage());
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setGraphic(eventTypeImageView);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        setMinHeight(24);
        setAlignment(Pos.CENTER_LEFT);
        if (event.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (event.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        if (chart.getController().getEventsModel().getEventTypeZoom() == EventTypeZoomLevel.SUB_TYPE) {
            evtColor = getEventType().getColor();
        } else {
            evtColor = getEventType().getBaseType().getColor();
        }
        final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));
        setBorder(clusterBorder);
        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        setBackground(defaultBackground);
        setMaxWidth(USE_PREF_SIZE);
        infoHBox.setMaxWidth(USE_PREF_SIZE);
        getChildren().add(infoHBox);

    }

    @Override
    public TimeLineChart<DateTime> getChart() {
        return chart;
    }

    @Override
    public List<EventNodeBase<?>> getSubNodes() {
        return Collections.emptyList();
    }

    void installActionButtons() {
        if (pinButton == null) {
            pinButton = new Button("", new ImageView(PIN));
            infoHBox.getChildren().add(pinButton);
            configureActionButton(pinButton);

            pinButton.setOnAction(actionEvent -> {
                TimeLineController controller = getChart().getController();
                if (controller.getPinnedEventIDs().contains(ievent.getEventID())) {
                    new UnPinEventAction(controller, ievent.getEventIDs()).handle(actionEvent);
                    pinButton.setGraphic(new ImageView(PIN));
                } else {
                    new PinEventAction(controller, ievent.getEventIDs()).handle(actionEvent);
                    pinButton.setGraphic(new ImageView(UNPIN));
                }
            });

        }
    }

    @Override
    void showHoverControls(final boolean showControls) {
        super.showHoverControls(showControls);
        installActionButtons();
        show(pinButton, showControls);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void installTooltip() {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    String getDescription() {
        return ievent.getFullDescription();
    }

    @Override
    void requestChartLayout() {
        chart.requestTimelineChartLayout();
    }

    @Override
    void applyHighlightEffect(boolean b) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void setMaxDescriptionWidth(double descriptionWidth) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void setDescriptionVisibiltiyImpl(DescriptionVisibility descrVis) {

        switch (descrVis) {
            case HIDDEN:
                descrLabel.setText("");
                break;
            case COUNT_ONLY:
                descrLabel.setText("");
                break;
            default:
            case SHOWN:
                String description = ievent.getFullDescription();
                description = parentNode != null
                        ? "    ..." + StringUtils.substringAfter(description, parentNode.getDescription())
                        : description;
                descrLabel.setText(description);
                break;
        }
    }

    @Override
    Collection<Long> getEventIDs() {
        return getEvent().getEventIDs();
    }
}
