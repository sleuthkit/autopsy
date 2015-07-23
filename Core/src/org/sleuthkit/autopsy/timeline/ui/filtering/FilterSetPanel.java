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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
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
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.actions.DefaultFiltersAction;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;

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

    private final ObservableMap<String, Boolean> expansionMap = FXCollections.observableHashMap();

    @FXML
    void initialize() {
        assert applyButton != null : "fx:id=\"applyButton\" was not injected: check your FXML file 'FilterSetPanel.fxml'."; // NON-NLS

        applyButton.setOnAction(e -> {
            controller.pushFilters((RootFilter) filterTreeTable.getRoot().getValue().copyOf());
        });
        applyButton.setText(NbBundle.getMessage(this.getClass(), "FilterSetPanel.applyButton.text"));
        defaultButton.setText(NbBundle.getMessage(this.getClass(), "FilterSetPanel.defaultButton.text"));

        //remove column headers via css.
        filterTreeTable.getStylesheets().addAll(getClass().getResource("FilterTable.css").toExternalForm()); // NON-NLS

        //use row factory as hook to attach context menus to.
        filterTreeTable.setRowFactory((TreeTableView<Filter> param) -> {
            final TreeTableRow<Filter> row = new TreeTableRow<>();

            MenuItem all = new MenuItem(NbBundle.getMessage(this.getClass(), "Timeline.ui.filtering.menuItem.all"));
            all.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setSelected(Boolean.TRUE);
                });
            });
            MenuItem none = new MenuItem(NbBundle.getMessage(this.getClass(), "Timeline.ui.filtering.menuItem.none"));
            none.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setSelected(Boolean.FALSE);
                });
            });

            MenuItem only = new MenuItem(NbBundle.getMessage(this.getClass(), "Timeline.ui.filtering.menuItem.only"));
            only.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    if (t == row.getTreeItem()) {
                        t.getValue().setSelected(Boolean.TRUE);
                    } else {
                        t.getValue().setSelected(Boolean.FALSE);
                    }
                });
            });
            MenuItem others = new MenuItem(NbBundle.getMessage(this.getClass(), "Timeline.ui.filtering.menuItem.others"));
            others.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    if (t == row.getTreeItem()) {
                        t.getValue().setSelected(Boolean.FALSE);
                    } else {
                        t.getValue().setSelected(Boolean.TRUE);
                    }
                });
            });
            final ContextMenu rowMenu = new ContextMenu();
            Menu select = new Menu(NbBundle.getMessage(this.getClass(), "Timeline.ui.filtering.menuItem.select"));
            select.setOnAction(e -> {
                row.getItem().setSelected(!row.getItem().isSelected());
            });
            select.getItems().addAll(all, none, only, others);
            rowMenu.getItems().addAll(select);
            row.setContextMenu(rowMenu);

            return row;
        });

        //configure tree column to show name of filter and checkbox
        treeColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        treeColumn.setCellFactory(col -> new FilterCheckBoxCell());
        treeColumn.setText(NbBundle.getMessage(this.getClass(), "FilterSetPanel.treeColumn.text"));

        //configure legend column to show legend (or othe supplamantal ui, eg, text field for text filter)
        legendColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        legendColumn.setCellFactory(col -> new LegendCell(this.controller));
        legendColumn.setText(NbBundle.getMessage(this.getClass(), "FilterSetPanel.legendColumn.text"));
    }

    public FilterSetPanel() {
        FXMLConstructor.construct(this, "FilterSetPanel.fxml"); // NON-NLS
        expansionMap.put(NbBundle.getMessage(this.getClass(), "FilterSetPanel.eventTypeFilter.title"), Boolean.TRUE);
    }

    @Override
    public void setController(TimeLineController timeLineController) {
        this.controller = timeLineController;
        Action defaultFiltersAction = new DefaultFiltersAction(controller);
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
        filterTreeTable.setRoot(new FilterTreeItem(this.filteredEvents.filter().get().copyOf(), expansionMap));
    }

}
