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
package org.sleuthkit.autopsy.report.modules.stix;

import org.sleuthkit.autopsy.report.ReportModuleSettings;

/**
 * Settings for the STIX report module.
 */
class STIXReportModuleSettings  implements ReportModuleSettings {
    
    private static final long serialVersionUID = 1L;
    
    private final String stixFile;
    private final boolean showAllResults;
    
    STIXReportModuleSettings() {
        stixFile = null;
        showAllResults = false;
    }
    
    STIXReportModuleSettings(String stixFile, boolean showAllResults) {
        this.stixFile = stixFile;
        this.showAllResults = showAllResults;
    }
    
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * @return the stixFile
     */
    String getStixFile() {
        return stixFile;
    }

    /**
     * @return the showAllResults
     */
    boolean isShowAllResults() {
        return showAllResults;
    }
    
}
