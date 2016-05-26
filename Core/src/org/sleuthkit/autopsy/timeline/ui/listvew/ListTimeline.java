/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.function.Function;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.binding.StringBinding;
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
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The inner component that makes up the List view. Manages the TableView.
 */
class ListTimeline extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ListTimeline.class.getName());

    /**
     * call-back used to wrap the event ID inn a ObservableValue<Long>
     */
    private static final Callback<TableColumn.CellDataFeatures<Long, Long>, ObservableValue<Long>> CELL_VALUE_FACTORY = param -> new SimpleObjectProperty<>(param.getValue());

    @FXML
    private Label eventCountLabel;
    @FXML
    private TableView<Long> table;
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

    private final TimeLineController controller;

    /**
     * Constructor
     *
     * @param controller The controller for this timeline
     */
    ListTimeline(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, ListTimeline.class, "ListTimeline.fxml");
    }

    @FXML
    @NbBundle.Messages({
        "# {0} - the number of events",
        "ListTimeline.evetnCountLabel.text={0} events"})
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

        //override default row with one that provides context menu.S
        table.setRowFactory(tableView -> new EventRow());

        //remove idColumn (can be restored for debugging).  
        table.getColumns().remove(idColumn);

        ///// set up cell and cell-value factories for columns
        millisColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        millisColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                TimeLineController.getZonedFormatter().print(singleEvent.getStartMillis())));

        iconColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        iconColumn.setCellFactory(col -> new ImageCell());

        descriptionColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        descriptionColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getDescription(DescriptionLoD.FULL)));

        baseTypeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        baseTypeColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getEventType().getBaseType().getDisplayName()));

        subTypeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        subTypeColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getEventType().getDisplayName()));

        knownColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        knownColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getKnown().getName()));

        //bind event count label to number of items in the table
        eventCountLabel.textProperty().bind(new StringBinding() {
            {
                bind(table.getItems());
            }

            @Override
            protected String computeValue() {
                return Bundle.ListTimeline_evetnCountLabel_text(table.getItems().size());
            }
        });
    }

    /**
     * Clear all the events out of the table.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void clear() {
        table.getItems().clear();
    }

    /**
     * Get the selected event ID.
     *
     * @return The selected event ID.
     */
    Long getSelectedEventID() {
        return table.getSelectionModel().getSelectedItem();
    }

    /**
     * Set the Collection of events (by ID) to show in the table.
     *
     * @param eventIDs The Collection of event IDs to sho in the table.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void setEventIDs(Collection<Long> eventIDs) {
        table.getItems().setAll(eventIDs);
    }

    /**
     * Get an ObservableList of IDs of events that are selected in this table.
     *
     * @return An ObservableList of IDs of events that are selected in this
     *         table.
     */
    ObservableList<Long> getSelectedEventIDs() {
        return table.getSelectionModel().getSelectedItems();
    }

    /**
     * Set the ID of the event that is selected.
     *
     * @param selectedEventID The ID of the event that should be selected.
     */
    void selectEventID(Long selectedEventID) {
        //restore selection.
        table.scrollTo(selectedEventID);
        table.getSelectionModel().select(selectedEventID);
        table.requestFocus();
    }

    /**
     * TableCell to show the icon for the type of an event.
     */
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

    /**
     * TableCell to show text derived from a SingleEvent by the given Funtion.
     */
    private class TextEventTableCell extends EventTableCell {

        private final Function<SingleEvent, String> textSupplier;

        TextEventTableCell(Function<SingleEvent, String> textSupplier) {
            this.textSupplier = textSupplier;
        }

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(textSupplier.apply(getEvent()));
            }
        }
    }

    /**
     * Base class for TableCells that represent a SingleEvent by its ID
     */
    private abstract class EventTableCell extends TableCell<Long, Long> {

        private SingleEvent event;

        /**
         * Get the SingleEvent this cell represents.
         *
         * @return The SingleEvent this cell represents.
         */
        SingleEvent getEvent() {
            return event;
        }

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                event = null;
            } else {
                //stash the event in the cell for derived classed to use.
                event = controller.getEventsModel().getEventById(item);
            }
        }
    }

    /**
     * TableRow that adds a right-click context menu.
     */
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

                setOnContextMenuRequested(contextMenuEvent -> {
                    //make a new context menu on each request in order to include uptodate tag names and hash sets
                    try {
                        EventNode node = EventNode.createEventNode(item, controller.getEventsModel());
                        List<MenuItem> menuItems = new ArrayList<>();

                        //for each actions avaialable on node, make a menu item.
                        for (Action action : node.getActions(false)) {
                            if (action == null) {
                                // swing/netbeans uses null action to represent separator in menu
                                menuItems.add(new SeparatorMenuItem());
                            } else {
                                String actionName = Objects.toString(action.getValue(Action.NAME));
                                //for now, suppress properties and tools actions, by ignoring them
                                if (Arrays.asList("&Properties", "Tools").contains(actionName) == false) {
                                    if (action instanceof Presenter.Popup) {
                                        /*
                                         * If the action is really the root of a
                                         * set of actions (eg, tagging). Make a
                                         * menu that parallels the action's
                                         * menu.
                                         */
                                        JMenuItem submenu = ((Presenter.Popup) action).getPopupPresenter();
                                        menuItems.add(SwingFXMenuUtils.createFXMenu(submenu));
                                    } else {
                                        menuItems.add(SwingFXMenuUtils.createFXMenu(new Actions.MenuItem(action, false)));
                                    }
                                }
                            }
                        };

                        //show new context menu.
                        new ContextMenu(menuItems.toArray(new MenuItem[menuItems.size()]))
                                .show(this, contextMenuEvent.getScreenX(), contextMenuEvent.getScreenY());
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
                });

            }
        }
    }
}
