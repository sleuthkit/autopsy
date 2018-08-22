/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * An AbstractAction to manage adding and modifying a Central Repository file
 * instance comment.
 */
@Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
public final class AddEditCentralRepoCommentAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(AddEditCentralRepoCommentAction.class.getName());
    private static final long serialVersionUID = 1L;

    private boolean addToDatabase;
    private CorrelationAttribute correlationAttribute;
    private String comment;
    private final Long fileId;

    /**
     * Constructor to create an instance given an AbstractFile.
     *
     * @param file The file from which a correlation attribute to modify is
     *             derived.
     *
     */
    public AddEditCentralRepoCommentAction(AbstractFile file) {
        super(Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
        correlationAttribute = EamArtifactUtil.getCorrelationAttributeFromContent(file);
        fileId = file.getId();
        if (correlationAttribute == null) {
            addToDatabase = true;
            correlationAttribute = EamArtifactUtil.makeCorrelationAttributeFromContent(file);
        }

    }

    /**
     * Create a Add/Edit dialog for the correlation attribute file instance
     * comment. The comment will be updated in the database if the file instance
     * exists there, or a new file instance will be added to the database with
     * the comment attached otherwise.
     *
     * The current comment for this instance is saved in case it is needed to
     * update the display. If the comment was not changed either due to the
     * action being canceled or the occurrence of an error, the comment will be
     * null.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute);
        centralRepoCommentDialog.display();

        comment = null;

        if (centralRepoCommentDialog.isCommentUpdated()) {
            EamDb dbManager;

            try {
                dbManager = EamDb.getInstance();

                if (addToDatabase) {
                    dbManager.addArtifact(correlationAttribute);
                } else {
                    dbManager.updateAttributeInstanceComment(correlationAttribute);
                }

                comment = centralRepoCommentDialog.getComment();
                if (fileId != null) {
                    try {
                        Case.getCurrentCaseThrows().notifyCentralRepoCommentChanged(fileId, comment);
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "Case not open after changing central repository comment", ex);
                    }
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error adding comment", ex);
                NotifyDescriptor notifyDescriptor = new NotifyDescriptor.Message(
                        "An error occurred while trying to save the comment to the central repository.",
                        NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(notifyDescriptor);
            }
        }
    }

    /**
     * Retrieve the comment that was last saved. If a comment update was
     * canceled or an error occurred while attempting to save the comment, the
     * comment will be null.
     *
     * @return The comment.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Retrieve the associated correlation attribute.
     *
     * @return The correlation attribute.
     */
    public CorrelationAttribute getCorrelationAttribute() {
        return correlationAttribute;
    }
}
