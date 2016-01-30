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

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
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
    private final UndoRedoManager undoManager;

    public CategorizeAction(ImageGalleryController controller) {
        super();
        this.controller = controller;
        undoManager = controller.getUndoManager();
    }

    public Menu getPopupMenu() {
        return new CategoryMenu(controller);
    }

    @Override
    protected String getActionDisplayName() {
        return "Categorize";
    }

    @Override
    public void addTag(TagName tagName, String comment) {
        Set<Long> selectedFiles = new HashSet<>(controller.getSelectionModel().getSelected());
        addTagsToFiles(tagName, comment, selectedFiles);
    }

    @Override
    protected void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles) {
        addTagsToFiles(tagName, comment, selectedFiles, true);
    }

    public void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles, boolean createUndo) {
        Logger.getAnonymousLogger().log(Level.INFO, "categorizing{0} as {1}", new Object[]{selectedFiles.toString(), tagName.getDisplayName()});
        controller.queueDBWorkerTask(new CategorizeTask(selectedFiles, tagName, comment, createUndo));
    }

    /**
     * Instances of this class implement a context menu user interface for
     * selecting a category
     */
    static private class CategoryMenu extends Menu {

        CategoryMenu(ImageGalleryController controller) {
            super("Categorize");

            // Each category get an item in the sub-menu. Selecting one of these menu items adds
            // a tag with the associated category.
            for (final Category cat : Category.values()) {

                MenuItem categoryItem = new MenuItem(cat.getDisplayName());
                categoryItem.setOnAction((ActionEvent t) -> {
                    final CategorizeAction categorizeAction = new CategorizeAction(controller);
                    categorizeAction.addTag(controller.getCategoryManager().getTagName(cat), NO_COMMENT);
                });
                categoryItem.setAccelerator(new KeyCodeCombination(KeyCode.getKeyCode(Integer.toString(cat.getCategoryNumber()))));
                getItems().add(categoryItem);
            }
        }
    }

    private class CategorizeTask extends ImageGalleryController.InnerTask {

        private final Set<Long> fileIDs;
        @Nonnull
        private final TagName tagName;
        private final String comment;
        private final boolean createUndo;

        CategorizeTask(Set<Long> fileIDs, @Nonnull TagName tagName, String comment, boolean createUndo) {
            super();
            this.fileIDs = fileIDs;
            java.util.Objects.requireNonNull(tagName);
            this.tagName = tagName;
            this.comment = comment;
            this.createUndo = createUndo;

        }

        @Override
        public void run() {
            final DrawableTagsManager tagsManager = controller.getTagsManager();
            final CategoryManager categoryManager = controller.getCategoryManager();
            Map<Long, TagName> oldCats = new HashMap<>();
            for (long fileID : fileIDs) {
                try {
                    DrawableFile<?> file = controller.getFileFromId(fileID);   //drawable db access
                    if (createUndo) {
                        Category oldCat = file.getCategory();  //drawable db access
                        TagName oldCatTagName = categoryManager.getTagName(oldCat);
                        if (false == tagName.equals(oldCatTagName)) {
                            oldCats.put(fileID, oldCatTagName);
                        }
                    }

                    final List<ContentTag> fileTags = tagsManager.getContentTagsByContent(file);
                    if (tagName == categoryManager.getTagName(Category.ZERO)) {
                        // delete all cat tags for cat-0
                        fileTags.stream()
                                .filter(tag -> CategoryManager.isCategoryTagName(tag.getName()))
                                .forEach((ct) -> {
                                    try {
                                        tagsManager.deleteContentTag(ct);
                                    } catch (TskCoreException ex) {
                                        LOGGER.log(Level.SEVERE, "Error removing old categories result", ex);
                                    }
                                });
                    } else {
                        //add cat tag if no existing cat tag for that cat
                        if (fileTags.stream()
                                .map(Tag::getName)
                                .filter(tagName::equals)
                                .collect(Collectors.toList()).isEmpty()) {
                            tagsManager.addContentTag(file, tagName, comment);
                        }
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error categorizing result", ex);
                    JOptionPane.showMessageDialog(null, "Unable to categorize " + fileID + ".", "Categorizing Error", JOptionPane.ERROR_MESSAGE);
                }
            }

            if (createUndo && oldCats.isEmpty() == false) {
                undoManager.addToUndo(new CategorizationChange(controller, tagName, oldCats));
            }
        }
    }

    /**
     *
     */
    @Immutable
    private final class CategorizationChange implements UndoRedoManager.UndoableCommand {

        private final TagName newCategory;
        private final ImmutableMap<Long, TagName> oldCategories;
        private final ImageGalleryController controller;

        CategorizationChange(ImageGalleryController controller, TagName newCategory, Map<Long, TagName> oldCategories) {
            this.controller = controller;
            this.newCategory = newCategory;
            this.oldCategories = ImmutableMap.copyOf(oldCategories);
        }

        /**
         *
         * @param controller the controller to apply the changes with
         */
        @Override
        public void run() {
            CategorizeAction categorizeAction = new CategorizeAction(controller);
            categorizeAction.addTagsToFiles(newCategory, "", this.oldCategories.keySet(), false);
        }

        /**
         *
         * @param controller the value of controller
         */
        @Override
        public void undo() {
            CategorizeAction categorizeAction = new CategorizeAction(controller);
            for (Map.Entry<Long, TagName> entry : oldCategories.entrySet()) {
                categorizeAction.addTagsToFiles(entry.getValue(), "", Collections.singleton(entry.getKey()), false);
            }
        }
    }
}
