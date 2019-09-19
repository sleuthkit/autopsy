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
package org.sleuthkit.autopsy.report.infrastructure;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

class ReportWizardPanel2 implements WizardDescriptor.Panel<WizardDescriptor> {

    private ReportVisualPanel2 component;
    private final JButton finishButton;
    private final JButton nextButton;
    private WizardDescriptor wiz;
    private final boolean useCaseSpecificData;
    private final TableReportSettings settings;

    ReportWizardPanel2(boolean useCaseSpecificData, TableReportSettings settings) {
        this.useCaseSpecificData = useCaseSpecificData;
        this.settings = settings;
        finishButton = new JButton(NbBundle.getMessage(this.getClass(), "ReportWizardPanel2.finishButton.text"));
        nextButton = new JButton(NbBundle.getMessage(this.getClass(), "ReportWizardPanel2.nextButton.text"));
        nextButton.setEnabled(true);

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
    public ReportVisualPanel2 getComponent() {
        if (component == null) {
            component = new ReportVisualPanel2(this, useCaseSpecificData, settings);
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    public void setFinish(boolean enabled) {
        nextButton.setEnabled(false);
        finishButton.setEnabled(enabled);
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        // Re-enable the normal wizard buttons
        this.wiz = wiz;
        wiz.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, nextButton, finishButton, WizardDescriptor.CANCEL_OPTION});
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        wiz.putProperty("tableReportSettings", new TableReportSettings(getComponent().getArtifactStates(), getComponent().getTagStates(), useCaseSpecificData, getComponent().getSelectedReportType()));
    }
}
