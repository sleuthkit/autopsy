/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2022 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModuleSettings;
import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModule;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.WizardDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.ReportModule;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.report.infrastructure.ReportWizardAction")
@ActionRegistration(displayName = "#CTL_ReportWizardAction", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 301, separatorAfter = 399)
    ,
    @ActionReference(path = "Toolbars/Case", position = 106)})
public final class ReportWizardAction extends CallableSystemAction implements Presenter.Toolbar, ActionListener {

    private static final Logger logger = Logger.getLogger(ReportWizardAction.class.getName());
    private static final String REPORTING_CONFIGURATION_NAME = "ReportAction";
    private static final boolean DISPLAY_CASE_SPECIFIC_DATA = true;
    private static final boolean RUN_REPORTS = true;
    private final JButton toolbarButton = new JButton();
    private static final String ACTION_NAME = NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.actionName.text");
    private static ReportGenerationPanel panel;

    /**
     * When the Generate Report button or menu item is selected, open the
     * reporting wizard. When the wizard is finished, create a ReportGenerator
     * with the wizard information, and start all necessary reports.
     *
     * @param configName              Name of the reporting configuration to use
     * @param displayCaseSpecificData Flag whether to use case specific data in
     *                                UI panels or to use all possible result
     *                                types
     * @param runReports              Flag whether to produce report(s)
     */
    @SuppressWarnings("unchecked")
    public static void doReportWizard(String configName, boolean displayCaseSpecificData, boolean runReports) {
        WizardDescriptor wiz = new WizardDescriptor(new ReportWizardIterator(configName, displayCaseSpecificData));
        wiz.setTitleFormat(new MessageFormat("{0} {1}"));
        wiz.setTitle(NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.reportWiz.title"));
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {

            // save reporting configuration
            try {
                saveReportingConfiguration(configName, wiz);
            } catch (ReportConfigException ex) {
                logger.log(Level.SEVERE, "Failed to save reporting configuration " + configName, ex); //NON-NLS
                NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                        NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.unableToSaveConfig.errorLabel.text"),
                        NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(descriptor);
            }

            if (runReports) {
                // generate reports in a separate thread
                panel = new ReportGenerationPanel();
                Map<String, ReportModule> modules = (Map<String, ReportModule>) wiz.getProperty("modules");
                ReportGenerator generator = new ReportGenerator(configName, panel); //NON-NLS
                ReportWorker worker = new ReportWorker(() -> {
                    try {
                        generator.generateReports(modules);
                    } catch (ReportGenerationException ex) {
                        // do nothing. the error message will be logged and 
                        // displayed by the progress panel.
                    }
                });
                worker.execute();
                generator.displayProgressPanel();
            }
        }
    }

    @SuppressWarnings(value = "unchecked")
    private static void saveReportingConfiguration(String configName, WizardDescriptor wiz) throws ReportConfigException {

        ReportingConfig reportingConfig = new ReportingConfig(configName);
        List<Long> selectedDataSourceIds = (List<Long>) wiz.getProperty("dataSourceSelections");
        
        // Set the selected data source ids.
        FileReportSettings fileSettings = (FileReportSettings) wiz.getProperty("fileReportSettings");
        TableReportSettings tableSettings = (TableReportSettings) wiz.getProperty("tableReportSettings");
        GeneralReportSettings generalSettings = new GeneralReportSettings();
        if(selectedDataSourceIds != null) {
            generalSettings.setSelectedDataSources(selectedDataSourceIds);
            if(fileSettings != null) {
                fileSettings.setSelectedDataSources(selectedDataSourceIds);
            }
            if(tableSettings != null) {
                tableSettings.setSelectedDataSources(selectedDataSourceIds);
            }
        }
        
        reportingConfig.setFileReportSettings(fileSettings);
        reportingConfig.setTableReportSettings(tableSettings);
        reportingConfig.setGeneralReportSettings(generalSettings);

        Map<String, ReportModuleConfig> moduleConfigs = (Map<String, ReportModuleConfig>) wiz.getProperty("moduleConfigs");

        // update portable case settings
        ReportModuleConfig config = moduleConfigs.get(PortableCaseReportModule.class.getCanonicalName());
        PortableCaseReportModuleSettings portableCaseReportSettings = (PortableCaseReportModuleSettings) wiz.getProperty("portableCaseReportSettings");
        if (portableCaseReportSettings != null) {
            config.setModuleSettings(portableCaseReportSettings);
            moduleConfigs.put(PortableCaseReportModule.class.getCanonicalName(), config);
        }

        // set module configs
        reportingConfig.setModuleConfigs(moduleConfigs);

        // save reporting configuration
        ReportingConfigLoader.saveConfig(reportingConfig);
    }

    public ReportWizardAction() {
        setEnabled(false);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                Case newCase = (Case) evt.getNewValue();
                setEnabled(newCase != null && RuntimeProperties.runningWithGUI());
            }
        });

        // Initialize the Generate Report button
        toolbarButton.addActionListener(ReportWizardAction.this::actionPerformed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void actionPerformed(ActionEvent e) {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        doReportWizard(REPORTING_CONFIGURATION_NAME, DISPLAY_CASE_SPECIFIC_DATA, RUN_REPORTS);
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void performAction() {
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Returns the tool bar component of this action
     *
     * @return component the tool bar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/btn_icon_generate_report.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(NbBundle.getMessage(this.getClass(), "ReportWizardAction.toolBarButton.text"));
        return toolbarButton;
    }
    
    /**
     * Returns a set of the existing report profile names, removing the special
     * named ReportAction.
     *
     * @return A set of user configurable report profiles, empty list is
     *         returned if none were found.
     */
    public static Set<String> getReportConfigNames() {
        Set<String> nameList = ReportingConfigLoader.getListOfReportConfigs();
        //Remove this default name, users cannot change this report.
        nameList.remove(REPORTING_CONFIGURATION_NAME);

        return nameList;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }

    private static class ReportWorker extends SwingWorker<Void, Void> {

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
                panel.getProgressPanel().updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage());
                logger.log(Level.SEVERE, "failed to generate reports", ex); //NON-NLS
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            }
        }
    }
}
