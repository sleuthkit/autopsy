/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.EventTypeUtils;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter.EventTypeFilter;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.TextFilterState;

/**
 * A TreeTableCell that shows an icon and color corresponding to the represented
 * filter
 */
final class LegendCell extends TreeTableCell<FilterState<?>, FilterState<?>> {

    private static final Color CLEAR = Color.rgb(0, 0, 0, 0);

    private final TimeLineController controller;

    private final EventsModel filteredEvents;

    //We need a controller so we can listen to changes in EventTypeZoom to show/hide legends
    LegendCell(TimeLineController controller) {
        setEditable(false);
        this.controller = controller;
        this.filteredEvents = this.controller.getEventsModel();
    }

    @Override
    @NbBundle.Messages("Timeline.ui.filtering.promptText=enter filter string")
    public void updateItem(FilterState<?> item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null) {
            Platform.runLater(() -> {
                setGraphic(null);
                setBackground(null);
            });
        } else {
            
            //TODO: make some subclasses rather than use this if else chain.
            if (item instanceof TextFilterState) {
                TextFilterState filterState = (TextFilterState)item;
                TextField textField = new TextField();
                textField.setPromptText(Bundle.Timeline_ui_filtering_promptText());
                textField.textProperty().bindBidirectional(filterState.descriptionSubstringProperty());
                Platform.runLater(() -> setGraphic(textField));
                
            } else if (item.getFilter() instanceof EventTypeFilter) {
                EventTypeFilter filter = (EventTypeFilter) item.getFilter();
                Rectangle rect = new Rectangle(20, 20);

                rect.setArcHeight(5);
                rect.setArcWidth(5);
                rect.setStrokeWidth(3);
                setLegendColor(filter, rect, this.filteredEvents.getEventTypeZoom());
                this.filteredEvents.eventTypesHierarchyLevelProperty().addListener((obs, oldZoomLevel, newZoomLevel) -> {
                    setLegendColor(filter, rect, newZoomLevel);
                });

                HBox hBox = new HBox(new Rectangle(filter.getRootEventType().getTypeHierarchyLevel().ordinal() * 10, 5, CLEAR),
                        new ImageView(EventTypeUtils.getImagePath(filter.getRootEventType())), rect
                );
                hBox.setAlignment(Pos.CENTER);
                Platform.runLater(() -> {
                    setGraphic(hBox);
                    setContentDisplay(ContentDisplay.CENTER);
                });

            } else {
                Platform.runLater(() -> {
                    setGraphic(null);
                    setBackground(null);
                });
            }
        }
    }

    private void setLegendColor(EventTypeFilter filter, Rectangle rect, TimelineEventType.HierarchyLevel eventTypeZoom) {
        //only show legend color if filter is of the same zoomlevel as requested in filteredEvents
        if (eventTypeZoom.equals(filter.getRootEventType().getTypeHierarchyLevel())) {
            Platform.runLater(() -> {
                rect.setStroke(EventTypeUtils.getColor(filter.getRootEventType().getParent()));
                rect.setFill(EventTypeUtils.getColor(filter.getRootEventType()));
            });
        } else {
            Platform.runLater(() -> {
                rect.setStroke(CLEAR);
                rect.setFill(CLEAR);
            });
        }
    }
}
