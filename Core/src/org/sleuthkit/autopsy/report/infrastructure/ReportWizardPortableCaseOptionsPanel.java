/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * Wizard panel that allows configuration of Portable Case options.
 *
 */
class ReportWizardPortableCaseOptionsPanel implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private WizardDescriptor wiz;
    private ReportWizardPortableCaseOptionsVisualPanel component;
    private final JButton finishButton;
    private Map<String, ReportModuleConfig> moduleConfigs;
    private final boolean useCaseSpecificData;

    ReportWizardPortableCaseOptionsPanel(Map<String, ReportModuleConfig> moduleConfigs, boolean useCaseSpecificData) {
        this.moduleConfigs = moduleConfigs;
        this.useCaseSpecificData = useCaseSpecificData;
        finishButton = new JButton(
                NbBundle.getMessage(this.getClass(), "ReportWizardFileOptionsPanel.finishButton.text"));
        finishButton.setEnabled(false);

        finishButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wiz.doFinishClick();
            }
        });
    }
    
    /**
     * Set whether the finish button is enabled
     * 
     * @param enable true if the finish button should be enabled
     */
    void setFinish(boolean enable) {
        finishButton.setEnabled(enable);
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }

    @Override
    public ReportWizardPortableCaseOptionsVisualPanel getComponent() {
        if (component == null) {
            component = new ReportWizardPortableCaseOptionsVisualPanel(this, moduleConfigs, useCaseSpecificData);
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void readSettings(WizardDescriptor data) {
        this.wiz = data;
        wiz.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, finishButton, WizardDescriptor.CANCEL_OPTION});
    }

    @Override
    public void storeSettings(WizardDescriptor data) {
        data.putProperty("portableCaseReportSettings", getComponent().getPortableCaseReportSettings()); //NON-NLS
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        // Nothing to do
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        // Nothing to do
    }

}

