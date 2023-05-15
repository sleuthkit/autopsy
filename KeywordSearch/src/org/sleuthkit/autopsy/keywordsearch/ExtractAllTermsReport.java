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
package org.sleuthkit.autopsy.keywordsearch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.keywordsearch.infastructure.NoReportConfigurationPanel;

/**
 * Instances of this class plug in to the reporting infrastructure to provide a
 * convenient way to extract all unique terms from Solr index.
 */
@ServiceProvider(service = GeneralReportModule.class)
public class ExtractAllTermsReport implements GeneralReportModule {
    
    private static final Logger logger = Logger.getLogger(ExtractAllTermsReport.class.getName());
    private static final String OUTPUT_FILE_NAME = "Unique Words.txt";

    @NbBundle.Messages({
        "ExtractAllTermsReport.getName.text=Extract Unique Words"})
    @Override
    public String getName() {
        return Bundle.ExtractAllTermsReport_getName_text();
    }
    
    @NbBundle.Messages({
        "ExtractAllTermsReport.error.noOpenCase=No currently open case.",
        "# {0} - Keyword search commit frequency",
        "ExtractAllTermsReport.search.noFilesInIdxMsg=No files are in index yet. Try again later. Index is updated every {0} minutes.",
        "ExtractAllTermsReport.search.noFilesInIdxMsg2=No files are in index yet. Try again later",
        "ExtractAllTermsReport.search.searchIngestInProgressTitle=Keyword Search Ingest in Progress",
        "ExtractAllTermsReport.search.ingestInProgressBody=<html>Keyword Search Ingest is currently running.<br />Not all files have been indexed and unique word extraction might yield incomplete results.<br />Do you want to proceed with unique word extraction anyway?</html>",
        "ExtractAllTermsReport.startExport=Starting Unique Word Extraction",
        "ExtractAllTermsReport.export.error=Error During Unique Word Extraction",
        "ExtractAllTermsReport.exportComplete=Unique Word Extraction Complete"
    })
    @Override
    public void generateReport(GeneralReportSettings settings, ReportProgressPanel progressPanel) {
        
        if (!Case.isCaseOpen()) {
            logger.log(Level.SEVERE, "No open case when attempting to run {0} report", Bundle.ExtractAllTermsReport_getName_text()); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.ExtractAllTermsReport_error_noOpenCase());
            return;
        }
        
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel("Extracting unique words...");

        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();

        int filesIndexed = 0;
            try { // see if there are any indexed files
                filesIndexed = KeywordSearch.getServer().queryNumIndexedFiles();
            } catch (KeywordSearchModuleException | NoOpenCoreException ignored) {
            }

        if (filesIndexed == 0) {
            if (isIngestRunning) {
                progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.ExtractAllTermsReport_search_noFilesInIdxMsg(KeywordSearchSettings.getUpdateFrequency().getTime()));
            } else {
                progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.ExtractAllTermsReport_search_noFilesInIdxMsg2());
            }
            progressPanel.setIndeterminate(false);
            return;
        }

        // check if keyword search module ingest is running (indexing, etc)
        if (isIngestRunning) {
            if (KeywordSearchUtil.displayConfirmDialog(Bundle.ExtractAllTermsReport_search_searchIngestInProgressTitle(),
                    Bundle.ExtractAllTermsReport_search_ingestInProgressBody(), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) == false) {
                progressPanel.cancel();
                return;
            }
        }

        final Server server = KeywordSearch.getServer();
        try {
            progressPanel.updateStatusLabel(Bundle.ExtractAllTermsReport_startExport());
            Path outputFile = Paths.get(settings.getReportDirectoryPath(), getRelativeFilePath());
            server.extractAllTermsForDataSource(outputFile, progressPanel);
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            logger.log(Level.SEVERE, "Exception while extracting unique terms", ex); //NON-NLS
            progressPanel.setIndeterminate(false);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.ExtractAllTermsReport_export_error());
            return;
        }

        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE, Bundle.ExtractAllTermsReport_exportComplete());
    }

    @Override
    public boolean supportsDataSourceSelection() {
        return false;
    }

    @NbBundle.Messages({
        "ExtractAllTermsReport.description.text=Extracts all unique words out of the current case. NOTE: The extracted words are lower-cased."})
    @Override
    public String getDescription() {
        return Bundle.ExtractAllTermsReport_description_text();
    }
    @Override
    public JPanel getConfigurationPanel() {
        return new NoReportConfigurationPanel();
    }

    @Override
    public String getRelativeFilePath() {
        return OUTPUT_FILE_NAME;
    }

}
