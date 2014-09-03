package org.sleuthkit.autopsy.imageanalyzer.gui.navpanel;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.grouping.Grouping;

/**
 * A {@link Node} in the tree that listens to its associated group. Manages
 * visual representation of TreeNode in Tree. Listens to properties of group
 * that don't impact hierarchy and updates ui to reflect them
 */
class GroupTreeCell extends TreeCell<TreeNode> {

    /**
     * icon to use if this cell's TreeNode doesn't represent a group but just a
     * folder(with no DrawableFiles) in the hierarchy.
     */
    private static final Image EMPTY_FOLDER_ICON = new Image("org/sleuthkit/autopsy/imageanalyzer/images/folder.png");

    public GroupTreeCell() {
        //adjust indent, default is 10 which uses up a lot of space.
        setStyle("-fx-indent:5;");
        //since end of path is probably more interesting put ellipsis at front
        setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
    }

    @Override
    synchronized protected void updateItem(final TreeNode tNode, boolean empty) {
        super.updateItem(tNode, empty);
        prefWidthProperty().bind(getTreeView().widthProperty().subtract(15));

        if (tNode != null) {
            final String name = StringUtils.defaultIfBlank(tNode.getPath(), Grouping.UNKNOWN);
            setTooltip(new Tooltip(name));

            if (tNode.getGroup() == null) {
                setText(name);
                setGraphic(new ImageView(EMPTY_FOLDER_ICON));
            } else {
                //this TreeNode has a group so append counts to name ...
                setText(name + " (" + getNumerator() + getDenominator() + ")");

                //if number of files in this group changes (eg file is recategorized), update counts
                tNode.getGroup().fileIds().addListener((Observable o) -> {
                    Platform.runLater(() -> {
                        setText(name + " (" + getNumerator() + getDenominator() + ")");
                    });
                });

                //... and use icon corresponding to group type
                setGraphic(new ImageView(tNode.getGroup().groupKey.getAttribute().getIcon()));
            }
        } else {
            setTooltip(null);
            setText(null);
            setGraphic(null);
        }
    }

    /**
     * @return the Numerator of the count to append to the group name = number
     * of hashset hits + "/"
     */
    synchronized private String getNumerator() {
        final String numerator = (getItem().getGroup().groupKey.getAttribute() != DrawableAttribute.HASHSET)
                ? getItem().getGroup().getFilesWithHashSetHitsCount() + "/"
                : "";
        return numerator;
    }

    /**
     * @return the Denominator of the count to append to the group name = number
     * of files in group
     */
    synchronized private Integer getDenominator() {
        try {
            return getItem().getGroup().getSize();
        } catch (NullPointerException ex) {
            return 0;
        }
    }
}
