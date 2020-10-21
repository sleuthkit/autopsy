/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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

import java.util.Arrays;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Cell;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.ResetFilters;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DescriptionFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;

/**
 * The FXML controller for the filter ui.
 *
 * This also implements TimeLineView since it dynamically updates its filters
 * based on the contents of a FilteredEventsModel
 */
final public class FilterSetPanel extends BorderPane {

    private static final Image TICK = new Image("org/sleuthkit/autopsy/timeline/images/tick.png"); //NON-NLS

    @FXML
    private Button applyButton;

    @FXML
    private Button defaultButton;

    @FXML
    private TreeTableView<FilterState<?>> filterTreeTable;

    @FXML
    private TreeTableColumn< FilterState< ?>, FilterState<?>> treeColumn;

    @FXML
    private TreeTableColumn<FilterState<?>, FilterState<?>> legendColumn;

    @FXML
    private ListView<DescriptionFilterState> hiddenDescriptionsListView;
    @FXML
    private TitledPane hiddenDescriptionsPane;
    @FXML
    private SplitPane splitPane;

    private final EventsModel filteredEvents;
    private final TimeLineController controller;

    /**
     * Map from filter to its expansion state in the ui, used to restore the
     * expansion state as we navigate back and forward in the history
     */
    private final ObservableMap< Object, Boolean> expansionMap = FXCollections.observableHashMap();
    private double dividerPosition;

    @NbBundle.Messages({
        "FilterSetPanel.defaultButton.text=Reset",
        "FilsetSetPanel.hiddenDescriptionsPane.displayName=Hidden Descriptions"})
    @FXML
    void initialize() {
        assert applyButton != null : "fx:id=\"applyButton\" was not injected: check your FXML file 'FilterSetPanel.fxml'."; // NON-NLS

        ActionUtils.configureButton(new ApplyFiltersAction(), applyButton);
        ActionUtils.configureButton(new ResetFilters(Bundle.FilterSetPanel_defaultButton_text(), controller), defaultButton);

        hiddenDescriptionsPane.setText(Bundle.FilsetSetPanel_hiddenDescriptionsPane_displayName());
        //remove column headers via css.
        filterTreeTable.getStylesheets().addAll(FilterSetPanel.class.getResource("FilterTable.css").toExternalForm()); // NON-NLS

        //use row factory as hook to attach context menus to.
        filterTreeTable.setRowFactory(ttv -> new FilterTreeTableRow());

        //configure tree column to show name of filter and checkbox
        treeColumn.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().valueProperty());
        treeColumn.setCellFactory(col -> new FilterCheckBoxCellFactory<>().forTreeTable(col));

        //configure legend column to show legend (or othe supplamantal ui, eg, text field for text filter)
        legendColumn.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().valueProperty());
        legendColumn.setCellFactory(col -> new LegendCell(this.controller));

        //type is the only filter expanded initialy
        expansionMap.put(filteredEvents.getEventFilterState().getFilter(), true);
        expansionMap.put(filteredEvents.getEventFilterState().getEventTypeFilterState().getFilter(), true);
        expansionMap.put(filteredEvents.getEventFilterState().getDataSourcesFilterState().getFilter(), true);
        expansionMap.put(filteredEvents.getEventFilterState().getFileTypesFilterState().getFilter(), true);

        InvalidationListener applyFiltersListener = observable -> applyFilters();

        filteredEvents.eventTypesHierarchyLevelProperty().addListener(applyFiltersListener);
        filteredEvents.descriptionLODProperty().addListener(applyFiltersListener);
        filteredEvents.timeRangeProperty().addListener(applyFiltersListener);

        filteredEvents.eventFilterProperty().addListener(observable -> refreshFilterUI());
        refreshFilterUI();

        hiddenDescriptionsListView.setItems(controller.getQuickHideFilters());
        hiddenDescriptionsListView.setCellFactory(listView -> getNewDescriptionFilterListCell());

        //show and hide the "hidden descriptions" panel depending on the current view mode
        controller.viewModeProperty().addListener(observable -> {
            switch (controller.getViewMode()) {
                case COUNTS:
                case LIST:
                    //hide for counts and lists, but remember divider position
                    dividerPosition = splitPane.getDividerPositions()[0];
                    splitPane.setDividerPositions(1);
                    hiddenDescriptionsPane.setExpanded(false);
                    hiddenDescriptionsPane.setCollapsible(false);
                    hiddenDescriptionsPane.setDisable(true);
                    break;
                case DETAIL:
                    //show and restore divider position.
                    splitPane.setDividerPositions(dividerPosition);
                    hiddenDescriptionsPane.setDisable(false);
                    hiddenDescriptionsPane.setCollapsible(true);
                    hiddenDescriptionsPane.setExpanded(true);
                    hiddenDescriptionsPane.setCollapsible(false);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown ViewMode: " + controller.getViewMode());
            }
        });
        
        //Block the default treetable keyboard events.
        filterTreeTable.addEventFilter(KeyEvent.ANY, new EventHandler<KeyEvent>() {
            @Override 
            public void handle(KeyEvent event) {
                event.consume();
            }
        });
    }

    public FilterSetPanel(TimeLineController controller) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        FXMLConstructor.construct(this, "FilterSetPanel.fxml"); // NON-NLS
    }

    private void refreshFilterUI() {
        Platform.runLater(()
                -> filterTreeTable.setRoot(new FilterTreeItem(filteredEvents.eventFilterProperty().get().copyOf(), expansionMap)));
    }

    private void applyFilters() {
        Platform.runLater(() -> {
            controller.pushFilters(((RootFilterState) filterTreeTable.getRoot().getValue().copyOf()));
        });
    }

    private ListCell<DescriptionFilterState> getNewDescriptionFilterListCell() {
        final ListCell<DescriptionFilterState> cell = new FilterCheckBoxCellFactory< DescriptionFilterState>().forList();
        cell.itemProperty().addListener(itemProperty -> {
            if (cell.getItem() == null) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(ActionUtils.createContextMenu(Arrays.asList(
                        new RemoveDescriptionFilterAction(controller, cell))
                ));
            }
        });
        return cell;
    }

    @NbBundle.Messages({"FilterSetPanel.applyButton.text=Apply",
        "FilterSetPanel.applyButton.longText=(Re)Apply filters"})
    private class ApplyFiltersAction extends Action {

        ApplyFiltersAction() {
            super(Bundle.FilterSetPanel_applyButton_text());
            setLongText(Bundle.FilterSetPanel_applyButton_longText());
            setGraphic(new ImageView(TICK));
            setEventHandler(actionEvent -> applyFilters());
        }
    }

    @NbBundle.Messages({
        "FilterSetPanel.hiddenDescriptionsListView.unhideAndRemove=Unhide and remove from list",
        "FilterSetPanel.hiddenDescriptionsListView.remove=Remove from list",})
    private static class RemoveDescriptionFilterAction extends Action {

        private static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS

        RemoveDescriptionFilterAction(TimeLineController controller, Cell<DescriptionFilterState> cell) {
            super(actionEvent -> controller.getQuickHideFilters().remove(cell.getItem()));
            setGraphic(new ImageView(SHOW));
            textProperty().bind(
                    Bindings.when(cell.getItem().selectedProperty())
                            .then(Bundle.FilterSetPanel_hiddenDescriptionsListView_unhideAndRemove())
                            .otherwise(Bundle.FilterSetPanel_hiddenDescriptionsListView_remove()));
        }
    }
}
