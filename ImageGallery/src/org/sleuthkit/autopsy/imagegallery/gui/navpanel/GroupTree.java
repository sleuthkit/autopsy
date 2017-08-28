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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

/**
 * Shows path based groups as a tree and others kinds of groups as a flat list (
 * a tree with an invisible root and only one level of children). Shows controls
 * to adjust the sorting only in flat list mode.
 */
final public class GroupTree extends NavPanel<TreeItem<GroupTreeNode>> {

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final GroupTreeItem groupTreeRoot = new GroupTreeItem("", null, true);

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final TreeView<GroupTreeNode> groupTree = new TreeView<>(groupTreeRoot);

    public GroupTree(ImageGalleryController controller) {
        super(controller);
        FXMLConstructor.construct(this, "NavPanel.fxml"); //NON-NLS
    }

    @FXML
    @Override
    @NbBundle.Messages({"GroupTree.displayName.allGroups=All Groups"})
    void initialize() {
        super.initialize();
        setText(Bundle.GroupTree_displayName_allGroups());
        setGraphic(new ImageView("org/sleuthkit/autopsy/imagegallery/images/Folder-icon.png")); //NON-NLS

        getBorderPane().setCenter(groupTree);

        //only show sorting controls if not grouping by path
        BooleanBinding groupedByPath = Bindings.equal(getGroupManager().getGroupByProperty(), DrawableAttribute.PATH);
        getToolBar().visibleProperty().bind(groupedByPath.not());
        getToolBar().managedProperty().bind(groupedByPath.not());

        GroupCellFactory groupCellFactory = new GroupCellFactory(getController(), comparatorProperty());
        groupTree.setCellFactory(groupCellFactory::getTreeCell);
        groupTree.setShowRoot(false);

        getGroupManager().getAnalyzedGroups().addListener((ListChangeListener.Change<? extends DrawableGroup> change) -> {
            while (change.next()) {
                change.getAddedSubList().stream().forEach(this::insertGroup);
                change.getRemoved().stream().forEach(this::removeFromTree);
            }
            sortGroups();
        });

        for (DrawableGroup g : getGroupManager().getAnalyzedGroups()) {
            insertGroup(g);
        }
        sortGroups();
    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    void setFocusedGroup(DrawableGroup grouping) {
        final GroupTreeItem treeItemForGroup = groupTreeRoot.getTreeItemForPath(groupingToPath(grouping));

        if (treeItemForGroup != null) {
            groupTree.getSelectionModel().select(treeItemForGroup);
            Platform.runLater(() -> {
                int row = groupTree.getRow(treeItemForGroup);
                if (row != -1) {
                    groupTree.scrollTo(row - 2); //put newly selected row 3 from the top
                }
            });
        }
    }

    @Override
    Function<TreeItem<GroupTreeNode>, DrawableGroup> getDataItemMapper() {
        return treeItem -> treeItem.getValue().getGroup();
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    SelectionModel<TreeItem<GroupTreeNode>> getSelectionModel() {
        return groupTree.getSelectionModel();
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void applyGroupComparator() {
        groupTreeRoot.resortChildren(getComparator());
    }

    @Override
    GroupComparators<String> getDefaultComparator() {
        return GroupComparators.ALPHABETICAL;
    }

    private void insertGroup(DrawableGroup g) {
        groupTreeRoot.insert(groupingToPath(g), g, true);
    }

    private void removeFromTree(DrawableGroup g) {
        Optional.ofNullable(groupTreeRoot.getTreeItemForGroup(g))
                .ifPresent(GroupTreeItem::removeFromParent);
    }

    private static List<String> groupingToPath(DrawableGroup g) {
        String path = g.getGroupByValueDislpayName();
        if (g.getGroupByAttribute() == DrawableAttribute.PATH) {
            String[] cleanPathTokens = StringUtils.stripStart(path, "/").split("/");
            return Arrays.asList(cleanPathTokens);
        } else {
            String stripStart = StringUtils.strip(path, "/");
            return Arrays.asList(stripStart);
        }
    }

}
