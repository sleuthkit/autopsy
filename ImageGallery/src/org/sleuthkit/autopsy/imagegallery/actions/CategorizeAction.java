/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.collections.ObservableSet;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.swing.JOptionPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
@NbBundle.Messages({"CategorizeAction.displayName=Categorize"})
public class CategorizeAction extends Action {

    private static final Logger LOGGER = Logger.getLogger(CategorizeAction.class.getName());

    private final ImageGalleryController controller;
    private final UndoRedoManager undoManager;
    private final Category cat;
    private final Set<Long> selectedFileIDs;
    private final Boolean createUndo;

    public CategorizeAction(ImageGalleryController controller, Category cat, Set<Long> selectedFileIDs) {
        this(controller, cat, selectedFileIDs, true);
    }

    private CategorizeAction(ImageGalleryController controller, Category cat, Set<Long> selectedFileIDs, Boolean createUndo) {
        super(cat.getDisplayName());
        this.controller = controller;
        this.undoManager = controller.getUndoManager();
        this.cat = cat;
        this.selectedFileIDs = selectedFileIDs;
        this.createUndo = createUndo;
        setGraphic(cat.getGraphic());
        setEventHandler(actionEvent -> addCatToFiles(selectedFileIDs));
        setAccelerator(new KeyCodeCombination(KeyCode.getKeyCode(Integer.toString(cat.getCategoryNumber()))));
    }

    static public Menu getCategoriesMenu(ImageGalleryController controller) {
        return new CategoryMenu(controller);
    }


    final void addCatToFiles(Set<Long> ids) {
        Logger.getAnonymousLogger().log(Level.INFO, "categorizing{0} as {1}", new Object[]{ids.toString(), cat.getDisplayName()}); //NON-NLS
        controller.queueDBWorkerTask(new CategorizeTask(ids, cat, createUndo));
    }

    /**
     * Instances of this class implement a context menu user interface for
     * selecting a category
     */
    static private class CategoryMenu extends Menu {

        CategoryMenu(ImageGalleryController controller) {
            super(Bundle.CategorizeAction_displayName());
            setGraphic(new ImageView(DrawableAttribute.CATEGORY.getIcon()));
            ObservableSet<Long> selected = controller.getSelectionModel().getSelected();

            // Each category get an item in the sub-menu. Selecting one of these menu items adds
            // a tag with the associated category.
            for (final Category cat : Category.values()) {
                MenuItem categoryItem = ActionUtils.createMenuItem(new CategorizeAction(controller, cat, selected));
                getItems().add(categoryItem);
            }
        }
    }

    @NbBundle.Messages({"# {0} - fileID number",
        "CategorizeTask.errorUnable.msg=Unable to categorize {0}.",
        "CategorizeTask.errorUnable.title=Categorizing Error"})
    private class CategorizeTask extends ImageGalleryController.BackgroundTask {

        private final Set<Long> fileIDs;

        private final boolean createUndo;
        private final Category cat;

        CategorizeTask(Set<Long> fileIDs, @Nonnull Category cat, boolean createUndo) {
            super();
            this.fileIDs = fileIDs;
            java.util.Objects.requireNonNull(cat);
            this.cat = cat;
            this.createUndo = createUndo;
        }

        @Override
        public void run() {
            final DrawableTagsManager tagsManager = controller.getTagsManager();
            final CategoryManager categoryManager = controller.getCategoryManager();
            Map<Long, Category> oldCats = new HashMap<>();
            TagName tagName = categoryManager.getTagName(cat);
            TagName catZeroTagName = categoryManager.getTagName(Category.ZERO);
            for (long fileID : fileIDs) {
                try {
                    DrawableFile file = controller.getFileFromId(fileID);   //drawable db access
                    if (createUndo) {
                        Category oldCat = file.getCategory();  //drawable db access
                        TagName oldCatTagName = categoryManager.getTagName(oldCat);
                        if (false == tagName.equals(oldCatTagName)) {
                            oldCats.put(fileID, oldCat);
                        }
                    }

                    final List<ContentTag> fileTags = tagsManager.getContentTags(file);
                    if (tagName == categoryManager.getTagName(Category.ZERO)) {
                        // delete all cat tags for cat-0
                        fileTags.stream()
                                .filter(tag -> CategoryManager.isCategoryTagName(tag.getName()))
                                .forEach((ct) -> {
                                    try {
                                        tagsManager.deleteContentTag(ct);
                                    } catch (TskCoreException ex) {
                                        LOGGER.log(Level.SEVERE, "Error removing old categories result", ex); //NON-NLS
                                    }
                                });
                    } else {
                        //add cat tag if no existing cat tag for that cat
                        if (fileTags.stream()
                                .map(Tag::getName)
                                .filter(tagName::equals)
                                .collect(Collectors.toList()).isEmpty()) {
                            tagsManager.addContentTag(file, tagName, "");
                        }
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error categorizing result", ex); //NON-NLS
                    JOptionPane.showMessageDialog(null,
                            Bundle.CategorizeTask_errorUnable_msg(fileID),
                            Bundle.CategorizeTask_errorUnable_title(),
                            JOptionPane.ERROR_MESSAGE);
                    break;
                }
            }

            if (createUndo && oldCats.isEmpty() == false) {
                undoManager.addToUndo(new CategorizationChange(controller, cat, oldCats));
            }
        }
    }

    /**
     *
     */
    @Immutable
    private final class CategorizationChange implements UndoRedoManager.UndoableCommand {

        private final Category newCategory;
        private final ImmutableMap<Long, Category> oldCategories;
        private final ImageGalleryController controller;

        CategorizationChange(ImageGalleryController controller, Category newCategory, Map<Long, Category> oldCategories) {
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
            new CategorizeAction(controller, newCategory, this.oldCategories.keySet(), false)
                    .handle(null);
        }

        /**
         *
         * @param controller the value of controller
         */
        @Override
        public void undo() {

            for (Map.Entry<Long, Category> entry : oldCategories.entrySet()) {
                new CategorizeAction(controller, entry.getValue(), Collections.singleton(entry.getKey()), false)
                        .handle(null);
            }
        }
    }
}
