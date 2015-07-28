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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javax.swing.SwingWorker;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to content.
 *
 * //TODO: since we are not using actionsGlobalContext anymore and this has
 * diverged from autopsy action, make this extend from controlsfx Action
 */
public class AddDrawableTagAction extends AddTagAction {

    private static final Logger LOGGER = Logger.getLogger(AddDrawableTagAction.class.getName());

    private final ImageGalleryController controller;

    public AddDrawableTagAction(ImageGalleryController controller) {
        this.controller = controller;
    }

    public Menu getPopupMenu() {
        return new TagMenu(controller);
    }

    @Override
    protected String getActionDisplayName() {
        return Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size() > 1 ? "Tag Files" : "Tag File";
    }

    @Override
    public void addTag(TagName tagName, String comment) {
        Set<Long> selectedFiles = new HashSet<>(FileIDSelectionModel.getInstance().getSelected());
        addTagsToFiles(tagName, comment, selectedFiles);
    }

    @Override
    public void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles) {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                for (Long fileID : selectedFiles) {
                    try {
                        final DrawableFile<?> file = controller.getFileFromId(fileID);
                        LOGGER.log(Level.INFO, "tagging {0} with {1} and comment {2}", new Object[]{file.getName(), tagName.getDisplayName(), comment});

                        // check if the same tag is being added for the same abstract file.
                        DrawableTagsManager tagsManager = controller.getTagsManager();
                        List<ContentTag> contentTags = tagsManager.getContentTagsByContent(file);
                        Optional<TagName> duplicateTagName = contentTags.stream()
                                .map(ContentTag::getName)
                                .filter(tagName::equals)
                                .findAny();

                        if (duplicateTagName.isPresent()) {
                            LOGGER.log(Level.INFO, "{0} already tagged as {1}. Skipping.", new Object[]{file.getName(), tagName.getDisplayName()});
                        } else {
                            LOGGER.log(Level.INFO, "Tagging {0} as {1}", new Object[]{file.getName(), tagName.getDisplayName()});
                            controller.getTagsManager().addContentTag(file, tagName, comment);
                        }

                    } catch (TskCoreException tskCoreException) {
                        LOGGER.log(Level.SEVERE, "Error tagging file", tskCoreException);
                        Platform.runLater(() -> {
                            new Alert(Alert.AlertType.ERROR, "Unable to tag file " + fileID + ".").show();
                        });
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "unexpected exception while tagging files", ex);
                }
            }
        }.execute();
    }
}
