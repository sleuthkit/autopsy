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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.VisualizationMode;
import org.sleuthkit.autopsy.timeline.actions.ResetFilters;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;

/**
 * The FXML controller for the filter ui.
 *
 * This also implements {@link TimeLineView} since it dynamically updates its
 * filters based on the contents of a {@link FilteredEventsModel}
 */
final public class FilterSetPanel extends BorderPane {

    private static final Image TICK = new Image("org/sleuthkit/autopsy/timeline/images/tick.png"); //NON-NLS

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

    @FXML
    private ListView<DescriptionFilter> hiddenDescriptionsListView;
    @FXML
    private TitledPane hiddenDescriptionsPane;
    @FXML
    private SplitPane splitPane;

    private FilteredEventsModel filteredEvents;

    private TimeLineController controller;

    private final ObservableMap<String, Boolean> expansionMap = FXCollections.observableHashMap();
    private double position;

    @FXML
    @NbBundle.Messages({
        "Timeline.ui.filtering.menuItem.all=all",
        "FilterSetPanel.defaultButton.text=Default",
        "Timeline.ui.filtering.menuItem.none=none",
        "Timeline.ui.filtering.menuItem.only=only",
        "Timeline.ui.filtering.menuItem.others=others",
        "Timeline.ui.filtering.menuItem.select=select",
        "FilterSetPanel.hiddenDescriptionsListView.unhideAndRm=Unhide and remove from list",
        "FilterSetPanel.hiddenDescriptionsListView.remove=Remove from list",
        "FilsetSetPanel.hiddenDescriptionsPane.displayName=Hidden Descriptions"})
    void initialize() {
        assert applyButton != null : "fx:id=\"applyButton\" was not injected: check your FXML file 'FilterSetPanel.fxml'."; // NON-NLS

        ActionUtils.configureButton(new ApplyFiltersAction(), applyButton);
        defaultButton.setText(Bundle.FilterSetPanel_defaultButton_text());
        hiddenDescriptionsPane.setText(Bundle.FilsetSetPanel_hiddenDescriptionsPane_displayName());
        //remove column headers via css.
        filterTreeTable.getStylesheets().addAll(FilterSetPanel.class.getResource("FilterTable.css").toExternalForm()); // NON-NLS

        //use row factory as hook to attach context menus to.
        filterTreeTable.setRowFactory((TreeTableView<Filter> param) -> {
            final TreeTableRow<Filter> row = new TreeTableRow<>();

            MenuItem all = new MenuItem(Bundle.Timeline_ui_filtering_menuItem_all());
            all.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setSelected(Boolean.TRUE);
                });
            });
            MenuItem none = new MenuItem(Bundle.Timeline_ui_filtering_menuItem_none());
            none.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    t.getValue().setSelected(Boolean.FALSE);
                });
            });

            MenuItem only = new MenuItem(Bundle.Timeline_ui_filtering_menuItem_only());
            only.setOnAction(e -> {
                row.getTreeItem().getParent().getChildren().forEach((TreeItem<Filter> t) -> {
                    if (t == row.getTreeItem()) {
                        t.getValue().setSelected(Boolean.TRUE);
                    } else {
                        t.getValue().setSelected(Boolean.FALSE);
                    }
                });
            });
            MenuItem others = new MenuItem(Bundle.Timeline_ui_filtering_menuItem_others());
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
            Menu select = new Menu(Bundle.Timeline_ui_filtering_menuItem_select());
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
        treeColumn.setCellFactory(col -> new FilterCheckBoxCellFactory<>().forTreeTable(col));

        //configure legend column to show legend (or othe supplamantal ui, eg, text field for text filter)
        legendColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        legendColumn.setCellFactory(col -> new LegendCell(this.controller));

        expansionMap.put(new TypeFilter(RootEventType.getInstance()).getDisplayName(), true);

        Action defaultFiltersAction = new ResetFilters(controller);
        defaultButton.setOnAction(defaultFiltersAction);
        defaultButton.disableProperty().bind(defaultFiltersAction.disabledProperty());

        this.filteredEvents.eventTypeZoomProperty().addListener((Observable observable) -> {
            applyFilters();
        });
        this.filteredEvents.descriptionLODProperty().addListener((Observable observable1) -> {
            applyFilters();
        });
        this.filteredEvents.timeRangeProperty().addListener((Observable observable2) -> {
            applyFilters();
        });
        this.filteredEvents.filterProperty().addListener((Observable o) -> {
            refresh();
        });
        refresh();

        hiddenDescriptionsListView.setItems(controller.getQuickHideFilters());
        hiddenDescriptionsListView.setCellFactory((ListView<DescriptionFilter> param) -> {
            final ListCell<DescriptionFilter> forList = new FilterCheckBoxCellFactory<DescriptionFilter>().forList();

            forList.itemProperty().addListener((Observable observable) -> {
                if (forList.getItem() == null) {
                    forList.setContextMenu(null);
                } else {
                    forList.setContextMenu(new ContextMenu(new MenuItem() {
                        {
                            forList.getItem().selectedProperty().addListener((observable, wasSelected, isSelected) -> {
                                configureText(isSelected);
                            });

                            configureText(forList.getItem().selectedProperty().get());
                            setOnAction((ActionEvent event) -> {
                                controller.getQuickHideFilters().remove(forList.getItem());
                            });
                        }

                        private void configureText(Boolean newValue) {
                            if (newValue) {
                                setText(Bundle.FilterSetPanel_hiddenDescriptionsListView_unhideAndRm());
                            } else {
                                setText(Bundle.FilterSetPanel_hiddenDescriptionsListView_remove());
                            }
                        }
                    }));
                }
            });

            return forList;
        });

        controller.viewModeProperty().addListener(observable -> {
            applyFilters();
            if (controller.viewModeProperty().get() == VisualizationMode.COUNTS) {
                position = splitPane.getDividerPositions()[0];
                splitPane.setDividerPositions(1);
                hiddenDescriptionsPane.setExpanded(false);
                hiddenDescriptionsPane.setCollapsible(false);
                hiddenDescriptionsPane.setDisable(true);
            } else {
                splitPane.setDividerPositions(position);
                hiddenDescriptionsPane.setDisable(false);
                hiddenDescriptionsPane.setCollapsible(true);
                hiddenDescriptionsPane.setExpanded(true);
                hiddenDescriptionsPane.setCollapsible(false);

            }
        });

    }

    public FilterSetPanel(TimeLineController controller) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        FXMLConstructor.construct(this, "FilterSetPanel.fxml"); // NON-NLS

    }

    private void refresh() {
        Platform.runLater(() -> {
            filterTreeTable.setRoot(new FilterTreeItem(filteredEvents.getFilter().copyOf(), expansionMap));
        });
    }

    @NbBundle.Messages({"FilterSetPanel.applyButton.text=Apply",
            "FilterSetPanel.applyButton.longText=(Re)Apply filters"})
    private class ApplyFiltersAction extends Action {

        ApplyFiltersAction() {
            super(Bundle.FilterSetPanel_applyButton_text());
            setLongText(Bundle.FilterSetPanel_applyButton_longText());
            setGraphic(new ImageView(TICK));
            setEventHandler((ActionEvent t) -> {
                applyFilters();
            });
        }
    }

    private void applyFilters() {
        Platform.runLater(() -> {
            controller.pushFilters((RootFilter) filterTreeTable.getRoot().getValue());
        });
    }
}
