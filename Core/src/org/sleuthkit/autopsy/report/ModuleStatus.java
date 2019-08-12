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
package org.sleuthkit.autopsy.report;

import java.io.Serializable;

/**
 * Class for persisting information about a report module (e.g. whether the report
 * module is enabled).
 */
final class ModuleStatus implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String moduleName;
    private boolean enabled;

    /**
     * Creates ModuleStatus object.
     *
     * @param module Implementation of a ReportModule interface
     * @param enabled Boolean flag whether the module is enabled
     */
    ModuleStatus(ReportModule module, boolean enabled) {
        this.moduleName = module.getClass().getCanonicalName();
        this.enabled = enabled;
    }

    String getModuleClassName() {
        return moduleName;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled() {
        return this.enabled;
    }
}
