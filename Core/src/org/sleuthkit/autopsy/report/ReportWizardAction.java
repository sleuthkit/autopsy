/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JButton;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.report.ReportWizardAction")
@ActionRegistration(displayName = "#CTL_ReportWizardAction", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 301, separatorAfter = 399),
    @ActionReference(path = "Toolbars/Case", position = 105)})
public final class ReportWizardAction extends CallableSystemAction implements Presenter.Toolbar, ActionListener {

    private static final Logger logger = Logger.getLogger(ReportWizardAction.class.getName());
    private static final String REPORTING_CONFIGURATION_NAME = "ReportAction";
    private static final boolean DISPLAY_CASE_SPECIFIC_DATA = true;
    private static final boolean RUN_REPORTS = true;
    private final JButton toolbarButton = new JButton();
    private static final String ACTION_NAME = NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.actionName.text");

    /**
     * When the Generate Report button or menu item is selected, open the
     * reporting wizard. When the wizard is finished, create a ReportGenerator
     * with the wizard information, and start all necessary reports.
     * @param configName Name of the reporting configuration to use
     * @param displayCaseSpecificData Flag whether to use case specific data in UI panels or to use all possible result types
     * @param runReports Flag whether to produce report(s) 
     */
    @SuppressWarnings("unchecked")
    public static void doReportWizard(String configName, boolean displayCaseSpecificData, boolean runReports) {
        WizardDescriptor wiz = new WizardDescriptor(new ReportWizardIterator(configName, displayCaseSpecificData));
        wiz.setTitleFormat(new MessageFormat("{0} {1}"));
        wiz.setTitle(NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.reportWiz.title"));
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
            ReportGenerator generator = new ReportGenerator(); //NON-NLS
            TableReportModule tableReport = (TableReportModule) wiz.getProperty("tableModule");
            GeneralReportModule generalReport = (GeneralReportModule) wiz.getProperty("generalModule");
            FileReportModule fileReport = (FileReportModule) wiz.getProperty("fileModule");
            PortableCaseReportModule portableCaseReport = (PortableCaseReportModule) wiz.getProperty("portableCaseModule");  // NON-NLS
            try {
                if (tableReport != null) {
                    // get table report settings
                    TableReportSettings settings = (TableReportSettings) wiz.getProperty("tableReportSettings");
                    if (settings == null) {
                        NotifyDescriptor descriptor = new NotifyDescriptor.Message(NbBundle.getMessage(ReportWizardAction.class, "ReportGenerator.errList.noReportSettings"), NotifyDescriptor.ERROR_MESSAGE);
                        DialogDisplayer.getDefault().notify(descriptor);
                        return;
                    }
                    
                    // save reporting configuration
                    saveReportingConfiguration(configName, wiz);
                    
                    if (runReports) {
                        generator.generateTableReport(tableReport, settings); //NON-NLS
                    }
                } else if (generalReport != null) {                    
                    // save reporting configuration
                    saveReportingConfiguration(configName, wiz);
                    
                    if (runReports) {
                        generator.generateGeneralReport(generalReport);
                    }
                } else if (fileReport != null) {
                    // get file report settings
                    FileReportSettings settings = (FileReportSettings) wiz.getProperty("fileReportSettings");
                    if (settings == null) {
                        NotifyDescriptor descriptor = new NotifyDescriptor.Message(NbBundle.getMessage(ReportWizardAction.class, "ReportGenerator.errList.noReportSettings"), NotifyDescriptor.ERROR_MESSAGE);
                        DialogDisplayer.getDefault().notify(descriptor);
                        return;
                    }
                    // save reporting configuration
                    saveReportingConfiguration(configName, wiz);
                    
                    if (runReports) {
                        generator.generateFileListReport(fileReport, settings); //NON-NLS
                    }
                } else if (portableCaseReport != null) {                    
                    // save reporting configuration
                    saveReportingConfiguration(configName, wiz);
                    
                    if (runReports) {
                        generator.generatePortableCaseReport(portableCaseReport, (PortableCaseReportModule.PortableCaseOptions) wiz.getProperty("portableCaseReportOptions"));
                    }
                }
            } catch (IOException e) {
                NotifyDescriptor descriptor = new NotifyDescriptor.Message(e.getMessage(), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(descriptor);
            }
        }
    }
    
    private static void saveReportingConfiguration(String configName, WizardDescriptor wiz) {

        ReportingConfig reportingConfig = new ReportingConfig(configName);
        reportingConfig.setModuleConfigs((Map<String, ReportModuleConfig>) wiz.getProperty("moduleConfigs"));
        reportingConfig.setFileReportSettings((FileReportSettings) wiz.getProperty("fileReportSettings"));
        reportingConfig.setTableReportSettings((TableReportSettings) wiz.getProperty("tableReportSettings")); 

        try {
            // save reporting configuration
            ReportingConfigLoader.saveConfig(reportingConfig);
        } catch (ReportConfigException ex) {
            // ELTODO should we do more to let the user know?
            logger.log(Level.SEVERE, "Failed to save reporting configuration " + reportingConfig.getName(), ex); //NON-NLS
        }
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
        doReportWizard(REPORTING_CONFIGURATION_NAME, DISPLAY_CASE_SPECIFIC_DATA, RUN_REPORTS);
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
        ImageIcon icon = new ImageIcon(getClass().getResource("images/btn_icon_generate_report.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(NbBundle.getMessage(this.getClass(), "ReportWizardAction.toolBarButton.text"));
        return toolbarButton;
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
}
