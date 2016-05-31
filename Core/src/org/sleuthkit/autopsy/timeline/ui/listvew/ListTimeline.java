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

import com.google.common.collect.Iterables;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javax.swing.Action;
import javax.swing.JMenuItem;
import org.controlsfx.control.Notifications;
import org.openide.awt.Actions;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.CombinedEvent;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.BaseTypes;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.FileSystemTypes;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The inner component that makes up the List view. Manages the TableView.
 */
class ListTimeline extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ListTimeline.class.getName());

    private static final Image HASH_HIT = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png");  //NON-NLS 
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png");  //NON-NLS

    /**
     * call-back used to wrap the CombinedEvent in a ObservableValue
     */
    private static final Callback<TableColumn.CellDataFeatures<CombinedEvent, CombinedEvent>, ObservableValue<CombinedEvent>> CELL_VALUE_FACTORY = param -> new SimpleObjectProperty<>(param.getValue());

    @FXML
    private HBox navControls;

    @FXML
    private ComboBox<ChronoUnit> scrollInrementComboBox;

    @FXML
    private Button firstButton;

    @FXML
    private Button previousButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button lastButton;

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

    /**
     * Observable list used to track selected events.
     */
    private final ObservableList<Long> selectedEventIDs = FXCollections.observableArrayList();

    private final TimeLineController controller;
    private final SleuthkitCase sleuthkitCase;
    private final TagsManager tagsManager;

    /**
     * Constructor
     *
     * @param controller The controller for this timeline
     */
    ListTimeline(TimeLineController controller) {
        this.controller = controller;
        sleuthkitCase = controller.getAutopsyCase().getSleuthkitCase();
        tagsManager = controller.getAutopsyCase().getServices().getTagsManager();
        FXMLConstructor.construct(this, ListTimeline.class, "ListTimeline.fxml"); //NON-NLS
    }

    @FXML
    @NbBundle.Messages({
        "# {0} - the number of events",
        "ListTimeline.eventCountLabel.text={0} events"})
    void initialize() {
        assert eventCountLabel != null : "fx:id=\"eventCountLabel\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert table != null : "fx:id=\"table\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert idColumn != null : "fx:id=\"idColumn\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert dateTimeColumn != null : "fx:id=\"dateTimeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert descriptionColumn != null : "fx:id=\"descriptionColumn\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert typeColumn != null : "fx:id=\"typeColumn\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS
        assert knownColumn != null : "fx:id=\"knownColumn\" was not injected: check your FXML file 'ListViewPane.fxml'."; //NON-NLS

        scrollInrementComboBox.getItems().setAll(
                ChronoUnit.YEARS,
                ChronoUnit.MONTHS,
                ChronoUnit.DAYS,
                ChronoUnit.HOURS,
                ChronoUnit.MINUTES,
                ChronoUnit.SECONDS,
                ChronoUnit.MILLIS
        );
        scrollInrementComboBox.getSelectionModel().select(ChronoUnit.YEARS);

        firstButton.setOnAction(actionEvent -> table.scrollTo(0));
        previousButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                LocalDateTime selectedDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(table.getSelectionModel().getSelectedItem().getStartMillis()),
                        TimeLineController.getTimeZone().get().toZoneId());
                LocalDateTime nextDateTime = scrollInrementComboBox.getSelectionModel().getSelectedItem().addTo(selectedDateTime, -1);

                table.getItems().stream().filter(combinedEvent -> {
                    LocalDateTime eventDateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(combinedEvent.getStartMillis()), TimeLineController.getTimeZone().get().toZoneId());
                    return eventDateTime.isBefore(nextDateTime);
                }).findFirst().ifPresent(table::scrollTo);
            }
        });

        nextButton.setOnAction(actionEvent -> {
            LocalDateTime selectedDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(table.getSelectionModel().getSelectedItem().getStartMillis()),
                    TimeLineController.getTimeZone().get().toZoneId());
            LocalDateTime nextDateTime = scrollInrementComboBox.getSelectionModel().getSelectedItem().addTo(selectedDateTime, 1);

            table.getItems().stream().filter(combinedEvent -> {
                LocalDateTime eventDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(combinedEvent.getStartMillis()), TimeLineController.getTimeZone().get().toZoneId());
                return eventDateTime.isAfter(nextDateTime);
            }).findFirst().ifPresent(table::scrollTo);
        });

        lastButton.setOnAction(actionEvent -> table.scrollTo(table.getItems().size()));

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
                return Bundle.ListTimeline_eventCountLabel_text(table.getItems().size());
            }
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener((Observable observable) -> {
            //keep the selectedEventsIDs in sync with the table's selection model, via getRepresentitiveEventID(). 
            selectedEventIDs.setAll(table.getSelectionModel().getSelectedItems().stream()
                    .filter(Objects::nonNull)
                    .map(CombinedEvent::getRepresentativeEventID)
                    .collect(Collectors.toSet()));
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

    List<Node> getNavControls() {
        return Collections.singletonList(navControls);
    }

    /**
     * TableCell to show the (sub) type of an event.
     */
    private class EventTypeCell extends EventTableCell {

        @NbBundle.Messages({
            "ListView.EventTypeCell.modifiedTooltip=File Modified ( M )",
            "ListView.EventTypeCell.accessedTooltip=File Accessed ( A )",
            "ListView.EventTypeCell.createdTooltip=File Created ( B, for Born )",
            "ListView.EventTypeCell.changedTooltip=File Changed ( C )"
        })
        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                if (item.getEventTypes().stream().allMatch(eventType -> eventType instanceof FileSystemTypes)) {
                    String typeString = ""; //NON-NLS
                    VBox toolTipVbox = new VBox(5);

                    for (FileSystemTypes type : Arrays.asList(FileSystemTypes.FILE_MODIFIED, FileSystemTypes.FILE_ACCESSED, FileSystemTypes.FILE_CHANGED, FileSystemTypes.FILE_CREATED)) {
                        if (item.getEventTypes().contains(type)) {
                            switch (type) {
                                case FILE_MODIFIED:
                                    typeString += "M"; //NON-NLS
                                    toolTipVbox.getChildren().add(new Label(Bundle.ListView_EventTypeCell_modifiedTooltip(), new ImageView(type.getFXImage())));
                                    break;
                                case FILE_ACCESSED:
                                    typeString += "A"; //NON-NLS
                                    toolTipVbox.getChildren().add(new Label(Bundle.ListView_EventTypeCell_accessedTooltip(), new ImageView(type.getFXImage())));
                                    break;
                                case FILE_CREATED:
                                    typeString += "B"; //NON-NLS
                                    toolTipVbox.getChildren().add(new Label(Bundle.ListView_EventTypeCell_createdTooltip(), new ImageView(type.getFXImage())));
                                    break;
                                case FILE_CHANGED:
                                    typeString += "C"; //NON-NLS
                                    toolTipVbox.getChildren().add(new Label(Bundle.ListView_EventTypeCell_changedTooltip(), new ImageView(type.getFXImage())));
                                    break;
                                default:
                                    throw new UnsupportedOperationException("Unknown FileSystemType: " + type.name()); //NON-NLS
                            }
                        } else {
                            typeString += "_"; //NON-NLS
                        }
                    }
                    setText(typeString);
                    setGraphic(new ImageView(BaseTypes.FILE_SYSTEM.getFXImage()));
                    Tooltip tooltip = new Tooltip();
                    tooltip.setGraphic(toolTipVbox);
                    setTooltip(tooltip);

                } else {
                    EventType eventType = Iterables.getOnlyElement(item.getEventTypes());
                    setText(eventType.getDisplayName());
                    setGraphic(new ImageView(eventType.getFXImage()));
                    setTooltip(new Tooltip(eventType.getDisplayName()));
                };
            }
        }
    }

    /**
     * A TableCell that shows information about the tags applied to a event.
     */
    private class TaggedCell extends EventTableCell {

        /**
         * Constructor
         */
        TaggedCell() {
            setAlignment(Pos.CENTER);
        }

        @NbBundle.Messages({
            "ListTimeline.taggedTooltip.error=There was a problem getting the tag names for the selected event.",
            "# {0} - tag names",
            "ListTimeline.taggedTooltip.text=Tags:\n{0}"})
        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null || (getEvent().isTagged() == false)) {
                setGraphic(null);
                setTooltip(null);
            } else {
                /*
                 * if the cell is not empty and the event is tagged, show the
                 * tagged icon, and show a list of tag names in the tooltip
                 */
                setGraphic(new ImageView(TAG));

                SortedSet<String> tagNames = new TreeSet<>();
                try {
                    //get file tags
                    AbstractFile abstractFileById = sleuthkitCase.getAbstractFileById(getEvent().getFileID());
                    tagsManager.getContentTagsByContent(abstractFileById).stream()
                            .map(tag -> tag.getName().getDisplayName())
                            .forEach(tagNames::add);

                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to lookup tags for obj id " + getEvent().getFileID(), ex); //NON-NLS
                    Platform.runLater(() -> {
                        Notifications.create()
                                .owner(getScene().getWindow())
                                .text(Bundle.ListTimeline_taggedTooltip_error())
                                .showError();
                    });
                }
                getEvent().getArtifactID().ifPresent(artifactID -> {
                    //get artifact tags, if there is an artifact associated with the event.
                    try {
                        BlackboardArtifact artifact = sleuthkitCase.getBlackboardArtifact(artifactID);
                        tagsManager.getBlackboardArtifactTagsByArtifact(artifact).stream()
                                .map(tag -> tag.getName().getDisplayName())
                                .forEach(tagNames::add);
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to lookup tags for artifact id " + artifactID, ex); //NON-NLS
                        Platform.runLater(() -> {
                            Notifications.create()
                                    .owner(getScene().getWindow())
                                    .text(Bundle.ListTimeline_taggedTooltip_error())
                                    .showError();
                        });
                    }
                });
                Tooltip tooltip = new Tooltip(Bundle.ListTimeline_taggedTooltip_text(String.join("\n", tagNames))); //NON-NLS
                tooltip.setGraphic(new ImageView(TAG));
                setTooltip(tooltip);
            }
        }
    }

    /**
     * TableCell to show the hash hits if any associated with the file backing
     * an event.
     */
    private class HashHitCell extends EventTableCell {

        /**
         * Constructor
         */
        HashHitCell() {
            setAlignment(Pos.CENTER);
        }

        @NbBundle.Messages({
            "ListTimeline.hashHitTooltip.error=There was a problem getting the hash set names for the selected event.",
            "# {0} - hash set names",
            "ListTimeline.hashHitTooltip.text=Hash Sets:\n{0}"})
        @Override
        protected void updateItem(CombinedEvent item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null || (getEvent().isHashHit() == false)) {
                setGraphic(null);
                setTooltip(null);
            } else {
                /*
                 * if the cell is not empty and the event's file is a hash hit,
                 * show the hash hit icon, and show a list of hash set names in
                 * the tooltip
                 */
                setGraphic(new ImageView(HASH_HIT));
                try {
                    Set<String> hashSetNames = new TreeSet<>(sleuthkitCase.getAbstractFileById(getEvent().getFileID()).getHashSetNames());
                    Tooltip tooltip = new Tooltip(Bundle.ListTimeline_hashHitTooltip_text(String.join("\n", hashSetNames))); //NON-NLS
                    tooltip.setGraphic(new ImageView(HASH_HIT));
                    setTooltip(tooltip);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to lookup hash set names for obj id " + getEvent().getFileID(), ex); //NON-NLS
                    Platform.runLater(() -> {
                        Notifications.create()
                                .owner(getScene().getWindow())
                                .text(Bundle.ListTimeline_hashHitTooltip_error())
                                .showError();
                    });
                }
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
            setEllipsisString(" ... "); //NON-NLS
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
                                if (Arrays.asList("&Properties", "Tools").contains(actionName) == false) { //NON-NLS
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
                        LOGGER.log(Level.SEVERE, "There was no case open to lookup the Sleuthkit object backing a SingleEvent.", ex); //NON-NLS
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); //NON-NLS
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
