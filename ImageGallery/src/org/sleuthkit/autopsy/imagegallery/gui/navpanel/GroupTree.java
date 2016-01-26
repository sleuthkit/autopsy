/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui.navpanel;

import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 *
 */
public class GroupTree extends TreeView<GroupTreeNode> {

    @FXML
    private ComboBox<GroupComparators<?>> sortByBox;
    @FXML
    private ToggleGroup orderGroup;
    @FXML
    private RadioButton ascRadio;
    @FXML
    private RadioButton descRadio;

    private final GroupTreeItem navTreeRoot = new GroupTreeItem("", null, true);

    private final ImageGalleryController controller;

    public GroupTree(ImageGalleryController controller) {
        this.controller = controller;

        VBox.setVgrow(this, Priority.ALWAYS);

        sortByBox.getItems().setAll(GroupComparators.getValues());
        sortByBox.getSelectionModel().select(GroupComparators.ALPHABETICAL);

        sortByBox.getSelectionModel().selectedItemProperty().addListener(observable -> {
            if (sortByBox.getSelectionModel().getSelectedItem() == GroupComparators.UNCATEGORIZED_COUNT) {
                controller.getCategoryManager().registerListener(GroupTree.this);
            } else {
                controller.getCategoryManager().unregisterListener(GroupTree.this);
            }
            resortTree();
        });
        orderGroup.selectedToggleProperty().addListener(observable -> resortTree());

        setCellFactory(treeView -> new GroupTreeCell(sortByBox.getSelectionModel().selectedItemProperty()));
        setShowRoot(false);
        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        getSelectionModel().selectedItemProperty().addListener(o -> updateControllersGroup());

        setRoot(navTreeRoot);

        controller.getGroupManager().getAnalyzedGroups().addListener((ListChangeListener.Change<? extends DrawableGroup> change) -> {
            while (change.next()) {
                change.getAddedSubList().stream().forEach(this::insertGroup);
                change.getRemoved().stream().forEach(this::removeFromTree);
            }
        });

        controller.viewState().addListener(observable -> {
            Optional.ofNullable(controller.viewState().get())
                    .map(GroupViewState::getGroup)
                    .ifPresent(this::setFocusedGroup);
        });

        for (DrawableGroup g : controller.getGroupManager().getAnalyzedGroups()) {
            insertGroup(g);
        }
    }

    private void insertGroup(DrawableGroup g) {
        navTreeRoot.insert(groupingToPath(g), g, true);
    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setFocusedGroup(DrawableGroup grouping) {
        final GroupTreeItem treeItemForGroup = navTreeRoot.getTreeItemForPath(groupingToPath(grouping));

        if (treeItemForGroup != null) {
            getSelectionModel().select(treeItemForGroup);
            Platform.runLater(() -> {
                int row = getRow(treeItemForGroup);
                if (row != -1) {
                    scrollTo(row - 2); //put newly selected row 3 from the top
                }
            });
        }
    }

    /**
     *
     * @param g        the value of g
     * @param treeRoot the value of treeRoot
     */
    private void removeFromTree(DrawableGroup g) {
        Optional.ofNullable(navTreeRoot.getTreeItemForGroup(g))
                .ifPresent(GroupTreeItem::removeFromParent);
    }

    private List<String> groupingToPath(DrawableGroup g) {
        String path = g.groupKey.getValueDisplayName();
        if (g.groupKey.getAttribute() != DrawableAttribute.PATH) {
            String stripStart = StringUtils.strip(path, "/");
            return Arrays.asList(stripStart);
        } else {
            String[] cleanPathTokens = StringUtils.stripStart(path, "/").split("/");
            return Arrays.asList(cleanPathTokens);
        }
    }

    private void updateControllersGroup() {
        Optional.ofNullable(getSelectionModel().getSelectedItem())
                .map(TreeItem::getValue)
                .map(GroupTreeNode::getGroup)
                .ifPresent(group -> controller.advance(GroupViewState.tile(group), false));
    }

    @Subscribe
    public void handleCategoryChange(CategoryManager.CategoryChangeEvent event) {
        resortTree();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void resortTree() {
        Comparator<DrawableGroup> groupComparator = sortByBox.getSelectionModel().getSelectedItem();
        if (orderGroup.getSelectedToggle() == descRadio) {
            groupComparator = groupComparator.reversed();
        }
        TreeItem<GroupTreeNode> selectedItem = getSelectionModel().getSelectedItem();
        navTreeRoot.resortChildren(groupComparator);
        getSelectionModel().select(selectedItem);
    }
}
