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

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Settings for the Plaso Ingest Module.
 */
public class PlasoModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    /** Map from parser name (or match pattern) to its enabled state.
     *
     */
    Map<String, Boolean> parsers = new HashMap<>();

    Map<String, Boolean> getParsers() {
        return ImmutableMap.copyOf(parsers);
    }

    public PlasoModuleSettings() {
        parsers.put("winreg", Boolean.FALSE);
        parsers.put("pe", Boolean.FALSE);

        parsers.put("chrome_preferences", Boolean.FALSE);
        parsers.put("chrome_cache", Boolean.FALSE);
        parsers.put("chrome_27_history", Boolean.FALSE);
        parsers.put("chrome_8_history", Boolean.FALSE);
        parsers.put("chrome_cookies", Boolean.FALSE);
        parsers.put("chrome_extension_activity", Boolean.FALSE);

        parsers.put("firefox_cache", Boolean.FALSE);
        parsers.put("firefox_cache2", Boolean.FALSE);
        parsers.put("firefox_cookies", Boolean.FALSE);
        parsers.put("firefox_downloads", Boolean.FALSE);
        parsers.put("firefox_history", Boolean.FALSE);

        parsers.put("msiecf", Boolean.FALSE);
        parsers.put("msie_webcache", Boolean.FALSE);
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
