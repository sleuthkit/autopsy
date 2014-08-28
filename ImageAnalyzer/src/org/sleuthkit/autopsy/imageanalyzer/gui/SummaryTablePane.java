/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.gui;

import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;
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
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.imageanalyzer.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.ImageAnalyzerController;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.Category;
import org.sleuthkit.datamodel.TskCoreException;

/** Displays summary statistics (counts) for each group */
public class SummaryTablePane extends AnchorPane implements Category.CategoryListener {

    private static SummaryTablePane instance;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private TableColumn<Pair<Category, Integer>, String> catColumn;

    @FXML
    private TableColumn<Pair<Category, Integer>, Integer> countColumn;

    @FXML
    private TableView<Pair<Category, Integer>> tableView;

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
        catColumn.setCellValueFactory((TableColumn.CellDataFeatures<Pair<Category, Integer>, String> p) -> new SimpleObjectProperty<>(p.getValue().getKey().getDisplayName()));
        catColumn.setPrefWidth(USE_COMPUTED_SIZE);

        countColumn.setCellValueFactory((TableColumn.CellDataFeatures<Pair<Category, Integer>, Integer> p) -> new SimpleObjectProperty<>(p.getValue().getValue()));
        countColumn.setPrefWidth(USE_COMPUTED_SIZE);

        tableView.getColumns().setAll(catColumn, countColumn);

//        //register for category events
        Category.registerListener(this);
    }

    private SummaryTablePane() {
        FXMLConstructor.construct(this, "SummaryTablePane.fxml");
    }

    public static synchronized SummaryTablePane getDefault() {
        if (instance == null) {
            instance = new SummaryTablePane();
        }

        return instance;
    }

    /** listen to Category updates and rebuild the table */
    @Override
    public void handleCategoryChanged(Collection<Long> ids) {
        if (Case.isCaseOpen()) {
            final ObservableList<Pair<Category, Integer>> data = FXCollections.observableArrayList();

            for (Category cat : Category.values()) {
                try {
                    data.add(new Pair<>(cat, ImageAnalyzerController.getDefault().getGroupManager().getFileIDsWithCategory(cat).size()));
                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            Platform.runLater(() -> {
                if (tableView != null) {
                    tableView.setItems(data);
                }
            });
        }
    }
}
