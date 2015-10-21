/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 * A TreeTableCell that shows an icon and color corresponding to the represented
 * filter
 */
final class LegendCell extends TreeTableCell<AbstractFilter, AbstractFilter> {

    private static final Color CLEAR = Color.rgb(0, 0, 0, 0);

    private final TimeLineController controller;

    private final FilteredEventsModel filteredEvents;

    //We need a controller so we can listen to changes in EventTypeZoom to show/hide legends
    LegendCell(TimeLineController controller) {
        setEditable(false);
        this.controller = controller;
        this.filteredEvents = this.controller.getEventsModel();
    }

    @Override
    @NbBundle.Messages("Timeline.ui.filtering.promptText=enter filter string")
    public void updateItem(AbstractFilter item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null) {
            Platform.runLater(() -> {
                setGraphic(null);
                setBackground(null);
            });
        } else {

            //TODO: have the filter return an appropriate node, rather than use instanceof
            if (item instanceof TypeFilter) {
                TypeFilter filter = (TypeFilter) item;
                Rectangle rect = new Rectangle(20, 20);

                rect.setArcHeight(5);
                rect.setArcWidth(5);
                rect.setStrokeWidth(3);
                setLegendColor(filter, rect, this.filteredEvents.getEventTypeZoom());
                this.filteredEvents.eventTypeZoomProperty().addListener((obs, oldZoomLevel, newZoomLevel) -> {
                    setLegendColor(filter, rect, newZoomLevel);
                });

                HBox hBox = new HBox(new Rectangle(filter.getEventType().getZoomLevel().ordinal() * 10, 5, CLEAR),
                        new ImageView(((TypeFilter) item).getFXImage()), rect
                );
                hBox.setAlignment(Pos.CENTER);
                Platform.runLater(() -> {
                    setGraphic(hBox);
                    setContentDisplay(ContentDisplay.CENTER);
                });

            } else if (item instanceof TextFilter) {
                TextFilter f = (TextFilter) item;
                TextField textField = new TextField();
                textField.setPromptText(Bundle.Timeline_ui_filtering_promptText());
                textField.textProperty().bindBidirectional(f.textProperty());
                Platform.runLater(() -> {
                    setGraphic(textField);
                });

            } else {
                Platform.runLater(() -> {
                    setGraphic(null);
                    setBackground(null);
                });
            }
        }
    }

    private void setLegendColor(TypeFilter filter, Rectangle rect, EventTypeZoomLevel eventTypeZoom) {
        //only show legend color if filter is of the same zoomlevel as requested in filteredEvents
        if (eventTypeZoom.equals(filter.getEventType().getZoomLevel())) {
            Platform.runLater(() -> {
                rect.setStroke(filter.getEventType().getSuperType().getColor());
                rect.setFill(filter.getColor());
            });
        } else {
            Platform.runLater(() -> {
                rect.setStroke(CLEAR);
                rect.setFill(CLEAR);
            });
        }
    }
}
