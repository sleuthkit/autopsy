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
import org.sleuthkit.autopsy.featureaccess.FeatureAccessUtils;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * An Action that allows a user to remove a data source from the current case.
 */
public final class DeleteDataSourceAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private long dataSourceObjectID;
    private Path caseMetadataFilePath;

    /**
     * Constructs an Action that allows a user to remove a data source from a
     * case.
     *
     * @param dataSourceObjectID The object ID of the data source to be removed.
     */
    @NbBundle.Messages({
        "DeleteDataSourceAction.name.text=Remove Data Source"
    })
    public DeleteDataSourceAction(Long dataSourceObjectID) {
        super(Bundle.DeleteDataSourceAction_name_text());
        this.dataSourceObjectID = dataSourceObjectID;
        this.setEnabled(FeatureAccessUtils.canDeleteDataSources());
    }

    @NbBundle.Messages({
        "DeleteDataSourceAction.ingestRunningWarningDialog.message=Data sources cannot be removed from a case when ingest is running.",
        "DeleteDataSourceAction.confirmationDialog.message=Are you sure you want to remove the selected data source from the case?\nNote that the case will be closed and re-opened during the removal.",
        "# {0} - exception message", "DeleteDataSourceAction.exceptionMessage.dataSourceDeletionError=An error occurred while removing the data source:\n{0}\nPlease see the application log for details.",
        "# {0} - exception message", "DeleteDataSourceAction.exceptionMessage.couldNotReopenCase=Failed to re-open the case:\n{0}\nPlease see the application log for details."
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        if (IngestManager.getInstance().isIngestRunning()) {
            MessageNotifyUtil.Message.warn(Bundle.DeleteDataSourceAction_ingestRunningWarningDialog_message());
            return;
        }

        if (MessageNotifyUtil.Message.confirm(Bundle.DeleteDataSourceAction_confirmationDialog_message())) {
            new DataSourceDeletionWorker().execute();
        }
    }

    /**
     * A SwingWorker to do the data source deletion.
     */
    private class DataSourceDeletionWorker extends SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() throws Exception {
            /*
             * Save the case metadata file path so the case can be reopened if
             * something goes wrong and the case ends up closed.
             */
            caseMetadataFilePath = Case.getCurrentCase().getMetadata().getFilePath();
            Case.deleteDataSourceFromCurrentCase(dataSourceObjectID);
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, String.format("Error deleting data source (obj_id=%d)", dataSourceObjectID), ex);
                MessageNotifyUtil.Message.show(Bundle.DeleteDataSourceAction_exceptionMessage_dataSourceDeletionError(ex.getLocalizedMessage()), MessageNotifyUtil.MessageType.ERROR);
                if (!Case.isCaseOpen()) {
                    new CaseReopeningWorker().execute();
                }
            }
        }

    }

    /**
     * A SwingWorker to attempt to re-open the case after a data source deletion
     * exception.
     */
    private class CaseReopeningWorker extends SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() throws Exception {
            Case.openAsCurrentCase(caseMetadataFilePath.toString());
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, String.format("Interrupted reopening case after error deleting data source (obj_id=%d)", dataSourceObjectID), ex);

            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, String.format("Error reopening case after error deleting data source (obj_id=%d)", dataSourceObjectID), ex);
                MessageNotifyUtil.Message.show(Bundle.DeleteDataSourceAction_exceptionMessage_dataSourceDeletionError(ex.getCause().getLocalizedMessage()), MessageNotifyUtil.MessageType.ERROR);
                StartupWindowProvider.getInstance().open();
            }
        }
        
    }

    @Override
    public DeleteDataSourceAction clone() throws CloneNotSupportedException {
        DeleteDataSourceAction clonedObject = ((DeleteDataSourceAction) super.clone());
        clonedObject.setDataSourceID(this.dataSourceObjectID);
        return clonedObject;
    }

    /**
     * Allows the setting of the data source object ID field of a clone of this
     * action.
     *
     * @param dataSourceObjectID The data source object ID.
     */
    private void setDataSourceID(long dataSourceObjectID) {
        this.dataSourceObjectID = dataSourceObjectID;
    }

}
