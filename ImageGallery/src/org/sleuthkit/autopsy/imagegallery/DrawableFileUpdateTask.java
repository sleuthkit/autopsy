/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A bulk update task for adding images to the image gallery.
 */
final class DrawableFileUpdateTask extends DrawableDbTask {

    private static final Logger logger = Logger.getLogger(DrawableFileUpdateTask.class.getName());

    private static final String MIMETYPE_CLAUSE = "(mime_type LIKE '" //NON-NLS
            + String.join("' OR mime_type LIKE '", FileTypeUtils.getAllSupportedMimeTypes()) //NON-NLS
            + "') ";

    private final ImageGalleryController controller;

    /**
     * Construct a new task.
     * 
     * @param controller A handle to the IG controller.
     */
    DrawableFileUpdateTask(ImageGalleryController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        for (Long dataSourceObjId : controller.getStaleDataSourceIds()) {
            updateFileForDataSource(dataSourceObjId);
        }
    }

    /**
     * Gets the drawables database that is part of the model for the controller.
     * 
     * @return The the drawable db object.
     */
    private DrawableDB getDrawableDB() {
        return controller.getDrawablesDatabase();
    }

    /**
     * Return the sleuthkit case object for the open case.
     * 
     * @return The case db object.
     */
    private SleuthkitCase getCaseDB() {
        return controller.getCaseDatabase();
    }

    /**
     * Returns a list of files to be processed by the task for the given
     * datasource.
     * 
     * @param dataSourceObjId
     * @return
     * @throws TskCoreException 
     */
    private List<AbstractFile> getFilesForDataSource(long dataSourceObjId) throws TskCoreException {
        List<AbstractFile> list = getCaseDB().findAllFilesWhere(getDrawableQuery(dataSourceObjId));
        return list;

    }

    /**
     * Process a single file for the IG drawable db.
     *
     * @param file              The file to process.
     * @param tr                A valid DrawableTransaction object.
     * @param caseDbTransaction A valid caseDBTransaction object.
     *
     * @throws TskCoreException
     */
    void processFile(AbstractFile file, DrawableDB.DrawableTransaction tr, SleuthkitCase.CaseDbTransaction caseDbTransaction) throws TskCoreException {
        final boolean known = file.getKnown() == TskData.FileKnown.KNOWN;
        if (known) {
            getDrawableDB().removeFile(file.getId(), tr); //remove known files
        } else {
            // NOTE: Files are being processed because they have the right MIME type,
            // so we do not need to worry about this calculating them
            if (FileTypeUtils.hasDrawableMIMEType(file)) {
                getDrawableDB().updateFile(DrawableFile.create(file, true, false), tr, caseDbTransaction);
            } //unsupported mimtype => analyzed but shouldn't include
            else {
                getDrawableDB().removeFile(file.getId(), tr);
            }
        }
    }

    /**
     * Returns the image query for the given data source.
     *
     * @param dataSourceObjId
     *
     * @return SQL query for given data source.
     */
    private String getDrawableQuery(long dataSourceObjId) {
        return " (data_source_obj_id = " + dataSourceObjId + ") "
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")" + " AND ( " + MIMETYPE_CLAUSE //NON-NLS
                + " OR mime_type LIKE 'video/%' OR mime_type LIKE 'image/%' )" //NON-NLS
                + " ORDER BY parent_path ";
    }

    @Messages({
        "DrawableFileUpdateTask_populatingDb_status=populating analyzed image/video database",
        "DrawableFileUpdateTask_committingDb.status=committing image/video database",
        "DrawableFileUpdateTask_stopCopy_status=Stopping copy to drawable db task.",
        "DrawableFileUpdateTask_errPopulating_errMsg=There was an error populating Image Gallery database."
    })
    private void updateFileForDataSource(long dataSourceObjId) {
        ProgressHandle progressHandle = getInitialProgressHandle();
        progressHandle.start();
        updateMessage(Bundle.DrawableFileUpdateTask_populatingDb_status() + " (Data Source " + dataSourceObjId + ")");

        DrawableDB.DrawableTransaction drawableDbTransaction = null;
        SleuthkitCase.CaseDbTransaction caseDbTransaction = null;
        boolean hasFilesWithNoMime = true;
        boolean endedEarly = false;
        try {

            getDrawableDB().buildFileMetaDataCache();
            // See if there are any files in the DS w/out a MIME TYPE
            hasFilesWithNoMime = controller.hasFilesWithNoMimeType(dataSourceObjId);

            //grab all files with detected mime types
            final List<AbstractFile> files = getFilesForDataSource(dataSourceObjId);
            progressHandle.switchToDeterminate(files.size());
            getDrawableDB().insertOrUpdateDataSource(dataSourceObjId, DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);
            updateProgress(0.0);
            int workDone = 0;
            // Cycle through all of the files returned and call processFile on each
            //do in transaction
            drawableDbTransaction = getDrawableDB().beginTransaction();
            /*
             * We are going to periodically commit the CaseDB transaction and
             * sleep so that the user can have Autopsy do other stuff while
             * these bulk tasks are ongoing.
             */
            int caseDbCounter = 0;

            for (final AbstractFile f : files) {
                updateMessage(f.getName());
                if (caseDbTransaction == null) {
                    caseDbTransaction = getCaseDB().beginTransaction();
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
                    Thread.sleep(500); // 1/2 millisecond
                }
            }
            progressHandle.finish();
            progressHandle = ProgressHandle.createHandle(Bundle.DrawableFileUpdateTask_committingDb_status());
            updateMessage(Bundle.DrawableFileUpdateTask_committingDb_status() + " (Data Source " + dataSourceObjId + ")");
            updateProgress(1.0);
            if (caseDbTransaction != null) {
                caseDbTransaction.commit();
                caseDbTransaction = null;
            }
            // pass true so that groupmanager is notified of the changes
            getDrawableDB().commitTransaction(drawableDbTransaction, true);
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
                    getDrawableDB().rollbackTransaction(drawableDbTransaction);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            progressHandle.progress(Bundle.DrawableFileUpdateTask_stopCopy_status());
            logger.log(Level.WARNING, "Stopping copy to drawable db task.  Failed to transfer all database contents", ex); //NON-NLS
            MessageNotifyUtil.Notify.warn(Bundle.DrawableFileUpdateTask_errPopulating_errMsg(), ex.getMessage());
            endedEarly = true;
        } finally {
            progressHandle.finish();
            // Mark to REBUILT_STALE if some files didnt' have MIME (ingest was still ongoing) or
            // if there was cancellation or errors
            DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus = ((hasFilesWithNoMime == true) || (endedEarly == true)) ? DrawableDB.DrawableDbBuildStatusEnum.REBUILT_STALE : DrawableDB.DrawableDbBuildStatusEnum.COMPLETE;
            try {
                getDrawableDB().insertOrUpdateDataSource(dataSourceObjId, datasourceDrawableDBStatus);
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, String.format("Error updating datasources table (data source object ID = %d, status = %s)", dataSourceObjId, datasourceDrawableDBStatus.toString(), ex)); //NON-NLS
            }
            updateMessage("");
            updateProgress(-1.0);

            getDrawableDB().freeFileMetaDataCache();
            // at the end of the task, set the stale status based on the
            // cumulative status of all data sources
            controller.setModelIsStale(controller.isDataSourcesTableStale());
        }

    }

    /**
     * Returns a ProgressHandle.
     * 
     * @return A new ProgressHandle.
     */
    private ProgressHandle getInitialProgressHandle() {
        return ProgressHandle.createHandle(Bundle.DrawableFileUpdateTask_populatingDb_status(), this);
    }
}
