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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
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
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TL_EVENT_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Action that prompts the user for the event data and then adds it to the case
 * via an artifact.
 */
public class AddManualEvent extends Action {

    private final static Logger logger = Logger.getLogger(AddManualEvent.class.getName());
    private static final String MANUAL_CREATION = "Manual Creation";
    private static final Image ADD_EVENT_IMAGE = new Image("/org/sleuthkit/autopsy/timeline/images/add.png", 16, 16, true, true, true); // NON-NLS

    private final TimeLineController controller;

    static {
        ValueExtractor.addObservableValueExtractor(LocalDateTimeTextField.class::isInstance,
                control -> ((LocalDateTimeTextField) control).localDateTimeProperty());
    }

    @NbBundle.Messages({
        "CreateManualEvent.text=Add Event",
        "CreateManualEvent.longText=Manually add an event to the timeline."})
    public AddManualEvent(TimeLineController controller) {
        super(Bundle.CreateManualEvent_text());
        this.controller = controller;
        setGraphic(new ImageView(ADD_EVENT_IMAGE));
        setLongText(Bundle.CreateManualEvent_longText());
        setEventHandler(actionEvent -> new EventCreationDialog(controller).showAndWait().ifPresent(this::addEvent));
    }

    private void addEvent(ManualEventInfo eventInfo) throws IllegalArgumentException {
        SleuthkitCase sleuthkitCase = controller.getEventsModel().getSleuthkitCase();

        try {
            BlackboardArtifact artifact = sleuthkitCase.newBlackboardArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT, eventInfo.datasource.getId());
            Collection<BlackboardAttribute> attributes = Arrays.asList(
                    new BlackboardAttribute(
                            TSK_TL_EVENT_TYPE, MANUAL_CREATION,
                            EventType.OTHER.getTypeID()),
                    new BlackboardAttribute(
                            TSK_DESCRIPTION, MANUAL_CREATION,
                            eventInfo.description),
                    new BlackboardAttribute(
                            TSK_DATETIME, MANUAL_CREATION,
                            eventInfo.time)
            );
            artifact.addAttributes(attributes);
            sleuthkitCase.getBlackboard().postArtifact(artifact, MANUAL_CREATION);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error creatig new artifact.", ex);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error posting artifact to the blackboard.", ex);
        }
    }

    /**
     * The dialog that allows the user to enter the event information.
     */
    private static class EventCreationDialog extends Dialog<ManualEventInfo> {

        private final EventCreationDialogPane eventCreationDialogPane;

        EventCreationDialog(TimeLineController controller) {
            this.eventCreationDialogPane = new EventCreationDialogPane(controller);
            setTitle("Add Event");
            setDialogPane(eventCreationDialogPane);
            setOnShown(dialogEvent -> {
                Platform.runLater(() -> {
                    PromptDialogManager.setDialogIcons(this);
                    eventCreationDialogPane.installValidation();
                });
            });

            setResultConverter(buttonType
                    -> (buttonType == ButtonType.OK)
                            ? eventCreationDialogPane.getManualEventInfo()
                            : null
            );
        }

        /**
         * The dialog pane that hosts the controls that allows the user to enter
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

            EventCreationDialogPane(TimeLineController controller) {
                this.controller = controller;
                FXMLConstructor.construct(this, "EventCreationDialog.fxml");
            }

            @FXML
            @NbBundle.Messages("AddManualEvent.EventCreationDialogPane.initialize.dataSourcesError=Error getting datasources in case.")
            void initialize() {
                assert descriptionTextField != null : "fx:id=\"descriptionTextField\" was not injected: check your FXML file 'EventCreationDialog.fxml'.";

                timeZoneChooser.getItems().setAll(timeZoneList);
                timeZoneChooser.getSelectionModel().select(TimeZoneUtils.createTimeZoneString(TimeZone.getDefault()));
                TextFields.bindAutoCompletion(timeZoneChooser.getEditor(), timeZoneList);

                timePicker.setLocalDateTime(LocalDateTime.now());

                try {
                    dataSourceChooser.getItems().setAll(controller.getAutopsyCase().getSleuthkitCase().getDataSources());

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting datasources in case.", ex);
                    SwingUtilities.invokeLater(() -> MessageNotifyUtil.Message.error(Bundle.AddManualEvent_EventCreationDialogPane_initialize_dataSourcesError()));
                }
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
            }

            void installValidation() {
                validationSupport.registerValidator(descriptionTextField, false, Validator.createEmptyValidator("Description is required"));
                validationSupport.registerValidator(timePicker, false, Validator.createPredicateValidator(Objects::nonNull, "Invalid datetime"));
                validationSupport.registerValidator(timeZoneChooser, false, Validator.createPredicateValidator((String zone) -> timeZoneList.contains(zone.trim()), "Invalid time zone"));

                validationSupport.initInitialDecoration();

                lookupButton(ButtonType.OK).disableProperty().bind(validationSupport.invalidProperty());
            }

            ManualEventInfo getManualEventInfo() {
                String zone = StringUtils.substringAfter(timeZoneChooser.getValue(), ")").trim();
                long toEpochSecond = timePicker.getLocalDateTime().atZone(ZoneId.of(zone)).toEpochSecond();
                return new ManualEventInfo(dataSourceChooser.getValue(), descriptionTextField.getText(), toEpochSecond);
            }
        }
    }

    /**
     * Minimal info required to manually create a timeline event.
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
