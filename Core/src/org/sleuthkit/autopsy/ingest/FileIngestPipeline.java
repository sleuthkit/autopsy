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
import org.sleuthkit.datamodel.AbstractFile;

/**
 * This class manages a sequence of file ingest modules. It starts them, 
 * shuts them down, and runs a file through them. 
 */
final class FileIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJob job;
    private final List<FileIngestModuleDecorator> modules = new ArrayList<>();

    FileIngestPipeline(IngestJob job, List<IngestModuleTemplate> moduleTemplates) {
        this.job = job;

        // Create an ingest module instance from each file ingest module 
        // template. 
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isFileIngestModuleTemplate()) {
                FileIngestModuleDecorator module = new FileIngestModuleDecorator(template.createFileIngestModule(), template.getModuleName());
                modules.add(module);
            }
        }
    }

    boolean isEmpty() {
        return this.modules.isEmpty();
    }

    /**
     * Start up all of the modules in the pipeline. 
     * @return List of errors or empty list if no errors
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (FileIngestModuleDecorator module : this.modules) {
            try {
                module.startUp(new IngestJobContext(this.job));
            } catch (Throwable ex) { // Catch-all exception firewall
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
    List<IngestModuleError> process(FileIngestTask task) {
        List<IngestModuleError> errors = new ArrayList<>();
        AbstractFile file = task.getFile();
        for (FileIngestModuleDecorator module : this.modules) {
            try {
                FileIngestPipeline.ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                module.process(file);
            } catch (Throwable ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
            if (this.job.isCancelled()) {
                break;
            }
        }
        file.close();
        if (!this.job.isCancelled()) {
            IngestManager.getInstance().fireFileIngestDone(file);
        }
        FileIngestPipeline.ingestManager.setIngestTaskProgressCompleted(task);
        return errors;
    }

    List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (FileIngestModuleDecorator module : this.modules) {
            try {
                module.shutDown();
            } catch (Throwable ex) { // Catch-all exception firewall
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
