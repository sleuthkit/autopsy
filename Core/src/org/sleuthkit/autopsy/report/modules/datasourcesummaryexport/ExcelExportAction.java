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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action that exports tab data to an excel workbook.
 */
@Messages({
    "ExcelExportAction_moduleName=Data Source Summary",})
class ExcelExportAction implements Consumer<DataSource> {

    private static final Logger logger = Logger.getLogger(ExcelExportAction.class.getName());

    /**
     * A tab that can be exported.
     */
    interface ExportableTab {

        /**
         * Returns the name of the tab.
         *
         * @return The tab name.
         */
        String getTabTitle();

        /**
         * Given the data source, provides the excel exports for this tab.
         *
         * @param dataSource The data source.
         * @return The excel exports or null.
         */
        List<ExcelSheetExport> getExcelExports(DataSource dataSource);
    }

    private final ExcelExport excelExport = ExcelExport.getInstance();
    private final List<? extends ExportableTab> tabExports;

    /**
     * Main constructor.
     *
     * @param tabExports The different tabs that may have excel exports.
     */
    ExcelExportAction(List<? extends ExportableTab> tabExports) {
        this.tabExports = Collections.unmodifiableList(new ArrayList<>(tabExports));
    }

    /**
     * Accepts the data source for which this export pertains, prompts user for
     * output location, and exports the data.
     *
     * @param ds The data source.
     */
    @Override
    public void accept(DataSource ds) {
        if (ds == null) {
            return;
        }

        File outputLoc = getXLSXPath(ds.getName());
        if (outputLoc == null) {
            return;
        }

        runXLSXExport(ds, outputLoc);
    }

    /**
     * Generates an xlsx path for the data source summary export.
     *
     * @param dataSourceName The name of the data source.
     * @return The file to which the excel document should be written or null if
     * file already exists or cancellation.
     */
    @NbBundle.Messages({
        "ExcelExportAction_getXLSXPath_directory=DataSourceSummary",})
    private File getXLSXPath(String dataSourceName) {
        // set initial path to reports directory with filename that is 
        // a combination of the data source name and time stamp
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        String fileName = String.format("%s-%s.xlsx", dataSourceName == null ? "" : FileUtil.escapeFileName(dataSourceName), dateFormat.format(new Date()));
        try {
            String reportsDir = Case.getCurrentCaseThrows().getReportDirectory();
            File reportsDirFile = Paths.get(reportsDir, Bundle.ExcelExportAction_getXLSXPath_directory()).toFile();
            if (!reportsDirFile.exists()) {
                reportsDirFile.mkdirs();
            }

            return Paths.get(reportsDirFile.getAbsolutePath(), fileName).toFile();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to find reports directory.", ex);
        }

        return null;
    }

    /**
     * An action listener that handles cancellation of the export process.
     */
    private class CancelExportListener implements ActionListener {

        private SwingWorker<Boolean, Void> worker = null;

        @Override
        public void actionPerformed(ActionEvent e) {
            if (worker != null && !worker.isCancelled() && !worker.isDone()) {
                worker.cancel(true);
            }
        }

        /**
         * Returns the swing worker that could be cancelled.
         *
         * @return The swing worker that could be cancelled.
         */
        SwingWorker<Boolean, Void> getWorker() {
            return worker;
        }

        /**
         * Sets the swing worker that could be cancelled.
         *
         * @param worker The swing worker that could be cancelled.
         */
        void setWorker(SwingWorker<Boolean, Void> worker) {
            this.worker = worker;
        }
    }

    /**
     * Handles managing the gui and exporting data from the tabs into an excel
     * document.
     *
     * @param dataSource The data source.
     * @param path The output path.
     */
    @NbBundle.Messages({
        "# {0} - dataSource",
        "ExcelExportAction_runXLSXExport_progressTitle=Exporting {0} to XLSX",
        "ExcelExportAction_runXLSXExport_progressCancelTitle=Cancel",
        "ExcelExportAction_runXLSXExport_progressCancelActionTitle=Cancelling...",
        "ExcelExportAction_runXLSXExport_errorTitle=Error While Exporting",
        "ExcelExportAction_runXLSXExport_errorMessage=There was an error while exporting.",
    })
    private void runXLSXExport(DataSource dataSource, File path) {

        CancelExportListener cancelButtonListener = new CancelExportListener();

        ProgressIndicator progressIndicator = new ModalDialogProgressIndicator(
                WindowManager.getDefault().getMainWindow(),
                Bundle.ExcelExportAction_runXLSXExport_progressTitle(dataSource.getName()),
                new String[]{Bundle.ExcelExportAction_runXLSXExport_progressCancelTitle()},
                Bundle.ExcelExportAction_runXLSXExport_progressCancelTitle(),
                cancelButtonListener
        );

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                exportToXLSX(progressIndicator, dataSource, path);
                return true;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (ExecutionException ex) {
                    logger.log(Level.WARNING, "Error while trying to export data source summary to xlsx.", ex);
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                            Bundle.ExcelExportAction_runXLSXExport_errorMessage(),
                            Bundle.ExcelExportAction_runXLSXExport_errorTitle(),
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException | CancellationException ex) {
                    // no op on cancellation
                } finally {
                    progressIndicator.finish();
                }
            }
        };

        cancelButtonListener.setWorker(worker);
        worker.execute();
    }

    /**
     * Action that handles updating progress and exporting data from the tabs.
     *
     * @param progressIndicator The progress indicator.
     * @param dataSource The data source to be exported.
     * @param path The path of the excel export.
     * @throws InterruptedException
     * @throws IOException
     * @throws ExcelExportException
     */
    @NbBundle.Messages({
        "ExcelExportAction_exportToXLSX_beginExport=Beginning Export...",
        "# {0} - tabName",
        "ExcelExportAction_exportToXLSX_gatheringTabData=Fetching Data for {0} Tab...",
        "ExcelExportAction_exportToXLSX_writingToFile=Writing to File...",})

    private void exportToXLSX(ProgressIndicator progressIndicator, DataSource dataSource, File path)
            throws InterruptedException, IOException, ExcelExport.ExcelExportException {

        int exportWeight = 3;
        int totalWeight = tabExports.size() + exportWeight;
        progressIndicator.start(Bundle.ExcelExportAction_exportToXLSX_beginExport(), totalWeight);
        List<ExcelExport.ExcelSheetExport> sheetExports = new ArrayList<>();
        for (int i = 0; i < tabExports.size(); i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Export has been cancelled.");
            }

            ExportableTab tab = tabExports.get(i);
            progressIndicator.progress(Bundle.ExcelExportAction_exportToXLSX_gatheringTabData(tab == null ? "" : tab.getTabTitle()), i);

            List<ExcelExport.ExcelSheetExport> exports = tab.getExcelExports(dataSource);
            if (exports != null) {
                sheetExports.addAll(exports);
            }
        }

        if (Thread.interrupted()) {
            throw new InterruptedException("Export has been cancelled.");
        }

        progressIndicator.progress(Bundle.ExcelExportAction_exportToXLSX_writingToFile(), tabExports.size());
        excelExport.writeExcel(sheetExports, path);

        progressIndicator.finish();

        try {
            // add to reports
            Case curCase = Case.getCurrentCaseThrows();
            curCase.addReport(path.getParent(),
                    Bundle.ExcelExportAction_moduleName(),
                    path.getName(),
                    dataSource);

            // and show finished dialog
            /* ELTODO SwingUtilities.invokeLater(() -> {
                ExcelExportDialog dialog = new ExcelExportDialog(WindowManager.getDefault().getMainWindow(), path);
                dialog.setResizable(false);
                dialog.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
                dialog.setVisible(true);
                dialog.toFront();
            });*/

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
         * @return The excel sheet export.
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
     * @param data The data. If data is null, null will be returned.
     * @param sheetName The name(s) of the sheet (to be used in the error
     * message).
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
     * @param dataFetcher The means of fetching data.
     * @param excelConverter The means of converting data to excel.
     * @param sheetName The name of the sheet (for error handling reporting).
     * @param ds The data source to use for fetching data.
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
     * @param sheetName The name for the sheet.
     * @param data The data to be exported.
     * @return The excel table export or null if no export could be generated.
     */
    protected static <T, C extends CellModel> ExcelSheetExport getTableExport(List<ColumnModel<T, C>> columnsModel,
            String sheetName, List<T> data) {

        return convertToExcel((dataList) -> new ExcelTableExport<T, C>(sheetName, columnsModel, dataList),
                data,
                sheetName);
    }

    /**
     * Returns an excel table export of the data or null if no export created.
     *
     * @param dataFetcher The means of fetching data for the data source and the
     * export.
     * @param columnsModel The model for the columns.
     * @param sheetName The name for the sheet.
     * @param ds The data source.
     * @return The excel export or null if no export created.
     */
    protected static <T, C extends CellModel> ExcelSheetExport getTableExport(
            DataFetcher<DataSource, List<T>> dataFetcher, List<ColumnModel<T, C>> columnsModel,
            String sheetName, DataSource ds) {

        return getExport(dataFetcher,
                (dataList) -> new ExcelTableExport<T, C>(sheetName, columnsModel, dataList),
                sheetName,
                ds);
    }
}
