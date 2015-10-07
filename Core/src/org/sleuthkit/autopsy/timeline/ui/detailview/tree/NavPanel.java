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
import java.util.Comparator;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;

/**
 * Shows all {@link  EventBundles} from the assigned {@link DetailViewPane} in a
 * tree organized by type and then description. Hidden bundles are shown grayed
 * out. Right clicking on a item in the tree shows a context menu to show/hide
 * it.
 */
public class NavPanel extends BorderPane implements TimeLineView {

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    private DetailViewPane detailViewPane;

    @FXML
    private TreeView<EventBundle<?>> eventsTree;

    @FXML
    private Label eventsTreeLabel;

    @FXML
    private ComboBox<Comparator<TreeItem<EventBundle<?>>>> sortByBox;

    public NavPanel() {
        FXMLConstructor.construct(this, "NavPanel.fxml"); // NON-NLS 
    }

    public void setDetailViewPane(DetailViewPane detailViewPane) {
        this.detailViewPane = detailViewPane;
        detailViewPane.setSelectionModel(eventsTree.getSelectionModel());

        detailViewPane.getEventBundles().addListener((Observable observable) -> {
            setRoot();
        });
        setRoot();

        detailViewPane.getSelectedNodes().addListener((Observable observable) -> {
            eventsTree.getSelectionModel().clearSelection();
            detailViewPane.getSelectedNodes().forEach(eventBundleNode -> {
                eventsTree.getSelectionModel().select(getRoot().findTreeItemForEvent(eventBundleNode.getEventBundle()));
            });
        });

    }

    private NavTreeItem getRoot() {
        return (NavTreeItem) eventsTree.getRoot();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setRoot() {
        RootItem root = new RootItem();
        for (EventBundle<?> bundle : detailViewPane.getEventBundles()) {
            root.insert(bundle);
        }
        eventsTree.setRoot(root);

    }

    @Override
    public void setController(TimeLineController controller) {
        this.controller = controller;
        setModel(controller.getEventsModel());
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {
        this.filteredEvents = filteredEvents;

    }

    @FXML
    void initialize() {
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'NavPanel.fxml'."; // NON-NLS

        sortByBox.getItems().setAll(Arrays.asList(TreeComparator.Description, TreeComparator.Count));
        sortByBox.getSelectionModel().select(TreeComparator.Description);
        sortByBox.getSelectionModel().selectedItemProperty().addListener((Observable o) -> {
            getRoot().resort(sortByBox.getSelectionModel().getSelectedItem());
        });
        eventsTree.setShowRoot(false);
        eventsTree.setCellFactory((TreeView<EventBundle<?>> p) -> new EventBundleTreeCell());
        eventsTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        eventsTreeLabel.setText(NbBundle.getMessage(this.getClass(), "NavPanel.eventsTreeLabel.text"));
    }

    /**
     * A tree cell to display {@link EventBundle}s. Shows the description, and
     * count, as well a a "legend icon" for the event type.
     */
    private class EventBundleTreeCell extends TreeCell<EventBundle<?>> {

        private static final double HIDDEN_MULTIPLIER = .6;
        private final Rectangle rect = new Rectangle(24, 24);
        private final ImageView imageView = new ImageView();
        private InvalidationListener filterStateChangeListener;

        EventBundleTreeCell() {
            rect.setArcHeight(5);
            rect.setArcWidth(5);
            rect.setStrokeWidth(2);
        }

        @Override
        protected void updateItem(EventBundle<?> item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                setContextMenu(null);
                deRegisterListeners(controller.getQuickHideFilters());
            } else {
                filterStateChangeListener = (filterState) -> updateHiddenState(item);
                controller.getQuickHideFilters().addListener((ListChangeListener.Change<? extends DescriptionFilter> listChange) -> {
                    while (listChange.next()) {
                        deRegisterListeners(listChange.getRemoved());
                        registerListeners(listChange.getAddedSubList(), item);
                    }
                    updateHiddenState(item);
                });
                registerListeners(controller.getQuickHideFilters(), item);
                String text = item.getDescription() + " (" + item.getCount() + ")"; // NON-NLS
                TreeItem<EventBundle<?>> parent = getTreeItem().getParent();
                if (parent != null && parent.getValue() != null && (parent instanceof EventDescriptionTreeItem)) {
                    text = StringUtils.substringAfter(text, parent.getValue().getDescription());
                }
                setText(text);
                setTooltip(new Tooltip(text));
                imageView.setImage(item.getEventType().getFXImage());
                setGraphic(new StackPane(rect, imageView));
                updateHiddenState(item);
            }
        }

        private void registerListeners(Collection<? extends DescriptionFilter> filters, EventBundle<?> item) {
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

        private void updateHiddenState(EventBundle<?> item) {
            TreeItem<EventBundle<?>> treeItem = getTreeItem();
            ContextMenu newMenu;
            if (controller.getQuickHideFilters().stream().
                    filter(AbstractFilter::isActive)
                    .anyMatch(filter -> filter.getDescription().equals(item.getDescription()))) {
                if (treeItem != null) {
                    treeItem.setExpanded(false);
                }
                setTextFill(Color.gray(0, HIDDEN_MULTIPLIER));
                imageView.setOpacity(HIDDEN_MULTIPLIER);
                rect.setStroke(item.getEventType().getColor().deriveColor(0, HIDDEN_MULTIPLIER, 1, HIDDEN_MULTIPLIER));
                rect.setFill(item.getEventType().getColor().deriveColor(0, HIDDEN_MULTIPLIER, HIDDEN_MULTIPLIER, 0.1));
                newMenu = ActionUtils.createContextMenu(ImmutableList.of(detailViewPane.newUnhideDescriptionAction(item.getDescription(), item.getDescriptionLoD())));
            } else {
                setTextFill(Color.BLACK);
                imageView.setOpacity(1);
                rect.setStroke(item.getEventType().getColor());
                rect.setFill(item.getEventType().getColor().deriveColor(0, 1, 1, 0.1));
                newMenu = ActionUtils.createContextMenu(ImmutableList.of(detailViewPane.newHideDescriptionAction(item.getDescription(), item.getDescriptionLoD())));
            }
            if (treeItem instanceof EventDescriptionTreeItem) {
                setContextMenu(newMenu);
            } else {
                setContextMenu(null);
            }
        }

    }
}
