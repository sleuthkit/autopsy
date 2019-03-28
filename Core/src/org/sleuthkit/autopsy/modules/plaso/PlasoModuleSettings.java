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
package org.sleuthkit.autopsy.modules.plaso;

import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 *
 */
public class PlasoModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    Map<String, Boolean> parsers = new HashMap<>();

    public PlasoModuleSettings() {
    }

    /**
     * Gets the serialization version number.
     *
     * @return A serialization version number.
     */
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    void setParserEnabled(String parserName, boolean selected) {
        parsers.put(parserName, selected);
    }
}
