/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javax.swing.SwingWorker;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this Action allow users to remove tags from content.
 */
public class DeleteTagAction extends Action {

    private static final Logger logger = Logger.getLogger(DeleteTagAction.class.getName());

    private final ImageGalleryController controller;
    private final long fileId;
    private final TagName tagName;
    private final ContentTag contentTag;

    public DeleteTagAction(ImageGalleryController controller, TagName tagName, ContentTag contentTag, long fileId) {
        super(tagName.getDisplayName());
        this.controller = controller;
        this.fileId = fileId;
        this.tagName = tagName;
        this.contentTag = contentTag;
        setGraphic(controller.getTagsManager().getGraphic(tagName));
        String notableString = tagName.getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
        setText(tagName.getDisplayName() + notableString);
        setEventHandler(actionEvent -> deleteTag());
    }

    static public Menu getTagMenu(ImageGalleryController controller) {
        return new TagMenu(controller);
    }

    @NbBundle.Messages({"# {0} - fileID",
        "DeleteDrawableTagAction.deleteTag.alert=Unable to untag file {0}."})
    private void deleteTag() {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                DrawableTagsManager tagsManager = controller.getTagsManager();

                try {
                    logger.log(Level.INFO, "Removing tag {0} from {1}", new Object[]{tagName.getDisplayName(), contentTag.getContent().getName()}); //NON-NLS
                    tagsManager.deleteContentTag(contentTag);
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error untagging file", tskCoreException); //NON-NLS
                    Platform.runLater(()
                            -> new Alert(Alert.AlertType.ERROR, Bundle.DeleteDrawableTagAction_deleteTag_alert(fileId)).show()
                    );
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Unexpected exception while untagging file", ex); //NON-NLS
                }
            }
        }.execute();
    }

    @NbBundle.Messages({"DeleteDrawableTagAction.displayName=Remove File Tag"})
    private static class TagMenu extends Menu {

        TagMenu(ImageGalleryController controller) {
            setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
            setText(Bundle.DeleteDrawableTagAction_displayName());

            // For this menu, we shouldn't have more than one file selected.
            // Therefore, we will simply grab the first file and work with that.
            final Collection<AbstractFile> selectedFilesList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            AbstractFile file = selectedFilesList.iterator().next();

            try {
                List<ContentTag> existingTagsList
                        = Case.getOpenCase().getServices().getTagsManager()
                        .getContentTagsByContent(file);

                Collection<TagName> tagNamesList
                        = controller.getTagsManager().getNonCategoryTagNames();
                Iterator<TagName> tagNameIterator = tagNamesList.iterator();
                for (int i = 0; tagNameIterator.hasNext(); i++) {
                    TagName tagName = tagNameIterator.next();
                    for (ContentTag contentTag : existingTagsList) {
                        if (contentTag.getName().getId() == tagName.getId()) {
                            DeleteTagAction deleteDrawableTagAction = new DeleteTagAction(controller, tagName, contentTag, file.getId());
                            MenuItem tagNameItem = ActionUtils.createMenuItem(deleteDrawableTagAction);
                            getItems().add(tagNameItem);
                        }
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagMenu.class.getName())
                        .log(Level.SEVERE, "Error retrieving tags for TagMenu", ex); //NON-NLS
            }

            if (getItems().isEmpty()) {
                setDisable(true);
            }
        }
    }
}
