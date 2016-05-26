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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
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
import org.sleuthkit.autopsy.timeline.datamodel.CombinedEvent;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.BaseTypes;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.FileSystemTypes;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The inner component that makes up the List view. Manages the TableView.
 */
class ListTimeline extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ListTimeline.class.getName());

    /**
     * call-back used to wrap the CombinedEvent in a ObservableValue
     */
    private static final Callback<TableColumn.CellDataFeatures<CombinedEvent, CombinedEvent>, ObservableValue<CombinedEvent>> CELL_VALUE_FACTORY = param -> new SimpleObjectProperty<>(param.getValue());

    @FXML
    private Label eventCountLabel;
    @FXML
    private TableView<CombinedEvent> table;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> idColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> dateTimeColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> descriptionColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> typeColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> knownColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> taggedColumn;
    @FXML
    private TableColumn<CombinedEvent, CombinedEvent> hashHitColumn;

    private final TimeLineController controller;

    /**
     * Observable list used to track selected events.
     */
    private final ObservableList<Long> selectedEventIDs = FXCollections.observableArrayList();

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
        assert dateTimeColumn != null : "fx:id=\"dateTimeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert descriptionColumn != null : "fx:id=\"descriptionColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert typeColumn != null : "fx:id=\"typeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";
        assert knownColumn != null : "fx:id=\"knownColumn\" was not injected: check your FXML file 'ListViewPane.fxml'.";

        //override default row with one that provides context menus
        table.setRowFactory(tableView -> new EventRow());

        //remove idColumn (can be restored for debugging).  
        table.getColumns().remove(idColumn);

        //// set up cell and cell-value factories for columns
        dateTimeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        dateTimeColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                TimeLineController.getZonedFormatter().print(singleEvent.getStartMillis())));

        descriptionColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        descriptionColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getDescription(DescriptionLoD.FULL)));

        typeColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        typeColumn.setCellFactory(col -> new EventTypeCell());

        knownColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        knownColumn.setCellFactory(col -> new TextEventTableCell(singleEvent ->
                singleEvent.getKnown().getName()));

        taggedColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        taggedColumn.setCellFactory(col -> new TaggedCell());

        hashHitColumn.setCellValueFactory(CELL_VALUE_FACTORY);
        hashHitColumn.setCellFactory(col -> new HashHitCell());

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

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener((Observable observable) -> {
            //keep the selectedEventsIDs in sync with the table's selection model, via getRepresentitiveEventID(). 
            selectedEventIDs.setAll(FluentIterable.from(table.getSelectionModel().getSelectedItems())
                    .filter(Objects::nonNull)
                    .transform(CombinedEvent::getRepresentativeEventID)
                    .toSet());
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
     * Set the Collection of CombinedEvents to show in the table.
     *
     * @param events The Collection of events to sho in the table.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void setCombinedEvents(Collection<CombinedEvent> events) {
        table.getItems().setAll(events);
    }

    /**
     * Get an ObservableList of IDs of events that are selected in this table.
     *
     * @return An ObservableList of IDs of events that are selected in this
     *         table.
     */
    ObservableList<Long> getSelectedEventIDs() {
        return selectedEventIDs;
    }
    private static final Image HASH_HIT = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png");  // NON-NLS 
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png");  // NON-NLS
    private static final Image PIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--plus.png");  // NON-NLS
    private static final Image UNPIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--minus.png");  // NON-NLS

    private class TaggedCell extends EventTableCell {

        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(getEvent().isTagged() ? new ImageView(TAG) : null);
            }
        }
    }

    private class HashHitCell extends EventTableCell {

        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(getEvent().isHashHit() ? new ImageView(HASH_HIT) : null);
            }
        }
    }

    /**
     * Get an ObservableList of combined events that are selected in this table.
     *
     * @return An ObservableList of combined events that are selected in this
     *         table.
     */
    ObservableList<CombinedEvent> getSelectedEvents() {
        return table.getSelectionModel().getSelectedItems();
    }

    /**
     * Set the combined events that are selected in this view.
     *
     * @param selectedEvents The events that should be selected.
     */
    void selectEvents(Collection<CombinedEvent> selectedEvents) {
        CombinedEvent firstSelected = selectedEvents.stream().min(Comparator.comparing(CombinedEvent::getStartMillis)).orElse(null);
        table.getSelectionModel().clearSelection();
        table.scrollTo(firstSelected);
        selectedEvents.forEach(table.getSelectionModel()::select);
        table.requestFocus();
    }

    /**
     * TableCell to show the (sub) type of an event.
     */
    private class EventTypeCell extends EventTableCell {

        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                if (item.getEventTypes().stream().allMatch(eventType -> eventType instanceof FileSystemTypes)) {
                    String s = "";
                    for (FileSystemTypes type : Arrays.asList(FileSystemTypes.FILE_MODIFIED, FileSystemTypes.FILE_ACCESSED, FileSystemTypes.FILE_CHANGED, FileSystemTypes.FILE_CREATED)) {
                        if (item.getEventTypes().contains(type)) {
                            switch (type) {
                                case FILE_MODIFIED:
                                    s += "M";
                                    break;
                                case FILE_ACCESSED:
                                    s += "A";
                                    break;
                                case FILE_CREATED:
                                    s += "B";
                                    break;
                                case FILE_CHANGED:
                                    s += "C";
                                    break;
                                default:
                                    throw new UnsupportedOperationException("Unknown FileSystemType: " + type.name());
                            }
                        } else {
                            s += "_";
                        }
                    }
                    setText(s);
                    setGraphic(new ImageView(BaseTypes.FILE_SYSTEM.getFXImage()));
                } else {
                    setText(Iterables.getOnlyElement(item.getEventTypes()).getDisplayName());
                    setGraphic(new ImageView(Iterables.getOnlyElement(item.getEventTypes()).getFXImage()));
                };
            }
        }
    }

    /**
     * TableCell to show text derived from a SingleEvent by the given Function.
     */
    private class TextEventTableCell extends EventTableCell {

        private final Function<SingleEvent, String> textSupplier;

        /**
         * Constructor
         *
         * @param textSupplier Function that takes a SingleEvent and produces a
         *                     String to show in this TableCell.
         */
        TextEventTableCell(Function<SingleEvent, String> textSupplier) {
            this.textSupplier = textSupplier;
            setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            setEllipsisString(" ... ");
        }

        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(textSupplier.apply(getEvent()));
            }
        }
    }

    /**
     * Base class for TableCells that represent a MergedEvent by way of a
     * representative SingleEvent.
     */
    private abstract class EventTableCell extends TableCell<CombinedEvent, CombinedEvent> {

        private SingleEvent event;

        /**
         * Get the representative SingleEvent for this cell.
         *
         * @return The representative SingleEvent for this cell.
         */
        SingleEvent getEvent() {
            return event;
        }

        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                event = null;
            } else {
                //stash the event in the cell for derived classed to use.
                event = controller.getEventsModel().getEventById(item.getRepresentativeEventID());
            }
        }
    }

    /**
     * TableRow that adds a right-click context menu.
     */
    private class EventRow extends TableRow<CombinedEvent> {

        private SingleEvent event;

        /**
         * Get the representative SingleEvent for this row .
         *
         * @return The representative SingleEvent for this row .
         */
        SingleEvent getEvent() {
            return event;
        }

        @NbBundle.Messages({
            "ListChart.errorMsg=There was a problem getting the content for the selected event."})
        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                event = null;
            } else {
                event = controller.getEventsModel().getEventById(item.getRepresentativeEventID());

                setOnContextMenuRequested(contextMenuEvent -> {
                    //make a new context menu on each request in order to include uptodate tag names and hash sets
                    try {
                        EventNode node = EventNode.createEventNode(item.getRepresentativeEventID(), controller.getEventsModel());
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
