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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Abstract base class for all Drone file extractors.
 */
abstract class DroneExtractor {

    static private final String TEMP_FOLDER_NAME = "DroneExtractor"; //NON-NLS
    private final Case currentCase;

    /**
     * Common constructor. Subclasses should call super in their constructor.
     *
     * @throws DroneIngestException
     */
    protected DroneExtractor() throws DroneIngestException {
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new DroneIngestException("Unable to create drone extractor, no open case.", ex); //NON-NLS
        }
    }

    abstract void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) throws DroneIngestException;

    abstract String getName();

    /**
     * Return the current case object.
     *
     * @return Current case
     */
    final protected Case getCurrentCase() {
        return currentCase;
    }

    /**
     * Return the current SleuthkitCase.
     *
     * @return Current sleuthkit case
     */
    final protected SleuthkitCase getSleuthkitCase() {
        return currentCase.getSleuthkitCase();
    }

    /**
     * Build the temp path and create the directory if it does not currently
     * exist.
     *
     * @param currentCase   Currently open case
     * @param extractorName Name of extractor
     *
     * @return Path of the temp directory for this module
     */
    protected Path getExtractorTempPath() {
        Path path = Paths.get(currentCase.getTempDirectory(), TEMP_FOLDER_NAME, this.getClass().getCanonicalName());
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return path;
    }

    /**
     * Create a copy of file in the case temp directory.
     *
     * @param context Current ingest context
     * @param file    File to be copied
     *
     * @return File copy.
     *
     * @throws DroneIngestException
     */
    protected File getTemporaryFile(IngestJobContext context, AbstractFile file) throws DroneIngestException {
        String tempFileName = file.getName() + file.getId() + file.getNameExtension();

        Path tempFilePath = Paths.get(getExtractorTempPath().toString(), tempFileName);

        try {
            ContentUtils.writeToFile(file, tempFilePath.toFile(), context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new DroneIngestException(String.format("Unable to create temp file %s for abstract file %s objectID: %d", tempFilePath.toString(), file.getName(), file.getId()), ex); //NON-NLS
        }

        return tempFilePath.toFile();
    }

}
