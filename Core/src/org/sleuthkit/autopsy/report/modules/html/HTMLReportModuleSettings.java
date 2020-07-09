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
package org.sleuthkit.autopsy.report.modules.html;

import org.sleuthkit.autopsy.report.ReportModuleSettings;

/**
 * Settings for the HTML report module.
 */
class HTMLReportModuleSettings implements ReportModuleSettings {
    
    private static final long serialVersionUID = 1L;
    
    private String header;
    private String footer;
    
    HTMLReportModuleSettings() {
        this.header = "";
        this.footer = "";
    }
    
    HTMLReportModuleSettings(String header, String footer) {
        this.header = header;
        this.footer = footer;
    }
    
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }
    
    /**
     * @return the header
     */
    String getHeader() {
        return header;
    }

    /**
     * @param header the header to set
     */
    void setHeader(String header) {
        this.header = header;
    }

    /**
     * @return the footer
     */
    String getFooter() {
        return footer;
    }

    /**
     * @param footer the footer to set
     */
    void setFooter(String footer) {
        this.footer = footer;
    }
}