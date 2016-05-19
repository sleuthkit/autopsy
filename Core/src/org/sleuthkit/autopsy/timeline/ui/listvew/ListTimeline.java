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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import javax.swing.Action;
import javax.swing.JMenuItem;
import org.controlsfx.control.Notifications;
import org.openide.awt.Actions;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
class ListTimeline extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ListTimeline.class.getName());

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

    ListTimeline(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, ListTimeline.class, "ListTimeline.fxml");
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

        @NbBundle.Messages({
            "ListChart.errorMsg=There was a problem getting the content for the selected event."})
        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                event = null;
            } else {
                event = controller.getEventsModel().getEventById(item);

                try {
                    EventNode node = EventNode.createEventNode(item, controller.getEventsModel());
                    List<MenuItem> menuItems = new ArrayList<>();

                    for (Action element : node.getActions(false)) {
                        if (element == null) {
                            menuItems.add(new SeparatorMenuItem());
                        } else {
                            String actionName = Objects.toString(element.getValue(Action.NAME));

                            if (Arrays.asList("&Properties", "Tools").contains(actionName) == false) {
                                if (element instanceof Presenter.Popup) {
                                    JMenuItem submenu = ((Presenter.Popup) element).getPopupPresenter();
                                    menuItems.add(SwingMenuItemAdapter.create(submenu));
                                } else {
                                    menuItems.add(SwingMenuItemAdapter.create(new Actions.MenuItem(element, false)));
                                }
                            }
                        }
                    };

                    setContextMenu(new ContextMenu(menuItems.toArray(new MenuItem[menuItems.size()])));
                } catch (IllegalStateException ex) {
                    //Since the case is closed, the user probably doesn't care about this, just log it as a precaution.
                    LOGGER.log(Level.SEVERE, "There was no case open to lookup the Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                    Platform.runLater(() -> {
                        Notifications.create()
                                .owner(getScene().getWindow())
                                .text(Bundle.ListChart_errorMsg())
                                .showError();
                    });
                }
            }
        }
    }
}
