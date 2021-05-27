/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.ingestmodule;

import java.util.concurrent.atomic.AtomicLong;
import org.sleuthkit.autopsy.ingest.DataArtifactIngestModule;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * A placeholder data artifact ingest module that counts the number of data
 * artifacts it processes and posts the final count to the ingest inbox.
 */
public class CentralRepoDataArtifactIngestModule implements DataArtifactIngestModule {

    private final AtomicLong artifactCounter = new AtomicLong();

    @Override
    public ProcessResult process(DataArtifact artifact) {
        artifactCounter.incrementAndGet();
        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        IngestServices.getInstance().postMessage(IngestMessage.createMessage(
                IngestMessage.MessageType.INFO,
                CentralRepoIngestModuleFactory.getModuleName(),
                String.format("%d data artifacts processed", artifactCounter.get()))); //NON-NLS 
    }

}
