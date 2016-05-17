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
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.Collection;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
class ListChart extends BorderPane {
    
    @FXML
    private Label eventCountLabel;
    
    @FXML
    private TableView<Long> table;
    
    private static final Callback<TableColumn.CellDataFeatures<Long, Long>, ObservableValue<Long>> CELL_VALUE_FACTORY = param -> new SimpleObjectProperty<>(param.getValue());
    
    private final TimeLineController controller;
    
    @FXML
    private TableColumn<Long, Long> idColumn;
    
    @FXML
    private TableColumn<Long, Long> millisColumn;
    
    @FXML
    private TableColumn<Long, Long> iconColumn;
    
    @FXML
    private TableColumn<Long, Long> descriptionColumn;
    
    @FXML
    private TableColumn<Long, Long> baseTypeColumn;
    
    @FXML
    private TableColumn<Long, Long> subTypeColumn;
    
    @FXML
    private TableColumn<Long, Long> knownColumn;
    
    ListChart(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, ListChart.class, "ListViewChart.fxml");
    }
    
    @FXML
    void initialize() {
        assert eventCountLabel != null : "fx:id=\"eventCountLabel\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert table != null : "fx:id=\"table\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert idColumn != null : "fx:id=\"idColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert millisColumn != null : "fx:id=\"millisColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert iconColumn != null : "fx:id=\"iconColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert descriptionColumn != null : "fx:id=\"descriptionColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert baseTypeColumn != null : "fx:id=\"baseTypeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert subTypeColumn != null : "fx:id=\"subTypeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert knownColumn != null : "fx:id=\"knownColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        
        table.setRowFactory(tableView -> new EventRow());
        idColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        
        millisColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        millisColumn.setCellFactory(col -> new EpochMillisCell());
        
        iconColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        iconColumn.setCellFactory(col -> new ImageCell());
        
        descriptionColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        descriptionColumn.setCellFactory(col -> new DescriptionCell());
        
        baseTypeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        baseTypeColumn.setCellFactory(col -> new BaseTypeCell());
        
        subTypeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        subTypeColumn.setCellFactory(col -> new EventTypeCell());
        
        knownColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        knownColumn.setCellFactory(col -> new KnownCell());
        
        eventCountLabel.textProperty().bind(Bindings.size(table.getItems()).asString().concat(" events"));
    }
    
    public TimeLineController getController() {
        return controller;
    }
    
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void clear() {
        table.getItems().clear();
    }
    
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void setEventIDs(Collection<Long> eventIDs) {
        table.getItems().setAll(eventIDs);
    }

    /**
     * Get the List of IDs of events that are selected in this list.
     *
     * @return The List of IDs of events that are selected in this list.
     */
    ObservableList<Long> getSelectedEventIDs() {
        return table.getSelectionModel().getSelectedItems();
    }
    
    private class ImageCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setContextMenu(null);
            } else {
                setGraphic(new ImageView(getEvent().getEventType().getFXImage()));
                EventNode node = EventNode.createEventNode(item, controller.getEventsModel());
                setContextMenu(new ContextMenu(SwingMenuItemAdapter.create(node.getContextMenu())));
            }
            
        }
    }
    
    private class DescriptionCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText("");
            } else {
                setText(getEvent().getDescription(DescriptionLoD.FULL));
            }
            setContextMenu(new ContextMenu(new MenuItem("sampleS")));
        }
    }
    
    private class BaseTypeCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText("");
            } else {
                setText(getEvent().getEventType().getBaseType().getDisplayName());
            }
        }
    }
    
    private class EventTypeCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText("");
            } else {
                setText(getEvent().getEventType().getDisplayName());
            }
        }
    }
    
    private class KnownCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText("");
            } else {
                setText(getEvent().getKnown().getName());
            }
        }
    }
    
    private class EventTableCell extends TableCell<Long, Long> {
        
        private SingleEvent event;
        
        SingleEvent getEvent() {
            return event;
        }
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                event = null;
            } else {
                event = controller.getEventsModel().getEventById(item);
            }
        }
    }
    
    private class EpochMillisCell extends EventTableCell {
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText("");
            } else {
                setText(TimeLineController.getZonedFormatter().print(getEvent().getStartMillis()));
            }
        }
    }
    
    private class EventRow extends TableRow<Long> {
        
        private SingleEvent event;
        
        SingleEvent getEvent() {
            return event;
        }
        
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                event = null;
            } else {
                event = controller.getEventsModel().getEventById(item);
            }
        }
    }
}
