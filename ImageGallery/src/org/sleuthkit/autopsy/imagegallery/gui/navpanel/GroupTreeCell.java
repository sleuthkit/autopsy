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
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.grouping.DrawableGroup;

/**
 * A cell in the NavPanel tree that listens to its associated group's fileids.
 * Manages visual representation of TreeNode in Tree. Listens to properties of
 * group that don't impact hierarchy and updates ui to reflect them
 */
class GroupTreeCell extends TreeCell<TreeNode> {

    /**
     * icon to use if this cell's TreeNode doesn't represent a group but just a
     * folder(with no DrawableFiles) in the file system hierarchy.
     */
    private static final Image EMPTY_FOLDER_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/folder.png");

    /**
     * reference to listener that allows us to remove it from a group when a new
     * group is assigned to this Cell
     */
    private InvalidationListener listener;

    public GroupTreeCell() {
        //TODO: move this to .css file
        //adjust indent, default is 10 which uses up a lot of space.
        setStyle("-fx-indent:5;");
        //since end of path is probably more interesting put ellipsis at front
        setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        Platform.runLater(() -> {
            prefWidthProperty().bind(getTreeView().widthProperty().subtract(15));
        });

    }

    @Override
    protected synchronized void updateItem(final TreeNode tNode, boolean empty) {
        //if there was a previous group, remove the listener
        Optional.ofNullable(getItem())
                .map(TreeNode::getGroup)
                .ifPresent((DrawableGroup t) -> {
                    t.fileIds().removeListener(listener);
                });

        super.updateItem(tNode, empty);

        if (isNull(tNode) || empty) {
            Platform.runLater(() -> {
                setTooltip(null);
                setText(null);
                setGraphic(null);
            });
        } else {
            final String groupName = StringUtils.defaultIfBlank(tNode.getPath(), DrawableGroup.getBlankGroupName());

            if (isNull(tNode.getGroup())) {
                //"dummy" group in file system tree <=>  a folder with no drawables
                Platform.runLater(() -> {
                    setTooltip(new Tooltip(groupName));
                    setText(groupName);
                    setGraphic(new ImageView(EMPTY_FOLDER_ICON));
                });

            } else {
                listener = (Observable o) -> {
                    final String countsText = getCountsText();
                    Platform.runLater(() -> {
                        setText(groupName + countsText);
                    });
                };
                //if number of files in this group changes (eg file is recategorized), update counts via listener
                tNode.getGroup().fileIds().addListener(listener);

                //... and use icon corresponding to group type
                final Image icon = tNode.getGroup().groupKey.getAttribute().getIcon();
                final String countsText = getCountsText();
                Platform.runLater(() -> {
                    setTooltip(new Tooltip(groupName));
                    setGraphic(new ImageView(icon));
                    setText(groupName + countsText);
                });
            }
        }
    }

    private synchronized String getCountsText() {
        final String counts = Optional.ofNullable(getItem())
                .map(TreeNode::getGroup)
                .map((DrawableGroup t) -> {
                    return " (" + ((t.groupKey.getAttribute() == DrawableAttribute.HASHSET)
                            ? Integer.toString(t.getSize())
                            : t.getHashSetHitsCount() + "/" + t.getSize()) + ")";
                }).orElse(""); //if item is null or group is null

        return counts;
    }
}
