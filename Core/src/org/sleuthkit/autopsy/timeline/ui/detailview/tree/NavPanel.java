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
import java.util.Comparator;
import javafx.beans.Observable;
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
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewNode;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;

public class NavPanel extends BorderPane implements TimeLineView {

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    private DetailViewPane detailViewPane;

    @FXML
    private TreeView< NavTreeNode> eventsTree;

    @FXML
    private Label eventsTreeLabel;

    @FXML
    private ComboBox<Comparator<TreeItem<NavTreeNode>>> sortByBox;

    public NavPanel() {
        FXMLConstructor.construct(this, "NavPanel.fxml"); // NON-NLS
    }

    public void setDetailViewPane(DetailViewPane detailViewPane) {
        this.detailViewPane = detailViewPane;
        detailViewPane.setSelectionModel(eventsTree.getSelectionModel());
        setRoot();
        detailViewPane.getEventBundles().addListener((Observable observable) -> {
            setRoot();
        });
        detailViewPane.getSelectedNodes().addListener((Observable observable) -> {
            eventsTree.getSelectionModel().clearSelection();
            detailViewPane.getSelectedNodes().forEach((DetailViewNode<?> t) -> {
                eventsTree.getSelectionModel().select(((NavTreeItem) eventsTree.getRoot()).findTreeItemForEvent(t.getEventBundle()));
            });
        });

    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setRoot() {
        RootItem root = new RootItem();
        for (EventBundle bundle : detailViewPane.getEventBundles()) {
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
            ((NavTreeItem) eventsTree.getRoot()).resort(sortByBox.getSelectionModel().getSelectedItem());
        });
        eventsTree.setShowRoot(false);
        eventsTree.setCellFactory((TreeView<NavTreeNode> p) -> new EventTreeCell());
        eventsTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        eventsTreeLabel.setText(NbBundle.getMessage(this.getClass(), "NavPanel.eventsTreeLabel.text"));
    }

    /**
     * A tree cell to display {@link NavTreeNode}s. Shows the description, and
     * count, as well a a "legend icon" for the event type.
     */
    private class EventTreeCell extends TreeCell<NavTreeNode> {

        @Override
        protected void updateItem(NavTreeNode item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                setContextMenu(null);
            } else {
                String text = item.getDescription() + " (" + item.getCount() + ")"; // NON-NLS
                TreeItem<NavTreeNode> parent = getTreeItem().getParent();
                if (parent != null && parent.getValue() != null && (parent instanceof EventDescriptionTreeItem)) {
                    text = StringUtils.substringAfter(text, parent.getValue().getDescription());
                }
                setText(text);
                setTooltip(new Tooltip(text));
                Rectangle rect = new Rectangle(24, 24);
                rect.setArcHeight(5);
                rect.setArcWidth(5);
                rect.setStrokeWidth(2);
                ImageView imageView = new ImageView(item.getType().getFXImage());

                setGraphic(new StackPane(rect, imageView));
                controller.getQuickHideMasks().addListener((Observable observable) -> {
                    configureHiddenState(item, rect, imageView);
                });
                configureHiddenState(item, rect, imageView);

            }

        }

        private void configureHiddenState(NavTreeNode item, Rectangle rect, ImageView imageView) {
            TreeItem<NavTreeNode> treeItem = getTreeItem();
            ContextMenu newMenu;
            if (controller.getQuickHideMasks().stream().anyMatch(mask -> mask.getDescription().equals(item.getDescription()))) {
                setTextFill(Color.gray(0, .6));
                imageView.setOpacity(.6);
                rect.setStroke(item.getType().getColor().deriveColor(0, .6, 1, .6));
                rect.setFill(item.getType().getColor().deriveColor(0, .6, .6, 0.1));
                if (treeItem != null) {
                    treeItem.setExpanded(false);
                }
                newMenu = ActionUtils.createContextMenu(ImmutableList.of(detailViewPane.newUnhideDescriptionAction(item.getDescription(), item.getDescriptionLod())));
            } else {
                setTextFill(Color.BLACK);
                imageView.setOpacity(1);
                rect.setStroke(item.getType().getColor());
                rect.setFill(item.getType().getColor().deriveColor(0, 1, 1, 0.1));
                newMenu = ActionUtils.createContextMenu(ImmutableList.of(detailViewPane.newHideDescriptionAction(item.getDescription(), item.getDescriptionLod())));
            }

            if (treeItem instanceof EventDescriptionTreeItem) {
                setContextMenu(newMenu);
            } else {
                setContextMenu(null);
            }
        }
    }

}
