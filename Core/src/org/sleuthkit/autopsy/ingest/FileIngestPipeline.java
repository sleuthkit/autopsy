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
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A file ingest pipeline composed of a sequence of file ingest modules
 * constructed from ingest module templates.
 */
final class FileIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJobContext context;
    private final List<FileIngestModuleDecorator> modules = new ArrayList<>();

    FileIngestPipeline(IngestJobContext context, List<IngestModuleTemplate> moduleTemplates) {
        this.context = context;

        // Create an ingest module instance from each file ingest module 
        // template. Put the modules in a map of module class names to module 
        // instances to facilitate loading the modules into the pipeline in the 
        // sequence indicated by the ordered list of module class names that 
        // will be obtained from the file ingest pipeline configuration.
        Map<String, FileIngestModuleDecorator> modulesByClass = new HashMap<>();
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isFileIngestModuleTemplate()) {
                FileIngestModuleDecorator module = new FileIngestModuleDecorator(template.createFileIngestModule(), template.getModuleName());
                modulesByClass.put(module.getClassName(), module);
            }
        }

        // Add the ingest modules to the pipeline in the order indicated by the 
        // data source ingest pipeline configuration, adding any additional 
        // modules found in the global lookup, but not mentioned in the 
        // configuration, to the end of the pipeline in arbitrary order.
        List<String> pipelineConfig = IngestPipelinesConfiguration.getInstance().getFileIngestPipelineConfig();
        for (String moduleClassName : pipelineConfig) {
            if (modulesByClass.containsKey(moduleClassName)) {
                modules.add(modulesByClass.remove(moduleClassName));
            }
        }
        for (FileIngestModuleDecorator module : modulesByClass.values()) {
            modules.add(module);
        }
    }

    boolean isEmpty() {
        return modules.isEmpty();
    }

    /**
     * Start up all of the modules in the pipeline. 
     * @return List of errors or empty list if no errors
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (FileIngestModuleDecorator module : modules) {
            try {
                module.startUp(context);
            } catch (Exception ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    /**
     * Process the file down the pipeline of modules.
     * Startup must have been called before this is called.
     * 
     * @param file File to analyze
     * @return List of errors or empty list if no errors
     */
    List<IngestModuleError> process(FileIngestTask task, long threadId) {
        List<IngestModuleError> errors = new ArrayList<>();
        AbstractFile file = task.getFile();
        for (FileIngestModuleDecorator module : modules) {
            try {
                ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                module.process(file);
            } catch (Exception ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
            if (context.isJobCancelled()) {
                break;
            }
        }
        file.close();
        if (!context.isJobCancelled()) {
            IngestManager.getInstance().fireFileIngestDone(file.getId());
        }
        ingestManager.setIngestTaskProgressCompleted(task);
        return errors;
    }

    List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (FileIngestModuleDecorator module : modules) {
            try {
                module.shutDown();
            } catch (Exception ex) {
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    private static final class FileIngestModuleDecorator implements FileIngestModule {

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
        public void shutDown() {
            module.shutDown();
        }
    }
}
