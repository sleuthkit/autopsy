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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * A file ingest pipeline composed of a sequence of file ingest modules
 * constructed from ingest module templates. The pipeline is specific to a
 * single ingest job.
 */
class FileIngestPipeline {

    private static final Logger logger = Logger.getLogger(FileIngestPipeline.class.getName());
    private final IngestJob ingestJob;
    private final List<IngestModuleTemplate> moduleTemplates;
    private List<FileIngestModule> modules = new ArrayList<>();

    FileIngestPipeline(IngestJob ingestJob, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJob = ingestJob;
        this.moduleTemplates = moduleTemplates;
    }

    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (IngestModuleTemplate template : moduleTemplates) {
            IngestModuleFactory factory = template.getIngestModuleFactory();
            if (factory.isFileIngestModuleFactory()) {
                IngestModuleSettings ingestOptions = template.getIngestOptions();
                FileIngestModule module = factory.createFileIngestModule(ingestOptions);
                IngestModuleContext context = new IngestModuleContext(this.ingestJob, factory);
                try {
                    module.startUp(context);
                    this.modules.add(module);
                    IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), factory.getModuleDisplayName());
                } catch (Exception ex) {
                    errors.add(new IngestModuleError(module.getContext().getModuleDisplayName(), ex));
                }
            }
        }
        return errors;
    }

    List<IngestModuleError> ingestFile(AbstractFile file) {
        List<IngestModuleError> errors = new ArrayList<>();
        Content dataSource = this.ingestJob.getDataSource();
        logger.log(Level.INFO, String.format("Ingesting {0} from {1}", file.getName(), dataSource.getName()));
        for (FileIngestModule module : this.modules) {
            try {
                module.process(file);
            } catch (Exception ex) {
                errors.add(new IngestModuleError(module.getContext().getModuleDisplayName(), ex));
            }
            IngestModuleContext context = module.getContext();
            if (context.isIngestJobCancelled()) {
                break;
            }
        }
        file.close();
        IngestManager.fireFileDone(file.getId());
        return errors;
    }

    List<IngestModuleError> shutDown(boolean ingestJobCancelled) {
        List<IngestModuleError> errors = new ArrayList<>();
        for (FileIngestModule module : this.modules) {
            try {
                module.shutDown(ingestJobCancelled);
            } catch (Exception ex) {
                errors.add(new IngestModuleError(module.getContext().getModuleDisplayName(), ex));
            } finally {
                IngestModuleContext context = module.getContext();
                IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), context.getModuleDisplayName());
            }
        }
        return errors;
    }
}
