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
package org.sleuthkit.autopsy.imageanalyzer.gui.navpanel;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.imageanalyzer.ImageAnalyzerController;
import org.sleuthkit.autopsy.imageanalyzer.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute.AttributeName;
import static org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute.AttributeName.PATH;
import static org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute.AttributeName.TAGS;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupKey;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupSortBy;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupViewState;
import org.sleuthkit.autopsy.imageanalyzer.grouping.Grouping;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/** Display two trees. one shows all folders (groups) and calls out folders
 * with images. the user can select folders with images to see them in the
 * main GroupPane The other shows folders with hash set hits.
 */
public class NavPanel extends TabPane {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    /** TreeView for folders with hash hits */
    @FXML
    private TreeView<TreeNode> hashTree;

    @FXML
    private TabPane navTabPane;

    /** TreeView for all folders */
    @FXML
    private TreeView<TreeNode> navTree;

    @FXML
    private Tab hashTab;

    @FXML
    private Tab navTab;

    @FXML
    private ComboBox<TreeNodeComparators> sortByBox;

    /** contains the 'active tree' */
    private final SimpleObjectProperty<TreeView<TreeNode>> activeTreeProperty = new SimpleObjectProperty<>();

    private GroupTreeItem navTreeRoot;

    private GroupTreeItem hashTreeRoot;

    private final ImageAnalyzerController controller;

    public NavPanel(ImageAnalyzerController controller) {
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

        initHashTree();
        initNavTree();

        controller.getGroupManager().getAnalyzedGroups().addListener((ListChangeListener.Change<? extends Grouping> change) -> {
            while (change.next()) {
                for (Grouping g : change.getAddedSubList()) {
                    insertIntoNavTree(g);
                    if (g.getFilesWithHashSetHitsCount() > 0) {
                        insertIntoHashTree(g);
                    }
                }
                for (Grouping g : change.getRemoved()) {
                    removeFromNavTree(g);
                    removeFromHashTree(g);
                }
            }
        });

        for (Grouping g : controller.getGroupManager().getAnalyzedGroups()) {
            insertIntoNavTree(g);
            if (g.getFilesWithHashSetHitsCount() > 0) {
                insertIntoHashTree(g);
            }
        }

        controller.viewState().addListener((ObservableValue<? extends GroupViewState> observable, GroupViewState oldValue, GroupViewState newValue) -> {
            if (newValue != null && newValue.getGroup() != null) {
                setFocusedGroup(newValue.getGroup());
            }
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

    private void insertIntoHashTree(Grouping g) {
        initHashTree();
        hashTreeRoot.insert(g.groupKey.getValueDisplayName(), g, false);
    }

    /** Set the tree to the passed in group
     *
     * @param grouping */
    public void setFocusedGroup(Grouping grouping) {

        List<String> path = groupingToPath(grouping);

        final GroupTreeItem treeItemForGroup = ((GroupTreeItem) activeTreeProperty.get().getRoot()).getTreeItemForPath(path);

        if (treeItemForGroup != null) {
            Platform.runLater(() -> {
                TreeItem ti = treeItemForGroup;
                while (ti != null) {
                    ti.setExpanded(true);
                    ti = ti.getParent();
                }
                int row = activeTreeProperty.get().getRow(treeItemForGroup);
                if (row != -1) {
                    activeTreeProperty.get().getSelectionModel().select(treeItemForGroup);
                    activeTreeProperty.get().scrollTo(row);
                }
            });
        }
    }

    private static List<String> groupingToPath(Grouping g) {

        AttributeName attrName = g.groupKey.getAttribute().attrName;
        String path = null;
        switch (attrName) {
            case PATH:
                path = ((String) g.groupKey.getValue());
                break;
            case TAGS:
                path = ((TagName) g.groupKey.getValue()).getDisplayName();
            default:
                if (path == null) {
                    path = g.groupKey.getValue().toString();
                }
        }

        String cleanPath = StringUtils.stripStart(path, "/");
        String[] tokens = cleanPath.split("/");

        return Arrays.asList(tokens);
    }

    private void insertIntoNavTree(Grouping g) {
        initNavTree();
        List<String> path = groupingToPath(g);

        AttributeName attrName = g.groupKey.getAttribute().attrName;
        switch (attrName) {
            case PATH:
                navTreeRoot.insert(path, g, true);
                break;
            default:
                navTreeRoot.insert(path, g, false);
                break;
        }
    }

    private void removeFromNavTree(Grouping g) {
        initNavTree();
        final GroupTreeItem treeItemForGroup = GroupTreeItem.getTreeItemForGroup(navTreeRoot, g);
        if (treeItemForGroup != null) {
            treeItemForGroup.removeFromParent();
        }
    }

    private void removeFromHashTree(Grouping g) {
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

    //these are not used anymore, but could be usefull at some point
    //TODO: remove them or find a use and undeprecate
    @Deprecated
    private void rebuildNavTree() {
        navTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().selectedItemProperty().get());

        ObservableList<Grouping> groups = controller.getGroupManager().getAnalyzedGroups();

        for (Grouping g : groups) {
            insertIntoNavTree(g);
        }

        Platform.runLater(() -> {
            navTree.setRoot(navTreeRoot);
            navTreeRoot.setExpanded(true);
        });
    }

    @Deprecated
    private void rebuildHashTree() {
        hashTreeRoot = new GroupTreeItem("", null, sortByBox.getSelectionModel().getSelectedItem());
        //TODO: can we do this as db query?
        List<String> hashSetNames = controller.getGroupManager().findValuesForAttribute(DrawableAttribute.HASHSET, GroupSortBy.NONE);
        for (String name : hashSetNames) {
            try {
                List<Long> fileIDsInGroup = controller.getGroupManager().getFileIDsInGroup(new GroupKey(DrawableAttribute.HASHSET, name));

                for (Long fileId : fileIDsInGroup) {

                    DrawableFile file = controller.getFileFromId(fileId);
                    Collection<GroupKey> groupKeysForFile;
                    if (controller.getGroupManager().getGroupBy() == DrawableAttribute.TAGS) {
                        Collection<TagName> tagNames = (Collection<TagName>) file.getValueOfAttribute(DrawableAttribute.TAGS);
                        groupKeysForFile = new ArrayList<>();
                        for (TagName tn : tagNames) {
                            groupKeysForFile.add(new GroupKey(DrawableAttribute.TAGS, tn));
                        }
                    } else {
                        groupKeysForFile = controller.getGroupManager().getGroupKeysForFile(file);
                    }

                    for (GroupKey k : groupKeysForFile) {
                        final Grouping groupForKey = controller.getGroupManager().getGroupForKey(k);
                        if (groupForKey != null) {
                            insertIntoHashTree(groupForKey);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        Platform.runLater(() -> {
            hashTree.setRoot(hashTreeRoot);
            hashTreeRoot.setExpanded(true);
        });
    }
}
