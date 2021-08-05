/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

class DataSourceSummaryReport implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(DataSourceSummaryReport.class.getName());
    private static DataSourceSummaryReport instance;

    // Get the default instance of this report
    public static synchronized DataSourceSummaryReport getDefault() {
        if (instance == null) {
            instance = new DataSourceSummaryReport();
        }
        return instance;
    }

    // Hidden constructor
    private DataSourceSummaryReport() {
    }

    
    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "DataSourceSummaryReport.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return "DataSourceSummaryReport.xlsx"; //NON-NLS
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "DataSourceSummaryReport.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null; // ELTODO
    }
    
    @Override
    public boolean supportsDataSourceSelection() {
        return true;
    }
    
    @Override
    public void generateReport(GeneralReportSettings settings, ReportProgressPanel progressPanel) {
        Case currentCase = null;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        
        if(settings.getSelectedDataSources() == null) {
            // Process all data sources if the list is null.
            try {
                List<Long> selectedDataSources = currentCase.getDataSources()
                        .stream()
                        .map(Content::getId)
                        .collect(Collectors.toList());
                settings.setSelectedDataSources(selectedDataSources);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get the datasources from the case", ex);
                return;
            }
        }
        
        String baseReportDir = settings.getReportDirectoryPath();
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        //progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.querying"));
        String reportFullPath = baseReportDir + getRelativeFilePath(); //NON-NLS
        String errorMessage = "";

        //progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.loading"));

        ReportProgressPanel.ReportStatus result = ReportProgressPanel.ReportStatus.COMPLETE;
    }

}
