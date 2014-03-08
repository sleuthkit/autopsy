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
 * Implementation of the IngestModuleResourcesConfig interface for use by ingest
 * modules that do not have ingest options.
 */
public final class NoResourcesConfiguration implements IngestModuleResourcesConfig {

    private final String configuration = "None";

    /**
     * Gets the string used as an resources configuration placeholder for
     * serialization purposes.
     *
     * @return The string "None"
     */
    String getConfig() {
        return configuration;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
