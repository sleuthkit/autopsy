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
import java.util.Map;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
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
    private FileIngestPipeline initialFileIngestPipeline = null;
    private DataSourceIngestPipeline initialDataSourceIngestPipeline = null;
    private boolean cancelled;

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

    synchronized void cancel() {
        cancelled = true;
    }

    synchronized boolean isCancelled() {
        return cancelled;
    }

    synchronized List<IngestModuleError> startUpIngestPipelines() {
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
        }
        this.dataSourceIngestPipelines.remove(threadId);

        FileIngestPipeline fileIngestPipeline = fileIngestPipelines.get(threadId);
        if (fileIngestPipeline != null) {
            errors.addAll(fileIngestPipeline.shutDown(cancelled));
        }
        this.fileIngestPipelines.remove(threadId);

        return errors;
    }

    synchronized boolean areIngestPipelinesShutDown() {
        return (dataSourceIngestPipelines.isEmpty() && fileIngestPipelines.isEmpty());
    }

    /**
     * A data source ingest pipeline composed of a sequence of data source ingest
     * modules constructed from ingest module templates.
     */
    static final class DataSourceIngestPipeline {

        private static final Logger logger = Logger.getLogger(DataSourceIngestPipeline.class.getName());
        private final IngestJob task;
        private final List<IngestModuleTemplate> moduleTemplates;
        private List<DataSourceIngestModuleDecorator> modules = new ArrayList<>();

        private DataSourceIngestPipeline(IngestJob task, List<IngestModuleTemplate> moduleTemplates) {
            this.task = task;
            this.moduleTemplates = moduleTemplates;
        }

        private List<IngestModuleError> startUp() {
            List<IngestModuleError> errors = new ArrayList<>();

            // Create an ingest module instance from each ingest module template 
            // that has an ingest module factory capable of making data source 
            // ingest modules. Map the module class names to the module instance 
            // to allow the modules to be put in the sequence indicated by the
            // ingest pipelines configuration.
            Map<String, DataSourceIngestModuleDecorator> modulesByClass = new HashMap<>();
            for (IngestModuleTemplate template : moduleTemplates) {
                IngestModuleFactory factory = template.getIngestModuleFactory();
                if (factory.isDataSourceIngestModuleFactory()) {
                    IngestModuleSettings ingestOptions = template.getIngestOptions();
                    DataSourceIngestModuleDecorator module = new DataSourceIngestModuleDecorator(factory.createDataSourceIngestModule(ingestOptions), factory.getModuleDisplayName());
                    IngestJobContext context = new IngestJobContext(task, factory);
                    try {
                        module.startUp(context);
                        modulesByClass.put(module.getClassName(), module);
                        IngestManager.fireModuleEvent(IngestManager.IngestModuleEvent.STARTED.toString(), factory.getModuleDisplayName());
                    } catch (Exception ex) {
                        errors.add(new IngestModuleError(module.getDisplayName(), ex));
                    }
                }
            }

            // Establish the module sequence of the core ingest modules 
            // indicated by the ingest pipeline configuration, adding any 
            // additional modules found in the global lookup to the end of the 
            // pipeline in arbitrary order.
            List<String> pipelineConfig = IngestPipelinesConfiguration.getInstance().getDataSourceIngestPipelineConfig();
            for (String moduleClassName : pipelineConfig) {
                if (modulesByClass.containsKey(moduleClassName)) {
                    modules.add(modulesByClass.remove(moduleClassName));
                }
            }
            for (DataSourceIngestModuleDecorator module : modulesByClass.values()) {
                modules.add(module);
            }

            return errors;
        }

        List<IngestModuleError> process(SwingWorker worker, ProgressHandle progress) {
            List<IngestModuleError> errors = new ArrayList<>();
            Content dataSource = this.task.getDataSource();
            logger.log(Level.INFO, "Processing data source {0}", dataSource.getName());
            for (DataSourceIngestModuleDecorator module : this.modules) {
                try {
                    progress.start();
                    progress.switchToIndeterminate();
                    module.process(dataSource, new DataSourceIngestModuleStatusHelper(worker, progress, dataSource));
                    progress.finish();
                } catch (Exception ex) {
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                }
                if (task.isCancelled()) {
                    break;
                }
            }
            return errors;
        }

        private List<IngestModuleError> shutDown(boolean ingestJobCancelled) {
            List<IngestModuleError> errors = new ArrayList<>();
            for (DataSourceIngestModuleDecorator module : this.modules) {
                try {
                    module.shutDown(ingestJobCancelled);
                } catch (Exception ex) {
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                } finally {
                    IngestManager.fireModuleEvent(IngestManager.IngestModuleEvent.COMPLETED.toString(), module.getDisplayName());
                }
            }
            return errors;
        }

        private static class DataSourceIngestModuleDecorator implements DataSourceIngestModule {

            private final DataSourceIngestModule module;
            private final String displayName;

            DataSourceIngestModuleDecorator(DataSourceIngestModule module, String displayName) {
                this.module = module;
                this.displayName = displayName;
            }

            String getClassName() {
                return module.getClass().getCanonicalName();
            }

            String getDisplayName() {
                return displayName;
            }

            @Override
            public void startUp(IngestJobContext context) throws IngestModuleException {
               module.startUp(context);
            }

            @Override
            public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleStatusHelper statusHelper) {
                return module.process(dataSource, statusHelper);
            }

            @Override
            public void shutDown(boolean ingestJobWasCancelled) {
                module.shutDown(ingestJobWasCancelled);
            }            
        }
    }

    /**
     * A file ingest pipeline composed of a sequence of file ingest modules
     * constructed from ingest module templates.
     */
    static final class FileIngestPipeline {

        private static final Logger logger = Logger.getLogger(FileIngestPipeline.class.getName());
        private final IngestJob task;
        private final List<IngestModuleTemplate> moduleTemplates;
        private List<FileIngestModuleDecorator> modules = new ArrayList<>();

        private FileIngestPipeline(IngestJob task, List<IngestModuleTemplate> moduleTemplates) {
            this.task = task;
            this.moduleTemplates = moduleTemplates;
        }

        private List<IngestModuleError> startUp() {
            List<IngestModuleError> errors = new ArrayList<>();
            
            // Create an ingest module instance from each ingest module template 
            // that has an ingest module factory capable of making data source 
            // ingest modules. Map the module class names to the module instance 
            // to allow the modules to be put in the sequence indicated by the
            // ingest pipelines configuration.
            Map<String, FileIngestModuleDecorator> modulesByClass = new HashMap<>();
            for (IngestModuleTemplate template : moduleTemplates) {
                IngestModuleFactory factory = template.getIngestModuleFactory();
                if (factory.isFileIngestModuleFactory()) {
                    IngestModuleSettings ingestOptions = template.getIngestOptions();
                    FileIngestModuleDecorator module = new FileIngestModuleDecorator(factory.createFileIngestModule(ingestOptions), factory.getModuleDisplayName());
                    IngestJobContext context = new IngestJobContext(task, factory);
                    try {
                        module.startUp(context);
                        modulesByClass.put(module.getClassName(), module);
                        IngestManager.fireModuleEvent(IngestManager.IngestModuleEvent.STARTED.toString(), factory.getModuleDisplayName());
                    } catch (Exception ex) {
                        errors.add(new IngestModuleError(module.getDisplayName(), ex));
                    }
                }
            }
            
            // Establish the module sequence of the core ingest modules 
            // indicated by the ingest pipeline configuration, adding any 
            // additional modules found in the global lookup to the end of the 
            // pipeline in arbitrary order.
            List<String> pipelineConfig = IngestPipelinesConfiguration.getInstance().getFileIngestPipelineConfig();
            for (String moduleClassName : pipelineConfig) {
                if (modulesByClass.containsKey(moduleClassName)) {
                    modules.add(modulesByClass.remove(moduleClassName));
                }
            }
            for (FileIngestModuleDecorator module : modulesByClass.values()) {
                modules.add(module);
            }
            
            return errors;
        }

        List<IngestModuleError> process(AbstractFile file) {
            List<IngestModuleError> errors = new ArrayList<>();
            Content dataSource = this.task.getDataSource();
            logger.log(Level.INFO, String.format("Processing {0} from {1}", file.getName(), dataSource.getName()));
            for (FileIngestModuleDecorator module : this.modules) {
                try {
                    module.process(file);
                } catch (Exception ex) {
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                }
                if (task.isCancelled()) {
                    break;
                }
            }
            file.close();
            IngestManager.fireFileDone(file.getId());
            return errors;
        }

        private List<IngestModuleError> shutDown(boolean ingestJobCancelled) {
            List<IngestModuleError> errors = new ArrayList<>();
            for (FileIngestModuleDecorator module : this.modules) {
                try {
                    module.shutDown(ingestJobCancelled);
                } catch (Exception ex) {
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                } finally {
                    IngestManager.fireModuleEvent(IngestManager.IngestModuleEvent.COMPLETED.toString(), module.getDisplayName());
                }
            }
            return errors;
        }
        
        private static class FileIngestModuleDecorator implements FileIngestModule {

            private final FileIngestModule module;
            private final String displayName;

            FileIngestModuleDecorator(FileIngestModule module, String displayName) {
                this.module = module;
                this.displayName = displayName;
            }

            String getClassName() {
                return module.getClass().getCanonicalName();
            }

            String getDisplayName() {
                return displayName;
            }

            @Override
            public void startUp(IngestJobContext context) throws IngestModuleException {
               module.startUp(context);
            }

            @Override
            public IngestModule.ProcessResult process(AbstractFile file) {
                return module.process(file);
            }

            @Override
            public void shutDown(boolean ingestJobWasCancelled) {
                module.shutDown(ingestJobWasCancelled);
            }            
        }
    }    
}
