/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 * Display two trees. one shows all folders (groups) and calls out folders with
 * images. the user can select folders with images to see them in the main
 * GroupPane The other shows folders with hash set hits.
 */
final public class NavPanel extends VBox {

    @FXML
    private ComboBox<TreeNodeComparator<?>> sortByBox;

    @FXML
    private TabPane navTabPane;

    @FXML
    private TreeView<TreeNode> navTree;

    @FXML
    private AnchorPane hashAnchor;

    @FXML
    private AnchorPane navAnchor;
    @FXML
    private Tab hashTab;

    @FXML
    private Tab navTab;

    private final GroupTreeItem navTreeRoot = new GroupTreeItem("", null, TreeNodeComparator.ALPHABETICAL, true);
    private final GroupTreeItem hashTreeRoot = new GroupTreeItem("", null, TreeNodeComparator.HIT_COUNT, true);

    @FXML
    private RadioButton ascRadio;

    @FXML
    private ToggleGroup orderGroup;

    @FXML
    private RadioButton descRadio;

    private final ImageGalleryController controller;

    private TreeNodeComparator<?> hashSortOrder = TreeNodeComparator.HIT_COUNT;

    public NavPanel(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "NavPanel.fxml");
    }

    @Subscribe
    public void handleCategoryChange(CategoryManager.CategoryChangeEvent event) {
        resortHashTree();
    }

    @FXML
    void initialize() {
        assert hashTab != null : "fx:id=\"hashTab\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTab != null : "fx:id=\"navTab\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTabPane != null : "fx:id=\"navTabPane\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTree != null : "fx:id=\"navTree\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'NavPanel.fxml'.";

        VBox.setVgrow(this, Priority.ALWAYS);

        sortByBox.getItems().setAll(TreeNodeComparator.getValues());
        sortByBox.getSelectionModel().select(TreeNodeComparator.ALPHABETICAL);

        sortByBox.getSelectionModel().selectedItemProperty().addListener(observable -> {

            if (sortByBox.getSelectionModel().getSelectedItem() == TreeNodeComparator.UNCATEGORIZED_COUNT) {
                controller.getCategoryManager().registerListener(NavPanel.this);
            } else {
                controller.getCategoryManager().unregisterListener(NavPanel.this);
            }
            resortHashTree();
        });
        orderGroup.selectedToggleProperty().addListener(observable -> resortHashTree());

        navTree.setCellFactory(treeView -> new GroupTreeCell(sortByBox.getSelectionModel().selectedItemProperty()));
        navTree.setShowRoot(false);
        navTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        navTree.getSelectionModel().selectedItemProperty().addListener(o -> updateControllersGroup());

        navTree.setRoot(navTreeRoot);
        navTabPane.getSelectionModel().selectedItemProperty().addListener(observable -> {
            Tab selectedTab = navTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab == navTab) {
                hashSortOrder = sortByBox.getSelectionModel().getSelectedItem();
                sortByBox.getSelectionModel().select(TreeNodeComparator.ALPHABETICAL);
                navTree.setRoot(navTreeRoot);
                hashAnchor.getChildren().clear();
                navAnchor.getChildren().add(navTree);
            } else if (selectedTab == hashTab) {
                sortByBox.getSelectionModel().select(hashSortOrder);
                navTree.setRoot(hashTreeRoot);
                navAnchor.getChildren().clear();
                hashAnchor.getChildren().add(navTree);
                resortHashTree();
            }
        });

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
        if (g.getHashSetHitsCount() > 0) {
            hashTreeRoot.insert(groupingToPath(g), g, false);
        }
    }

    private void updateControllersGroup() {
        Optional.ofNullable(navTree.getSelectionModel().getSelectedItem())
                .map(TreeItem::getValue)
                .map(TreeNode::getGroup)
                .ifPresent(group -> controller.advance(GroupViewState.tile(group), false));
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void resortHashTree() {
        Comparator<TreeNode> treeNodeComparator = sortByBox.getSelectionModel().getSelectedItem();
        if (orderGroup.getSelectedToggle() == descRadio) {
            treeNodeComparator = treeNodeComparator.reversed();
        }
        TreeItem<TreeNode> selectedItem = navTree.getSelectionModel().getSelectedItem();
        hashTreeRoot.resortChildren(treeNodeComparator);
        navTree.getSelectionModel().select(selectedItem);
    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadType.JFX)
    private void setFocusedGroup(DrawableGroup grouping) {
        final GroupTreeItem treeItemForGroup = ((GroupTreeItem) navTree.getRoot()).getTreeItemForPath(groupingToPath(grouping));

        if (treeItemForGroup != null) {
            navTree.getSelectionModel().select(treeItemForGroup);
            Platform.runLater(() -> {
                int row = navTree.getRow(treeItemForGroup);
                if (row != -1) {
                    navTree.scrollTo(row - 2); //put newly selected row 3 from the top
                }
            });
        }
    }

    private List<String> groupingToPath(DrawableGroup g) {
        String path = g.groupKey.getValueDisplayName();
        if (g.groupKey.getAttribute() != DrawableAttribute.PATH
                || navTabPane.getSelectionModel().getSelectedItem() == hashTab) {
            String stripStart = StringUtils.strip(path, "/");
            return Arrays.asList(stripStart);
        } else {
            String[] cleanPathTokens = StringUtils.stripStart(path, "/").split("/");
            return Arrays.asList(cleanPathTokens);
        }
    }

    /**
     *
     * @param g        the value of g
     * @param treeRoot the value of treeRoot
     */
    private void removeFromTree(DrawableGroup g) {
        GroupTreeItem treeItemForGroup = navTreeRoot.getTreeItemForGroup(g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }

        treeItemForGroup = hashTreeRoot.getTreeItemForGroup(g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }
    }

    public void showTree() {
        Platform.runLater(() -> {
            navTabPane.getSelectionModel().select(navTab);
        });
    }
}
