/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javafx.beans.binding.Bindings;
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
import javafx.util.converter.IntegerStringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.controlsfx.validation.ValidationMessage;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.events.ViewInTimelineRequestedEvent;
import org.sleuthkit.autopsy.timeline.ui.EventTypeUtils;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineEvent;

/**
 * A Dialog that, given an AbstractFile or BlackBoardArtifact, allows the user
 * to choose a specific event and a time range around it to show in the Timeline
 * List View.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class ShowInTimelineDialog extends Dialog<ViewInTimelineRequestedEvent> {

    private static final Logger LOGGER = Logger.getLogger(ShowInTimelineDialog.class.getName());

    @NbBundle.Messages({"ShowInTimelineDialog.showTimelineButtonType.text=Show Timeline"})
    private static final ButtonType SHOW = new ButtonType(Bundle.ShowInTimelineDialog_showTimelineButtonType_text(), ButtonBar.ButtonData.OK_DONE);

    /**
     * List of ChronoUnits the user can select from when choosing a time range
     * to show.
     */
    private static final List<ChronoField> SCROLL_BY_UNITS = Arrays.asList(
            ChronoField.YEAR,
            ChronoField.MONTH_OF_YEAR,
            ChronoField.DAY_OF_MONTH,
            ChronoField.HOUR_OF_DAY,
            ChronoField.MINUTE_OF_HOUR,
            ChronoField.SECOND_OF_MINUTE);

    @FXML
    private TableView<TimelineEvent> eventTable;

    @FXML
    private TableColumn<TimelineEvent, TimelineEventType> typeColumn;

    @FXML
    private TableColumn<TimelineEvent, Long> dateTimeColumn;

    @FXML
    private Spinner<Integer> amountSpinner;

    @FXML
    private ComboBox<ChronoField> unitComboBox;

    @FXML
    private Label chooseEventLabel;

    private final VBox contentRoot = new VBox();

    private final ValidationSupport validationSupport = new ValidationSupport();

    /**
     * Common Private Constructor
     *
     * @param controller The controller for this Dialog.
     * @param eventIDS   A List of eventIDs to present to the user to choose
     *                   from.
     */
    @NbBundle.Messages({
        "ShowInTimelineDialog.amountValidator.message=The entered amount must only contain digits."})
    private ShowInTimelineDialog(TimeLineController controller, Collection<Long> eventIDS) throws TskCoreException {

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

        //validat that spinner has a integer in the text field.
        validationSupport.registerValidator(amountSpinner.getEditor(), false,
                Validator.createPredicateValidator(NumberUtils::isDigits, Bundle.ShowInTimelineDialog_amountValidator_message()));

        //configure dialog properties
        PromptDialogManager.setDialogIcons(this);
        initModality(Modality.APPLICATION_MODAL);

        //add scenegraph loaded from fxml to this dialog.
        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(contentRoot);
        //add buttons to dialog
        dialogPane.getButtonTypes().setAll(SHOW, ButtonType.CANCEL);

        ///configure dialog controls
        amountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000));
        amountSpinner.getValueFactory().setConverter(new IntegerStringConverter() {
            /**
             * Convert the String to an Integer using Integer.valueOf, but if
             * that throws a NumberFormatException, reset the spinner to the
             * last valid value.
             *
             * @param string The String to convert
             *
             * @return The Integer value of string.
             */
            @Override
            public Integer fromString(String string) {
                try {
                    return super.fromString(string);
                } catch (NumberFormatException ex) {
                    return amountSpinner.getValue();
                }
            }
        });

        unitComboBox.setButtonCell(new ChronoFieldListCell());
        unitComboBox.setCellFactory(comboBox -> new ChronoFieldListCell());
        unitComboBox.getItems().setAll(SCROLL_BY_UNITS);
        unitComboBox.getSelectionModel().select(ChronoField.MINUTE_OF_HOUR);

        typeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getEventType()));
        typeColumn.setCellFactory(param -> new TypeTableCell<>());

        dateTimeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getEventTimeInMs()));
        dateTimeColumn.setCellFactory(param -> new DateTimeTableCell<>());

        //add events to table
        Set<TimelineEvent> events = new HashSet<>();
        EventsModel eventsModel = controller.getEventsModel();
        for (Long eventID : eventIDS) {
            try {
                events.add(eventsModel.getEventById(eventID));
            } catch (TskCoreException ex) {
                throw new TskCoreException("Error getting event by id.", ex);
            }
        }
        eventTable.getItems().setAll(events);
        eventTable.setPrefHeight(Math.min(200, 24 * eventTable.getItems().size() + 28));
    }

    /**
     * Constructor for artifact based dialog. suppressed the choosing event
     * aspect as each artifact is assumed to have only one associated event.
     *
     * @param controller The controller for this Dialog
     * @param artifact   The BlackboardArtifact to configure this dialog for.
     */
    @NbBundle.Messages({"ShowInTimelineDialog.artifactTitle=View Result in Timeline."})
    ShowInTimelineDialog(TimeLineController controller, BlackboardArtifact artifact) throws TskCoreException {
        //get events IDs from artifact
        this(controller, controller.getEventsModel().getEventIDsForArtifact(artifact));

        //hide instructional label and autoselect first(and only) event.
        chooseEventLabel.setVisible(false);
        chooseEventLabel.setManaged(false);
        eventTable.getSelectionModel().select(0);

        //require validation of ammount spinner to enable show button
        getDialogPane().lookupButton(SHOW).disableProperty().bind(validationSupport.invalidProperty());

        //set result converter that does not require selection.
        setResultConverter(buttonType -> (buttonType == SHOW)
                ? makeEventInTimeRange(eventTable.getItems().get(0))
                : null
        );
        setTitle(Bundle.ShowInTimelineDialog_artifactTitle());
    }

    /**
     * Constructor for file based dialog. Allows the user to choose an event
     * (MAC time) derived from the given file
     *
     * @param controller The controller for this Dialog.
     * @param file       The AbstractFile to configure this dialog for.
     */
    @NbBundle.Messages({"# {0} - file path",
        "ShowInTimelineDialog.fileTitle=View {0} in timeline.",
        "ShowInTimelineDialog.eventSelectionValidator.message=You must select an event."})
    ShowInTimelineDialog(TimeLineController controller, AbstractFile file) throws TskCoreException {
        this(controller, controller.getEventsModel().getEventIDsForFile(file, false));

        /*
         * since ValidationSupport does not support list selection, we will
         * manually apply and remove decoration in response to selection
         * property changes.
         */
        eventTable.getSelectionModel().selectedItemProperty().isNull().addListener((selectedItemNullProperty, wasNull, isNull) -> {
            if (isNull) {
                validationSupport.getValidationDecorator().applyValidationDecoration(
                        ValidationMessage.error(eventTable, Bundle.ShowInTimelineDialog_eventSelectionValidator_message()));
            } else {
                validationSupport.getValidationDecorator().removeDecorations(eventTable);
            }
        });

        //require selection and validation of ammount spinner to enable show button
        getDialogPane().lookupButton(SHOW).disableProperty().bind(Bindings.or(
                validationSupport.invalidProperty(),
                eventTable.getSelectionModel().selectedItemProperty().isNull()
        ));

        //set result converter that uses selection.
        setResultConverter(buttonType -> (buttonType == SHOW)
                ? makeEventInTimeRange(eventTable.getSelectionModel().getSelectedItem())
                : null
        );

        setTitle(Bundle.ShowInTimelineDialog_fileTitle(StringUtils.abbreviateMiddle(getContentPathSafe(file), " ... ", 50)));
    }

    /**
     * Get the unique path for the content, or if that fails, just return the
     * name.
     *
     * NOTE: This was copied from IamgeUtils and should be refactored to avoid
     * duplication.
     *
     * @param content
     *
     * @return the unique path for the content, or if that fails, just the name.
     */
    static String getContentPathSafe(Content content) {
        try {
            return content.getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = content.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NON-NLS
            return contentName;
        }
    }

    /**
     * Construct this Dialog's "result" from the given event.
     *
     * @param selectedEvent The TimeLineEvent to include in the EventInTimeRange
     *
     * @return The EventInTimeRange that is the "result" of this dialog.
     */
    private ViewInTimelineRequestedEvent makeEventInTimeRange(TimelineEvent selectedEvent) {
        Duration selectedDuration = unitComboBox.getSelectionModel().getSelectedItem().getBaseUnit().getDuration().multipliedBy(amountSpinner.getValue());
        Interval range = IntervalUtils.getIntervalAround(Instant.ofEpochMilli(selectedEvent.getEventTimeInMs()), selectedDuration);
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
     * TableCell that shows a TimelineEventType including the associated icon.
     *
     * @param <X> Anything
     */
    static private class TypeTableCell<X> extends TableCell<X, TimelineEventType> {

        @Override
        protected void updateItem(TimelineEventType item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDisplayName());
                setGraphic(new ImageView(EventTypeUtils.getImagePath(item)));
            }
        }
    }
}
