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
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file ingest module that associates custom artifacts and attributes with
 * files for test purposes.
 */
@NbBundle.Messages({
    "CustomArtifactsCreatorFileIngestModule.exceptionMessage.errorCreatingCustomType=Error creating custom artifact type."
})
final class CustomArtifactsCreatorFileIngestModule extends FileIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(CustomArtifactsCreatorFileIngestModule.class.getName());

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
            throw new IngestModuleException(Bundle.CustomArtifactsCreatorFileIngestModule_exceptionMessage_errorCreatingCustomType(), ex);
        }
    }

    /**
     * Creates a custom artifact instance associated with the file to be
     * processed.
     *
     * @param file The file to be processed.
     *
     * @return A result code indicating success or failure of the processing.
     */
    @Override
    public ProcessResult process(AbstractFile file) {
        if (file.isDir() || file.isVirtual()) {
            return ProcessResult.OK;
        }
        try {
            CustomArtifactType.createInstance(file);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to process file (obj_id = %d)", file.getId()), ex);
            return ProcessResult.ERROR;
        }
        return ProcessResult.OK;
    }

}
