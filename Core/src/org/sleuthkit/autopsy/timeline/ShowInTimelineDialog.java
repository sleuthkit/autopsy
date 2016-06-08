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
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 */
public class ShowInTimelineDialog extends Dialog<SingleEvent> {

    private static final ButtonType show = new ButtonType("Show Timeline", ButtonBar.ButtonData.OK_DONE);

    private static final Logger LOGGER = Logger.getLogger(ShowInTimelineDialog.class.getName());

    @FXML
    private TableView<SingleEvent> eventTable;

    @FXML
    private TableColumn<SingleEvent, EventType> typeColumn;

    @FXML
    private TableColumn<SingleEvent, Long> dateTimeColumn;

    @FXML
    private Spinner<?> amountsSpinner;

    @FXML
    private ChoiceBox<?> unitChoiceBox;
    private final VBox contentRoot;
    private final TimeLineController controller;

    public ShowInTimelineDialog(TimeLineController controller, AbstractFile file, Set<BlackboardArtifact> artifacts) {
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
        assert amountsSpinner != null : "fx:id=\"amountsSpinner\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        assert unitChoiceBox != null : "fx:id=\"unitChoiceBox\" was not injected: check your FXML file 'ShowInTimelineDialog.fxml'.";
        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(contentRoot);
        dialogPane.getButtonTypes().setAll(show, ButtonType.CANCEL);
        dialogPane.lookupButton(show).disableProperty().bind(eventTable.getSelectionModel().selectedItemProperty().isNull());

        setResultConverter((ButtonType param) -> {
            if (param == show) {
                return eventTable.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        typeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getEventType()));
        typeColumn.setCellFactory((TableColumn<SingleEvent, EventType> param) -> new TableCell<SingleEvent, EventType>() {
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

        });
        dateTimeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getStartMillis()));
        dateTimeColumn.setCellFactory((TableColumn<SingleEvent, Long> param) -> new TableCell<SingleEvent, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(TimeLineController.getZonedFormatter().print(item));
                }
            }

        });
        List<Long> eventIDS = controller.getEventsModel().getDerivedEventIDs(Collections.singleton(file.getId()), artifacts.stream().map(BlackboardArtifact::getArtifactID).collect(Collectors.toSet()));
        eventTable.getItems().setAll(eventIDS.stream().map(controller.getEventsModel()::getEventById).collect(Collectors.toSet()));
    }
}
