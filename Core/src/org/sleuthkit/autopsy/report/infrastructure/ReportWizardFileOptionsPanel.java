/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import org.sleuthkit.autopsy.report.infrastructure.FileReportSettings;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * Wizard panel that allows configuration of File Report options.
 *
 * @author jwallace
 */
class ReportWizardFileOptionsPanel implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private WizardDescriptor wiz;
    private ReportWizardFileOptionsVisualPanel component;
    private final JButton finishButton;
    private final FileReportSettings settings;

    ReportWizardFileOptionsPanel(FileReportSettings settings) {
        this.settings = settings;
        finishButton = new JButton(
                NbBundle.getMessage(this.getClass(), "ReportWizardFileOptionsPanel.finishButton.text"));
        finishButton.setEnabled(false);

        finishButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wiz.doFinishClick();
            }
        ;
    }

    );
    }
    
    public void setFinish(boolean enable) {
        finishButton.setEnabled(enable);
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }

    @Override
    public ReportWizardFileOptionsVisualPanel getComponent() {
        if (component == null) {
            component = new ReportWizardFileOptionsVisualPanel(this, settings);
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
        wiz.putProperty("fileReportSettings", new FileReportSettings(getComponent().getFileReportOptions()));
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
    }

}
