/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2018-2019 Basis Technology Corp.
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.report.modules.caseuco;

import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;

/**
 * ReportCaseUco generates a report in the CASE-UCO format. It saves basic file
 * info like full caseDirPath, name, MIME type, times, and hash.
 */
public final class ReportCaseUco implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportCaseUco.class.getName());
    private static ReportCaseUco instance = null;

    private static final String REPORT_FILE_NAME = "CASE_UCO_output.json-ld";

    // Hidden constructor for the report
    private ReportCaseUco() {
    }

    // Get the default implementation of this report
    public static synchronized ReportCaseUco getDefault() {
        if (instance == null) {
            instance = new ReportCaseUco();
        }
        return instance;
    }

    /**
     * Get default configuration for this report module.
     *
     * @return Object which contains default report module settings.
     */
    @Override
    public ReportModuleSettings getDefaultConfiguration() {
        // This module does not have configuration
        return new NoReportModuleSettings();
    }

    /**
     * Get current configuration for this report module.
     *
     * @return Object which contains current report module settings.
     */
    @Override
    public ReportModuleSettings getConfiguration() {
        // This module does not have configuration
        return new NoReportModuleSettings();
    }

    /**
     * Set report module configuration.
     *
     * @param settings Object which contains report module settings.
     */
    @Override
    public void setConfiguration(ReportModuleSettings settings) {
        // This module does not have configuration
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getName.text");
        return name;
    }
    
    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }

    @Override
    public String getRelativeFilePath() {
        return REPORT_FILE_NAME;
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getDesc.text");
        return desc;
    }

    /**
     * Returns CASE-UCO report file name
     *
     * @return the REPORT_FILE_NAME
     */
    public static String getReportFileName() {
        return REPORT_FILE_NAME;
    }

    /**
     * Generates a CASE-UCO format report.
     *
     * @param baseReportDir caseDirPath to save the report
     * @param progressPanel panel to update the report's progress
     */
    @NbBundle.Messages({
        "ReportCaseUco.notInitialized=CASE-UCO settings panel has not been initialized",
        "ReportCaseUco.noDataSourceSelected=No data source selected for CASE-UCO report"
    })
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        String reportPath = baseReportDir + getRelativeFilePath();
        CaseUcoFormatExporter.generateReport(reportPath, progressPanel);
    }
}
