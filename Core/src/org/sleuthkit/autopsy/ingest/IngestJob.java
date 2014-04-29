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
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Encapsulates a data source and the ingest module pipelines to be used to
 * ingest the data source.
 */
final class IngestJob {

    private static final AtomicLong nextIngestJobId = new AtomicLong(0L);
    private static final ConcurrentHashMap<Long, IngestJob> ingestJobs = new ConcurrentHashMap<>(); // Maps job ids to jobs.
    private final long jobId;
    private final Content dataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final LinkedBlockingQueue<DataSourceIngestPipeline> dataSourceIngestPipelines = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines = new LinkedBlockingQueue<>();
    private final AtomicInteger tasksInProgress = new AtomicInteger(0);
    private final AtomicLong processedFiles = new AtomicLong(0L);
    private ProgressHandle dataSourceTasksProgress;
    private ProgressHandle fileTasksProgress;
    private long filesToIngestEstimate = 0;
    private volatile boolean cancelled;

    static List<IngestModuleError> startIngestJob(Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) { // RJCTODO: return errors
        long jobId = nextIngestJobId.incrementAndGet();
        IngestJob ingestJob = new IngestJob(jobId, dataSource, ingestModuleTemplates, processUnallocatedSpace);
        List<IngestModuleError> errors = ingestJob.start();
        if (errors.isEmpty()) {
            ingestJobs.put(jobId, ingestJob);
        }
        return errors;
    }

    static boolean jobsAreRunning() {
        for (IngestJob job : ingestJobs.values()) {
            if (!job.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    static void addFileToIngestJob(long ingestJobId, AbstractFile file) { // RJCTODO: Move back to IngestManager
        IngestJob job = ingestJobs.get(ingestJobId);
        if (job != null) {
            FileIngestTaskScheduler.getInstance().addTask(job, file);
        }
    }

    static void cancelAllIngestJobs() {
        for (IngestJob job : ingestJobs.values()) {
            job.cancel();
        }
    }

    private IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.jobId = id;
        this.dataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.cancelled = false;
    }

    long getJobId() {
        return jobId;
    }

    Content getDataSource() {
        return dataSource;
    }

    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    List<IngestModuleError> start() {
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            DataSourceIngestTaskScheduler.getInstance().addTask(new DataSourceIngestTask(this, dataSource));
            FileIngestTaskScheduler.getInstance().addTasks(this, dataSource);
            startDataSourceIngestProgressBar();
            startFileIngestProgressBar();
        }
        return errors;
    }

    private List<IngestModuleError> startUpIngestPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();

        int maxNumberOfPipelines = IngestManager.getMaxNumberOfDataSourceIngestThreads();
        for (int i = 0; i < maxNumberOfPipelines; ++i) {
            DataSourceIngestPipeline pipeline = new DataSourceIngestPipeline(this, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            try {
                dataSourceIngestPipelines.put(pipeline);
            } catch (InterruptedException ex) {
                // RJCTODO: log unexpected block and interrupt, or throw
            }
            if (errors.isEmpty()) {
                // No need to accumulate presumably redundant erros.
                break;
            }
        }

        maxNumberOfPipelines = IngestManager.getMaxNumberOfFileIngestThreads();
        for (int i = 0; i < maxNumberOfPipelines; ++i) {
            FileIngestPipeline pipeline = new FileIngestPipeline(this, ingestModuleTemplates);
            errors.addAll(pipeline.startUp());
            try {
                fileIngestPipelines.put(pipeline);
            } catch (InterruptedException ex) {
                // RJCTODO: log unexpected block and interrupt, or throw
            }
            if (errors.isEmpty()) {
                // No need to accumulate presumably redundant erros.
                break;
            }
        }

        return errors;
    }

    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.displayName",
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
        dataSourceTasksProgress.switchToIndeterminate(); // RJCTODO: check out the logic in the pipleine class
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
        filesToIngestEstimate = dataSource.accept(new GetFilesCountVisitor());
        fileTasksProgress.start();
        fileTasksProgress.switchToDeterminate((int) filesToIngestEstimate);
    }

    /**
     * Called by the ingest task schedulers when an ingest task for this ingest
     * job is added to the scheduler's task queue.
     */
    void notifyTaskScheduled() {
        // Increment the task counter when a task is scheduled so that there is 
        // a persistent record of the task's existence even after it is removed
        // from the scheduler by an ingest thread. The task counter is used by
        // the job to determine when it is done.
        tasksInProgress.incrementAndGet();
    }

    /**
     * Called by the ingest schedulers as an "undo" operation for
     * notifyTaskScheduled().
     */
    void notifyTaskCompleted() {
        // Decrement the task counter when a task is discarded by a scheduler.  
        // The task counter is used by the job to determine when it is done.
        tasksInProgress.decrementAndGet();
    }

    void process() throws InterruptedException {
        if (!isCancelled()) {
            try {
                DataSourceIngestPipeline pipeline = dataSourceIngestPipelines.take();
                pipeline.process(); // RJCTODO: Pass data source through?
                dataSourceIngestPipelines.put(pipeline);
            } catch (InterruptedException ex) {
                // RJCTODO:
            }
        }
        ifCompletedShutDown();
    }

    void process(AbstractFile file) {
        if (!isCancelled()) {
            try {
                FileIngestPipeline pipeline = fileIngestPipelines.take();
                fileTasksProgress.progress(file.getName(), (int) processedFiles.incrementAndGet());
                pipeline.process(file);
                fileIngestPipelines.put(pipeline);
            } catch (InterruptedException ex) {
                // RJCTODO: Log block and interrupt
            }
        }
        ifCompletedShutDown();
    }

    void ifCompletedShutDown() {
        if (tasksInProgress.decrementAndGet() == 0) {
            while (!dataSourceIngestPipelines.isEmpty()) {
                DataSourceIngestPipeline pipeline = dataSourceIngestPipelines.poll();
                pipeline.shutDown(cancelled);
            }
            while (!fileIngestPipelines.isEmpty()) {
                FileIngestPipeline pipeline = fileIngestPipelines.poll();
                pipeline.shutDown(cancelled);
            }
            ingestJobs.remove(jobId);
            IngestManager.getInstance().fireIngestJobCompleted(jobId);        
        }
    }

    ProgressHandle getDataSourceTaskProgressBar() {
        return dataSourceTasksProgress; // RJCTODO: Should just pass the progress handle or the object to the pipeline
    }

    boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
        fileTasksProgress.finish();
        IngestManager.getInstance().fireIngestJobCancelled(jobId);
    }
}
