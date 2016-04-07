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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action to delete the follow up tag a
 */
public class DeleteFollowUpTagAction extends Action {

    private static final Logger LOGGER = Logger.getLogger(DeleteFollowUpTagAction.class.getName());

    @NbBundle.Messages("DeleteFollwUpTagAction.displayName=Delete Follow Up Tag")
    public DeleteFollowUpTagAction(final ImageGalleryController controller, final DrawableFile file) {
        super(Bundle.DeleteFollwUpTagAction_displayName());
        setEventHandler((ActionEvent t) -> {
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    final DrawableTagsManager tagsManager = controller.getTagsManager();

                    try {
                        final TagName followUpTagName = tagsManager.getFollowUpTagName();

                        List<ContentTag> contentTagsByContent = tagsManager.getContentTags(file);
                        for (ContentTag ct : contentTagsByContent) {
                            if (ct.getName().getDisplayName().equals(followUpTagName.getDisplayName())) {
                                tagsManager.deleteContentTag(ct);
                            }
                        }
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to delete follow up tag.", ex); //NON-NLS
                    }
                    return null;
                }
            }.execute();
        });
    }
}
