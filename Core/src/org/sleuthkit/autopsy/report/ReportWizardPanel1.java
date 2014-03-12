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
    private JButton nextButton;
    private JButton finishButton;
    
    ReportWizardPanel1() {
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
            component = new ReportVisualPanel1(this);
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
        wiz.setOptions(new Object[] {WizardDescriptor.PREVIOUS_OPTION, nextButton, finishButton, WizardDescriptor.CANCEL_OPTION});
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        Map<TableReportModule, Boolean> tables = getComponent().getTableModuleStates();
        Map<GeneralReportModule, Boolean> generals = getComponent().getGeneralModuleStates();
        wiz.putProperty("tableModuleStates", tables);
        wiz.putProperty("generalModuleStates", generals);
        wiz.putProperty("fileModuleStates", getComponent().getFileModuleStates());
        
        // Store preferences that WizardIterator will use to determine what 
        // panels need to be shown
        Preferences prefs = NbPreferences.forModule(ReportWizardPanel1.class);
        prefs.putBoolean("tableModule", any(tables.values()));
        prefs.putBoolean("generalModule", any(generals.values()));
    }
    
    /**
     * Are any of the given booleans true?
     * @param bools
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
