 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.openide.DialogDisplayer;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.report.ReportWizardAction")
@ActionRegistration(displayName = "#CTL_ReportWizardAction", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 80)})
@NbBundle.Messages(value = "CTL_ReportWizardAction=Run Report")
public final class ReportWizardAction  extends CallableSystemAction implements Presenter.Toolbar, ActionListener {
    private static final Logger logger = Logger.getLogger(ReportWizardAction.class.getName());
    
    private JButton toolbarButton = new JButton();
    private static final String ACTION_NAME = "Generate Report";

    /**
     * When the Generate Report button or menu item is selected, open the reporting wizard.
     * When the wizard is finished, create a ReportGenerator with the wizard information,
     * and start all necessary reports.
     */
    public static void doReportWizard() {
        // Create the wizard
        WizardDescriptor wiz = new WizardDescriptor(new ReportWizardIterator());
        wiz.setTitleFormat(new MessageFormat("{0} {1}"));
        wiz.setTitle("Generate Report");
        
        // When the user presses the finish button
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
            // Get the wizard information
            Object wizProp1 = wiz.getProperty("tableModuleStates");
            Object wizProp2 = wiz.getProperty("generalModuleStates");
            Object wizProp3 = wiz.getProperty("isTagsSelected");
            Object wizProp4 = wiz.getProperty("tagStates");
            Object wizProp5 = wiz.getProperty("artifactStates");
            
            // Initialize variables
            Map<TableReportModule, Boolean> tableModuleStates = (Map<TableReportModule, Boolean>) wizProp1;
            Map<GeneralReportModule, Boolean> generalModuleStates = (Map<GeneralReportModule, Boolean>) wizProp2;
            Boolean isTagsSelected = (Boolean) wizProp3;
            Map<String, Boolean> tagSelections = (Map<String, Boolean>) wizProp4;
            Map<ARTIFACT_TYPE, Boolean> artifactTypeSelections = (Map<ARTIFACT_TYPE, Boolean>) wizProp5;
            
            // Create the generator and generate reports
            ReportGenerator generator = new ReportGenerator(tableModuleStates, generalModuleStates);
            if (isTagsSelected) {
                generator.generateArtifactTableReports(artifactTypeSelections, tagSelections);
            }
            else {
                generator.generateArtifactTableReports(artifactTypeSelections, null);                
            }
            generator.generateGeneralReports();
            
            // Open the progress window for the user
            generator.displayProgressPanels();
        }        
    }

    public ReportWizardAction() {
        setEnabled(false);
        Case.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(Case.CASE_CURRENT_CASE)) {
                    Case newCase = (Case) evt.getNewValue();
                    setEnabled(newCase != null);

                    // Make the cases' Reoports folder, if it doesn't exist
                    if (newCase != null) {
                        boolean exists = (new File(newCase.getCaseDirectory() + File.separator + "Reports")).exists();
                        if (!exists) {
                            boolean reportCreate = (new File(newCase.getCaseDirectory() + File.separator + "Reports")).mkdirs();
                            if (!reportCreate) {
                                logger.log(Level.WARNING, "Could not create Reports directory for case. It does not exist.");
                            }
                        }
                    }
                }
            }
        });

        // Initialize the Generate Report button
        toolbarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReportWizardAction.this.actionPerformed(e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void actionPerformed(ActionEvent e) {
        doReportWizard();
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
     * Returns the toolbar component of this action
     *
     * @return component the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("images/btn_icon_generate_report.png"));
        toolbarButton.setIcon(icon);
        toolbarButton.setText("Generate Report");
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
