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
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
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

    /**
     * Private constructor to create an instance given a CorrelationAttribute.
     *
     * @param correlationAttribute The correlation attribute to modify.
     * @param displayName          The text for the menu item.
     */
    private AddEditCentralRepoCommentAction(CorrelationAttribute correlationAttribute, String displayName) {
        super(displayName);
        this.correlationAttribute = correlationAttribute;
    }

    /**
     * Private constructor to create an instance given an AbstractFile.
     *
     * @param file        The file from which a correlation attribute to modify
     *                    is derived.
     * @param displayName The text for the menu item.
     */
    private AddEditCentralRepoCommentAction(AbstractFile file, String displayName) throws EamDbException, NoCurrentCaseException, TskCoreException {

        super(displayName);
        CorrelationAttribute.Type type = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
        CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
        CorrelationDataSource correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
        String value = file.getMd5Hash();
        String filePath = (file.getParentPath() + file.getName()).toLowerCase();

        correlationAttribute = EamDb.getInstance().getCorrelationAttribute(type, correlationCase, correlationDataSource, value, filePath);
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
        CentralRepoCommentDialog centralRepoCommentDialog = new CentralRepoCommentDialog(correlationAttribute);
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
            throws EamDbException, NoCurrentCaseException, TskCoreException {
        
        return new AddEditCentralRepoCommentAction(file,
                Bundle.AddEditCentralRepoCommentAction_menuItemText_addEditCentralRepoComment());
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
}
