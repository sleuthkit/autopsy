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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javax.swing.SwingUtilities;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.actions.GetTagNameAndCommentDialog;
import org.sleuthkit.autopsy.actions.GetTagNameDialog;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for Actions that allow users to tag SleuthKit data
 * model objects.
 */
abstract class AddTagAction {

    /**
     * The way the "directory tree" currently works, a new tags sub-tree
     * needs to be made to reflect the results of invoking tag Actions.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    protected void refreshDirectoryTree() {
        DirectoryTreeTopComponent.findInstance().refreshContentTreeSafe();
    }

    protected static final String NO_COMMENT = "";

    /**
     * Template method to allow derived classes to provide a string for for a
     * menu item label.
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

        TagMenu() {
            super(getActionDisplayName());

            // Get the current set of tag names.
            TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
            List<TagName> tagNames = null;
            try {
                tagNames = tagsManager.getAllTagNames();
            } catch (TskCoreException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex);
            }

            // Create a "Quick Tag" sub-menu.
            Menu quickTagMenu = new Menu("Quick Tag");
            getItems().add(quickTagMenu);

            // Each tag name in the current set of tags gets its own menu item in
            // the "Quick Tags" sub-menu. Selecting one of these menu items adds
            // a tag with the associated tag name. 
            if (null != tagNames && !tagNames.isEmpty()) {
                for (final TagName tagName : tagNames) {
                    if (tagName.getDisplayName().startsWith(Category.CATEGORY_PREFIX) == false) {
                        MenuItem tagNameItem = new MenuItem(tagName.getDisplayName());
                        tagNameItem.setOnAction((ActionEvent t) -> {
                            addTag(tagName, NO_COMMENT);
                            refreshDirectoryTree();
                        });
                        quickTagMenu.getItems().add(tagNameItem);
                    }
                }
            } else {
                MenuItem empty = new MenuItem("No tags");
                empty.setDisable(true);
                quickTagMenu.getItems().add(empty);
            }

            //   quickTagMenu.addSeparator();
            // The "Quick Tag" menu also gets an "Choose Tag..." menu item.
            // Selecting this item initiates a dialog that can be used to create
            // or select a tag name and adds a tag with the resulting name.
            MenuItem newTagMenuItem = new MenuItem("New Tag...");
            newTagMenuItem.setOnAction((ActionEvent t) -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        TagName tagName = GetTagNameDialog.doDialog();
                        if (tagName != null) {
                            addTag(tagName, NO_COMMENT);
                            refreshDirectoryTree();
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            quickTagMenu.getItems().add(newTagMenuItem);

            // Create a "Choose Tag and Comment..." menu item. Selecting this item initiates
            // a dialog that can be used to create or select a tag name with an 
            // optional comment and adds a tag with the resulting name.
            MenuItem tagAndCommentItem = new MenuItem("Tag and Comment...");
            tagAndCommentItem.setOnAction((ActionEvent t) -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        GetTagNameAndCommentDialog.TagNameAndComment tagNameAndComment = GetTagNameAndCommentDialog.doDialog();
                        if (null != tagNameAndComment) {
                            if (tagNameAndComment.getTagName().getDisplayName().startsWith(Category.CATEGORY_PREFIX)) {
                                new CategorizeAction().addTag(tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                            } else {
                                AddDrawableTagAction.getInstance().addTag(tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                            }
                            refreshDirectoryTree();
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            getItems().add(tagAndCommentItem);
        }
    }
}
