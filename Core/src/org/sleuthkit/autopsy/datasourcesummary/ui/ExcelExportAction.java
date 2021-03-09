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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.awt.Component;
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
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;
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
    private final Component dialogParent;

    /**
     * Main constructor.
     *
     * @param tabExports The different tabs that may have excel exports.
     * @param dialogParent The component that jdialogs will claim as parent.
     */
    ExcelExportAction(List<? extends ExportableTab> tabExports, Component dialogParent) {
        this.tabExports = Collections.unmodifiableList(new ArrayList<>(tabExports));
        this.dialogParent = dialogParent;
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
     * Prompts the user for an output location.
     *
     * @param dataSourceName The name of the data source.
     * @return The file to which the excel document should be written or null if
     * file already exists or cancellation.
     */
    @NbBundle.Messages({
        "ExcelExportAction_getXLSXPath_directory=DataSourceSummary",})
    private File getXLSXPath(String dataSourceName) {
        String expectedExtension = "xlsx";
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
        "ExcelExportAction_runXLSXExport_progressCancelActionTitle=Cancelling..."
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
        onFinish(dataSource, path);
    }

    private void onFinish(DataSource ds, File outputLoc) {
        try {
            Case curCase = Case.getCurrentCaseThrows();
            curCase.addReport(outputLoc.getParent(),
                    Bundle.ExcelExportAction_moduleName(),
                    outputLoc.getName(),
                    ds);

            ExcelExportDialog dialog = new ExcelExportDialog(WindowManager.getDefault().getMainWindow(), outputLoc);
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
            dialog.setVisible(true);
            dialog.toFront();

        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "There was an error attaching report to case.", ex);
        }
    }
}
