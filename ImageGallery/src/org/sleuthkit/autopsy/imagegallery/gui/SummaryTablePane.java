/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui;

import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;

/**
 * Displays summary statistics (counts) for each group
 */
public class SummaryTablePane extends AnchorPane {

    @FXML
    private TableColumn<Pair<Category, Long>, String> catColumn;

    @FXML
    private TableColumn<Pair<Category, Long>, Long> countColumn;

    @FXML
    private TableView<Pair<Category, Long>> tableView;
    private final ImageGalleryController controller;

    @FXML
    void initialize() {
        assert catColumn != null : "fx:id=\"catColumn\" was not injected: check your FXML file 'SummaryTablePane.fxml'.";
        assert countColumn != null : "fx:id=\"countColumn\" was not injected: check your FXML file 'SummaryTablePane.fxml'.";
        assert tableView != null : "fx:id=\"tableView\" was not injected: check your FXML file 'SummaryTablePane.fxml'.";

        //set some properties related to layout/resizing
        VBox.setVgrow(this, Priority.NEVER);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.prefHeightProperty().set(7 * 25);

        //set up columns
        catColumn.setCellValueFactory((TableColumn.CellDataFeatures<Pair<Category, Long>, String> p) -> new SimpleObjectProperty<>(p.getValue().getKey().getDisplayName()));
        catColumn.setPrefWidth(USE_COMPUTED_SIZE);

        countColumn.setCellValueFactory((TableColumn.CellDataFeatures<Pair<Category, Long>, Long> p) -> new SimpleObjectProperty<>(p.getValue().getValue()));
        countColumn.setPrefWidth(USE_COMPUTED_SIZE);

        tableView.getColumns().setAll(Arrays.asList(catColumn, countColumn));

        //register for category events
        controller.getCategoryManager().registerListener(this);
        handleCategoryChanged(null);
    }

    public SummaryTablePane(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "SummaryTablePane.fxml");

    }

    /**
     * listen to Category updates and rebuild the table
     */
    @Subscribe
    public void handleCategoryChanged(org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager.CategoryChangeEvent evt) {
        final ObservableList<Pair<Category, Long>> data = FXCollections.observableArrayList();
        if (Case.isCaseOpen()) {
            for (Category cat : Category.values()) {
                data.add(new Pair<>(cat, controller.getCategoryManager().getCategoryCount(cat)));
            }
        }
        Platform.runLater(() -> {
            if (tableView != null) {
                tableView.setItems(data);
            }
        });
    }
}
