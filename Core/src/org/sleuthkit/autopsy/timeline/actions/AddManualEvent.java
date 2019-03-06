/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.actions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;
import javax.swing.SwingUtilities;
import jfxtras.scene.control.LocalDateTimeTextField;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.tools.ValueExtractor;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.PromptDialogManager;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TL_EVENT_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Action that allows the user the manually create timeline events. It prompts
 * the user for event data and then adds it to the case via an artifact.
 */
@NbBundle.Messages({
    "CreateManualEvent.text=Add Event",
    "CreateManualEvent.longText=Manually add an event to the timeline."})
public class AddManualEvent extends Action {

    private final static Logger logger = Logger.getLogger(AddManualEvent.class.getName());
    private static final String MANUAL_CREATION = "Manual Creation";
    private static final Image ADD_EVENT_IMAGE = new Image("/org/sleuthkit/autopsy/timeline/images/add.png", 16, 16, true, true, true); // NON-NLS

    private final TimeLineController controller;

    /**
     * Initialize the custom value extractor used by the ValidationSupport for
     * the LocalDateTimeTextField in the EventCreationDialogPane.
     */
    static {
        ValueExtractor.addObservableValueExtractor(LocalDateTimeTextField.class::isInstance,
                control -> ((LocalDateTimeTextField) control).localDateTimeProperty());
    }

    /**
     * Create an Action that allows the user the manually create timeline
     * events. It prompts the user for event data with a dialog and then adds it
     * to the case via an artifact. The datetiem in the dialog will be set to
     * "now" when the action is invoked.
     *
     * @param controller The controller for this action to use.
     *
     */
    public AddManualEvent(TimeLineController controller) {
        this(controller, null);
    }

    /**
     * Create an Action that allows the user the manually create timeline
     * events. It prompts the user for event data with a dialog and then adds it
     * to the case via an artifact.
     *
     * @param controller  The controller for this action to use.
     * @param epochMillis The initial datetime to populate the dialog with. The
     *                    user can ove ride this.
     */
    public AddManualEvent(TimeLineController controller, Long epochMillis) {
        super(Bundle.CreateManualEvent_text());
        this.controller = controller;
        setGraphic(new ImageView(ADD_EVENT_IMAGE));
        setLongText(Bundle.CreateManualEvent_longText());

        setEventHandler(actionEvent -> {
            //shoe the dialog and if it completed normally add the event.
            new EventCreationDialog(controller, epochMillis).showAndWait().ifPresent(this::addEvent);
        });
    }

    /**
     * Use the supplied ManualEventInfo to make an TSK_TL_EVENT artifact which
     * will trigger adding a TimelineEvent.
     *
     * @param eventInfo The ManualEventInfo with the info needed to create an
     *                  event.
     *
     * @throws IllegalArgumentException
     */
    private void addEvent(ManualEventInfo eventInfo) throws IllegalArgumentException {
        SleuthkitCase sleuthkitCase = controller.getEventsModel().getSleuthkitCase();

        try {
            //Use the current examiners name plus a fixed string as the source / module name.
            String source = MANUAL_CREATION + ": " + sleuthkitCase.getCurrentExaminer().getLoginName();

            BlackboardArtifact artifact = sleuthkitCase.newBlackboardArtifact(TSK_TL_EVENT, eventInfo.datasource.getId());
            artifact.addAttributes(asList(
                    new BlackboardAttribute(
                            TSK_TL_EVENT_TYPE, source,
                            EventType.USER_CREATED.getTypeID()),
                    new BlackboardAttribute(
                            TSK_DESCRIPTION, source,
                            eventInfo.description),
                    new BlackboardAttribute(
                            TSK_DATETIME, source,
                            eventInfo.time)
            ));
            try {
                sleuthkitCase.getBlackboard().postArtifact(artifact, source);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Error posting artifact to the blackboard.", ex);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error creatig new artifact.", ex);
        }
    }

    /** The dialog that allows the user to enter the event information. */
    private static class EventCreationDialog extends Dialog<ManualEventInfo> {

        /** Custom DialogPane defined below. */
        private final EventCreationDialogPane eventCreationDialogPane;

        EventCreationDialog(TimeLineController controller, Long epochMillis) {
            this.eventCreationDialogPane = new EventCreationDialogPane(controller, epochMillis);
            setTitle("Add Event");
            setDialogPane(eventCreationDialogPane);

            //We can't do these steps until after the dialog is shown or we get an error.
            setOnShown(dialogEvent -> {
                Platform.runLater(() -> {
                    PromptDialogManager.setDialogIcons(this);
                    eventCreationDialogPane.installValidation();
                });
            });

            // convert button presses to ManualEventInfo
            setResultConverter(buttonType
                    -> (buttonType == ButtonType.OK)
                            ? eventCreationDialogPane.getManualEventInfo()
                            : null
            );
        }

        /**
         * The DialogPane that hosts the controls that allows the user to enter
         * the event information.
         */
        static private class EventCreationDialogPane extends DialogPane {

            @FXML
            private ChoiceBox<DataSource> dataSourceChooser;
            @FXML
            private TextField descriptionTextField;
            @FXML
            private ComboBox<String> timeZoneChooser;
            @FXML
            private LocalDateTimeTextField timePicker;

            private final List<String> timeZoneList = TimeZoneUtils.createTimeZoneList();
            private final ValidationSupport validationSupport = new ValidationSupport();
            private final TimeLineController controller;

            EventCreationDialogPane(TimeLineController controller, Long epochMillis) {
                this.controller = controller;
                FXMLConstructor.construct(this, "EventCreationDialog.fxml");
                if (epochMillis == null) {
                    timePicker.setLocalDateTime(LocalDateTime.now());
                } else {
                    timePicker.setLocalDateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), TimeLineController.getTimeZoneID()));
                }
            }

            @FXML
            @NbBundle.Messages("AddManualEvent.EventCreationDialogPane.initialize.dataSourcesError=Error getting datasources in case.")
            void initialize() {
                assert descriptionTextField != null : "fx:id=\"descriptionTextField\" was not injected: check your FXML file 'EventCreationDialog.fxml'.";

                timeZoneChooser.getItems().setAll(timeZoneList);
                timeZoneChooser.getSelectionModel().select(TimeZoneUtils.createTimeZoneString(TimeLineController.getTimeZone()));
                TextFields.bindAutoCompletion(timeZoneChooser.getEditor(), timeZoneList);

                try {
                    dataSourceChooser.getItems().setAll(controller.getAutopsyCase().getSleuthkitCase().getDataSources());
                    dataSourceChooser.getSelectionModel().select(0);
                    dataSourceChooser.setConverter(new StringConverter<DataSource>() {
                        @Override
                        public String toString(DataSource dataSource) {
                            return dataSource.getName() + "(ID: " + dataSource.getId() + ")";
                        }

                        @Override
                        public DataSource fromString(String string) {
                            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                        }
                    });
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting datasources in case.", ex);
                    SwingUtilities.invokeLater(() -> MessageNotifyUtil.Message.error(Bundle.AddManualEvent_EventCreationDialogPane_initialize_dataSourcesError()));
                }
            }

            /**
             * Install/Configure the ValidationSupport.
             */
            void installValidation() {
                validationSupport.registerValidator(descriptionTextField, false, Validator.createEmptyValidator("Description is required"));
                validationSupport.registerValidator(timePicker, false, Validator.createPredicateValidator(Objects::nonNull, "Invalid datetime"));
                validationSupport.registerValidator(timeZoneChooser, false, Validator.createPredicateValidator((String zone) -> timeZoneList.contains(zone.trim()), "Invalid time zone"));

                validationSupport.initInitialDecoration();

                //The ok button is only enabled if all fields are validated.
                lookupButton(ButtonType.OK).disableProperty().bind(validationSupport.invalidProperty());
            }

            /**
             * Combine the user entered data into a ManulEventInfo object.
             *
             * @return The ManualEventInfo containing the user entered event
             *         info.
             */
            ManualEventInfo getManualEventInfo() {
                //Trim off the offset part of the string from the chooser, to get something that ZoneId can parse.
                String zone = StringUtils.substringAfter(timeZoneChooser.getValue(), ")").trim();
                long toEpochSecond = timePicker.getLocalDateTime().atZone(ZoneId.of(zone)).toEpochSecond();
                return new ManualEventInfo(dataSourceChooser.getValue(), descriptionTextField.getText(), toEpochSecond);
            }
        }
    }

    /**
     * Info required from user to manually create a timeline event.
     */
    private static class ManualEventInfo {

        DataSource datasource;
        String description;
        long time;

        ManualEventInfo(DataSource datasource, String description, long time) {
            this.datasource = datasource;
            this.description = description;
            this.time = time;
        }
    }
}
