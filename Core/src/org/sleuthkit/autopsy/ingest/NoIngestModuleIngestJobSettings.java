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
package org.sleuthkit.autopsy.ingest;

/**
 * Implementation of the IngestModuleOptions interface for use by ingest modules
 * that do not have per ingest job options.
 */
public final class NoIngestModuleIngestJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private final String setting = "None"; //NON-NLS

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Gets the string used as an ingest options placeholder for serialization
     * purposes.
     *
     * @return The string "None"
     */
    String getSetting() {
        return setting;
    }
}
