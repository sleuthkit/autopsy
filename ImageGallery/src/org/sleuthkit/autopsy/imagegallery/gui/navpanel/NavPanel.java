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

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.Observable;
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
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
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
public class NavPanel extends TabPane {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    /**
     * TreeView for folders with hash hits
     */
    @FXML
    private TreeView<TreeNode> hashTree;

    @FXML
    private TabPane navTabPane;

    /**
     * TreeView for all folders
     */
    @FXML
    private TreeView<TreeNode> navTree;

    @FXML
    private Tab hashTab;

    @FXML
    private Tab navTab;

    @FXML
    private ComboBox<TreeNodeComparators> sortByBox;

    /**
     * contains the 'active tree'
     */
    private final SimpleObjectProperty<TreeView<TreeNode>> activeTreeProperty = new SimpleObjectProperty<>();

    private GroupTreeItem navTreeRoot;

    private GroupTreeItem hashTreeRoot;

    private final ImageGalleryController controller;

    public NavPanel(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "NavPanel.fxml");
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

        navTree.setShowRoot(false);
        hashTree.setShowRoot(false);

        navTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        hashTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        sortByBox.setCellFactory((ListView<TreeNodeComparators> p) -> new TreeNodeComparators.ComparatorListCell());

        sortByBox.setButtonCell(new TreeNodeComparators.ComparatorListCell());
        sortByBox.setItems(FXCollections.observableArrayList(FXCollections.observableArrayList(TreeNodeComparators.values())));
        sortByBox.getSelectionModel().select(TreeNodeComparators.HIT_COUNT);
        sortByBox.getSelectionModel().selectedItemProperty().addListener((Observable o) -> {
            resortHashTree();
        });

        navTree.setCellFactory((TreeView<TreeNode> p) -> new GroupTreeCell());

        hashTree.setCellFactory((TreeView<TreeNode> p) -> new GroupTreeCell());

        activeTreeProperty.addListener((Observable o) -> {
            updateControllersGroup();
            activeTreeProperty.get().getSelectionModel().selectedItemProperty().addListener((Observable o1) -> {
                updateControllersGroup();
            });
        });

        this.activeTreeProperty.set(navTree);

        navTabPane.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends Tab> ov, Tab t, Tab t1) -> {
            if (t1 == hashTab) {
                activeTreeProperty.set(hashTree);
            } else if (t1 == navTab) {
                activeTreeProperty.set(navTree);
            }
        });

        controller.getGroupManager().getAnalyzedGroups().addListener((ListChangeListener.Change<? extends DrawableGroup> change) -> {
            TreeItem<TreeNode> selectedItem = activeTreeProperty.get().getSelectionModel().getSelectedItem();
            boolean wasPermuted = false;
            while (change.next()) {
                if (change.wasPermutated()) {
                    // Handle this afterward
                    wasPermuted = true;
                    break;
                }
                for (DrawableGroup g : change.getAddedSubList()) {
                    insertIntoNavTree(g);
                    if (g.getHashSetHitsCount() > 0) {
                        insertIntoHashTree(g);
                    }
                }
                for (DrawableGroup g : change.getRemoved()) {
                    removeFromNavTree(g);
                    removeFromHashTree(g);
                }
            }

            if (wasPermuted) {
                rebuildTrees();
            }
            if (selectedItem != null && selectedItem.getValue().getGroup() != null) {
                Platform.runLater(() -> {
                    setFocusedGroup(selectedItem.getValue().getGroup());
                });
            }
        });

        rebuildTrees();

        controller.viewState().addListener((ObservableValue<? extends GroupViewState> observable, GroupViewState oldValue, GroupViewState newValue) -> {
            if (newValue != null && newValue.getGroup() != null) {
                setFocusedGroup(newValue.getGroup());
            }
        });
    }

    private void rebuildTrees() {
        navTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().selectedItemProperty().get());
        hashTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().selectedItemProperty().get());

        for (DrawableGroup g : controller.getGroupManager().getAnalyzedGroups()) {
            insertIntoNavTree(g);
            if (g.getHashSetHitsCount() > 0) {
                insertIntoHashTree(g);
            }
        }

        Platform.runLater(() -> {
            navTree.setRoot(navTreeRoot);
            navTreeRoot.setExpanded(true);
            hashTree.setRoot(hashTreeRoot);
            hashTreeRoot.setExpanded(true);
        });
    }

    private void updateControllersGroup() {
        final TreeItem<TreeNode> selectedItem = activeTreeProperty.get().getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() != null && selectedItem.getValue().getGroup() != null) {
            controller.advance(GroupViewState.tile(selectedItem.getValue().getGroup()));
        }
    }

    private void resortHashTree() {
        hashTreeRoot.resortChildren(sortByBox.getSelectionModel().getSelectedItem());
    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadType.JFX)
    public void setFocusedGroup(DrawableGroup grouping) {

        List<String> path = groupingToPath(grouping);

        final GroupTreeItem treeItemForGroup = ((GroupTreeItem) activeTreeProperty.get().getRoot()).getTreeItemForPath(path);

        if (treeItemForGroup != null) {
            /* When we used to run the below code on the FX thread, it would
             * get into infinite loops when the next group button was pressed
             * quickly because the udpates became out of order and History could
             * not
             * keep track of what was current.
             *
             * Currently (4/2/15), this method is already on the FX thread, so
             * it is OK. */
            //Platform.runLater(() -> {
            TreeItem<TreeNode> ti = treeItemForGroup;
            while (ti != null) {
                ti.setExpanded(true);
                ti = ti.getParent();
            }
            int row = activeTreeProperty.get().getRow(treeItemForGroup);
            if (row != -1) {
                activeTreeProperty.get().getSelectionModel().select(treeItemForGroup);
                activeTreeProperty.get().scrollTo(row);
            }
            //});   //end Platform.runLater
        }
    }

    private static List<String> groupingToPath(DrawableGroup g) {
        if (g.groupKey.getAttribute() == DrawableAttribute.PATH) {
            String path = g.groupKey.getValueDisplayName();

            String cleanPath = StringUtils.stripStart(path, "/");
            String[] tokens = cleanPath.split("/");

            return Arrays.asList(tokens);
        } else {
            return Arrays.asList(g.groupKey.getValueDisplayName());
        }
    }

    private void insertIntoHashTree(DrawableGroup g) {
        initHashTree();
        hashTreeRoot.insert(groupingToPath(g), g, false);
    }

    private void insertIntoNavTree(DrawableGroup g) {
        initNavTree();
        navTreeRoot.insert(groupingToPath(g), g, true);
    }

    private void removeFromNavTree(DrawableGroup g) {
        initNavTree();
        final GroupTreeItem treeItemForGroup = GroupTreeItem.getTreeItemForGroup(navTreeRoot, g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }
    }

    private void removeFromHashTree(DrawableGroup g) {
        initHashTree();
        final GroupTreeItem treeItemForGroup = GroupTreeItem.getTreeItemForGroup(hashTreeRoot, g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }
    }

    private void initNavTree() {
        if (navTreeRoot == null) {
            navTreeRoot = new GroupTreeItem("", null, null);

            Platform.runLater(() -> {
                navTree.setRoot(navTreeRoot);
                navTreeRoot.setExpanded(true);
            });
        }
    }

    private void initHashTree() {
        if (hashTreeRoot == null) {
            hashTreeRoot = new GroupTreeItem("", null, null);

            Platform.runLater(() -> {
                hashTree.setRoot(hashTreeRoot);
                hashTreeRoot.setExpanded(true);
            });
        }
    }
}
