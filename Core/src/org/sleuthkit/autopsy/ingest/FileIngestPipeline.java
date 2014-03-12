/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
 * RJCTODO
 */
public class FileIngestPipeline {

    private static final Logger logger = Logger.getLogger(FileIngestPipeline.class.getName());
    private final DataSourceIngestJob ingestJob;
    private List<FileIngestModule> ingestModules = new ArrayList<>();

    FileIngestPipeline(DataSourceIngestJob ingestJob, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJob = ingestJob;
        for (IngestModuleTemplate template : moduleTemplates) {
            IngestModuleFactory factory = template.getIngestModuleFactory();
            if (factory.isFileIngestModuleFactory()) {
                IngestModuleIngestJobOptions ingestOptions = template.getIngestOptions();
                FileIngestModule module = factory.createFileIngestModule(ingestOptions);
                this.ingestModules.add(module);
                IngestModuleProcessingContext context = new IngestModuleProcessingContext(this.ingestJob, factory);
                module.startUp(context);
                IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), factory.getModuleDisplayName());
            }
        }
    }

    // RJCTODO: Put exception handlers in place
    void ingestFile(AbstractFile file) {
        Content dataSource = this.ingestJob.getDataSource();
        logger.log(Level.INFO, String.format("Ingesting {0} from {1}", file.getName(), dataSource.getName())); // RJCTODO: Is this a good use of time?
        for (FileIngestModule module : ingestModules) {
            // RJCTODO: stats.logFileModuleStartProcess(module);            
            module.process(file);
            // RJCTODO: stats.logFileModuleEndProcess(module);
            // RJCTODO: Add cancellation check; if cancelled, break out
        }
        file.close();
        IngestManager.fireFileDone(file.getId()); // RJCTODO: Fire for each file?               
    }

    void shutDown(boolean ingestJobCancelled) {
        for (FileIngestModule module : ingestModules) {
            module.shutDown(ingestJobCancelled);
//                            IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), s.getName());
            
        }
    }
}
