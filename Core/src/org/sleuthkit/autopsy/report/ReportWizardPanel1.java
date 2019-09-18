/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

class ReportWizardPanel1 implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private WizardDescriptor wiz;
    private ReportVisualPanel1 component;
    private Map<String, ReportModuleConfig> moduleConfigs;
    private final JButton nextButton;
    private final JButton finishButton;

    ReportWizardPanel1(Map<String, ReportModuleConfig> moduleConfigs) {
        this.moduleConfigs = moduleConfigs;
        nextButton = new JButton(NbBundle.getMessage(this.getClass(), "ReportWizardPanel1.nextButton.text"));
        finishButton = new JButton(NbBundle.getMessage(this.getClass(), "ReportWizardPanel1.finishButton.text"));
        finishButton.setEnabled(false);

        // Initialize our custom next and finish buttons
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wiz.doNextClick();
            }
        });

        finishButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wiz.doFinishClick();
            }
        });
    }

    @Override
    public ReportVisualPanel1 getComponent() {
        if (component == null) {
            component = new ReportVisualPanel1(this, moduleConfigs);
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        // Always valid, but we control the enabled state
        // of our custom buttons
        return true;
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }

    public void setNext(boolean enabled) {
        nextButton.setEnabled(enabled);
    }

    public void setFinish(boolean enabled) {
        finishButton.setEnabled(enabled);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        // Add out custom buttons in place of the regular ones
        this.wiz = wiz;
        wiz.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, nextButton, finishButton, WizardDescriptor.CANCEL_OPTION});
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        TableReportModule module = getComponent().getTableModule();
        GeneralReportModule general = getComponent().getGeneralModule();
        PortableCaseReportModule portable = getComponent().getPortableCaseModule();
        wiz.putProperty("tableModule", module); //NON-NLS
        wiz.putProperty("generalModule", general); //NON-NLS
        wiz.putProperty("fileModule", getComponent().getFileModule()); //NON-NLS
        wiz.putProperty("portableCaseModule", portable); //NON-NLS

        // Store preferences that WizardIterator will use to determine what 
        // panels need to be shown
        Preferences prefs = NbPreferences.forModule(ReportWizardPanel1.class);
        prefs.putBoolean("tableModule", module != null); //NON-NLS
        prefs.putBoolean("generalModule", general != null); //NON-NLS
        prefs.putBoolean("portableCaseModule", portable != null); //NON-NLS
    }

    /**
     * Are any of the given booleans true?
     *
     * @param bools
     *
     * @return
     */
    private boolean any(Collection<Boolean> bools) {
        for (Boolean b : bools) {
            if (b) {
                return true;
            }
        }
        return false;
    }
}
