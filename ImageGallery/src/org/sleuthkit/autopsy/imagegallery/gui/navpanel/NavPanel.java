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
package org.sleuthkit.autopsy.imagegallery.gui.navpanel;

import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javax.annotation.Nonnull;
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
 *
 * //TODO: there is too much code duplication between the navTree and the
 * hashTree. Extract the common code to some new class.
 */
final public class NavPanel extends TabPane {

    @FXML
    private ComboBox<TreeNodeComparators> sortByBox;
    @FXML
    private TabPane navTabPane;
    /**
     * TreeView for folders with hash hits
     */
    @FXML
    private TreeView<TreeNode> hashTree;
    /**
     * TreeView for all folders
     */
    @FXML
    private TreeView<TreeNode> navTree;

    @FXML
    private Tab hashTab;

    @FXML
    private Tab navTab;

    private GroupTreeItem hashTreeRoot = new GroupTreeItem("", null, null);

    private GroupTreeItem navTreeRoot = new GroupTreeItem("", null, null);

    /**
     * contains the 'active tree', three in the selected Tab.
     */
    private final SimpleObjectProperty<TreeView<TreeNode>> activeTreeProperty = new SimpleObjectProperty<>();

    private final ImageGalleryController controller;

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
        assert hashTree != null : "fx:id=\"hashTree\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTab != null : "fx:id=\"navTab\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTabPane != null : "fx:id=\"navTabPane\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert navTree != null : "fx:id=\"navTree\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'NavPanel.fxml'.";

        VBox.setVgrow(this, Priority.ALWAYS);

        sortByBox.setCellFactory((ListView<TreeNodeComparators> p) -> new TreeNodeComparators.ComparatorListCell());
        sortByBox.setButtonCell(new TreeNodeComparators.ComparatorListCell());
        sortByBox.setItems(FXCollections.observableArrayList(FXCollections.observableArrayList(TreeNodeComparators.values())));
        sortByBox.getSelectionModel().select(TreeNodeComparators.HIT_COUNT);
        sortByBox.getSelectionModel().selectedItemProperty().addListener(o -> resortHashTree());
            if (sortByBox.getSelectionModel().getSelectedItem() == TreeNodeComparators.UNCATEGORIZED_COUNT) {
                controller.getCategoryManager().registerListener(NavPanel.this);
            } else {
                controller.getCategoryManager().unregisterListener(NavPanel.this);
            }

        configureTree(navTree, navTreeRoot);
        configureTree(hashTree, hashTreeRoot);

        activeTreeProperty.set(navTree);
        navTabPane.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends Tab> ov, Tab t, Tab t1) -> {
            if (t1 == hashTab) {
                activeTreeProperty.set(hashTree);
            } else if (t1 == navTab) {
                activeTreeProperty.set(navTree);
            }
        });

        controller.getGroupManager().getAnalyzedGroups().addListener((ListChangeListener.Change<? extends DrawableGroup> change) -> {
            while (change.next()) {
                for (DrawableGroup g : change.getAddedSubList()) {
                    insertIntoNavTree(g);
                    if (g.getHashSetHitsCount() > 0) {
                        insertIntoHashTree(g);
                    }
                }
                for (DrawableGroup g : change.getRemoved()) {
                    removeFromTree(g, navTreeRoot);
                    removeFromTree(g, hashTreeRoot);
                }
            }
        });

        controller.viewState().addListener((ObservableValue<? extends GroupViewState> observable, GroupViewState oldValue, GroupViewState newValue) -> {
            if (newValue != null && newValue.getGroup() != null) {
                setFocusedGroup(newValue.getGroup());
            }
        });

        rebuildTrees();
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void configureTree(final TreeView<TreeNode> navTree1, final GroupTreeItem navTreeRoot1) {
        navTree1.setRoot(navTreeRoot1);
        navTreeRoot1.setExpanded(true);
        navTree1.setCellFactory(treeView -> new GroupTreeCell());
        navTree1.setShowRoot(false);
        navTree1.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        navTree1.getSelectionModel().selectedItemProperty().addListener(o -> updateControllersGroup());
    }

    private void rebuildTrees() {
        navTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().selectedItemProperty().get());
        hashTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().selectedItemProperty().get());

        Platform.runLater(() -> {
            navTree.setRoot(navTreeRoot);
            navTreeRoot.setExpanded(true);
            hashTree.setRoot(hashTreeRoot);
            hashTreeRoot.setExpanded(true);
        });

        for (DrawableGroup g : controller.getGroupManager().getAnalyzedGroups()) {
            insertIntoNavTree(g);
            if (g.getHashSetHitsCount() > 0) {
                insertIntoHashTree(g);
            }
        }
    }

    private void updateControllersGroup() {
        final TreeItem<TreeNode> selectedItem = activeTreeProperty.get().getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() != null && selectedItem.getValue().getGroup() != null) {
            controller.advance(GroupViewState.tile(selectedItem.getValue().getGroup()), false);
        }
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void resortHashTree() {
        TreeItem<TreeNode> selectedItem = hashTree.getSelectionModel().getSelectedItem();
        hashTreeRoot.resortChildren(sortByBox.getSelectionModel().getSelectedItem());
        hashTree.getSelectionModel().select(selectedItem);
    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadType.JFX)
    private void setFocusedGroup(DrawableGroup grouping) {
        final GroupTreeItem treeItemForGroup = ((GroupTreeItem) activeTreeProperty.get().getRoot()).getTreeItemForPath(groupingToPath(grouping));

        if (treeItemForGroup != null) {
            activeTreeProperty.get().getSelectionModel().select(treeItemForGroup);
            Platform.runLater(() -> {
                int row = activeTreeProperty.get().getRow(treeItemForGroup);
                if (row != -1) {
                    activeTreeProperty.get().scrollTo(row - 2); //put newly selected row 3 from the top
                }
            });
        }
    }

    private List<String> groupingToPath(DrawableGroup g) {
        if (g.groupKey.getAttribute() != DrawableAttribute.PATH
                || activeTreeProperty.get() == hashTree) {
            String stripStart = StringUtils.strip(g.groupKey.getValueDisplayName(), "/");
            return Arrays.asList(stripStart);
        } else {
            String path = g.groupKey.getValueDisplayName();
            String[] cleanPathTokens = StringUtils.stripStart(path, "/").split("/");
            return Arrays.asList(cleanPathTokens);
        }
    }

    private void insertIntoHashTree(DrawableGroup g) {
        hashTreeRoot.insert(groupingToPath(g), g, false);
    }

    private void insertIntoNavTree(DrawableGroup g) {
        navTreeRoot.insert(groupingToPath(g), g, true);
    }

    /**
     *
     * @param g        the value of g
     * @param treeRoot the value of treeRoot
     */
    private void removeFromTree(DrawableGroup g, @Nonnull final GroupTreeItem treeRoot) {
        final GroupTreeItem treeItemForGroup = treeRoot.getTreeItemForGroup(g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }
    }

    public void showTree() {
        Platform.runLater(() -> {
            getSelectionModel().select(navTab);
        });
    }
}
