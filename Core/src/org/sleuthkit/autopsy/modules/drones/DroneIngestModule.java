/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.drones;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;

/**
 * Drone file ingest module.
 *
 */
public final class DroneIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(DroneIngestModule.class.getName());
    final private List<DroneExtractor> extractors;

    private IngestJobContext context;

    /**
     * Construct a new drone ingest module.
     */
    DroneIngestModule() {
        extractors = new ArrayList<>();
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        extractors.add(new DATExtractor());
    }

    @Messages({
        "# {0} - AbstractFileName",
        "DroneIngestModule_process_start=Started {0}"
    })
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        ProcessResult processResult = ProcessResult.OK;

        IngestServices services = IngestServices.getInstance();

        services.postMessage(IngestMessage.createMessage(
                IngestMessage.MessageType.INFO,
                DroneIngestModuleFactory.getModuleName(),
                Bundle.DroneIngestModule_process_start(dataSource.getName())));

        progressBar.switchToIndeterminate();

        for (DroneExtractor extractor : extractors) {

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, "Drone ingest has been canceled, quitting before {0}", extractor.getName()); //NON-NLS
                break;
            }

            progressBar.progress(extractor.getName());

            try {
                extractor.process(dataSource, context, progressBar);
            } catch (DroneIngestException ex) {
                logger.log(Level.SEVERE, String.format("Exception thrown from drone extractor %s", extractor.getName()), ex);
            }
        }

        return processResult;
    }

}
