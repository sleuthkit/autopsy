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
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An AbstractAction to manage adding and modifying a Central Repository file
 * instance comment.
 */
public final class AddEditCentralRepoCommentAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(AddEditCentralRepoCommentAction.class.getName());

    private boolean addToDatabase;
    private CorrelationAttribute correlationAttribute;
    String title;

    /**
     * Private constructor to create an instance given a CorrelationAttribute.
     *
     * @param correlationAttribute The correlation attribute to modify.
     * @param title                The text for the menu item.
     */
    private AddEditCentralRepoCommentAction(CorrelationAttribute correlationAttribute, String title) {
        super(title);
        this.title = title;
        this.correlationAttribute = correlationAttribute;
    }

    /**
     * Private constructor to create an instance given an AbstractFile.
     *
     * @param file  The file from which a correlation attribute to modify is
     *              derived.
     * @param title The text for the menu item.
     */
    private AddEditCentralRepoCommentAction(AbstractFile file, String title) throws EamDbException, NoCurrentCaseException, TskCoreException {

        super(title);
        this.title = title;
        correlationAttribute = EamArtifactUtil.getCorrelationAttributeFromContent(file);
        if (correlationAttribute == null) {
            addToDatabase = true;
            correlationAttribute = EamArtifactUtil.makeCorrelationAttributeFromContent(file);
        }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        addEditCentralRepoComment();
    }

    /**
     * Create a Add/Edit dialog for the correlation attribute file instance
     * comment. The comment will be updated in the database if the file instance
     * exists there, or a new file instance will be added to the database with
     * the comment attached otherwise.
     */
    public void addEditCentralRepoComment() {
        CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute, title);
        centralRepoCommentDialog.display();

        if (centralRepoCommentDialog.isCommentUpdated()) {
            EamDb dbManager;

            try {
                dbManager = EamDb.getInstance();

                if (addToDatabase) {
                    dbManager.addArtifact(correlationAttribute);
                } else {
                    dbManager.updateAttributeInstanceComment(correlationAttribute);
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error connecting to Central Repository database.", ex);
            }
        }
    }

    /**
     * Create an instance labeled "Add/Edit Central Repository Comment" given an
     * AbstractFile. This is intended for the result view.
     *
     * @param file The file from which a correlation attribute to modify is
     *             derived.
     *
     * @return The instance.
     *
     * @throws EamDbException
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCentralRepoCommentAction(AbstractFile file)
            throws AddEditCentralRepoCommentException {

        try {
            return new AddEditCentralRepoCommentAction(file,
                    Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
        } catch (EamDbException ex) {
            throw new AddEditCentralRepoCommentException(
                    "Error connecting to Central Repository database.", ex);
        } catch (NoCurrentCaseException ex) {
            throw new AddEditCentralRepoCommentException(
                    "Exception while getting open case.", ex);
        } catch (TskCoreException ex) {
            throw new AddEditCentralRepoCommentException(String.format(
                    "Could not retrieve data source from file '%s' (objId=%d).", file.getName(), file.getId()), ex);
        }
    }

    /**
     * Create an instance labeled "Add/Edit Comment" given a
     * CorrelationAttribute. This is intended for the content view.
     *
     * @param correlationAttribute The correlation attribute to modify.
     *
     * @return The instance.
     */
    @Messages({"AddEditCentralRepoCommentAction.menuItemText.addEditComment=Add/Edit Comment"})
    public static AddEditCentralRepoCommentAction createAddEditCommentAction(CorrelationAttribute correlationAttribute) {

        return new AddEditCentralRepoCommentAction(correlationAttribute,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditComment());
    }

    /**
     * Thrown when there's an issue in the Add/Edit Central Repository Comment
     * action.
     */
    public static final class AddEditCentralRepoCommentException extends Exception {

        private static final long serialVersionUID = 1L;

        private AddEditCentralRepoCommentException(String message) {
            super(message);
        }

        private AddEditCentralRepoCommentException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
