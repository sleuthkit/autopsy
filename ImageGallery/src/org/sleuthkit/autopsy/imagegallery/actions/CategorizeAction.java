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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCodeCombination;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.FileUpdateEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupKey;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Adaptation of Tag Actions to enforce category-tag uniqueness
 *
 * TODO: since we are not using actionsGlobalContext anymore and this has
 * diverged from autopsy action, make this extend from controlsfx Action
 */
public class CategorizeAction extends AddTagAction {

    private static final Logger LOGGER = Logger.getLogger(CategorizeAction.class.getName());

    private final ImageGalleryController controller;

    public CategorizeAction() {
        super();
        this.controller = ImageGalleryController.getDefault();
    }

    static public Menu getPopupMenu() {
        return new CategoryMenu();
    }

    @Override
    protected String getActionDisplayName() {
        return "Categorize";
    }

    @Override
    public void addTag(TagName tagName, String comment) {
        Set<Long> selectedFiles = new HashSet<>(FileIDSelectionModel.getInstance().getSelected());
        addTagsToFiles(tagName, comment, selectedFiles);
    }

    @Override
    public void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles) {

        Logger.getAnonymousLogger().log(Level.INFO, "categorizing{0} as {1}", new Object[]{selectedFiles.toString(), tagName.getDisplayName()});

        for (Long fileID : selectedFiles) {
            controller.queueDBWorkerTask(new CategorizeTask(fileID, tagName, comment));
        }
    }

    /**
     * Instances of this class implement a context menu user interface for
     * selecting a category
     */
    static private class CategoryMenu extends Menu {

        CategoryMenu() {
            super("Categorize");

            // Each category get an item in the sub-menu. Selecting one of these menu items adds
            // a tag with the associated category.
            for (final Category cat : Category.values()) {

                MenuItem categoryItem = new MenuItem(cat.getDisplayName());
                categoryItem.setOnAction((ActionEvent t) -> {
                    final CategorizeAction categorizeAction = new CategorizeAction();
                    categorizeAction.addTag(cat.getTagName(), NO_COMMENT);
                });
                categoryItem.setAccelerator(new KeyCodeCombination(cat.getHotKeycode()));
                getItems().add(categoryItem);
            }
        }
    }

    private class CategorizeTask extends ImageGalleryController.InnerTask {

        private final long fileID;
        private final TagName tagName;
        private final String comment;

        public CategorizeTask(long fileID, TagName tagName, String comment) {
            super();
            this.fileID = fileID;
            this.tagName = tagName;
            this.comment = comment;
        }

        @Override
        public void run() {
            try {
                DrawableFile<?> file = controller.getFileFromId(fileID);   //drawable db
                Category oldCat = file.getCategory();
                // remove file from old category group
                controller.getGroupManager().removeFromGroup(new GroupKey<Category>(DrawableAttribute.CATEGORY, oldCat), fileID);  //memory

                //remove old category tag if necessary
                List<ContentTag> allContentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByContent(file); //tsk db
                for (ContentTag ct : allContentTags) {
                    //this is bad: treating tags as categories as long as their names start with prefix
                    //TODO:  abandon using tags for categories and instead add a new column to DrawableDB
                    if (ct.getName().getDisplayName().startsWith(Category.CATEGORY_PREFIX)) {
                        Case.getCurrentCase().getServices().getTagsManager().deleteContentTag(ct);   //tsk db
                        controller.getCategoryManager().decrementCategoryCount(Category.fromDisplayName(ct.getName().getDisplayName()));  //memory/drawable db
                    }

                }
                controller.getCategoryManager().incrementCategoryCount(Category.fromDisplayName(tagName.getDisplayName())); //memory/drawable db
                if (tagName != Category.ZERO.getTagName()) { // no tags for cat-0
                    Case.getCurrentCase().getServices().getTagsManager().addContentTag(file, tagName, comment); //tsk db
                }
                //make sure rest of ui  hears category change.
                controller.getGroupManager().handleFileUpdate(FileUpdateEvent.newUpdateEvent(Collections.singleton(fileID), DrawableAttribute.CATEGORY)); //memory/ui

            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error categorizing result", ex);
                JOptionPane.showMessageDialog(null, "Unable to categorize " + fileID + ".", "Categorizing Error", JOptionPane.ERROR_MESSAGE);
            }

            refreshDirectoryTree();
        }
    }
}
