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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final AtomicLong nextIngestJobId = new AtomicLong(0L);
    private static final ConcurrentHashMap<Long, IngestJob> ingestJobsById = new ConcurrentHashMap<>();
    private static final IngestScheduler taskScheduler = IngestScheduler.getInstance();
    private final long id;
    private final Content dataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines = new LinkedBlockingQueue<>();
    private long estimatedFilesToProcess = 0L; // Guarded by this
    private long processedFiles = 0L; // Guarded by this
    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private ProgressHandle dataSourceTasksProgress;
    private ProgressHandle fileTasksProgress;
    private volatile boolean cancelled = false;

    /**
     * Creates an ingest job for a data source.
     *
     * @param dataSource The data source to ingest.
     * @param ingestModuleTemplates The ingest module templates to use to create
     * the ingest pipelines for the job.
     * @param processUnallocatedSpace Whether or not the job should include
     * processing of unallocated space.
     * @return A collection of ingest module start up errors, empty on success.
     * @throws InterruptedException
     */
    static List<IngestModuleError> startIngestJob(Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) throws InterruptedException {
        long jobId = nextIngestJobId.incrementAndGet();
        IngestJob job = new IngestJob(jobId, dataSource, ingestModuleTemplates, processUnallocatedSpace);
        ingestJobsById.put(jobId, job);
        List<IngestModuleError> errors = job.start();
        if (errors.isEmpty()) {
            IngestManager.getInstance().fireIngestJobStarted(jobId);
            taskScheduler.scheduleTasksForIngestJob(job, dataSource);
        } else {
            ingestJobsById.remove(jobId);
        }
        return errors;
    }

    static boolean ingestJobsAreRunning() {
        for (IngestJob job : ingestJobsById.values()) {
            if (!job.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    static void cancelAllIngestJobs() {
        for (IngestJob job : ingestJobsById.values()) {
            job.cancel();
        }
    }

    IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
    }

    long getId() {
        return id;
    }

    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    private List<IngestModuleError> start() throws InterruptedException {
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            startFileIngestProgressBar();
            startDataSourceIngestProgressBar();
        }
        return errors;
    }

    private List<IngestModuleError> startUpIngestPipelines() throws InterruptedException {
        IngestJobContext context = new IngestJobContext(this);

        dataSourceIngestPipeline = new DataSourceIngestPipeline(context, ingestModuleTemplates);
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(dataSourceIngestPipeline.startUp());

        int numberOfPipelines = IngestManager.getInstance().getNumberOfFileIngestThreads();
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
        return errors;
    }

    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.initialDisplayName",
                dataSource.getName());
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
                dataSource.getName());
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
        estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
        fileTasksProgress.start();
        fileTasksProgress.switchToDeterminate((int) estimatedFilesToProcess);
    }

    void process(DataSourceIngestTask task) throws InterruptedException {
        if (!isCancelled()) {
            List<IngestModuleError> errors = new ArrayList<>();
            errors.addAll(dataSourceIngestPipeline.process(task.getDataSource(), dataSourceTasksProgress));
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
        
        // Because there is only one data source task per job, it is o.k. to 
        // call ProgressHandle.finish() now that the data source ingest modules 
        // are through using it via the DataSourceIngestModuleProgress wrapper.
        // Calling ProgressHandle.finish() again in finish() will be harmless.
        dataSourceTasksProgress.finish(); 
        
        if (taskScheduler.isLastTaskForIngestJob(task)) {
            finish();
        }
    }

    void process(FileIngestTask task) throws InterruptedException {
        if (!isCancelled()) {
            AbstractFile file = task.getFile();
            synchronized (this) {
                ++processedFiles;
                if (processedFiles <= estimatedFilesToProcess) {
                    fileTasksProgress.progress(file.getName(), (int) processedFiles);
                } else {
                    fileTasksProgress.progress(file.getName(), (int) estimatedFilesToProcess);
                }
            }
            FileIngestPipeline pipeline = fileIngestPipelines.take();
            List<IngestModuleError> errors = new ArrayList<>();
            errors.addAll(pipeline.process(file));
            fileIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
        if (taskScheduler.isLastTaskForIngestJob(task)) {
            finish();
        }
    }

    private void finish() {
        List<IngestModuleError> errors = new ArrayList<>();
        while (!fileIngestPipelines.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }
        dataSourceTasksProgress.finish();
        fileTasksProgress.finish();
        ingestJobsById.remove(id);
        if (!isCancelled()) {
            IngestManager.getInstance().fireIngestJobCompleted(id);
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

    private void cancel() {
        cancelled = true;
        IngestManager.getInstance().fireIngestJobCancelled(id);
    }
}
