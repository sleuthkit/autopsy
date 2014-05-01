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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Encapsulates a data source and the ingest module pipelines to be used to
 * ingest the data source.
 */
final class IngestJob {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final AtomicLong nextIngestJobId = new AtomicLong(0L);
    private static final ConcurrentHashMap<Long, IngestJob> ingestJobsById = new ConcurrentHashMap<>();
    private final long id;
    private final Content rootDataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final LinkedBlockingQueue<DataSourceIngestPipeline> dataSourceIngestPipelines = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines = new LinkedBlockingQueue<>();
    private final AtomicInteger tasksInProgress = new AtomicInteger(0);
    private final AtomicLong processedFiles = new AtomicLong(0L);
    private final AtomicLong filesToIngestEstimate = new AtomicLong(0L);
    private ProgressHandle dataSourceTasksProgress;
    private ProgressHandle fileTasksProgress;
    private volatile boolean cancelled;

    /**
     * Creates an ingest job for a data source and starts up the ingest
     * pipelines for the job.
     *
     * @param rootDataSource The data source to ingest.
     * @param ingestModuleTemplates The ingest module templates to use to create
     * the ingest pipelines for the job.
     * @param processUnallocatedSpace Whether or not the job should include
     * processing of unallocated space.
     * @return A collection of ingest module startUp up errors, empty on
     * success.
     * @throws InterruptedException
     */
    static List<IngestModuleError> startJob(Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) throws InterruptedException {
        long jobId = nextIngestJobId.incrementAndGet();
        IngestJob ingestJob = new IngestJob(jobId, dataSource, ingestModuleTemplates, processUnallocatedSpace);
        List<IngestModuleError> errors = ingestJob.startUp();
        if (errors.isEmpty()) {
            ingestJobsById.put(jobId, ingestJob);
            IngestManager.getInstance().fireIngestJobStarted(jobId);
        }
        return errors;
    }

    static boolean jobsAreRunning() {
        for (IngestJob job : ingestJobsById.values()) {
            if (!job.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    static void addFileToJob(long ingestJobId, AbstractFile file) { // RJCTODO: Just one at a time?
        IngestJob job = ingestJobsById.get(ingestJobId);
        if (job != null) {
//            long adjustedFilesCount = job.filesToIngestEstimate.incrementAndGet(); // RJCTODO: Not the best name now?
//            job.fileTasksProgress.switchToIndeterminate(); // RJCTODO: Comment this stuff
//            job.fileTasksProgress.switchToDeterminate((int) adjustedFilesCount);
//            job.fileTasksProgress.progress(job.processedFiles.intValue());
            FileIngestTaskScheduler.getInstance().addTask(new FileIngestTask(job, file));
        }
    }

    static void cancelAllJobs() {
        for (IngestJob job : ingestJobsById.values()) {
            job.cancel();
        }
    }

    private IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.rootDataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.cancelled = false;
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
            startDataSourceIngestProgressBar();
            startFileIngestProgressBar();
            FileIngestTaskScheduler.getInstance().addTasks(this, rootDataSource); // RJCTODO: Think about this ordering "solution" for small images
            DataSourceIngestTaskScheduler.getInstance().addTask(new DataSourceIngestTask(this, rootDataSource));
        }
        return errors;
    }

    private List<IngestModuleError> startUpIngestPipelines() throws InterruptedException {
        IngestJobContext context = new IngestJobContext(this);
        List<IngestModuleError> errors = new ArrayList<>();

        int maxNumberOfPipelines = IngestManager.getMaxNumberOfDataSourceIngestThreads();
        for (int i = 0; i < maxNumberOfPipelines; ++i) {
            DataSourceIngestPipeline pipeline = new DataSourceIngestPipeline(context, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            dataSourceIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                // No need to accumulate presumably redundant errors.
                break;
            }
        }

        maxNumberOfPipelines = IngestManager.getMaxNumberOfFileIngestThreads();
        for (int i = 0; i < maxNumberOfPipelines; ++i) {
            FileIngestPipeline pipeline = new FileIngestPipeline(context, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            fileIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                // No need to accumulate presumably redundant errors.
                break;
            }
        }

        return errors;
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
        long initialFilesCount = rootDataSource.accept(new GetFilesCountVisitor());
        filesToIngestEstimate.getAndAdd(initialFilesCount);
        fileTasksProgress.start();
        fileTasksProgress.switchToDeterminate((int) initialFilesCount); // RJCTODO: This cast is troublesome, can use intValue
    }

    /**
     * Called by the ingest task schedulers when an ingest task is added to this
     * ingest job.
     */
    void notifyTaskAdded() {
        tasksInProgress.incrementAndGet();
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
        shutDownIfAllTasksCompleted();
    }

    void process(AbstractFile file) throws InterruptedException {
        // If the job is not cancelled, complete the task, otherwise just flush 
        // it. In either case, the task counter needs to be decremented and the
        // shut down check needs to occur.
        if (!isCancelled()) {
            List<IngestModuleError> errors = new ArrayList<>();
            FileIngestPipeline pipeline = fileIngestPipelines.take();
//            fileTasksProgress.progress(file.getName(), (int) processedFiles.incrementAndGet()); RJCTODO
            errors.addAll(pipeline.process(file));
            fileIngestPipelines.put(pipeline);
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
        shutDownIfAllTasksCompleted();
    }

    private void shutDownIfAllTasksCompleted() {
        if (tasksInProgress.decrementAndGet() == 0) {
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
            ingestJobsById.remove(id);
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
            IngestManager.getInstance().fireIngestJobCompleted(id);
        }
    }

    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError());
        }
    }

    boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
        fileTasksProgress.finish(); // RJCTODO: What about the other progress bar?
        IngestManager.getInstance().fireIngestJobCancelled(id);
    }
}
