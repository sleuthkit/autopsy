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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import org.controlsfx.control.action.AbstractAction;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.actions.DefaultFilters;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;

/** The FXML controller for the filter ui.
 *
 * This also implements {@link TimeLineView} since it dynamically updates its
 * filters based on the contents of a {@link FilteredEventsModel}
 */
public class FilterSetPanel extends BorderPane implements TimeLineView {

    @FXML
    private Button applyButton;

    @FXML
    private Button defaultButton;

    @FXML
    private TreeTableView<Filter> filterTreeTable;

    @FXML
    private TreeTableColumn<AbstractFilter, AbstractFilter> treeColumn;

    @FXML
    private TreeTableColumn<AbstractFilter, AbstractFilter> legendColumn;

    private FilteredEventsModel filteredEvents;

    private TimeLineController controller;

    @FXML
    void initialize() {
        assert applyButton != null : "fx:id=\"applyButton\" was not injected: check your FXML file 'FilterSetPanel.fxml'.";

        applyButton.setOnAction(e -> {
            controller.pushFilters(filterTreeTable.getRoot().getValue().copyOf());
        });

        //remove column headers via css.
        filterTreeTable.getStylesheets().addAll(getClass().getResource("FilterTable.css").toExternalForm());

        //use row factory as hook to attach context menus to.
        filterTreeTable.setRowFactory((TreeTableView<Filter> param) -> {
            final TreeTableRow<Filter> row = new TreeTableRow<>();

            MenuItem all = new MenuItem("all");
            all.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setActive(Boolean.TRUE);
                });
            });
            MenuItem none = new MenuItem("none");
            none.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setActive(Boolean.FALSE);
                });
            });

            MenuItem only = new MenuItem("only");
            only.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    if (t == row.getTreeItem()) {
                        t.getValue().setActive(Boolean.TRUE);
                    } else {
                        t.getValue().setActive(Boolean.FALSE);
                    }
                });
            });
            MenuItem others = new MenuItem("others");
            others.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    if (t == row.getTreeItem()) {
                        t.getValue().setActive(Boolean.FALSE);
                    } else {
                        t.getValue().setActive(Boolean.TRUE);
                    }
                });
            });
            final ContextMenu rowMenu = new ContextMenu();
            Menu select = new Menu("select");
            select.setOnAction(e -> {
                row.getItem().setActive(!row.getItem().isActive());
            });
            select.getItems().addAll(all, none, only, others);
            rowMenu.getItems().addAll(select);
            row.setContextMenu(rowMenu);

            return row;
        });

        //configure tree column to show name of filter and checkbox
        treeColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        treeColumn.setCellFactory(col -> new FilterCheckBoxCell());

        //configure legend column to show legend (or othe supplamantal ui, eg, text field for text filter)
        legendColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        legendColumn.setCellFactory(col -> new LegendCell(this.controller));
    }

    public FilterSetPanel() {
        FXMLConstructor.construct(this, "FilterSetPanel.fxml");
    }

    @Override
    public void setController(TimeLineController timeLineController) {
        this.controller = timeLineController;
        AbstractAction defaultFiltersAction = new DefaultFilters(controller);
        defaultButton.setOnAction(defaultFiltersAction);
        defaultButton.disableProperty().bind(defaultFiltersAction.disabledProperty());
        this.setModel(timeLineController.getEventsModel());
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {
        this.filteredEvents = filteredEvents;

        refresh();

        this.filteredEvents.filter().addListener((Observable o) -> {
            refresh();
        });
    }

    private void refresh() {
        filterTreeTable.setRoot(new FilterTreeItem(this.filteredEvents.filter().get().copyOf()));
    }

    /**
     * A {@link TreeTableCell} that represents the active state of a
     * {@link AbstractFilter} as a checkbox
     */
    private static class FilterCheckBoxCell extends TreeTableCell<AbstractFilter, AbstractFilter> {

        private final CheckBox checkBox = new CheckBox();

        @Override
        protected void updateItem(AbstractFilter item, boolean empty) {
            super.updateItem(item, empty);
            Platform.runLater(() -> {
                if (item == null) {
                    setText(null);
                    setGraphic(null);
                    checkBox.selectedProperty().unbind();
                    checkBox.disableProperty().unbind();
                } else {
                    setText(item.getDisplayName());
                    checkBox.selectedProperty().bindBidirectional(item.getActiveProperty());
                    checkBox.disableProperty().bind(item.getDisabledProperty());
                    setGraphic(checkBox);
                }
            });
        }
    }
}
