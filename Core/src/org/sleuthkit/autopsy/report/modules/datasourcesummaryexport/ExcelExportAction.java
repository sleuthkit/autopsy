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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action that exports tab data to an excel workbook.
 */
@Messages({
    "ExcelExportAction_moduleName=Data Source Summary",})
class ExcelExportAction {

    private static final Logger logger = Logger.getLogger(ExcelExportAction.class.getName());

    /**
     * Main constructor.
     *
     * @param tabExports The different tabs that may have excel exports.
     */
    ExcelExportAction() {
    }

    /**
     * Generates an xlsx path for the data source summary export.
     *
     * @param dataSourceName The name of the data source.
     *
     * @return The file to which the excel document should be written or null if
     *         file already exists or cancellation.
     */
    @NbBundle.Messages({
        "ExcelExportAction_getXLSXPath_directory=DataSourceSummary",})
    File getXLSXPath(String dataSourceName, String baseReportDir) {
        // set initial path to reports directory with filename that is 
        // a combination of the data source name and time stamp
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        String fileName = String.format("%s-%s.xlsx", dataSourceName == null ? "" : FileUtil.escapeFileName(dataSourceName), dateFormat.format(new Date()));
        File reportsDirFile = Paths.get(baseReportDir, Bundle.ExcelExportAction_getXLSXPath_directory()).toFile();
        if (!reportsDirFile.exists()) {
            reportsDirFile.mkdirs();
        }

        return Paths.get(reportsDirFile.getAbsolutePath(), fileName).toFile();
    }

    /**
     * Action that handles updating progress and exporting data from the tabs.
     *
     * @param progressPanel The progress indicator.
     * @param dataSource    The data source to be exported.
     * @param path          The path of the excel export.
     *
     * @throws InterruptedException
     * @throws IOException
     * @throws ExcelExportException
     */
    @NbBundle.Messages({
        "ExcelExportAction_exportToXLSX_beginExport=Beginning Export...",
        "ExcelExportAction_exportToXLSX_gatheringRecentActivityData=Fetching Recent Activity Data",
        "ExcelExportAction_exportToXLSX_gatheringContainerData=Fetching Container & Image Data",
        "ExcelExportAction_exportToXLSX_gatheringTimelineData=Fetching Timeline Data",
        "ExcelExportAction_exportToXLSX_gatheringFileData=Fetching File and MIME Type Data",
        "ExcelExportAction_exportToXLSX_gatheringAnalysisData=Fetching Analysis Data",
        "ExcelExportAction_exportToXLSX_gatheringPastData=Fetching Historical Data",
        "ExcelExportAction_exportToXLSX_gatheringUserData=Fetching User Activity Data",
        "ExcelExportAction_exportToXLSX_gatheringGeoData=Fetching Geolocation Data",
        "ExcelExportAction_exportToXLSX_gatheringIngestData=Fetching Ingest History Data",
        "ExcelExportAction_exportToXLSX_writingToFile=Writing to File...",})

    void exportToXLSX(ReportProgressPanel progressPanel, DataSource dataSource, String baseReportDir)
            throws IOException, ExcelExport.ExcelExportException {

        File reportFile = getXLSXPath(dataSource.getName(), baseReportDir);
        int totalWeight = 11;
        int step = 1;
        progressPanel.setIndeterminate(false);
        progressPanel.setLabels(dataSource.getName(), reportFile.getPath());
        progressPanel.setMaximumProgress(totalWeight);
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_beginExport());
        List<ExcelExport.ExcelSheetExport> sheetExports = new ArrayList<>();

        // Export file and MIME type data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringFileData());
        progressPanel.setProgress(step);
        List<ExcelExport.ExcelSheetExport> exports = new ExportTypes().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export user activity
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringUserData());
        progressPanel.setProgress(++step);
        exports = new ExportUserActivity().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export Recent Activity data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringRecentActivityData());
        progressPanel.setProgress(++step);
        exports = new ExportRecentFiles().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export hash set hits, keyword hits, and interesting item hits
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringAnalysisData());
        progressPanel.setProgress(++step);
        exports = new ExportAnalysisResults().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export past cases data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringPastData());
        progressPanel.setProgress(++step);
        exports = new ExportPastCases().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }        
        
        // Export geolocation data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringGeoData());
        progressPanel.setProgress(++step);
        exports = new ExportGeolocation().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export Timeline data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringTimelineData());
        progressPanel.setProgress(++step);
        exports = new ExportTimeline().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export ingest history
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringIngestData());
        progressPanel.setProgress(++step);
        exports = ExportIngestHistory.getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        // Export Container & Image info data
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_gatheringContainerData());
        progressPanel.setProgress(++step);
        exports = new ExportContainerInfo().getExports(dataSource);
        if (exports != null) {
            sheetExports.addAll(exports);
        }
        
        progressPanel.updateStatusLabel(Bundle.ExcelExportAction_exportToXLSX_writingToFile());
        progressPanel.setProgress(++step);
        ExcelExport.writeExcel(sheetExports, reportFile);

        try {
            // add to reports
            Case curCase = Case.getCurrentCaseThrows();
            curCase.addReport(reportFile.getParent(),
                    Bundle.ExcelExportAction_moduleName(),
                    reportFile.getName(),
                    dataSource);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "There was an error attaching report to case.", ex);
        }
    }

    /**
     * Function that converts data into a excel sheet data.
     */
    protected interface ExcelExportFunction<T> {

        /**
         * Function that converts data into an excel sheet.
         *
         * @param data The data.
         *
         * @return The excel sheet export.
         *
         * @throws ExcelExportException
         */
        ExcelSheetExport convert(T data) throws ExcelExportException;
    }

    /**
     * Runs a data fetcher and returns the result handling any possible errors
     * with a log message.
     *
     * @param dataFetcher The means of fetching the data.
     * @param sheetName   The name of the sheet.
     * @param ds          The data source.
     *
     * @return The fetched data.
     */
    protected static <T> T getFetchResult(
            DataFetcher<DataSource, T> dataFetcher,
            String sheetName, DataSource ds) {

        try {
            return dataFetcher.runQuery(ds);
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    String.format("There was an error while acquiring data for exporting worksheet(s): '%s' for dataSource: %s",
                            sheetName == null ? "<null>" : sheetName,
                            ds == null || ds.getName() == null ? "<null>" : ds.getName()), ex);
            return null;
        }
    }

    /**
     * Helper method that converts data into an excel sheet export handling
     * possible excel exceptions.
     *
     * @param excelConverter Function to convert data to an excel sheet export.
     * @param data           The data. If data is null, null will be returned.
     * @param sheetName      The name(s) of the sheet (to be used in the error
     *                       message).
     *
     * @return The excel sheet export.
     */
    protected static <T> ExcelSheetExport convertToExcel(ExcelExportFunction<T> excelConverter, T data, String sheetName) {
        if (data == null) {
            return null;
        }

        try {
            return excelConverter.convert(data);
        } catch (ExcelExportException ex) {
            logger.log(Level.WARNING,
                    String.format("There was an error while preparing export of worksheet(s): '%s'",
                            sheetName == null ? "<null>" : sheetName), ex);
            return null;
        }
    }

    /**
     * Returns an excel sheet export given the fetching of data or null if no
     * export created.
     *
     * @param dataFetcher    The means of fetching data.
     * @param excelConverter The means of converting data to excel.
     * @param sheetName      The name of the sheet (for error handling
     *                       reporting).
     * @param ds             The data source to use for fetching data.
     *
     * @return The excel sheet export or null if no export could be generated.
     */
    protected static <T> ExcelSheetExport getExport(
            DataFetcher<DataSource, T> dataFetcher, ExcelExportFunction<T> excelConverter,
            String sheetName, DataSource ds) {

        T data = getFetchResult(dataFetcher, sheetName, ds);
        return convertToExcel(excelConverter, data, sheetName);
    }

    /**
     * Returns an excel table export of the data or null if no export created.
     *
     * @param columnsModel The model for the columns.
     * @param sheetName    The name for the sheet.
     * @param data         The data to be exported.
     *
     * @return The excel table export or null if no export could be generated.
     */
    protected static <T, C extends CellModel> ExcelSheetExport getTableExport(List<ColumnModel<T, C>> columnsModel,
            String sheetName, List<T> data) {

        return convertToExcel((dataList) -> new ExcelTableExport<>(sheetName, columnsModel, dataList),
                data,
                sheetName);
    }

    /**
     * Returns an excel table export of the data or null if no export created.
     *
     * @param dataFetcher  The means of fetching data for the data source and
     *                     the export.
     * @param columnsModel The model for the columns.
     * @param sheetName    The name for the sheet.
     * @param ds           The data source.
     *
     * @return The excel export or null if no export created.
     */
    protected static <T, C extends CellModel> ExcelSheetExport getTableExport(
            DataFetcher<DataSource, List<T>> dataFetcher, List<ColumnModel<T, C>> columnsModel,
            String sheetName, DataSource ds) {

        return getExport(dataFetcher,
                (dataList) -> new ExcelTableExport<>(sheetName, columnsModel, dataList),
                sheetName,
                ds);
    }
}
