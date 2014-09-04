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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for interesting files identifier ingest modules.
 */
final class FilesIdentifierIngestJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private final Set<String> enabledFilesSetNames = new HashSet<>();

    /**
     * Construct the ingest job settings for an interesting files identifier
     * ingest module.
     *
     * @param enabledFilesSetNames The names of the interesting files sets
     * that are enabled for the ingest job.
     */
    FilesIdentifierIngestJobSettings(List<String> enabledFilesSetNames) {
        this.enabledFilesSetNames.addAll(enabledFilesSetNames);
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
     * for an ingest job.
     *
     * @param filesSetName The name of the files set definition to check.
     * @return True if the file set is enabled, false otherwise.
     */
    boolean isInterestingFilesSetEnabled(String filesSetName) {
        return (this.enabledFilesSetNames.contains(filesSetName));
    }
}
