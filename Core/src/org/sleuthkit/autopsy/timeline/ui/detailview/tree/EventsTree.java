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
package org.sleuthkit.autopsy.timeline.ui.detailview.tree;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.datamodel.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;
import org.sleuthkit.autopsy.timeline.ui.EventTypeUtils;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getColor;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getImagePath;
import org.sleuthkit.datamodel.timeline.TimeLineEvent;

/**
 * Shows all EventBundles from the assigned DetailViewPane in a tree organized
 * by type and then description. Hidden bundles are shown grayed out. Right
 * clicking on a item in the tree shows a context menu to show/hide it.
 */
final public class EventsTree extends BorderPane {

    private final TimeLineController controller;

    private DetailViewPane detailViewPane;

    @FXML
    private TreeView<TimeLineEvent> eventsTree;

    @FXML
    private Label eventsTreeLabel;

    @FXML
    private ComboBox<TreeComparator> sortByBox;
    private final ObservableList<TimeLineEvent> selectedEvents = FXCollections.observableArrayList();

    public EventsTree(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "EventsTree.fxml"); // NON-NLS 
    }

    public void setDetailViewPane(DetailViewPane detailViewPane) {
        this.detailViewPane = detailViewPane;

        detailViewPane.getAllNestedEvents().addListener((ListChangeListener.Change<? extends TimeLineEvent> c) -> {
            //on jfx thread
            while (c.next()) {
                c.getRemoved().forEach(getRoot()::remove);
                c.getAddedSubList().forEach(getRoot()::insert);
            }
        });

        setRoot();

        detailViewPane.getSelectedEvents().addListener((Observable observable) -> {
            eventsTree.getSelectionModel().clearSelection();
            detailViewPane.getSelectedEvents().forEach(event -> {
                eventsTree.getSelectionModel().select(getRoot().findTreeItemForEvent(event));
            });
        });

    }

    private RootItem getRoot() {
        return (RootItem) eventsTree.getRoot();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setRoot() {
        RootItem root = new RootItem(TreeComparator.Type.reversed().thenComparing(sortByBox.getSelectionModel().getSelectedItem()));
        detailViewPane.getAllNestedEvents().forEach(root::insert);
        eventsTree.setRoot(root);
    }

    @FXML
    @NbBundle.Messages("EventsTree.Label.text=Sort By:")
    void initialize() {
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'NavPanel.fxml'."; // NON-NLS

        sortByBox.getItems().setAll(Arrays.asList(TreeComparator.Description, TreeComparator.Count));
        sortByBox.getSelectionModel().select(TreeComparator.Description);
        sortByBox.setCellFactory(listView -> new TreeComparatorCell());
        sortByBox.setButtonCell(new TreeComparatorCell());
        sortByBox.getSelectionModel().selectedItemProperty().addListener(selectedItemProperty -> {
            getRoot().sort(TreeComparator.Type.reversed().thenComparing(sortByBox.getSelectionModel().getSelectedItem()), true);
        });

        eventsTree.setShowRoot(false);
        eventsTree.setCellFactory(treeView -> new EventTreeCell());
        eventsTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        eventsTree.getSelectionModel().getSelectedItems().addListener((ListChangeListener.Change<? extends TreeItem<TimeLineEvent>> change) -> {
            while (change.next()) {
                change.getRemoved().stream().map(TreeItem<TimeLineEvent>::getValue).forEach(selectedEvents::remove);
                change.getAddedSubList().stream().map(TreeItem<TimeLineEvent>::getValue).filter(Objects::nonNull).forEach(selectedEvents::add);
            }
        });

        eventsTreeLabel.setText(Bundle.EventsTree_Label_text());
    }

    public ObservableList<TimeLineEvent> getSelectedEvents() {
        return selectedEvents;
    }

    /**
     * A tree cell to display TimeLineEvents. Shows the description, and count,
     * as well a a "legend icon" for the event type.
     */
    private class EventTreeCell extends TreeCell<TimeLineEvent> {

        private static final double HIDDEN_MULTIPLIER = .6;
        private final Rectangle rect = new Rectangle(24, 24);
        private final ImageView imageView = new ImageView();
        private InvalidationListener filterStateChangeListener;
        private final SimpleBooleanProperty hidden = new SimpleBooleanProperty(false);

        EventTreeCell() {
            rect.setArcHeight(5);
            rect.setArcWidth(5);
            rect.setStrokeWidth(2);
        }

        @Override
        protected void updateItem(TimeLineEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                deRegisterListeners(controller.getQuickHideFilters());
            } else {
                EventsTreeItem treeItem = (EventsTreeItem) getTreeItem();
                String text = treeItem.getDisplayText();
                setText(text);
                setTooltip(new Tooltip(text));

                imageView.setImage(new Image(getImagePath(treeItem.getEventType())));
                setGraphic(new StackPane(rect, imageView));
                updateHiddenState(treeItem);
                deRegisterListeners(controller.getQuickHideFilters());

                if (item != null) {
                    filterStateChangeListener = (filterState) -> updateHiddenState(treeItem);
                    controller.getQuickHideFilters().addListener((ListChangeListener.Change<? extends DescriptionFilter> listChange) -> {
                        while (listChange.next()) {
                            deRegisterListeners(listChange.getRemoved());
                            registerListeners(listChange.getAddedSubList(), item);
                        }
                        updateHiddenState(treeItem);
                    });
                    registerListeners(controller.getQuickHideFilters(), item);
                    setOnMouseClicked((MouseEvent event) -> {
                        if (event.getButton() == MouseButton.SECONDARY) {
                            Action action = hidden.get()
                                    ? detailViewPane.newUnhideDescriptionAction(item.getDescription(), item.getDescriptionLoD())
                                    : detailViewPane.newHideDescriptionAction(item.getDescription(), item.getDescriptionLoD());

                            ActionUtils.createContextMenu(ImmutableList.of(action))
                                    .show(this, event.getScreenX(), event.getScreenY());
                        }
                    });
                } else {
                    setOnMouseClicked(null);
                }
            }
        }

        private void registerListeners(Collection<? extends DescriptionFilter> filters, TimeLineEvent item) {
            for (DescriptionFilter filter : filters) {
                if (filter.getDescription().equals(item.getDescription())) {
                    filter.activeProperty().addListener(filterStateChangeListener);
                }
            }
        }

        private void deRegisterListeners(Collection<? extends DescriptionFilter> filters) {
            if (Objects.nonNull(filterStateChangeListener)) {
                for (DescriptionFilter filter : filters) {
                    filter.activeProperty().removeListener(filterStateChangeListener);
                }
            }
        }

        private void updateHiddenState(EventsTreeItem treeItem) {
            TimeLineEvent event = treeItem.getValue();
            hidden.set(event != null && controller.getQuickHideFilters().stream().
                    filter(DescriptionFilter::isActive)
                    .anyMatch(filter -> StringUtils.equalsIgnoreCase(filter.getDescription(), event.getDescription())));
            Color color = getColor(treeItem.getEventType());
            if (hidden.get()) {
                treeItem.setExpanded(false);
                setTextFill(Color.gray(0, HIDDEN_MULTIPLIER));
                imageView.setOpacity(HIDDEN_MULTIPLIER);
                rect.setStroke(color.deriveColor(0, HIDDEN_MULTIPLIER, 1, HIDDEN_MULTIPLIER));
                rect.setFill(color.deriveColor(0, HIDDEN_MULTIPLIER, HIDDEN_MULTIPLIER, 0.1));
            } else {
                setTextFill(Color.BLACK);
                imageView.setOpacity(1);
                rect.setStroke(color);
                rect.setFill(color.deriveColor(0, 1, 1, 0.1));
            }
        }
    }

    static private class TreeComparatorCell extends ListCell<TreeComparator> {

        @Override
        protected void updateItem(TreeComparator item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item.getDisplayName());
            }
        }
    }
}
