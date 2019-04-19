/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.test;

import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A data source ingest module that associates custom artifacts and attributes
 * with data sources for test purposes.
 */
@NbBundle.Messages({
    "CustomArtifactsCreatorDataSourceIngestModule.exceptionMessage.errorCreatingCustomType=Error creating custom artifact type."
})
public class CustomArtifactsCreatorDataSourceIngestModule extends DataSourceIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(CustomArtifactsCreatorDataSourceIngestModule.class.getName());

    /**
     * Adds the custom artifact type this module uses to the case database of
     * the current case.
     *
     * @param context Provides data and services specific to the ingest job and
     *                the ingest pipeline of which the module is a part.
     *
     * @throws IngestModuleException If there is an error adding the custom
     *                               artifact type.
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            CustomArtifactType.addToCaseDatabase();
        } catch (Blackboard.BlackboardException | NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.CustomArtifactsCreatorDataSourceIngestModule_exceptionMessage_errorCreatingCustomType(), ex);
        }
    }

    /**
     * Creates a custom artifact instance associated with the data source to be
     * processed.
     *
     * @param dataSource  The data source to process.
     * @param progressBar A progress bar to be used to report progress.
     *
     * @return A result code indicating success or failure of the processing.
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        try {
            CustomArtifactType.createInstance(dataSource);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to process data source (obj_id = %d)", dataSource.getId()), ex);
            return ProcessResult.ERROR;
        }
        return ProcessResult.OK;
    }

}
