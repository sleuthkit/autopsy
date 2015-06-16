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
import java.util.List;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileUpdateEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.TagUtils;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupKey;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class DeleteFollowUpTag extends Action {

    private static final Logger LOGGER = Logger.getLogger(DeleteFollowUpTag.class.getName());
    private final long fileID;
    private final DrawableFile<?> file;

    public DeleteFollowUpTag(DrawableFile<?> file) {
        super("Delete Follow Up Tag");
        this.file = file;
        this.fileID = file.getId();

        setEventHandler((ActionEvent t) -> {
            deleteFollowupTag();
        });
    }

    /**
     *
     * @param fileID1 the value of fileID1
     *
     * @throws IllegalStateException
     */
    private void deleteFollowupTag() throws IllegalStateException {

        final ImageGalleryController controller = ImageGalleryController.getDefault();
        final SleuthkitCase sleuthKitCase = controller.getSleuthKitCase();
        try {
            // remove file from old category group
            controller.getGroupManager().removeFromGroup(new GroupKey<TagName>(DrawableAttribute.TAGS, TagUtils.getFollowUpTagName()), fileID);

            List<ContentTag> contentTagsByContent = sleuthKitCase.getContentTagsByContent(file);
            for (ContentTag ct : contentTagsByContent) {
                if (ct.getName().getDisplayName().equals(TagUtils.getFollowUpTagName().getDisplayName())) {
                    sleuthKitCase.deleteContentTag(ct);
                }
            }
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent("TagAction", BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE)); //NON-NLS
            controller.getGroupManager().handleFileUpdate(FileUpdateEvent.newUpdateEvent(Collections.singleton(fileID), DrawableAttribute.TAGS));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete follow up tag.", ex);
        }
    }
}
