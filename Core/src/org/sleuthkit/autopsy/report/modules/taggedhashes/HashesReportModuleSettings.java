/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.taggedhashes;

import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.report.ReportModuleSettings;

/**
 * Settings for the Tagged Hashes report module.
 */
class HashesReportModuleSettings implements ReportModuleSettings {

    private static final long serialVersionUID = 1L;
    private final boolean exportAllTags;
    private final HashDb hashDb;

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Default configuration for the Tagged Hashes report module.
     */
    HashesReportModuleSettings() {
        exportAllTags = true;
        hashDb = null;
    }

    /**
     * Configuration for the Tagged Hashes report module.
     *
     * @param exportAllTags Flag whether to export all tags.
     * @param hashDb Selected HashDb object to export to
     */
    HashesReportModuleSettings(boolean exportAllTags, HashDb hashDb) {
        this.exportAllTags = exportAllTags;
        this.hashDb = hashDb;
    }

    /**
     * Flag whether to export all tags.
     *
     * @return Flag whether to export all tags
     */
    boolean isExportAllTags() {
        return exportAllTags;
    }

    /**
     * @return the hashDb
     */
    public HashDb getHashDb() {
        return hashDb;
    }
}
