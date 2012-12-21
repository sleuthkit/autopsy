/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import javax.swing.JPanel;

public interface GeneralReportModule extends ReportModule {
    
    /**
     * Generate the report and update the report's ProgressPanel, then save the report
     * to the reportPath.
     * 
     * @param reportPath path to save the report
     * @param progressPanel panel to update the report's progress
     */
    public void generateReport(String reportPath, ReportProgressPanel progressPanel);
    
    /**
     * Returns the configuration panel for the report, which is displayed in
     * the report configuration step of the report wizard.
     * 
     * @return the report's configuration panel
     */
    public JPanel getConfigurationPanel();
    
}
