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

import static java.util.Objects.isNull;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.datamodel.TagName;

/**
 * A Factory for Cells to use in a ListView<DrawableGroup> or
 * TreeView<GroupTreeNode>
 */
class GroupCellFactory {

    /**
     * icon to use if a cell doesn't represent a group but just a folder(with no
     * DrawableFiles) in the file system hierarchy.
     */
    private static final Image EMPTY_FOLDER_ICON = new Image("/org/sleuthkit/autopsy/imagegallery/images/folder.png"); //NON-NLS

    private final ReadOnlyObjectProperty<GroupComparators<?>> sortOrder;
    private final ImageGalleryController controller;

    GroupCellFactory(ImageGalleryController controller, ReadOnlyObjectProperty<GroupComparators<?>> sortOrderProperty) {
        this.controller = controller;
        this.sortOrder = sortOrderProperty;
    }

    GroupListCell getListCell(ListView<DrawableGroup> listview) {
        return initCell(new GroupListCell());
    }

    GroupTreeCell getTreeCell(TreeView<?> treeView) {
        return initCell(new GroupTreeCell());
    }

    /**
     * remove the listener when it is not needed any more
     *
     * @param listener
     * @param oldGroup
     */
    private void removeListeners(InvalidationListener listener, DrawableGroup oldGroup) {
        sortOrder.removeListener(listener);
        oldGroup.getFileIDs().removeListener(listener);
        oldGroup.seenProperty().removeListener(listener);
        oldGroup.uncatCountProperty().removeListener(listener);
        oldGroup.hashSetHitsCountProperty().removeListener(listener);
    }

    private void addListeners(InvalidationListener listener, DrawableGroup group) {
        //if the sort order changes, update the counts displayed to match the sorted by property
        sortOrder.addListener(listener);
        //if number of files in this group changes (eg a file is recategorized), update counts via listener
        group.getFileIDs().addListener(listener);
        group.uncatCountProperty().addListener(listener);
        group.hashSetHitsCountProperty().addListener(listener);
        //if the seen state of this group changes update its style
        group.seenProperty().addListener(listener);
    }

    private <X extends Cell<?> & GroupCell<?>> X initCell(X cell) {
        /*
         * reduce indent of TreeCells to 5, default is 10 which uses up a lot of
         * space. Define seen and unseen styles
         */
        cell.getStylesheets().add(GroupCellFactory.class.getResource("GroupCell.css").toExternalForm()); //NON-NLS
        cell.getStyleClass().add("groupCell");    //NON-NLS

        //since end of path is probably more interesting put ellipsis at front
        cell.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);

        Platform.runLater(() -> cell.prefWidthProperty().bind(cell.getView().widthProperty().subtract(15)));
        return cell;
    }

    private <X extends Cell<?> & GroupCell<?>> void updateGroup(X cell, DrawableGroup group) {
        addListeners(cell.getGroupListener(), group);

        //and use icon corresponding to group type
        final Node graphic = (group.getGroupByAttribute() == DrawableAttribute.TAGS)
                ? controller.getTagsManager().getGraphic((TagName) group.getGroupByValue())
                : group.getGroupKey().getGraphic();

        final String text = getCellText(cell);
        final String style = getSeenStyleClass(cell);

        Platform.runLater(() -> {
            cell.setTooltip(new Tooltip(text));
            cell.setGraphic(graphic);
            cell.setText(text);
            cell.setStyle(style);
        });
    }

    private <X extends Labeled & GroupCell<?>> void clearCell(X cell) {
        Platform.runLater(() -> {
            cell.setTooltip(null);
            cell.setText(null);
            cell.setGraphic(null);
            cell.setStyle("");
        });
    }

    /**
     * return the styleClass to apply based on the assigned group's seen status
     *
     * @return the style class to apply
     */
    private String getSeenStyleClass(GroupCell<?> cell) {
        return cell.getGroup()
                .map(DrawableGroup::isSeen)
                .map(seen -> seen ? "" : "-fx-font-weight:bold;") //NON-NLS
                .orElse(""); //if item is null or group is null
    }

    /**
     * get the counts part of the text to apply to this cell, including
     * parentheses
     *
     * @return get the counts part of the text to apply to this cell
     */
    private String getCountsText(GroupCell<?> cell) {
        return cell.getGroup()
                .map(group
                        -> " (" + (sortOrder.get() == GroupComparators.ALPHABETICAL
                ? group.getSize()
                : sortOrder.get().getFormattedValueOfGroup(group)) + ")"
                ).orElse(""); //if item is null or group is null
    }

    private String getCellText(GroupCell<?> cell) {
        return cell.getGroupName() + getCountsText(cell);
    }

    private class GroupTreeCell extends TreeCell<GroupTreeNode> implements GroupCell<TreeView<GroupTreeNode>> {

        private final InvalidationListener groupListener = new GroupListener<>(this);

        /**
         * Reference to group files listener that allows us to remove it from a
         * group when a new group is assigned to this Cell
         */
        @Override
        public InvalidationListener getGroupListener() {
            return groupListener;
        }

        @Override
        public TreeView<GroupTreeNode> getView() {
            return getTreeView();
        }

        @Override
        public String getGroupName() {
            return Optional.ofNullable(getItem())
                    .map(treeNode -> StringUtils.defaultIfBlank(treeNode.getDisplayName(), DrawableGroup.getBlankGroupName()))
                    .orElse("");
        }

        @Override
        public Optional<DrawableGroup> getGroup() {
            return Optional.ofNullable(getItem())
                    .map(GroupTreeNode::getGroup);
        }

        @Override
        protected synchronized void updateItem(final GroupTreeNode newItem, boolean empty) {
            //if there was a previous group, remove the listeners
            getGroup().ifPresent(oldGroup -> removeListeners(getGroupListener(), oldGroup));

            super.updateItem(newItem, empty);

            if (isNull(newItem) || empty) {
                clearCell(this);
            } else {
                DrawableGroup newGroup = newItem.getGroup();
                if (isNull(newGroup)) {
                    //this cod epath should only be invoked for non-group Tree
                    final String groupName = getGroupName();
                    //"dummy" group in file system tree <=>  a folder with no drawables
                    Platform.runLater(() -> {
                        setTooltip(new Tooltip(groupName));
                        setText(groupName);
                        setGraphic(new ImageView(EMPTY_FOLDER_ICON));
                        setStyle("");
                    });

                } else {
                    updateGroup(this, newGroup);
                }
            }
        }
    }

    private class GroupListCell extends ListCell<DrawableGroup> implements GroupCell<ListView<DrawableGroup>> {

        private final InvalidationListener groupListener = new GroupListener<>(this);

        /**
         * reference to group files listener that allows us to remove it from a
         * group when a new group is assigned to this Cell
         */
        @Override
        public InvalidationListener getGroupListener() {
            return groupListener;
        }

        @Override
        public ListView<DrawableGroup> getView() {
            return getListView();
        }

        @Override
        public String getGroupName() {
            return Optional.ofNullable(getItem())
                    .map(group -> StringUtils.defaultIfBlank(group.getGroupByValueDislpayName(), DrawableGroup.getBlankGroupName()))
                    .orElse("");
        }

        @Override
        public Optional<DrawableGroup> getGroup() {
            return Optional.ofNullable(getItem());
        }

        @Override
        protected synchronized void updateItem(final DrawableGroup newGroup, boolean empty) {
            //if there was a previous group, remove the listeners
            getGroup().ifPresent(oldGroup -> removeListeners(getGroupListener(), oldGroup));

            super.updateItem(newGroup, empty);

            if (isNull(newGroup) || empty) {
                clearCell(this);
            } else {
                updateGroup(this, newGroup);
            }
        }
    }

    private interface GroupCell<X extends Control> {

        String getGroupName();

        X getView();

        Optional<DrawableGroup> getGroup();

        InvalidationListener getGroupListener();
    }

    private class GroupListener<X extends Labeled & GroupCell<?>> implements InvalidationListener {

        private final X cell;

        GroupListener(X cell) {
            this.cell = cell;
        }

        @Override
        public void invalidated(Observable o) {
            final String text = getCellText(cell);
            final String style = getSeenStyleClass(cell);
            Platform.runLater(() -> {
                cell.setText(text);
                cell.setTooltip(new Tooltip(text));
                cell.setStyle(style);
            });
        }
    }
}
