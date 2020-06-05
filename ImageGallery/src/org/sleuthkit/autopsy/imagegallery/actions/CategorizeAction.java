/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.collections.ObservableSet;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.swing.JOptionPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.DrawableDbTask;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import javafx.scene.image.ImageView;

/**
 * An action that associates a drawable file with a Project Vic category.
 */
@NbBundle.Messages({"CategorizeAction.displayName=Categorize"})
public class CategorizeAction extends Action {

    private static final Logger logger = Logger.getLogger(CategorizeAction.class.getName());

    private final ImageGalleryController controller;
    private final UndoRedoManager undoManager;
    private final Set<Long> selectedFileIDs;
    private final Boolean createUndo;
    private final TagName tagName;

    public CategorizeAction(ImageGalleryController controller, TagName tagName, Set<Long> selectedFileIDs) {
        this(controller, tagName, selectedFileIDs, true);
    }

    private CategorizeAction(ImageGalleryController controller, TagName tagName, Set<Long> selectedFileIDs, Boolean createUndo) {
        super(tagName.getDisplayName());
        this.controller = controller;
        this.undoManager = controller.getUndoManager();
        this.selectedFileIDs = selectedFileIDs;
        this.createUndo = createUndo;
        this.tagName = tagName;
        setGraphic(getGraphic(tagName));
        setEventHandler(actionEvent -> addCatToFiles(selectedFileIDs));
        
        int rank = tagName.getRank();
        // Only map to a key if the rank is less than 10
        if(rank < 10) { 
            setAccelerator(new KeyCodeCombination(KeyCode.getKeyCode(Integer.toString(rank))));
        }
    }

    static public Menu getCategoriesMenu(ImageGalleryController controller) {
        return new CategoryMenu(controller);
    }

    final void addCatToFiles(Set<Long> ids) {
        Logger.getAnonymousLogger().log(Level.INFO, "categorizing{0} as {1}", new Object[]{ids.toString(), tagName.getDisplayName()}); //NON-NLS
        controller.queueDBTask(new CategorizeDrawableFileTask(ids, tagName, createUndo));
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
            for (TagName tagName : controller.getCategoryManager().getCategories()) {
                MenuItem categoryItem = ActionUtils.createMenuItem(new CategorizeAction(controller, tagName, selected));
                getItems().add(categoryItem);
            }
        }
    }

    /**
     * A task that associates a drawable file with a Project Vic category.
     */
    @NbBundle.Messages({
        "# {0} - fileID number",
        "CategorizeDrawableFileTask.errorUnable.msg=Unable to categorize {0}.",
        "CategorizeDrawableFileTask.errorUnable.title=Categorizing Error"
    })
    private class CategorizeDrawableFileTask extends DrawableDbTask {

        final Set<Long> fileIDs;

        final boolean createUndo;
        final TagName catTagName;

        CategorizeDrawableFileTask(Set<Long> fileIDs, @Nonnull TagName catTagName, boolean createUndo) {
            super();
            this.fileIDs = fileIDs;
            java.util.Objects.requireNonNull(catTagName);
            this.catTagName = catTagName;
            this.createUndo = createUndo;
        }

        @Override
        public void run() {
            final DrawableTagsManager tagsManager = controller.getTagsManager();
            Map<Long, TagName> oldCats = new HashMap<>();
            for (long fileID : fileIDs) {
                try {
                    DrawableFile file = controller.getFileFromID(fileID);   //drawable db access
                    if (createUndo) {
                        TagName oldCatTagName = file.getCategory();  //drawable db access
                        if (false == catTagName.equals(oldCatTagName)) {
                            oldCats.put(fileID, oldCatTagName);
                        }
                    }

                    final List<ContentTag> fileTags = tagsManager.getContentTags(file);

                    if (fileTags.stream()
                            .map(Tag::getName)
                            .filter(tagName::equals)
                            .collect(Collectors.toList()).isEmpty()) {
                        tagsManager.addContentTag(file, tagName, "");
                    }

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error categorizing result", ex); //NON-NLS
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                            Bundle.CategorizeDrawableFileTask_errorUnable_msg(fileID),
                            Bundle.CategorizeDrawableFileTask_errorUnable_title(),
                            JOptionPane.ERROR_MESSAGE);
                    break;
                }
            }

            if (createUndo && oldCats.isEmpty() == false) {
                undoManager.addToUndo(new CategorizationChange(controller, catTagName, oldCats));
            }
        }
    }

    /**
     *
     */
    @Immutable
    private final class CategorizationChange implements UndoRedoManager.UndoableCommand {

        private final TagName newTagNameCategory;
        private final ImmutableMap<Long, TagName> oldTagNameCategories;
        private final ImageGalleryController controller;

        CategorizationChange(ImageGalleryController controller, TagName newTagNameCategory, Map<Long, TagName> oldTagNameCategories) {
            this.controller = controller;
            this.newTagNameCategory = newTagNameCategory;
            this.oldTagNameCategories = ImmutableMap.copyOf(oldTagNameCategories);
        }

        /**
         *
         * @param controller the controller to apply the changes with
         */
        @Override
        public void run() {
            new CategorizeAction(controller, newTagNameCategory, this.oldTagNameCategories.keySet(), false)
                    .handle(null);
        }

        /**
         *
         * @param controller the value of controller
         */
        @Override
        public void undo() {

            for (Map.Entry<Long, TagName> entry : oldTagNameCategories.entrySet()) {
                new CategorizeAction(controller, entry.getValue(), Collections.singleton(entry.getKey()), false)
                        .handle(null);
            }
        }
    }

    /**
     * Create an BufferedImage to use as the icon for the given TagName.
     *
     * @param tagName The category TagName.
     *
     * @return TagName Icon BufferedImage.
     */
    private BufferedImage getImageForTagName(TagName tagName) {
        BufferedImage off_image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = off_image.createGraphics();

        g2.setColor(java.awt.Color.decode(tagName.getColor().getRgbValue()));
        g2.fillRect(0, 0, 16, 16);

        g2.setColor(Color.BLACK);
        g2.drawRect(0, 0, 16, 16);
        return off_image;
    }

    /**
     * Returns a Node which is a ImageView of the icon for the given TagName.
     *
     * @param tagname
     *
     * @return Node for use as the TagName menu item graphic.
     */
    private Node getGraphic(TagName tagname) {
        BufferedImage buff_image = getImageForTagName(tagname);
        return new ImageView(SwingFXUtils.toFXImage(buff_image, null));
    }

}
