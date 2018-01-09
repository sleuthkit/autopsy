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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for interesting files identifier ingest modules.
 */
final class FilesIdentifierIngestJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private Set<String> enabledFilesSetNames = new HashSet<>();
    private Set<String> disabledFilesSetNames = new HashSet<>();

    /**
     * Construct the ingest job settings for an interesting files identifier
     * ingest module.
     *
     * @param enabledFilesSetNames The names of the interesting files sets that
     *                             are enabled for the ingest job.
     */
    FilesIdentifierIngestJobSettings(List<String> enabledFilesSetNames) {
        this(enabledFilesSetNames, new ArrayList<>());
    }

    /**
     * Construct the ingest job settings for an interesting files identifier
     * ingest module.
     *
     * @param enabledFilesSetNames  The names of the interesting files sets that
     *                              are enabled for the ingest job.
     * @param disabledFilesSetNames The names of the interesting files sets that
     *                              are disabled for the ingest job.
     */
    FilesIdentifierIngestJobSettings(List<String> enabledFilesSetNames, List<String> disabledFilesSetNames) {
        this.enabledFilesSetNames = new HashSet<>(enabledFilesSetNames);
        this.disabledFilesSetNames = new HashSet<>(disabledFilesSetNames);
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        return FilesIdentifierIngestJobSettings.serialVersionUID;
    }

    /**
     * Determines whether or not an interesting files set definition is enabled
     * for an ingest job. If there is no setting for the requested files set, it
     * is deemed to be enabled.
     *
     * @param filesSetName The name of the files set definition to check.
     *
     * @return True if the file set is enabled, false otherwise.
     */
    boolean interestingFilesSetIsEnabled(String filesSetName) {
        return !(this.disabledFilesSetNames.contains(filesSetName));
    }

    /**
     * Get the names of all explicitly enabled interesting files set
     * definitions.
     *
     * @return The list of names.
     */
    List<String> getNamesOfEnabledInterestingFilesSets() {
        return new ArrayList<>(this.enabledFilesSetNames);
    }

    /**
     * Get the names of all explicitly disabled interesting files set
     * definitions.
     *
     * @return The list of names.
     */
    List<String> getNamesOfDisabledInterestingFilesSets() {
        return new ArrayList<>(disabledFilesSetNames);
    }

}
