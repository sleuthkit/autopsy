/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An abstract base class for tasks that add or modify the drawables database
 * records for multiple drawable files.
 */
@NbBundle.Messages({
    "BulkDrawableFilesTask.committingDb.status=committing image/video database",
    "BulkDrawableFilesTask.stopCopy.status=Stopping copy to drawable db task.",
    "BulkDrawableFilesTask.errPopulating.errMsg=There was an error populating Image Gallery database."
})
abstract class BulkDrawableFilesTask extends DrawableDbTask {

    private static final Logger logger = Logger.getLogger(BulkDrawableFilesTask.class.getName());
    private static final String MIMETYPE_CLAUSE = "(mime_type LIKE '" //NON-NLS
            + String.join("' OR mime_type LIKE '", FileTypeUtils.getAllSupportedMimeTypes()) //NON-NLS
            + "') ";
    private final String drawableQuery;
    private final ImageGalleryController controller;
    private final DrawableDB taskDB;
    private final SleuthkitCase tskCase;
    private final long dataSourceObjId;

    //NON-NLS
    BulkDrawableFilesTask(long dataSourceObjId, ImageGalleryController controller) {
        this.controller = controller;
        this.taskDB = controller.getDrawablesDatabase();
        this.tskCase = controller.getCaseDatabase();
        this.dataSourceObjId = dataSourceObjId;
        drawableQuery = " (data_source_obj_id = " + dataSourceObjId + ") "
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")" + " AND ( " + MIMETYPE_CLAUSE //NON-NLS
                + " OR mime_type LIKE 'video/%' OR mime_type LIKE 'image/%' )" //NON-NLS
                + " ORDER BY parent_path ";
    }

    /**
     * Do any cleanup for this task.
     */
    abstract void cleanup();

    abstract void processFile(final AbstractFile f, DrawableDB.DrawableTransaction tr, SleuthkitCase.CaseDbTransaction caseDBTransaction) throws TskCoreException;

    /**
     * Gets a list of files to process.
     *
     * @return list of files to process
     *
     * @throws TskCoreException
     */
    List<AbstractFile> getFiles() throws TskCoreException {
        return tskCase.findAllFilesWhere(drawableQuery);
    }

    @Override
    @NbBundle.Messages({
        "BulkDrawableFilesTask.populatingDb.status=populating analyzed image/video database"
    })
    public void run() {
        ProgressHandle progressHandle = getInitialProgressHandle();
        progressHandle.start();
        updateMessage(Bundle.BulkDrawableFilesTask_populatingDb_status() + " (Data Source " + dataSourceObjId + ")");
        DrawableDB.DrawableTransaction drawableDbTransaction = null;
        SleuthkitCase.CaseDbTransaction caseDbTransaction = null;
        boolean hasFilesWithNoMime = true;
        boolean endedEarly = false;
        try {
            // See if there are any files in the DS w/out a MIME TYPE
            hasFilesWithNoMime = controller.hasFilesWithNoMimeType(dataSourceObjId);
            //grab all files with detected mime types
            final List<AbstractFile> files = getFiles();
            progressHandle.switchToDeterminate(files.size());
            taskDB.insertOrUpdateDataSource(dataSourceObjId, DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);
            updateProgress(0.0);
            int workDone = 0;
            // Cycle through all of the files returned and call processFile on each
            //do in transaction
            drawableDbTransaction = taskDB.beginTransaction();
            /*
             * We are going to periodically commit the CaseDB transaction and
             * sleep so that the user can have Autopsy do other stuff while
             * these bulk tasks are ongoing.
             */
            int caseDbCounter = 0;
            for (final AbstractFile f : files) {
                if (caseDbTransaction == null) {
                    caseDbTransaction = tskCase.beginTransaction();
                }
                if (isCancelled() || Thread.interrupted()) {
                    logger.log(Level.WARNING, "Task cancelled or interrupted: not all contents may be transfered to drawable database."); //NON-NLS
                    endedEarly = true;
                    progressHandle.finish();
                    break;
                }
                processFile(f, drawableDbTransaction, caseDbTransaction);
                workDone++;
                progressHandle.progress(f.getName(), workDone);
                updateProgress(workDone - 1 / (double) files.size());
                updateMessage(f.getName());
                // Periodically, commit the transaction (which frees the lock) and sleep
                // to allow other threads to get some work done in CaseDB
                if ((++caseDbCounter % 200) == 0) {
                    caseDbTransaction.commit();
                    caseDbTransaction = null;
                    Thread.sleep(500); // 1/2 second
                }
            }
            progressHandle.finish();
            progressHandle = ProgressHandle.createHandle(Bundle.BulkDrawableFilesTask_committingDb_status());
            updateMessage(Bundle.BulkDrawableFilesTask_committingDb_status() + " (Data Source " + dataSourceObjId + ")");
            updateProgress(1.0);
            progressHandle.start();
            if (caseDbTransaction != null) {
                caseDbTransaction.commit();
                caseDbTransaction = null;
            }
            // pass true so that groupmanager is notified of the changes
            taskDB.commitTransaction(drawableDbTransaction, true);
            drawableDbTransaction = null;
        } catch (TskCoreException | SQLException | InterruptedException ex) {
            if (null != caseDbTransaction) {
                try {
                    caseDbTransaction.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back case db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            if (null != drawableDbTransaction) {
                try {
                    taskDB.rollbackTransaction(drawableDbTransaction);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            progressHandle.progress(Bundle.BulkDrawableFilesTask_stopCopy_status());
            logger.log(Level.WARNING, "Stopping copy to drawable db task.  Failed to transfer all database contents", ex); //NON-NLS
            MessageNotifyUtil.Notify.warn(Bundle.BulkDrawableFilesTask_errPopulating_errMsg(), ex.getMessage());
            endedEarly = true;
        } finally {
            progressHandle.finish();
            // Mark to REBUILT_STALE if some files didnt' have MIME (ingest was still ongoing) or
            // if there was cancellation or errors
            DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus = ((hasFilesWithNoMime == true) || (endedEarly == true)) ? DrawableDB.DrawableDbBuildStatusEnum.REBUILT_STALE : DrawableDB.DrawableDbBuildStatusEnum.COMPLETE;
            try {
                taskDB.insertOrUpdateDataSource(dataSourceObjId, datasourceDrawableDBStatus);
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, String.format("Error updating datasources table (data source object ID = %d, status = %s)", dataSourceObjId, datasourceDrawableDBStatus.toString(), ex)); //NON-NLS
            }
            updateMessage("");
            updateProgress(-1.0);
        }
        cleanup();
    }

    abstract ProgressHandle getInitialProgressHandle();
}
