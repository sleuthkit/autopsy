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
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.events.ViewInTimelineRequestedEvent;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * A Dialog that, given a AbstractFile OR BlackBoardArtifact, allows the user to
 * choose a specific event and a time range around it to show in the Timeline
 * List View.
 */
final class ShowInTimelineDialog extends Dialog<ViewInTimelineRequestedEvent> {

    private static final Logger LOGGER = Logger.getLogger(ShowInTimelineDialog.class.getName());

    @NbBundle.Messages({"ShowInTimelineDialog.showTimelineButtonType.text=Show Timeline"})
    private static final ButtonType SHOW = new ButtonType(Bundle.ShowInTimelineDialog_showTimelineButtonType_text(), ButtonBar.ButtonData.OK_DONE);

    @FXML
    private TableView<SingleEvent> eventTable;

    @FXML
    private TableColumn<SingleEvent, EventType> typeColumn;

    @FXML
    private TableColumn<SingleEvent, Long> dateTimeColumn;

    @FXML
    private Spinner<Integer> amountSpinner;

    @FXML
    private ComboBox<ChronoUnit> unitComboBox;

    @FXML
    private Label chooseEventLabel;

    private final VBox contentRoot = new VBox();

    private final TimeLineController controller;

    /**
     * List of ChronoUnits the user can select from when choosing a time range
     * to show.
     */
    private static final List<ChronoUnit> SCROLL_BY_UNITS = Arrays.asList(
            ChronoUnit.YEARS,
            ChronoUnit.MONTHS,
            ChronoUnit.DAYS,
            ChronoUnit.HOURS,
            ChronoUnit.MINUTES,
            ChronoUnit.SECONDS);

    /**
     * Common Private Constructor
     *
     * @param controller The controller for this Dialog.
     * @param eventIDS   A List of eventIDs to present to the user to choose
     *                   from.
     */
    private ShowInTimelineDialog(TimeLineController controller, List<Long> eventIDS) {
        this.controller = controller;

        //load dialog content fxml
        final String name = "nbres:/" + StringUtils.replace(ShowInTimelineDialog.class.getPackage().getName(), ".", "/") + "/ShowInTimelineDialog.fxml"; // NON-NLS
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(new URL(name));
            fxmlLoader.setRoot(contentRoot);
            fxmlLoader.setController(this);

            fxmlLoader.load();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to load FXML, node initialization may not be complete.", ex); //NON-NLS
        }
        //assert that fxml loading happened correctly
        assert eventTable != null : "fx:id=\"eventTable\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert typeColumn != null : "fx:id=\"typeColumn\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert dateTimeColumn != null : "fx:id=\"dateTimeColumn\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert amountSpinner != null : "fx:id=\"amountsSpinner\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert unitComboBox != null : "fx:id=\"unitChoiceBox\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";

        //configure dialog properties
        PromptDialogManager.setDialogIcons(this);
        setTitle(Bundle.Timeline_dialogs_title());
        initModality(Modality.APPLICATION_MODAL);

        //add scenegraph loaded from fxml to this dialog.
        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(contentRoot);
        //add buttons to dialog
        dialogPane.getButtonTypes().setAll(SHOW, ButtonType.CANCEL);

        ///configure dialog controls
        amountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000));

        unitComboBox.setButtonCell(new ChronoUnitListCell());
        unitComboBox.setCellFactory(comboBox -> new ChronoUnitListCell());
        unitComboBox.getItems().setAll(SCROLL_BY_UNITS);
        unitComboBox.getSelectionModel().select(ChronoUnit.MINUTES);

        typeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getEventType()));
        typeColumn.setCellFactory(param -> new TypeTableCell<>());

        dateTimeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getStartMillis()));
        dateTimeColumn.setCellFactory(param -> new DateTimeTableCell<>());

        //add events to table
        eventTable.getItems().setAll(eventIDS.stream().map(controller.getEventsModel()::getEventById).collect(Collectors.toSet()));
        eventTable.setPrefHeight(Math.min(200, 24 * eventTable.getItems().size() + 28));
    }

    /**
     * Constructor for artifact based dialog. suppressed the choosing event
     * aspect as each artifact is assumed to have only one associated event.
     *
     * @param controller The controller for this Dialog
     * @param artifact   The BlackboardArtifact to configure this dialog for.
     */
    ShowInTimelineDialog(TimeLineController controller, BlackboardArtifact artifact) {
        //get events IDs from artifact
        this(controller, controller.getEventsModel().getEventIDsForArtifact(artifact));

        //hide instructional label and autoselect first(and only) event.
        chooseEventLabel.setVisible(false);
        chooseEventLabel.setManaged(false);
        eventTable.getSelectionModel().select(0);

        //set result converter that does not require selection.
        setResultConverter(buttonType -> {
            if (buttonType == SHOW) {
                return makeEventInTimeRange(eventTable.getItems().get(0));
            } else {
                return null;
            }
        });
    }

    /**
     * Constructor for file based dialog. Allows the user to choose an event
     * (MAC time) derived from the given file
     *
     * @param controller The controller for this Dialog.
     * @param file       The AbstractFile to configure this dialog for.
     */
    ShowInTimelineDialog(TimeLineController controller, AbstractFile file) {
        this(controller, controller.getEventsModel().getEventIDsForFile(file, false));

        //require selection to enable show button
        getDialogPane().lookupButton(SHOW).disableProperty().bind(eventTable.getSelectionModel().selectedItemProperty().isNull());

        //set result converter that uses selection.
        setResultConverter(buttonType -> {
            if (buttonType == SHOW) {
                return makeEventInTimeRange(eventTable.getSelectionModel().getSelectedItem());
            } else {
                return null;
            }
        });
    }

    /**
     * Construct this Dialog's "result" from the given event.
     *
     * @param selectedEvent The SingleEvent to include in the EventInTimeRange
     *
     * @return The EventInTimeRange that is the "result" of this dialog.
     */
    private ViewInTimelineRequestedEvent makeEventInTimeRange(SingleEvent selectedEvent) {
        Duration selectedDuration = unitComboBox.getSelectionModel().getSelectedItem().getDuration().multipliedBy(amountSpinner.getValue());
        Interval range = IntervalUtils.getIntervalAround(Instant.ofEpochMilli(selectedEvent.getStartMillis()), selectedDuration);
        return new ViewInTimelineRequestedEvent(Collections.singleton(selectedEvent.getEventID()), range);
    }

    /**
     * ListCell that shows a ChronoUnit
     */
    static private class ChronoUnitListCell extends ListCell<ChronoUnit> {

        @Override
        protected void updateItem(ChronoUnit item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
            } else {
                setText(WordUtils.capitalizeFully(item.toString()));
            }
        }
    }

    /**
     * TableCell that shows a formatted date/time for a given millisecond since
     * the unix epoch
     *
     * @param <X> Anything
     */
    static private class DateTimeTableCell<X> extends TableCell<X, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setText(null);
            } else {
                setText(TimeLineController.getZonedFormatter().print(item));
            }
        }
    }

    /**
     * TableCell that shows a EventType including the associated icon.
     *
     * @param <X> Anything
     */
    static private class TypeTableCell<X> extends TableCell<X, EventType> {

        @Override
        protected void updateItem(EventType item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDisplayName());
                setGraphic(new ImageView(item.getFXImage()));
            }
        }
    }
}
