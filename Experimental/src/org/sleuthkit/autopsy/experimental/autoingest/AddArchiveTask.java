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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/*
 * A runnable that adds an archive data source to the case database.
 */
public class AddArchiveTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddArchiveTask.class.getName());
    private final String deviceId;
    private final String archivePath;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private boolean criticalErrorOccurred;

    private static final String AUTO_INGEST_MODULE_OUTPUT_DIR = "AutoIngest";

    /**
     * Constructs a runnable task that adds an archive and data sources
     * contained in the archive to the case database.
     *
     * @param deviceId An ASCII-printable identifier for the device associated
     * with the data source that is intended to be unique across multiple cases
     * (e.g., a UUID).
     * @param archivePath Path to the archive file.
     * @param progressMonitor Progress monitor to report progress during
     * processing.
     * @param callback Callback to call when processing is done.
     */
    AddArchiveTask(String deviceId, String archivePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.archivePath = archivePath;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
    }

    /**
     * Adds the archive to the case database.
     */
    @Override
    public void run() {
        if (!ArchiveUtil.isArchive(Paths.get(archivePath))) {
            List<String> errorMessages = new ArrayList<>();
            errorMessages.add("Input data source is not a valid datasource: " + archivePath.toString());
            List<Content> newDataSources = new ArrayList<>();
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            callback.done(result, errorMessages, newDataSources);
        }

        // extract the archive and pass the extracted folder as input
        Path destinationFolder = Paths.get("");
        try {
            Case currentCase = Case.getCurrentCase();

            // get file name without extension
            String dataSourceFileNameNoExt = FilenameUtils.removeExtension(archivePath);

            // create folder to extract archive to
            destinationFolder = Paths.get(currentCase.getModuleDirectory(), dataSourceFileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
            destinationFolder.toFile().mkdirs();

            // extract contents of ZIP archive into destination folder            
            ArchiveUtil.unpackArchiveFile(archivePath, destinationFolder.toString());
        } catch (Exception ex) {
            //throw new AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException(NbBundle.getMessage(ArchiveExtractorDSProcessor.class, "ArchiveExtractorDataSourceProcessor.process.exception.text"), ex);
        }

        // do processing
        return;
    }

    /*
     * Attempts to cancel adding the archive to the case database.
     */
    public void cancelTask() {

    }
}
