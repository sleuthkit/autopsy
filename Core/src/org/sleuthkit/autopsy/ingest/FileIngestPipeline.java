/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A pipeline of file ingest modules for executing file ingest tasks for an
 * ingest job.
 */
@NbBundle.Messages({
    "FileIngestPipeline_SaveResults_Activity=Saving Results"
})
final class FileIngestPipeline extends IngestTaskPipeline<FileIngestTask> {

    private static final int FILE_BATCH_SIZE = 500;
    private static final String SAVE_RESULTS_ACTIVITY = Bundle.FileIngestPipeline_SaveResults_Activity();
    private static final Logger logger = Logger.getLogger(FileIngestPipeline.class.getName());
    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJobPipeline ingestJobPipeline;
    private final List<AbstractFile> fileBatch;

    /**
     * Constructs a pipeline of file ingest modules for executing file ingest
     * tasks for an ingest job.
     *
     * @param ingestJobPipeline The ingest job pipeline that owns this pipeline.
     * @param moduleTemplates   The ingest module templates that define this
     *                          pipeline.
     */
    FileIngestPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobPipeline, moduleTemplates);
        this.ingestJobPipeline = ingestJobPipeline;
        fileBatch = new ArrayList<>();
    }

    @Override
    Optional<IngestTaskPipeline.PipelineModule<FileIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestTaskPipeline.PipelineModule<FileIngestTask>> module = Optional.empty();
        if (template.isFileIngestModuleTemplate()) {
            FileIngestModule ingestModule = template.createFileIngestModule();
            module = Optional.of(new FileIngestPipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(FileIngestTask task) throws IngestTaskPipelineException {
    }

    @Override
    void cleanUpAfterTask(FileIngestTask task) throws IngestTaskPipelineException {
        try {
            ingestManager.setIngestTaskProgress(task, SAVE_RESULTS_ACTIVITY);
            AbstractFile file = task.getFile();
            file.close();
            cacheFileForBatchUpdate(file);
        } catch (TskCoreException ex) {
            throw new IngestTaskPipelineException(String.format("Failed to get file (file objId = %d)", task.getFileId()), ex); //NON-NLS
        } finally {
            ingestManager.setIngestTaskProgressCompleted(task);
        }
    }

    @Override
    List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        Date start = new Date();
        try {
            updateBatchedFiles();
        } catch (IngestTaskPipelineException ex) {
            errors.add(new IngestModuleError(SAVE_RESULTS_ACTIVITY, ex));
        }
        Date finish = new Date();
        ingestManager.incrementModuleRunTime(SAVE_RESULTS_ACTIVITY, finish.getTime() - start.getTime());
        errors.addAll(super.shutDown());
        return errors;
    }

    /**
     * Adds a file to a file cache used to update the case database with any new
     * properties added to the files in the cache by the ingest modules that
     * processed them. If adding the file to the cache fills the cache, a batch
     * update is done immediately.
     *
     * @param file The file.
     *
     * @throws IngestTaskPipelineException if the case database update fails.
     */
    private void cacheFileForBatchUpdate(AbstractFile file) throws IngestTaskPipelineException {
        /*
         * Only one file ingest thread at a time will try to access the file
         * cache. The synchronization here is to ensure visibility of the files
         * in all of the threads that share the cache, rather than to prevent
         * simultaneous access in multiple threads.
         */
        synchronized (fileBatch) {
            fileBatch.add(file);
            if (fileBatch.size() >= FILE_BATCH_SIZE) {
                updateBatchedFiles();
            }
        }
    }

    /**
     * Updates the case database with new properties added to the files in the
     * cache by the ingest modules that processed them.
     *
     * @throws IngestTaskPipelineException if the case database update fails.
     */
    private void updateBatchedFiles() throws IngestTaskPipelineException {
        /*
         * Only one file ingest thread at a time will try to access the file
         * cache. The synchronization here is to ensure visibility of the files
         * in all of the threads that share the cache, rather than to prevent
         * simultaneous access in multiple threads.
         */
        synchronized (fileBatch) {
            CaseDbTransaction transaction = null;
            try {
                if (!ingestJobPipeline.isCancelled()) {
                    Case currentCase = Case.getCurrentCaseThrows();
                    SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                    transaction = caseDb.beginTransaction();
                    for (AbstractFile file : fileBatch) {
                        file.save(transaction);
                    }
                    transaction.commit();
                    for (AbstractFile file : fileBatch) {
                        IngestManager.getInstance().fireFileIngestDone(file);
                    }
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (TskCoreException ex1) {
                        logger.log(Level.SEVERE, "Error rolling back transaction after failure to save updated properties for cached files from tasks", ex1);
                    }
                }
                throw new IngestTaskPipelineException("Failed to save updated properties for cached files from tasks", ex); //NON-NLS                
            } finally {
                fileBatch.clear();
            }
        }
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a file ingest
     * module.
     */
    static final class FileIngestPipelineModule extends IngestTaskPipeline.PipelineModule<FileIngestTask> {

        private final FileIngestModule module;

        /**
         * Constructs a wrapper that adds ingest infrastructure operations to a
         * file ingest module.
         *
         *
         * @param module      The module.
         * @param displayName The display name of the module.
         */
        FileIngestPipelineModule(FileIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        @Override
        void executeTask(IngestJobPipeline ingestJobPipeline, FileIngestTask task) throws IngestModuleException {
            AbstractFile file = null;
            try {
                file = task.getFile();
            } catch (TskCoreException ex) {
                throw new IngestModuleException(String.format("Failed to get file (file objId = %d)", task.getFileId()), ex); //NON-NLS
            }
            ingestManager.setIngestTaskProgress(task, getDisplayName());
            module.process(file);
        }

    }

}
