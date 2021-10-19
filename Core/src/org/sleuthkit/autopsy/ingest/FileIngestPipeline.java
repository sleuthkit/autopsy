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
final class FileIngestPipeline extends IngestPipeline<FileIngestTask> {

    private static final int FILE_BATCH_SIZE = 500;
    private static final String SAVE_RESULTS_ACTIVITY = Bundle.FileIngestPipeline_SaveResults_Activity();
    private static final Logger logger = Logger.getLogger(FileIngestPipeline.class.getName());
    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJobExecutor ingestJobExecutor;
    private final List<AbstractFile> fileBatch;

    /**
     * Constructs a pipeline of file ingest modules for executing file ingest
     * tasks for an ingest job.
     *
     * @param ingestJobExecutor The ingest job executor for this pipeline.
     * @param moduleTemplates   The ingest module templates to be used to
     *                          construct the ingest modules for this pipeline.
     *                          May be an empty list if this type of pipeline is
     *                          not needed for the ingest job.
     */
    FileIngestPipeline(IngestJobExecutor ingestJobExecutor, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobExecutor, moduleTemplates);
        this.ingestJobExecutor = ingestJobExecutor;
        fileBatch = new ArrayList<>();
    }

    @Override
    Optional<IngestPipeline.PipelineModule<FileIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestPipeline.PipelineModule<FileIngestTask>> module = Optional.empty();
        if (template.isFileIngestModuleTemplate()) {
            FileIngestModule ingestModule = template.createFileIngestModule();
            module = Optional.of(new FileIngestPipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(FileIngestTask task) throws IngestPipelineException {
    }

    @Override
    void cleanUpAfterTask(FileIngestTask task) throws IngestPipelineException {
        try {
            ingestManager.setIngestTaskProgress(task, SAVE_RESULTS_ACTIVITY);
            AbstractFile file = task.getFile();
            file.close();
            cacheFileForBatchUpdate(file);
        } catch (TskCoreException ex) {
            throw new IngestPipelineException(String.format("Failed to get file (file objId = %d)", task.getFileId()), ex); //NON-NLS
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
        } catch (IngestPipelineException ex) {
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
     * @throws IngestPipelineException if the case database update fails.
     */
    private void cacheFileForBatchUpdate(AbstractFile file) throws IngestPipelineException {
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
     * @throws IngestPipelineException if the case database update fails.
     */
    private void updateBatchedFiles() throws IngestPipelineException {
        /*
         * Only one file ingest thread at a time will try to access the file
         * cache. The synchronization here is to ensure visibility of the files
         * in all of the threads that share the cache, rather than to prevent
         * simultaneous access in multiple threads.
         */
        synchronized (fileBatch) {
            CaseDbTransaction transaction = null;
            try {
                if (!ingestJobExecutor.isCancelled()) {
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
                throw new IngestPipelineException("Failed to save updated properties for cached files from tasks", ex); //NON-NLS                
            } finally {
                fileBatch.clear();
            }
        }
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a file ingest
     * module.
     */
    static final class FileIngestPipelineModule extends IngestPipeline.PipelineModule<FileIngestTask> {

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
        void process(IngestJobExecutor ingestJobExecutor, FileIngestTask task) throws IngestModuleException {
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
