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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.awt.Window;
import java.util.Collection;
import java.util.Set;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.GetTagNameAndCommentDialog;
import org.sleuthkit.autopsy.actions.GetTagNameDialog;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.datamodel.TagName;

/**
 * An abstract base class for actions that allow users to tag SleuthKit data
 * model objects.
 *
 * //TODO: this class started as a cut and paste from
 * org.sleuthkit.autopsy.actions.AddTagAction and needs to be refactored or
 * reintegrated to the AddTagAction hierarchy of Autopysy.
 */
abstract class AddTagAction {

    protected static final String NO_COMMENT = "";

    /**
     * Template method to allow derived classes to provide a string for a menu
     * item label.
     */
    abstract protected String getActionDisplayName();

    /**
     * Template method to allow derived classes to add the indicated tag and
     * comment to one or more a SleuthKit data model objects.
     */
    abstract protected void addTag(TagName tagName, String comment);

    /**
     * Template method to allow derived classes to add the indicated tag and
     * comment to a list of one or more file IDs.
     */
    abstract protected void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles);

    /**
     * Instances of this class implement a context menu user interface for
     * creating or selecting a tag name for a tag and specifying an optional tag
     * comment.
     */
    // @@@ This user interface has some significant usability issues and needs
    // to be reworked.
    protected class TagMenu extends Menu {

        TagMenu(ImageGalleryController controller) {
            super(getActionDisplayName());

            // Create a "Quick Tag" sub-menu.
            Menu quickTagMenu = new Menu(NbBundle.getMessage(this.getClass(), "AddTagAction.tagMenu.quickTag"));
            getItems().add(quickTagMenu);

            /*
             * Each non-Category tag name in the current set of tags gets its
             * own menu item in the "Quick Tags" sub-menu. Selecting one of
             * these menu items adds a tag with the associated tag name.
             */
            Collection<TagName> tagNames = controller.getTagsManager().getNonCategoryTagNames();
            if (tagNames.isEmpty()) {
                MenuItem empty = new MenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.tagMenu.noTags"));
                empty.setDisable(true);
                quickTagMenu.getItems().add(empty);
            } else {
                for (final TagName tagName : tagNames) {
                    MenuItem tagNameItem = new MenuItem(tagName.getDisplayName());
                    tagNameItem.setOnAction((ActionEvent t) -> {
                        addTag(tagName, NO_COMMENT);
                    });
                    quickTagMenu.getItems().add(tagNameItem);
                }
            }

            /*
             * The "Quick Tag" menu also gets an "New Tag..." menu item.
             * Selecting this item initiates a dialog that can be used to create
             * or select a tag name and adds a tag with the resulting name.
             */
            MenuItem newTagMenuItem = new MenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.tagMenu.newTag"));
            newTagMenuItem.setOnAction((ActionEvent t) -> {
                SwingUtilities.invokeLater(() -> {
                    TagName tagName = GetTagNameDialog.doDialog(getIGWindow());
                    if (tagName != null) {
                        addTag(tagName, NO_COMMENT);
                    }
                });
            });
            quickTagMenu.getItems().add(newTagMenuItem);

            /*
             * Create a "Tag and Comment..." menu item. Selecting this item
             * initiates a dialog that can be used to create or select a tag
             * name with an optional comment and adds a tag with the resulting
             * name.
             */
            MenuItem tagAndCommentItem = new MenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.tagMenu.tagAndComment"));
            tagAndCommentItem.setOnAction((ActionEvent t) -> {
                SwingUtilities.invokeLater(() -> {
                    GetTagNameAndCommentDialog.TagNameAndComment tagNameAndComment = GetTagNameAndCommentDialog.doDialog(getIGWindow());
                    if (null != tagNameAndComment) {
                        if (CategoryManager.isCategoryTagName(tagNameAndComment.getTagName())) {
                            new CategorizeAction(controller).addTag(tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                        } else {
                            new AddDrawableTagAction(controller).addTag(tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                        }
                    }
                });
            });
            getItems().add(tagAndCommentItem);
        }

    }

    /**
     * @return the Window containing the ImageGalleryTopComponent
     */
    static private Window getIGWindow() {
        TopComponent etc = WindowManager.getDefault().findTopComponent(ImageGalleryTopComponent.PREFERRED_ID);
        return SwingUtilities.getWindowAncestor(etc);
    }
}
