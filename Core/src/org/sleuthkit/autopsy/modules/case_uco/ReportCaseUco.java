/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.case_uco;

import java.util.logging.Level;
import javax.swing.JPanel;
import java.sql.SQLException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.*;

/**
 * ReportCaseUco generates a report in the CASE-UCO format. It saves basic file
 * info like full caseDirPath, name, MIME type, times, and hash.
 */
public class ReportCaseUco implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportCaseUco.class.getName());
    private static ReportCaseUco instance = null;
    private ReportCaseUcoConfigPanel configPanel;

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
    
    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getName.text");
        return name;
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

    @Override
    public JPanel getConfigurationPanel() {
        try {
            configPanel = new ReportCaseUcoConfigPanel();
        } catch (NoCurrentCaseException | TskCoreException | SQLException ex) {
            logger.log(Level.SEVERE, "Failed to initialize CASE-UCO settings panel", ex); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.ReportCaseUco_notInitialized());
            configPanel = null;
        }
        return configPanel;
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

        if (configPanel == null) {
            logger.log(Level.SEVERE, "CASE-UCO settings panel has not been initialized"); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.ReportCaseUco_notInitialized());
            progressPanel.complete(ReportStatus.ERROR);
            return;
        }

        Long selectedDataSourceId = configPanel.getSelectedDataSourceId();
        if (selectedDataSourceId == ReportCaseUcoConfigPanel.NO_DATA_SOURCE_SELECTED) {
            logger.log(Level.SEVERE, "No data source selected for CASE-UCO report"); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.ReportCaseUco_noDataSourceSelected());
            progressPanel.complete(ReportStatus.ERROR);
            return;
        }

        String reportPath = baseReportDir + getRelativeFilePath();
        CaseUcoFormatExporter.generateReport(selectedDataSourceId, reportPath, progressPanel);
    }
}
