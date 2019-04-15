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

    /** Map from parser name (or match pattern) to its enabled state. */
    final Map<String, Boolean> parsers = new HashMap<>();

    /**
     * Get an immutable map from parser name to its enabled state. Parsers
     * mapped to true or with no entry will be enabled. Parsers mapped to false,
     * will be disabled.
     */
    Map<String, Boolean> getParsers() {
        return ImmutableMap.copyOf(parsers);
    }

    /**
     * Constructor. The PlasoModuleSettings will have the default parsers
     * (winreg, pe, chrome, firefox, internet explorer) disabled.
     */
    public PlasoModuleSettings() {
        parsers.put("winreg", false);
        parsers.put("pe", false);

        //chrome
        parsers.put("chrome_preferences", false);
        parsers.put("chrome_cache", false);
        parsers.put("chrome_27_history", false);
        parsers.put("chrome_8_history", false);
        parsers.put("chrome_cookies", false);
        parsers.put("chrome_extension_activity", false);

        //firefox
        parsers.put("firefox_cache", false);
        parsers.put("firefox_cache2", false);
        parsers.put("firefox_cookies", false);
        parsers.put("firefox_downloads", false);
        parsers.put("firefox_history", false);

        //Internet Explorer
        parsers.put("msiecf", false);
        parsers.put("msie_webcache", false);
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

    /**
     * Set the given parser enabled/disabled
     *
     * @param parserName The name of the parser to enable/disable
     * @param selected   The new state (enabled/disabled) for the given parser.
     */
    void setParserEnabled(String parserName, boolean selected) {
        parsers.put(parserName, selected);
    }
}
