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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A pipeline of file ingest modules for performing file ingest tasks for an
 * ingest job.
 */
final class FileIngestPipeline extends IngestTaskPipeline<FileIngestTask> {

    private static final int FILE_BATCH_SIZE = 500;
    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJobPipeline ingestJobPipeline;
    private final List<AbstractFile> fileBatch;

    /**
     * Constructs a pipeline of file ingest modules for performing file ingest
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
    void prepareTask(FileIngestTask task) throws IngestTaskPipelineException {
    }

    @Override
    @NbBundle.Messages({
        "FileIngestPipeline_SaveResults_Activity=Saving Results"
    })
    void completeTask(FileIngestTask task) throws IngestTaskPipelineException {
        ingestManager.setIngestTaskProgress(task, Bundle.FileIngestPipeline_SaveResults_Activity());
        /*
         * Close and cache the file from the file ingest task. The cache will be
         * used for an eventual batch update of the case database with new
         * properties added to the files in the cache by the ingest modules that
         * processed them.
         *
         * Only one file ingest thread at a time will try to access the file
         * cache. The synchronization here is to ensure visibility of the files
         * in all of the threads that share the cache, rather than to prevent
         * simultaneous access in multiple threads.
         */
        synchronized (fileBatch) {
            AbstractFile file = null;
            try {
                file = task.getFile();
                file.close();
            } catch (TskCoreException ex) {
                throw new IngestTaskPipelineException(String.format("Failed to get file (file objId = %d)", task.getFileId()), ex); //NON-NLS
            }
            if (!ingestJobPipeline.isCancelled()) {
                fileBatch.add(file);
                if (fileBatch.size() >= FILE_BATCH_SIZE) {
                    clearFileCache();
                }
            }
            ingestManager.setIngestTaskProgressCompleted(task);
        }
    }

    @Override
    List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        if (!ingestJobPipeline.isCancelled()) {
            Date start = new Date();
            try {
                clearFileCache();
            } catch (IngestTaskPipelineException ex) {
                errors.add(new IngestModuleError(Bundle.FileIngestPipeline_SaveResults_Activity(), ex));
            }
            Date finish = new Date();
            ingestManager.incrementModuleRunTime(Bundle.FileIngestPipeline_SaveResults_Activity(), finish.getTime() - start.getTime());
        }
        errors.addAll(super.shutDown());
        return errors;
    }

    /**
     * Updates the case database with new properties added to the files in the
     * cache by the ingest modules that processed them.
     *
     * @throws IngestTaskPipelineException Exception thrown if the case database
     *                                     update fails.
     */
    private void clearFileCache() throws IngestTaskPipelineException {
        /*
         * Only one file ingest thread at a time will try to access the file
         * cache. The synchronization here is to ensure visibility of the files
         * in all of the threads that share the cache, rather than to prevent
         * simultaneous access in multiple threads.
         */
        synchronized (fileBatch) {
            CaseDbTransaction transaction = null;
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                transaction = caseDb.beginTransaction();
                for (AbstractFile file : fileBatch) {
                    if (!ingestJobPipeline.isCancelled()) {
                        file.save(transaction);
                    }
                }
                transaction.commit();
                if (!ingestJobPipeline.isCancelled()) {
                    for (AbstractFile file : fileBatch) {
                        IngestManager.getInstance().fireFileIngestDone(file);
                    }
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (TskCoreException ignored) {
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
        void performTask(IngestJobPipeline ingestJobPipeline, FileIngestTask task) throws IngestModuleException {
            AbstractFile file = null;
            try {
                file = task.getFile();
            } catch (TskCoreException ex) {
                throw new IngestModuleException(String.format("Failed to get file (file objId = %d)", task.getFileId()), ex); //NON-NLS
            }
            ingestManager.setIngestTaskProgress(task, getDisplayName());
            ingestJobPipeline.setCurrentFileIngestModule(getDisplayName(), file.getName());
            ProcessResult result = module.process(file);
            // See JIRA-7449
//            if (result == ProcessResult.ERROR) {
//                throw new IngestModuleException(String.format("%s experienced an error analyzing %s (file objId = %d)", getDisplayName(), file.getName(), file.getId())); //NON-NLS
//            }
        }

    }

}
