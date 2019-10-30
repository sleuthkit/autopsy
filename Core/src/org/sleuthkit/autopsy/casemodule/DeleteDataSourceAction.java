/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * An Action that allows a user to delete a data source from the current case.
 */
public final class DeleteDataSourceAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private long dataSourceObjectID;
    private Path caseMetadataFilePath;

    /**
     * Constructs an Action that allows a user to delete a data source.
     *
     * @param dataSourceObjectID The object ID of the data source to be deleted.
     */
    @NbBundle.Messages({
        "DeleteDataSourceAction.name.text=Delete Data Source"
    })
    public DeleteDataSourceAction(Long dataSourceObjectID) {
        super(Bundle.DeleteDataSourceAction_name_text());
        this.dataSourceObjectID = dataSourceObjectID;
    }

    @NbBundle.Messages({
        "DeleteDataSourceAction.confirmationDialog.message=Are you sure you want to delete the selected data source from the case?",
        "DeleteDataSourceAction.exceptionMessage.dataSourceDeletionError=An error occurred while deleting the data source.\nPlease see the application log for details.",
        "DeleteDataSourceAction.exceptionMessage.couldNotReopenCase=Failed to reopen the case.",})
    @Override
    public void actionPerformed(ActionEvent event) {
        if (MessageNotifyUtil.Message.confirm(Bundle.DeleteDataSourceAction_confirmationDialog_message())) {
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    caseMetadataFilePath = Case.getCurrentCase().getMetadata().getFilePath();
                    /*
                     * Note that the case is closed and re-opened by this case
                     * action.
                     */
                    Case.deleteDataSourceFromCurrentCase(dataSourceObjectID);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.log(Level.SEVERE, String.format("Error deleting data source (obj_id=%d)", dataSourceObjectID), ex);
                        MessageNotifyUtil.Message.show(Bundle.DeleteDataSourceAction_exceptionMessage_dataSourceDeletionError(), MessageNotifyUtil.MessageType.ERROR);
                        if (!Case.isCaseOpen()) {
                            try {
                                Case.openAsCurrentCase(caseMetadataFilePath.toString());
                            } catch (CaseActionException ex2) {
                                logger.log(Level.SEVERE, "Failed to reopen the case after data source deletion error", ex2);
                                MessageNotifyUtil.Message.show(Bundle.DeleteDataSourceAction_exceptionMessage_couldNotReopenCase(), MessageNotifyUtil.MessageType.ERROR);
                                StartupWindowProvider.getInstance().open();
                            }
                        }
                    }
                }
            }.execute();
        }
    }

    @Override
    public DeleteDataSourceAction clone() throws CloneNotSupportedException {
        DeleteDataSourceAction clonedObject = ((DeleteDataSourceAction) super.clone());
        clonedObject.setDataSourceID(this.dataSourceObjectID);
        return clonedObject;
    }

    private void setDataSourceID(long dataSourceID) {
        this.dataSourceObjectID = dataSourceID;
    }

}
