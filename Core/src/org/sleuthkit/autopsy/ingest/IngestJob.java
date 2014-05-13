/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

final class IngestJob {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private final long id;
    private final Content rootDataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final LinkedBlockingQueue<DataSourceIngestPipeline> dataSourceIngestPipelines = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines = new LinkedBlockingQueue<>();
    private long estimatedFilesToProcess = 0L; // Guarded by this
    private long processedFiles = 0L; // Guarded by this
    private ProgressHandle dataSourceTasksProgress;
    private ProgressHandle fileTasksProgress;
    private volatile boolean cancelled = false;

    IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.rootDataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
    }

    long getId() {
        return id;
    }

    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    List<IngestModuleError> startUp() throws InterruptedException {
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            startFileIngestProgressBar();
            startDataSourceIngestProgressBar();
        }
        return errors;
    }

    private List<IngestModuleError> startUpIngestPipelines() throws InterruptedException {
        IngestJobContext context = new IngestJobContext(this);
        List<IngestModuleError> errors = new ArrayList<>();

        int numberOfPipelines = IngestManager.getInstance().getNumberOfDataSourceIngestThreads();
        for (int i = 0; i < numberOfPipelines; ++i) {
            DataSourceIngestPipeline pipeline = new DataSourceIngestPipeline(context, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            dataSourceIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                // No need to accumulate presumably redundant errors.
                break;
            }
        }

        numberOfPipelines = IngestManager.getInstance().getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfPipelines; ++i) {
            FileIngestPipeline pipeline = new FileIngestPipeline(context, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            fileIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                // No need to accumulate presumably redundant errors.
                break;
            }
        }

        logIngestModuleErrors(errors);
        return errors; // Returned so UI can report to user.
    }

    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.displayName",
                rootDataSource.getName());
        dataSourceTasksProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                if (dataSourceTasksProgress != null) {
                    dataSourceTasksProgress.setDisplayName(
                            NbBundle.getMessage(this.getClass(),
                            "IngestJob.progress.cancelling",
                            displayName));
                }
                IngestJob.this.cancel();
                return true;
            }
        });
        dataSourceTasksProgress.start();
        dataSourceTasksProgress.switchToIndeterminate();
    }

    private void startFileIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.fileIngest.displayName",
                rootDataSource.getName());
        fileTasksProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                if (fileTasksProgress != null) {
                    fileTasksProgress.setDisplayName(
                            NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling",
                            displayName));
                }
                IngestJob.this.cancel();
                return true;
            }
        });
        estimatedFilesToProcess = rootDataSource.accept(new GetFilesCountVisitor());
        fileTasksProgress.start();
        fileTasksProgress.switchToDeterminate((int) estimatedFilesToProcess);
    }

    void process(Content dataSource) throws InterruptedException {
        // If the job is not cancelled, complete the task, otherwise just flush 
        // it. In either case, the task counter needs to be decremented and the
        // shut down check needs to occur.
        if (!isCancelled()) {
            List<IngestModuleError> errors = new ArrayList<>();
            DataSourceIngestPipeline pipeline = dataSourceIngestPipelines.take();
            errors.addAll(pipeline.process(dataSource, dataSourceTasksProgress));
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
            dataSourceIngestPipelines.put(pipeline);
        }
    }

    void process(AbstractFile file) throws InterruptedException {
        // If the job is not cancelled, complete the task, otherwise just flush 
        // it. In either case, the task counter needs to be decremented and the
        // shut down check needs to occur.
        if (!isCancelled()) {
            List<IngestModuleError> errors = new ArrayList<>();
            synchronized (this) {
                ++processedFiles;
                if (processedFiles <= estimatedFilesToProcess) {
                    fileTasksProgress.progress(file.getName(), (int) processedFiles);
                } else {
                    fileTasksProgress.progress(file.getName(), (int) estimatedFilesToProcess);
                }
            }
            FileIngestPipeline pipeline = fileIngestPipelines.take();
            errors.addAll(pipeline.process(file));
            fileIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
    }

    void shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        while (!dataSourceIngestPipelines.isEmpty()) {
            DataSourceIngestPipeline pipeline = dataSourceIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        while (!fileIngestPipelines.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        fileTasksProgress.finish();
        dataSourceTasksProgress.finish();
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }
    }

    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError()); //NON-NLS
        }
    }

    boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
        fileTasksProgress.finish(); 
        dataSourceTasksProgress.finish();
        IngestManager.getInstance().fireIngestJobCancelled(id);
    }
}
