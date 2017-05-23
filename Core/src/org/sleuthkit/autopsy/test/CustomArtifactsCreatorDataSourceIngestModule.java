/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A data source ingest module that associates custom artifacts and attributes with
 * data sources for test purposes.
 */
@NbBundle.Messages({
    "CustomArtifactsCreatorDataSourceIngestModule.exceptionMessage.errorCreatingCustomType=Error creating custom artifact type."
})
public class CustomArtifactsCreatorDataSourceIngestModule extends DataSourceIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(CustomArtifactsCreatorDataSourceIngestModule.class.getName());

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            CustomArtifactType.addToCaseDatabase();
        } catch (Blackboard.BlackboardException ex) {
            throw new IngestModuleException(Bundle.CustomArtifactsCreatorDataSourceIngestModule_exceptionMessage_errorCreatingCustomType(), ex);
        }
    }

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
