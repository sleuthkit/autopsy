/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;

/**
 *
 */
final class SingleEventNode extends EventNodeBase<SingleEvent> {

    private final DetailsChart chart;

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(0, 0, 0, 2);
    private final ImageView eventTypeImageView = new ImageView();

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> {
        };
    }

    @Override
    Collection<? extends Action> getActions() {
        TimeLineController controller = getChart().getController();
        if (controller.getPinnedEvents().contains(tlEvent)) {
            return Arrays.asList(new UnPinEventAction(controller, tlEvent));
        } else {
            return Arrays.asList(new PinEventAction(controller, tlEvent));
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

        final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));
        setBorder(clusterBorder);

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
        return tlEvent.getFullDescription();
    }

    @Override
    void requestChartLayout() {
        chart.requestTimelineChartLayout();
    }

    

    /**
     * @param w the maximum width the description label should have
     */
    @Override
    public void setMaxDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    @Override
    void setDescriptionVisibiltiyImpl(DescriptionVisibility descrVis) {

        switch (descrVis) {
            case HIDDEN:
                countLabel.setText(null);
                descrLabel.setText("");
                break;
            case COUNT_ONLY:
                countLabel.setText(null);
                descrLabel.setText("");
                break;
            default:
            case SHOWN:
                countLabel.setText(null);
                String description = tlEvent.getFullDescription();
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
