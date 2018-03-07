/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

class ReportGenerator {

    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());

    /**
     * Progress reportGenerationPanel that can be used to check for cancellation.
     */
    private ReportProgressPanel progressPanel;

    private static final String REPORT_PATH_FMT_STR = "%s" + File.separator + "%s %s %s" + File.separator;
    private final ReportGenerationPanel reportGenerationPanel = new ReportGenerationPanel();

    static final String REPORTS_DIR = "Reports"; //NON-NLS

    private List<String> errorList;

    /**
     * Displays the list of errors during report generation in user-friendly
     * way. MessageNotifyUtil used to display bubble notification.
     */
    private void displayReportErrors() {
        if (!errorList.isEmpty()) {
            String errorString = "";
            for (String error : errorList) {
                errorString += error + "\n";
            }
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.notifyErr.errsDuringRptGen"), errorString);
        }
    }

    /**
     * Creates a report generator.
     */
    ReportGenerator() {
        this.errorList = new ArrayList<>();
    }


    /**
     * Display the progress panels to the user, and add actions to close the
     * parent dialog.
     */
    private void displayProgressPanel() {
        final JDialog dialog = new JDialog((JFrame) WindowManager.getDefault().getMainWindow(), true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setTitle(NbBundle.getMessage(this.getClass(), "ReportGenerator.displayProgress.title.text"));
        dialog.add(this.reportGenerationPanel);
        dialog.pack();

        reportGenerationPanel.addCloseAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                reportGenerationPanel.close();
            }
        });

        dialog.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        dialog.setVisible(true);
    }

    /**
     * Run the GeneralReportModules using a SwingWorker.
     */
    void generateGeneralReport(GeneralReportModule generalReportModule) throws IOException {
        if (generalReportModule != null) {
            String reportDir = createReportDirectory(generalReportModule);
            setupProgressPanel(generalReportModule, reportDir);
            ReportWorker worker = new ReportWorker(() -> {
                generalReportModule.generateReport(reportDir, progressPanel);
            });
            worker.execute();
            displayProgressPanel();
        }
    }

    /**
     * Run the TableReportModules using a SwingWorker.
     *
     * @param artifactTypeSelections the enabled/disabled state of the artifact
     *                               types to be included in the report
     * @param tagSelections          the enabled/disabled state of the tag names
     *                               to be included in the report
     */
    void generateTableReport(TableReportModule tableReport, Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) throws IOException {
        if (tableReport != null && null != artifactTypeSelections) {
            String reportDir = createReportDirectory(tableReport);
            setupProgressPanel(tableReport, reportDir);
            ReportWorker worker = new ReportWorker(() -> {
                tableReport.startReport(reportDir);
                TableReportGenerator generator = new TableReportGenerator(artifactTypeSelections, tagNameSelections, progressPanel, tableReport);
                generator.execute();
                tableReport.endReport();
                // finish progress, wrap up
                progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
                errorList = generator.getErrorList();
            });
            worker.execute();
            displayProgressPanel();
        }
    }

    /**
     * Run the FileReportModules using a SwingWorker.
     *
     * @param enabledInfo the Information that should be included about each
     *                    file in the report.
     */
    void generateFileListReport(FileReportModule fileReportModule, Map<FileReportDataTypes, Boolean> enabledInfo) throws IOException {
        if (fileReportModule != null && null != enabledInfo) {
            String reportDir = createReportDirectory(fileReportModule);
            List<FileReportDataTypes> enabled = new ArrayList<>();
            for (Entry<FileReportDataTypes, Boolean> e : enabledInfo.entrySet()) {
                if (e.getValue()) {
                    enabled.add(e.getKey());
                }
            }
            setupProgressPanel(fileReportModule, reportDir);
            ReportWorker worker = new ReportWorker(() -> {
                if (progressPanel.getStatus() != ReportStatus.CANCELED) {
                    progressPanel.start();
                    progressPanel.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.queryingDb.text"));
                }

                List<AbstractFile> files = getFiles();
                int numFiles = files.size();
                if (progressPanel.getStatus() != ReportStatus.CANCELED) {
                    fileReportModule.startReport(reportDir);
                    fileReportModule.startTable(enabled);
                }
                progressPanel.setIndeterminate(false);
                progressPanel.setMaximumProgress(numFiles);

                int i = 0;
                // Add files to report.
                for (AbstractFile file : files) {
                    // Check to see if any reports have been cancelled.
                    if (progressPanel.getStatus() == ReportStatus.CANCELED) {
                        return;
                    } else {
                        fileReportModule.addRow(file, enabled);
                        progressPanel.increment();
                    }

                    if ((i % 100) == 0) {
                        progressPanel.updateStatusLabel(
                                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingFile.text",
                                        file.getName()));
                    }
                    i++;
                }

                fileReportModule.endTable();
                fileReportModule.endReport();
                progressPanel.complete(ReportStatus.COMPLETE);
            });
            worker.execute();
            displayProgressPanel();
        }
    }

    /**
     * Get all files in the image.
     *
     * @return
     */
    private List<AbstractFile> getFiles() {
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getOpenCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("meta_type != " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()); //NON-NLS
            return absFiles;
        } catch (TskCoreException | NoCurrentCaseException ex) {
            MessageNotifyUtil.Notify.show(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage(),
                    MessageNotifyUtil.MessageType.ERROR);
            logger.log(Level.SEVERE, "failed to generate reports. Unable to get all files in the image.", ex); //NON-NLS
            return Collections.<AbstractFile>emptyList();
        }
    }

    private void setupProgressPanel(ReportModule module, String reportDir) {
        String reportFilePath = module.getRelativeFilePath();
        if (!reportFilePath.isEmpty()) {
            this.progressPanel = reportGenerationPanel.addReport(module.getName(), reportDir + reportFilePath);
        } else {
            this.progressPanel = reportGenerationPanel.addReport(module.getName(), null);
        }
    }

    private static String createReportDirectory(ReportModule module) throws IOException {
        Case currentCase;
        try {
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            throw new IOException("Exception while getting open case.", ex);
        }
        // Create the root reports directory path of the form: <CASE DIRECTORY>/Reports/<Case fileName> <Timestamp>/
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String dateNoTime = dateFormat.format(date);
        String reportPath = String.format(REPORT_PATH_FMT_STR, currentCase.getReportDirectory(), currentCase.getDisplayName(), module.getName(), dateNoTime);
        // Create the root reports directory.
        try {
            FileUtil.createFolder(new File(reportPath));
        } catch (IOException ex) {
            throw new IOException("Failed to make report folder, unable to generate reports.", ex);
        }
        return reportPath;    
    }
    
    private class ReportWorker extends SwingWorker<Void, Void> {

        private final Runnable doInBackground;

        private ReportWorker(Runnable doInBackground) {
            this.doInBackground = doInBackground;
        }

        @Override
        protected Void doInBackground() throws Exception {
            doInBackground.run();
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
                logger.log(Level.SEVERE, "failed to generate reports", ex); //NON-NLS
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            } finally {
                displayReportErrors();
                errorList.clear();
            }
        }

    }
}
