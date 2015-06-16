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

import java.util.Collections;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileUpdateEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupKey;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupManager;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action to delete the follow up tag a
 *
 *
 */
public class DeleteTagAction extends Action {

    private static final Logger LOGGER = Logger.getLogger(DeleteTagAction.class.getName());
    private final long fileID;
    private final DrawableFile<?> file;
    private final ImageGalleryController controller;
    private final ContentTag tag;

    public DeleteTagAction(ImageGalleryController controller, DrawableFile<?> file, ContentTag tag) {
        super("Delete Follow Up Tag");
        this.controller = controller;
        this.file = file;
        this.fileID = file.getId();
        this.tag = tag;
        setEventHandler((ActionEvent t) -> {
            deleteTag();
        });
    }

    /**
     *
     * @param fileID1 the value of fileID1
     *
     * @throws IllegalStateException
     */
    private void deleteTag() throws IllegalStateException {

        final SleuthkitCase sleuthKitCase = controller.getSleuthKitCase();
        final GroupManager groupManager = controller.getGroupManager();

        try {
            // remove file from old category group
            groupManager.removeFromGroup(new GroupKey<TagName>(DrawableAttribute.TAGS, tag.getName()), fileID);
            sleuthKitCase.deleteContentTag(tag);
//
//            List<ContentTag> contentTagsByContent = sleuthKitCase.getContentTagsByContent(file);
//            for (ContentTag ct : contentTagsByContent) {
//                if (ct.getName().getDisplayName().equals(tagsManager.getFollowUpTagName().getDisplayName())) {
//                    sleuthKitCase.deleteContentTag(ct);
//                }
//            }
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent("TagAction", BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE)); //NON-NLS

            //make sure rest of ui  hears category change.
            groupManager.handleFileUpdate(FileUpdateEvent.newUpdateEvent(Collections.singleton(fileID), DrawableAttribute.TAGS));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete follow up tag.", ex);
        }
    }
}
