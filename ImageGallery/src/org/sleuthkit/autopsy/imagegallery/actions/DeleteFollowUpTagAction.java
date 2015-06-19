/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import javax.swing.SwingWorker;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.DrawableTagsManager;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupKey;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action to delete the follow up tag a
 */
public class DeleteFollowUpTagAction extends Action {

    private static final Logger LOGGER = Logger.getLogger(DeleteFollowUpTagAction.class.getName());
    private final long fileID;

    public DeleteFollowUpTagAction(final ImageGalleryController controller, final DrawableFile<?> file) {
        super("Delete Follow Up Tag");
        this.fileID = file.getId();
        setEventHandler((ActionEvent t) -> {
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    final GroupManager groupManager = controller.getGroupManager();
                    final DrawableTagsManager tagsManager = controller.getTagsManager();

                    try {
                        final TagName followUpTagName = tagsManager.getFollowUpTagName();
                        // remove file from old category group
                        groupManager.removeFromGroup(new GroupKey<TagName>(DrawableAttribute.TAGS, followUpTagName), fileID);

                        List<ContentTag> contentTagsByContent = tagsManager.getContentTagsByContent(file);
                        for (ContentTag ct : contentTagsByContent) {
                            if (ct.getName().getDisplayName().equals(followUpTagName.getDisplayName())) {
                                tagsManager.deleteContentTag(ct);
                            }
                        }

                        //make sure rest of ui  hears category change.
//                        groupManager.handleFileUpdate(FileUpdateEvent.newUpdateEvent(Collections.singleton(fileID), DrawableAttribute.TAGS));
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to delete follow up tag.", ex);
                    }
                    return null;
                }
            }.execute();
        });
    }
}
