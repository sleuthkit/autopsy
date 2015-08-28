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

import static java.util.Objects.isNull;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

/**
 * A cell in the NavPanel tree that listens to its associated group's fileids
 * and seen status,and updates GUI to reflect them.
 *
 * TODO: we should use getStyleClass().add() rather than setStyle but it didn't
 * seem to work properly
 */
class GroupTreeCell extends TreeCell<TreeNode> {

    /**
     * icon to use if this cell's TreeNode doesn't represent a group but just a
     * folder(with no DrawableFiles) in the file system hierarchy.
     */
    private static final Image EMPTY_FOLDER_ICON
            = new Image(GroupTreeCell.class.getResourceAsStream("/org/sleuthkit/autopsy/imagegallery/images/folder.png"));

    /**
     * reference to group files listener that allows us to remove it from a
     * group when a new group is assigned to this Cell
     */
    private final InvalidationListener fileCountListener = (Observable o) -> {
        final String text = getGroupName() + getCountsText();
        Platform.runLater(() -> {
            setText(text);
            setTooltip(new Tooltip(text));
        });
    };

    /**
     * reference to group seen listener that allows us to remove it from a group
     * when a new group is assigned to this Cell
     */
    private final InvalidationListener seenListener = (Observable o) -> {
        final String style = getSeenStyleClass();
        Platform.runLater(() -> {
            setStyle(style);
        });
    };

    public GroupTreeCell() {
        getStylesheets().add(GroupTreeCell.class.getResource("GroupTreeCell.css").toExternalForm());
        getStyleClass().add("groupTreeCell");        //reduce  indent to 5, default is 10 which uses up a lot of space.

        //since end of path is probably more interesting put ellipsis at front
        setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        Platform.runLater(() -> {
            prefWidthProperty().bind(getTreeView().widthProperty().subtract(15));
        });

    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected synchronized void updateItem(final TreeNode treeNode, boolean empty) {
        //if there was a previous group, remove the listeners
        Optional.ofNullable(getItem())
                .map(TreeNode::getGroup)
                .ifPresent(group -> {
                    group.fileIds().removeListener(fileCountListener);
                    group.seenProperty().removeListener(seenListener);
                });

        super.updateItem(treeNode, empty);

        if (isNull(treeNode) || empty) {
            Platform.runLater(() -> {
                setTooltip(null);
                setText(null);
                setGraphic(null);
                setStyle("");
            });
        } else {
            if (isNull(treeNode.getGroup())) {
                final String groupName = getGroupName();
                //"dummy" group in file system tree <=>  a folder with no drawables
                Platform.runLater(() -> {
                    setTooltip(new Tooltip(groupName));
                    setText(groupName);
                    setGraphic(new ImageView(EMPTY_FOLDER_ICON));
                    setStyle("");
                });

            } else {
                //if number of files in this group changes (eg a file is recategorized), update counts via listener
                treeNode.getGroup().fileIds().addListener(fileCountListener);

                //if the seen state of this group changes update its style
                treeNode.getGroup().seenProperty().addListener(seenListener);

                //and use icon corresponding to group type
                final Image icon = treeNode.getGroup().groupKey.getAttribute().getIcon();
                final String text = getGroupName() + getCountsText();
                final String style = getSeenStyleClass();
                Platform.runLater(() -> {
                    setTooltip(new Tooltip(text));
                    setGraphic(new ImageView(icon));
                    setText(text);
                    setStyle(style);
                });
            }
        }
    }

    private String getGroupName() {
        return Optional.ofNullable(getItem())
                .map(treeNode -> StringUtils.defaultIfBlank(treeNode.getPath(), DrawableGroup.getBlankGroupName()))
                .orElse("");
    }

    /**
     * return the styleClass to apply based on the assigned group's seen status
     *
     * @return the style class to apply
     */
    @Nonnull
    private String getSeenStyleClass() {
        return Optional.ofNullable(getItem())
                .map(TreeNode::getGroup)
                .map(DrawableGroup::isSeen)
                .map(seen -> seen ? "" : "-fx-font-weight:bold;")
                .orElse(""); //if item is null or group is null
    }

    /**
     * get the counts part of the text to apply to this cell, including
     * parentheses
     *
     * @return get the counts part of the text to apply to this cell
     */
    @Nonnull
    private String getCountsText() {
        return Optional.ofNullable(getItem())
                .map(TreeNode::getGroup)
                .map(group -> " ("
                        + ((group.getGroupByAttribute() == DrawableAttribute.HASHSET)
                                ? Integer.toString(group.getSize())
                                : group.getHashSetHitsCount() + "/" + group.getSize())
                        + ")"
                ).orElse(""); //if item is null or group is null
    }
}
