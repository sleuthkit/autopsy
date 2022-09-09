/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModuleSettings;
import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModule;
import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModule;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import org.sleuthkit.autopsy.report.GeneralReportModule;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.JDialog;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A report generator that generates one or more reports by running
 * user-selected report modules.
 */
public class ReportGenerator {

    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    private final ReportProgressPanel progressIndicator;
    private final ReportGenerationPanel reportGenerationPanel;
    private static final String REPORT_PATH_FMT_STR = "%s" + File.separator + "%s %s %s" + File.separator;
    private final String configName;
    private static final String REPORTS_DIR = "Reports"; //NON-NLS
    private List<String> errorList = new ArrayList<>();

    /**
     * Gets the name of the reports directory within the case direcotry
     * hierarchy.
     *
     * @return The directory name.
     */
    public static String getReportsDirectory() {
        return REPORTS_DIR;
    }

    /**
     * Displays a list of errors emitted by report modules during report
     * generation using this report generator's report progress indicator.
     */
    private void displayReportErrors() {
        if (!errorList.isEmpty()) {
            String errorString = "";
            for (String error : errorList) {
                errorString += error + "\n";
            }
            progressIndicator.updateStatusLabel(errorString);
        }
    }

    /**
     * Constructs a report generator that generates one or more reports by
     * running user-selected report modules and uses a report progress indicator
     * to display progress.
     *
     * @param configName        The name of the reporting configuration to use.
     * @param progressIndicator The report progress indicator.
     */
    public ReportGenerator(String configName, ReportProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
        this.reportGenerationPanel = null;
        this.configName = configName;
    }

    /**
     * Constructs a report generator that generates one or more reports by
     * running user-selected report modules and uses a report generation panel
     * to display progress.
     *
     * @param configName The name of the reporting configuration to use.
     * @param panel      The report generation panel.
     */
    ReportGenerator(String configName, ReportGenerationPanel panel) {
        this.reportGenerationPanel = panel;
        this.progressIndicator = panel.getProgressPanel();
        this.configName = configName;
    }

    /**
     * Generates the reports specified by the reporting configuration passed in
     * via the constructor. Does lookup of all existing report modules.
     *
     * @throws ReportGenerationException if an error occurred while generating
     *                                   the report.
     */
    public void generateReports() throws ReportGenerationException {
        // load all report modules 
        Map<String, ReportModule> modules = new HashMap<>();
        for (TableReportModule module : ReportModuleLoader.getTableReportModules()) {
            modules.put(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()), module);
        }

        for (GeneralReportModule module : ReportModuleLoader.getGeneralReportModules()) {
            modules.put(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()), module);
        }

        for (FileReportModule module : ReportModuleLoader.getFileReportModules()) {
            modules.put(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()), module);
        }

        // special case for PortableCaseReportModule
        modules.put(FactoryClassNameNormalizer.normalize(PortableCaseReportModule.class.getCanonicalName()), new PortableCaseReportModule());

        generateReports(modules);
    }

    @NbBundle.Messages({
        "ReportGenerator.error.noReportModules=No report modules found",
        "# {0} - report configuration name", "ReportGenerator.error.unableToLoadConfig=Unable to load reporting configuration {0}.",
        "# {0} - report module name", "ReportGenerator.error.moduleNotFound=Report module {0} not found",
        "# {0} - report module name", "ReportGenerator.error.noTableReportSettings=No table report settings for report module {0}",
        "# {0} - report module name", "ReportGenerator.error.noFileReportSettings=No file report settings for report module {0}",
        "# {0} - report module name", "ReportGenerator.error.invalidSettings=Invalid settings for report module {0}",
        "# {0} - report module name", "ReportGenerator.error.unsupportedType=Report module {0} has unsupported report module type",
        "# {0} - report module name", "ReportGenerator.error.exception=Exception while running report module {0}"})
    /**
     * Generates the reports specified by the reporting configuration passed in
     * via the constructor.
     *
     * @param modules Map of report module objects to use. This is useful when
     *                we want to re-use the module instances or limit which
     *                reports are generated.
     *
     * @throws ReportGenerationException if an error occurred while generating
     *                                   the report.
     */
    public void generateReports(Map<String, ReportModule> modules) throws ReportGenerationException {

        if (modules == null || modules.isEmpty()) {
            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_noReportModules());
            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_noReportModules());
            throw new ReportGenerationException(Bundle.ReportGenerator_error_noReportModules());
        }

        ReportingConfig config = null;
        try {
            config = ReportingConfigLoader.loadConfig(configName);
        } catch (ReportConfigException ex) {
            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_unableToLoadConfig(configName), ex);
            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_unableToLoadConfig(configName));
            throw new ReportGenerationException(Bundle.ReportGenerator_error_unableToLoadConfig(configName));
        }

        if (config == null) {
            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_unableToLoadConfig(configName));
            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_unableToLoadConfig(configName));
            throw new ReportGenerationException(Bundle.ReportGenerator_error_unableToLoadConfig(configName));
        }

        try {
            // generate reports for enabled modules
            for (Map.Entry<String, ReportModuleConfig> entry : config.getModuleConfigs().entrySet()) {
                ReportModuleConfig moduleConfig = entry.getValue();
                if (moduleConfig == null || !moduleConfig.isEnabled()) {
                    continue;
                }

                // found enabled module
                String moduleName = entry.getKey();
                ReportModule module = modules.get(moduleName);
                if (module == null) {
                    logger.log(Level.SEVERE, Bundle.ReportGenerator_error_moduleNotFound(moduleName));
                    progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_moduleNotFound(moduleName));
                    continue;
                }

                // get persisted module settings
                ReportModuleSettings settings = moduleConfig.getModuleSettings();
                if (settings == null) {
                    // use default configuration for this module
                    settings = module.getDefaultConfiguration();
                }

                // set module configuration
                module.setConfiguration(settings);

                try {
                    // generate report according to report module type
                    if (module instanceof GeneralReportModule) {

                        // generate report
                        generateGeneralReport((GeneralReportModule) module, config.getGeneralReportSettings());

                    } else if (module instanceof TableReportModule) {

                        // get table report settings
                        TableReportSettings tableSettings = config.getTableReportSettings();
                        if (tableSettings == null) {
                            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_noTableReportSettings(moduleName)); 
                            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_noTableReportSettings(moduleName));
                            continue;
                        }

                        generateTableReport((TableReportModule) module, tableSettings); //NON-NLS

                    } else if (module instanceof FileReportModule) {

                        // get file report settings
                        FileReportSettings fileSettings = config.getFileReportSettings();
                        if (fileSettings == null) {
                            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_noFileReportSettings(moduleName));
                            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_noFileReportSettings(moduleName));
                            continue;
                        }

                        generateFileListReport((FileReportModule) module, fileSettings); //NON-NLS

                    } else if (module instanceof PortableCaseReportModule) {
                        // get report settings
                        if (settings instanceof NoReportModuleSettings) {
                            settings = new PortableCaseReportModuleSettings();
                        } else if (!(settings instanceof PortableCaseReportModuleSettings)) {
                            logger.log(Level.SEVERE, Bundle.ReportGenerator_error_invalidSettings(moduleName)); 
                            progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_invalidSettings(moduleName));
                            continue;
                        }

                        generatePortableCaseReport((PortableCaseReportModule) module, (PortableCaseReportModuleSettings) settings);

                    } else {
                        logger.log(Level.SEVERE, Bundle.ReportGenerator_error_unsupportedType(moduleName)); 
                        progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_unsupportedType(moduleName));
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, Bundle.ReportGenerator_error_exception(moduleName)); 
                    progressIndicator.updateStatusLabel(Bundle.ReportGenerator_error_exception(moduleName));
                }
            }
        } finally {
            displayReportErrors();
            errorList.clear();
        }
    }

    /**
     * Display the progress panels to the user, and add actions to close the
     * parent dialog.
     */
    void displayProgressPanel() {
        if (reportGenerationPanel == null) {
            return;
        }

        final JDialog dialog = new JDialog(WindowManager.getDefault().getMainWindow(), true);
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
    private void generateGeneralReport(GeneralReportModule generalReportModule, GeneralReportSettings reportSettings) throws IOException {
        if (generalReportModule != null) {
            String reportDir = createReportDirectory(generalReportModule);
            setupProgressPanel(generalReportModule, reportDir);
            reportSettings.setReportDirectoryPath(reportDir);
            generalReportModule.generateReport(reportSettings, progressIndicator);
        }
    }

    /**
     * Run the TableReportModules using a SwingWorker.
     *
     * @param tableReport
     * @param tableReportSettings settings for the table report
     */
    private void generateTableReport(TableReportModule tableReport, TableReportSettings tableReportSettings) throws IOException {
        if (tableReport != null && tableReportSettings != null && null != tableReportSettings.getArtifactSelections()) {
            String reportDir = createReportDirectory(tableReport);
            setupProgressPanel(tableReport, reportDir);
            tableReport.startReport(reportDir);
            TableReportGenerator generator = new TableReportGenerator(tableReportSettings, progressIndicator, tableReport);
            generator.execute();
            tableReport.endReport();

            // finish progress, wrap up
            errorList = generator.getErrorList();

            // if error list is empty, the operation has completed successfully.  If not there is an error
            ReportProgressPanel.ReportStatus finalStatus = (errorList == null || errorList.isEmpty())
                    ? ReportProgressPanel.ReportStatus.COMPLETE
                    : ReportProgressPanel.ReportStatus.ERROR;

            progressIndicator.complete(finalStatus);
        }
    }

    /**
     * Run the FileReportModules using a SwingWorker.
     *
     * @param fileReportModule
     * @param fileReportSettings settings for the file report
     */
    private void generateFileListReport(FileReportModule fileReportModule, FileReportSettings fileReportSettings) throws IOException {
        if (fileReportModule != null && fileReportSettings != null && null != fileReportSettings.getFileProperties()) {
            String reportDir = createReportDirectory(fileReportModule);
            List<FileReportDataTypes> enabled = new ArrayList<>();
            for (Entry<FileReportDataTypes, Boolean> e : fileReportSettings.getFileProperties().entrySet()) {
                if (e.getValue()) {
                    enabled.add(e.getKey());
                }
            }
            setupProgressPanel(fileReportModule, reportDir);
            if (progressIndicator.getStatus() != ReportStatus.CANCELED) {
                progressIndicator.start();
                progressIndicator.updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.queryingDb.text"));
            }

            List<AbstractFile> files = getFiles();
            int numFiles = files.size();
            if (progressIndicator.getStatus() != ReportStatus.CANCELED) {
                fileReportModule.startReport(reportDir);
                fileReportModule.startTable(enabled);
            }
            progressIndicator.setIndeterminate(false);
            progressIndicator.setMaximumProgress(numFiles);

            int i = 0;
            // Add files to report.
            for (AbstractFile file : files) {
                if (shouldFilterFromReport(file, fileReportSettings)) {
                    continue;
                }

                // Check to see if any reports have been cancelled.
                if (progressIndicator.getStatus() == ReportStatus.CANCELED) {
                    return;
                } else {
                    fileReportModule.addRow(file, enabled);
                    progressIndicator.increment();
                }

                if ((i % 100) == 0) {
                    progressIndicator.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingFile.text",
                                    file.getName()));
                }
                i++;
            }

            fileReportModule.endTable();
            fileReportModule.endReport();
            progressIndicator.complete(ReportStatus.COMPLETE);
        }
    }

    private boolean shouldFilterFromReport(AbstractFile file, FileReportSettings fileReportSettings) {
        if (fileReportSettings.getSelectedDataSources() == null) {
            return false;
        }
        // Filter if the data source id is not in the list to process
        return !fileReportSettings.getSelectedDataSources().contains(file.getDataSourceObjectId());
    }

    /**
     * Run the Portable Case Report Module
     */
    private void generatePortableCaseReport(PortableCaseReportModule portableCaseReportModule, PortableCaseReportModuleSettings settings) throws IOException {
        if (portableCaseReportModule != null) {
            String reportDir = createReportDirectory(portableCaseReportModule);
            setupProgressPanel(portableCaseReportModule, reportDir);
            portableCaseReportModule.generateReport(reportDir, settings, progressIndicator);
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
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("meta_type != " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()); //NON-NLS
            return absFiles;
        } catch (TskCoreException | NoCurrentCaseException ex) {
            progressIndicator.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage());
            logger.log(Level.SEVERE, "failed to generate reports. Unable to get all files in the image.", ex); //NON-NLS
            return Collections.<AbstractFile>emptyList();
        }
    }

    private void setupProgressPanel(ReportModule module, String reportDir) {
        if (reportGenerationPanel != null) {
            String reportFilePath = module.getRelativeFilePath();
            if (reportFilePath == null) {
                reportGenerationPanel.addReport(module.getName(), null);
            } else if (reportFilePath.isEmpty()) {
                reportGenerationPanel.addReport(module.getName(), reportDir);
            } else {
                reportGenerationPanel.addReport(module.getName(), reportDir + reportFilePath);
            }
        }
    }

    private static String createReportDirectory(ReportModule module) throws IOException {
        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
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
}
