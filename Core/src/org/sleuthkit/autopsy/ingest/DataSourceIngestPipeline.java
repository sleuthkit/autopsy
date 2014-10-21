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
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;

/**
 * This class manages a sequence of data source ingest modules. It starts them,
 * shuts them down, and runs them in sequential order.
 */
final class DataSourceIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJob job;
    private final List<DataSourceIngestModuleDecorator> modules = new ArrayList<>();

    DataSourceIngestPipeline(IngestJob job, List<IngestModuleTemplate> moduleTemplates) {
        this.job = job;

        // Create an ingest module instance from each data source ingest module 
        // template. Put the modules in a map of module class names to module 
        // instances to facilitate loading the modules into the pipeline in the 
        // sequence indicated by the ordered list of module class names that 
        // will be obtained from the data source ingest pipeline configuration.
        Map<String, DataSourceIngestModuleDecorator> modulesByClass = new HashMap<>();
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                DataSourceIngestModuleDecorator module = new DataSourceIngestModuleDecorator(template.createDataSourceIngestModule(), template.getModuleName());
                modulesByClass.put(module.getClassName(), module);
            }
        }

        // Add the ingest modules to the pipeline in the order indicated by the 
        // data source ingest pipeline configuration, adding any additional 
        // modules found in the global lookup, but not mentioned in the 
        // configuration, to the end of the pipeline in arbitrary order.
        List<String> pipelineConfig = IngestPipelinesConfiguration.getInstance().getDataSourceIngestPipelineConfig();
        for (String moduleClassName : pipelineConfig) {
            if (modulesByClass.containsKey(moduleClassName)) {
                modules.add(modulesByClass.remove(moduleClassName));
            }
        }
        for (DataSourceIngestModuleDecorator module : modulesByClass.values()) {
            modules.add(module);
        }
    }

    boolean isEmpty() {
        return modules.isEmpty();
    }

    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (DataSourceIngestModuleDecorator module : modules) {
            try {
                module.startUp(new IngestJobContext(this.job));
            } catch (Exception ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    List<IngestModuleError> process(DataSourceIngestTask task, ProgressHandle progress) {
        List<IngestModuleError> errors = new ArrayList<>();
        Content dataSource = task.getDataSource();
        for (DataSourceIngestModuleDecorator module : modules) {
            try {
                progress.setDisplayName(NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.dataSourceIngest.displayName",
                        module.getDisplayName(), dataSource.getName()));
                ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                module.process(dataSource, new DataSourceIngestModuleProgress(progress));
            } catch (Exception ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
            if (this.job.dataSourceIngestPipelineIsInterrupted()) {
                this.job.resumeDataSourceIngestPipeline();
            } else if (this.job.dataSourceIngestIsCancelled()) {
                break;
            }
        }
        ingestManager.setIngestTaskProgressCompleted(task);
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
        public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
            return module.process(dataSource, statusHelper);
        }
    }
}
