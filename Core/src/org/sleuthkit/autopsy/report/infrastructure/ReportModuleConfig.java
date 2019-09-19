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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModule;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import java.io.Serializable;

/**
 * Class for persisting information about a report module (e.g. whether the
 * report module is enabled).
 */
final class ReportModuleConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String moduleName;
    private transient ReportModuleSettings settings; // these settings get serialized individually
    private boolean enabled;

    /**
     * Creates ReportModuleConfig object.
     *
     * @param module Implementation of a ReportModule interface
     * @param enabled Boolean flag whether the module is enabled
     */
    ReportModuleConfig(ReportModule module, boolean enabled) {
        this.moduleName = module.getClass().getCanonicalName();
        this.enabled = enabled;
        this.settings = new NoReportModuleSettings();
    }
    
    /**
     * Creates ReportModuleConfig object.
     *
     * @param module Implementation of a ReportModule interface
     * @param enabled Boolean flag whether the module is enabled
     * @param settings Report module settings object
     */
    ReportModuleConfig(ReportModule module, boolean enabled, ReportModuleSettings settings) {
        this.moduleName = module.getClass().getCanonicalName();
        this.enabled = enabled;
        this.settings = settings;
    }

    /**
     * Get full canonical report module name.
     *
     * @return Full canonical report module name.
     */
    String getModuleClassName() {
        return moduleName;
    }

    /**
     * Set flag whether the report module is enabled.
     *
     * @param enabled Flag whether the report module is enabled.
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Is report module enabled.
     *
     * @return True if the module is enabled, false otherwise.
     */
    boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Get settings object for the report module.
     *
     * @return the settings
     */
    ReportModuleSettings getModuleSettings() {
        return settings;
    }

    /**
     * Set settings for the report module.
     *
     * @param settings the settings to set
     */
    void setModuleSettings(ReportModuleSettings settings) {
        this.settings = settings;
    }
}
