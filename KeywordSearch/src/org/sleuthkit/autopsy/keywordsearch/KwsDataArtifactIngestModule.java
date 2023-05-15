/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.logging.Level;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataArtifactIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A data artifact ingest module that indexes text for keyword search. All
 * keyword searching of indexed text, whether from files, data artifacts, or
 * analysis results, including the final keyword search of an ingest job, is
 * done in the last instance of the companion keyword search file ingest module.
 */
public class KwsDataArtifactIngestModule implements DataArtifactIngestModule {

    private static final Logger LOGGER = Logger.getLogger(KeywordSearchIngestModule.class.getName());
    private static final int TSK_ASSOCIATED_OBJECT_TYPE_ID = BlackboardArtifact.Type.TSK_ASSOCIATED_OBJECT.getTypeID();
    private IngestJobContext context;
    private KeywordSearchService searchService;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
    }

    @Override
    public ProcessResult process(DataArtifact artifact) {
        try {
            if (artifact.getType().getTypeID() != TSK_ASSOCIATED_OBJECT_TYPE_ID) {
                searchService.index(artifact);
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error indexing data artifact '%s' (job ID=%d)", artifact, context.getJobId()), ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        return ProcessResult.OK;
    }

}
