/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-2018 Basis Technology Corp.
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
import java.util.List;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getImage;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.SingleDetailsViewEvent;

/**
 *
 */
final class SingleEventNode extends EventNodeBase<SingleDetailsViewEvent> {

    private static final Logger LOGGER = Logger.getLogger(SingleEventNode.class.getName());

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);
    private static final BorderWidths CLUSTER_BORDER_WIDTHS = new BorderWidths(0, 0, 0, 2);

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> {
        };
    }

    SingleEventNode(DetailsChartLane<?> chart, SingleDetailsViewEvent event, MultiEventNodeBase<?, ?, ?> parent) {
        super(event, parent, chart);
        this.descrLabel.setText(event.getFullDescription());
        eventTypeImageView.setImage(getImage(getEventType()));
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setGraphic(eventTypeImageView);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        setMinHeight(24);
        setAlignment(Pos.CENTER_LEFT);

        final Border clusterBorder = new Border(new BorderStroke(evtColor.deriveColor(0, 1, 1, .4), BorderStrokeStyle.SOLID, CORNER_RADII_1, CLUSTER_BORDER_WIDTHS));
        setBorder(clusterBorder);

        setMaxWidth(USE_PREF_SIZE);
        infoHBox.setMaxWidth(USE_PREF_SIZE);
        getChildren().add(infoHBox);
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
    String getDescription() {
        return getEvent().getFullDescription();
    }

    /**
     * @param w the maximum width the description label should have
     */
    @Override
    public void setMaxDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    @Override
    Collection<Long> getEventIDs() {
        return getEvent().getEventIDs();
    }
}
