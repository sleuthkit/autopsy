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
import java.util.HashMap;
import java.util.List;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;

/**
 * Encapsulates a data source and the ingest module pipelines to be used to
 * ingest the data source.
 */
final class IngestJob {

    private final long id;
    private final Content dataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final HashMap<Long, FileIngestPipeline> fileIngestPipelines = new HashMap<>();
    private final HashMap<Long, DataSourceIngestPipeline> dataSourceIngestPipelines = new HashMap<>();
    private final IngestScheduler.FileIngestScheduler fileScheduler = IngestScheduler.getInstance().getFileIngestScheduler();
    private FileIngestPipeline initialFileIngestPipeline = null;
    private DataSourceIngestPipeline initialDataSourceIngestPipeline = null;
    private ProgressHandle dataSourceTaskProgress;
    private ProgressHandle fileTasksProgress;
    int totalEnqueuedFiles = 0;
    private int processedFiles = 0;
    private volatile boolean cancelled;

    IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.cancelled = false;
    }

    long getId() {
        return id;
    }

    Content getDataSource() {
        return dataSource;
    }

    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    synchronized List<IngestModuleError> startUpIngestPipelines() {
        startDataSourceIngestProgressBar();
        startFileIngestProgressBar();
        return startUpInitialIngestPipelines();
    }

    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle
                .getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.displayName", this.dataSource.getName());
        dataSourceTaskProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                if (dataSourceTaskProgress != null) {
                    dataSourceTaskProgress.setDisplayName(NbBundle.getMessage(this.getClass(),
                            "IngestJob.progress.cancelling",
                            displayName));
                }
                IngestManager.getInstance().cancelIngestJobs();
                return true;
            }
        });
        dataSourceTaskProgress.start();
        dataSourceTaskProgress.switchToIndeterminate();
    }

    private void startFileIngestProgressBar() {
        final String displayName = NbBundle
                .getMessage(this.getClass(), "IngestJob.progress.fileIngest.displayName", this.dataSource.getName());
        fileTasksProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                if (fileTasksProgress != null) {
                    fileTasksProgress.setDisplayName(
                            NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling",
                            displayName));
                }
                IngestManager.getInstance().cancelIngestJobs();
                return true;
            }
        });
        fileTasksProgress.start();
        fileTasksProgress.switchToIndeterminate();
        totalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
        fileTasksProgress.switchToDeterminate(totalEnqueuedFiles);
    }

    private List<IngestModuleError> startUpInitialIngestPipelines() {
        // Create a per thread instance of each pipeline type right now to make 
        // (reasonably) sure that the ingest modules can be started.
        initialDataSourceIngestPipeline = new DataSourceIngestPipeline(this, ingestModuleTemplates);
        initialFileIngestPipeline = new FileIngestPipeline(this, ingestModuleTemplates);
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(initialDataSourceIngestPipeline.startUp());
        errors.addAll(initialFileIngestPipeline.startUp());
        return errors;
    }

    synchronized DataSourceIngestPipeline getDataSourceIngestPipelineForThread(long threadId) {
        DataSourceIngestPipeline pipeline;
        if (initialDataSourceIngestPipeline != null) {
            pipeline = initialDataSourceIngestPipeline;
            initialDataSourceIngestPipeline = null;
            dataSourceIngestPipelines.put(threadId, pipeline);
        } else if (!dataSourceIngestPipelines.containsKey(threadId)) {
            pipeline = new DataSourceIngestPipeline(this, ingestModuleTemplates);
            pipeline.startUp();
            dataSourceIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = dataSourceIngestPipelines.get(threadId);
        }
        return pipeline;
    }

    synchronized FileIngestPipeline getFileIngestPipelineForThread(long threadId) {
        FileIngestPipeline pipeline;
        if (initialFileIngestPipeline != null) {
            pipeline = initialFileIngestPipeline;
            initialFileIngestPipeline = null;
            fileIngestPipelines.put(threadId, pipeline);
        } else if (!fileIngestPipelines.containsKey(threadId)) {
            pipeline = new FileIngestPipeline(this, ingestModuleTemplates);
            pipeline.startUp();
            fileIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = fileIngestPipelines.get(threadId);
        }
        return pipeline;
    }

    synchronized List<IngestModuleError> releaseIngestPipelinesForThread(long threadId) {
        List<IngestModuleError> errors = new ArrayList<>();
        
        DataSourceIngestPipeline dataSourceIngestPipeline = dataSourceIngestPipelines.get(threadId);
        if (dataSourceIngestPipeline != null) {
            errors.addAll(dataSourceIngestPipeline.shutDown(cancelled));
            dataSourceIngestPipelines.remove(threadId);
        }
        if (initialDataSourceIngestPipeline == null && dataSourceIngestPipelines.isEmpty() && dataSourceTaskProgress != null) {
            dataSourceTaskProgress.finish();
            dataSourceTaskProgress = null;
        }

        FileIngestPipeline fileIngestPipeline = fileIngestPipelines.get(threadId);
        if (fileIngestPipeline != null) {
            errors.addAll(fileIngestPipeline.shutDown(cancelled));
            fileIngestPipelines.remove(threadId);
        }
        if (initialFileIngestPipeline == null && fileIngestPipelines.isEmpty() && fileTasksProgress != null) {
            fileTasksProgress.finish();
            fileTasksProgress = null;
        }

        return errors;
    }

    synchronized boolean areIngestPipelinesShutDown() {
        return (initialDataSourceIngestPipeline == null
                && dataSourceIngestPipelines.isEmpty()
                && initialFileIngestPipeline == null
                && fileIngestPipelines.isEmpty());
    }

    synchronized ProgressHandle getDataSourceTaskProgressBar() {
        return this.dataSourceTaskProgress;
    }

    synchronized void updateFileTasksProgressBar(String currentFileName) {
        int newTotalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
        if (newTotalEnqueuedFiles > totalEnqueuedFiles) {
            totalEnqueuedFiles = newTotalEnqueuedFiles + 1;
            fileTasksProgress.switchToIndeterminate();
            fileTasksProgress.switchToDeterminate(totalEnqueuedFiles);
        }
        if (processedFiles < totalEnqueuedFiles) {
            ++processedFiles;
        }

        fileTasksProgress.progress(currentFileName, processedFiles);
    }

    synchronized void cancel() {
        if (initialDataSourceIngestPipeline != null) {
            initialDataSourceIngestPipeline.shutDown(true);
            initialDataSourceIngestPipeline =  null;
        }
        if (initialFileIngestPipeline != null) {
            initialFileIngestPipeline.shutDown(true);
            initialFileIngestPipeline = null;
        }
       
        cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }
}
