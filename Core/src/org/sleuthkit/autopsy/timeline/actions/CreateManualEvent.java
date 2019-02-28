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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import jfxtras.scene.control.LocalDateTimeTextField;
import org.apache.commons.lang3.StringUtils;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.tools.ValueExtractor;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
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
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Action that prompts the user for the event data and then adds it to the case
 * via an artifact.
 */
public class CreateManualEvent extends Action {

    private final static Logger logger = Logger.getLogger(CreateManualEvent.class.getName());

    private final TimeLineController controller;

    private static final String MANUAL_CREATION = "Manual Creation";

    static {
        ValueExtractor.addObservableValueExtractor(LocalDateTimeTextField.class::isInstance,
                control -> ((LocalDateTimeTextField) control).localDateTimeProperty());
    }

    @NbBundle.Messages({
        "CreateManualEvent.text=Add Event",
        "CreateManualEvent.longText=Manually add an event to the timeline."})
    public CreateManualEvent(TimeLineController controller) {
        super(Bundle.CreateManualEvent_text());
        this.controller = controller;
//        setGraphic(value);
        setLongText(Bundle.CreateManualEvent_longText());
        setEventHandler(actionEvent -> new EventCreationDialog().showAndWait().ifPresent(this::addEvent));
    }

    private void addEvent(ManualEventInfo eventInfo) throws IllegalArgumentException {
        SleuthkitCase sleuthkitCase = controller.getEventsModel().getSleuthkitCase();

        try {
            BlackboardArtifact artifact = sleuthkitCase.newBlackboardArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT, 1);
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

    private static class EventCreationDialog extends Dialog<ManualEventInfo> {

        private final EventCreationDialogPane eventCreationDialogPane = new EventCreationDialogPane();

        EventCreationDialog() {
            setHeaderText("Add Event");
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

        static private class EventCreationDialogPane extends DialogPane {

            @FXML
            private TextField descriptionTextField;
            @FXML
            private ComboBox<String> timeZoneChooser;
            @FXML
            private LocalDateTimeTextField timePicker;

            private final List<String> timeZoneList = TimeZoneUtils.createTimeZoneList();
            private final ValidationSupport validationSupport = new ValidationSupport();

            EventCreationDialogPane() {
                FXMLConstructor.construct(this, "EventCreationDialog.fxml");
            }

            @FXML
            void initialize() {
                assert descriptionTextField != null : "fx:id=\"descriptionTextField\" was not injected: check your FXML file 'EventCreationDialog.fxml'.";

                timeZoneChooser.getItems().setAll(timeZoneList);
                timeZoneChooser.getSelectionModel().select(TimeZoneUtils.createTimeZoneString(TimeZone.getDefault()));
                TextFields.bindAutoCompletion(timeZoneChooser.getEditor(), timeZoneList);

                timePicker.setLocalDateTime(LocalDateTime.now());

            }

            void installValidation() {
                validationSupport.registerValidator(descriptionTextField, Validator.createEmptyValidator("Description is required"));
                validationSupport.registerValidator(timePicker, Validator.createPredicateValidator(Objects::nonNull, "Invalid datetime"));
                validationSupport.registerValidator(timeZoneChooser, Validator.createPredicateValidator((String zone) -> timeZoneList.contains(zone.trim()), "Invalid time zone"));

                validationSupport.initInitialDecoration();

                lookupButton(ButtonType.OK).disableProperty().bind(validationSupport.invalidProperty());
            }

            ManualEventInfo getManualEventInfo() {
                String zone = StringUtils.substringAfter(timeZoneChooser.getValue(), ")").trim();
                long toEpochSecond = timePicker.getLocalDateTime().atZone(ZoneId.of(zone)).toEpochSecond();
                return new ManualEventInfo(descriptionTextField.getText(), toEpochSecond);
            }
        }
    }

    /**
     * Minimal info required to manually create a timeline event.
     */
    private static class ManualEventInfo {

        String description;
        long time;

        ManualEventInfo(String description, long time) {
            this.description = description;
            this.time = time;
        }
    }
}
