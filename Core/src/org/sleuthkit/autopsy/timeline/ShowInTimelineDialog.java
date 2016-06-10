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
import java.util.Set;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 */
public class ShowInTimelineDialog extends Dialog<ShowInTimelineDialog.EventInTimeRange> {

    private static final ButtonType SHOW = new ButtonType("Show Timeline", ButtonBar.ButtonData.OK_DONE);

    private static final Logger LOGGER = Logger.getLogger(ShowInTimelineDialog.class.getName());

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

    private final VBox contentRoot;
    private final TimeLineController controller;

    private static final List<ChronoUnit> SCROLL_BY_UNITS = Arrays.asList(
            ChronoUnit.YEARS,
            ChronoUnit.MONTHS,
            ChronoUnit.DAYS,
            ChronoUnit.HOURS,
            ChronoUnit.MINUTES,
            ChronoUnit.SECONDS);

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

    public ShowInTimelineDialog(TimeLineController controller, AbstractFile file, BlackboardArtifact artifact) {
        super();
        this.controller = controller;
        contentRoot = new VBox();
        final String name = "nbres:/" + StringUtils.replace(ShowInTimelineDialog.class.getPackage().getName(), ".", "/") + "/ShowInTimelineDialog.fxml"; // NON-NLS

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(new URL(name));
            fxmlLoader.setRoot(contentRoot);
            fxmlLoader.setController(this);

            fxmlLoader.load();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to load FXML, node initialization may not be complete.", ex); //NON-NLS
        }

        assert eventTable != null : "fx:id=\"eventTable\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert typeColumn != null : "fx:id=\"typeColumn\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert dateTimeColumn != null : "fx:id=\"dateTimeColumn\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert amountSpinner != null : "fx:id=\"amountsSpinner\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert unitComboBox != null : "fx:id=\"unitChoiceBox\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(contentRoot);
        dialogPane.getButtonTypes().setAll(SHOW, ButtonType.CANCEL);

        setResultConverter(buttonType -> {
            if (buttonType == SHOW) {
                SingleEvent selectedEvent = eventTable.getSelectionModel().getSelectedItem();

                if (file == null) {
                    selectedEvent = eventTable.getItems().get(0);
                }
                Duration selectedDuration = Duration.of(amountSpinner.getValue(), unitComboBox.getSelectionModel().getSelectedItem());

                Interval range = IntervalUtils.getIntervalAround(Instant.ofEpochMilli(selectedEvent.getStartMillis()), selectedDuration);
                return new EventInTimeRange(Collections.singleton(selectedEvent.getEventID()), range);
            } else {
                return null;
            }
        });

        amountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000));

        unitComboBox.setButtonCell(new ChronoUnitListCell());
        unitComboBox.setCellFactory(comboBox -> new ChronoUnitListCell());
        unitComboBox.getItems().setAll(SCROLL_BY_UNITS);
        unitComboBox.getSelectionModel().select(ChronoUnit.MINUTES);

        typeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getEventType()));
        typeColumn.setCellFactory(param -> new TypeTableCell<>());

        dateTimeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getStartMillis()));
        dateTimeColumn.setCellFactory(param -> new DateTimeTableCell<>());

        List<Long> eventIDS;
        if (file != null) {
            eventIDS = controller.getEventsModel().getEventIDsForFile(file, false);
            dialogPane.lookupButton(SHOW).disableProperty().bind(eventTable.getSelectionModel().selectedItemProperty().isNull());
        } else if (artifact != null) {

            eventIDS = controller.getEventsModel().getEventIDsForArtifact(artifact);
        } else {
            throw new IllegalArgumentException();
        }
        setResizable(true);
        eventTable.getItems().setAll(eventIDS.stream().map(controller.getEventsModel()::getEventById).collect(Collectors.toSet()));
        if (eventIDS.size() == 1) {
            chooseEventLabel.setVisible(false);
            chooseEventLabel.setManaged(false);
            eventTable.getSelectionModel().select(0);
        }
        eventTable.setPrefHeight(Math.min(200, 24 * eventTable.getItems().size() + 28));
    }

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

    /**
     * Encapsulates the result of the ShowIntimelineDialog.
     */
    static final class EventInTimeRange {

        private final Set<Long> eventIDs;
        private final Interval range;

        EventInTimeRange(Set<Long> eventIDs, Interval range) {
            this.eventIDs = eventIDs;
            this.range = range;
        }

        public Set<Long> getEventIDs() {
            return eventIDs;
        }

        public Interval getRange() {
            return range;
        }

    }
}
