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
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

 class ReportWizardPanel2 implements WizardDescriptor.Panel<WizardDescriptor> {
    private ReportVisualPanel2 component;
    private JButton finishButton;
    private JButton nextButton;
    private WizardDescriptor wiz;
    
    ReportWizardPanel2() {
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
            component = new ReportVisualPanel2(this);
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
        setFinish(true);
        this.wiz = wiz;
        wiz.setOptions(new Object[] {WizardDescriptor.PREVIOUS_OPTION, nextButton, finishButton, WizardDescriptor.CANCEL_OPTION});
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        wiz.putProperty("tagStates", getComponent().getTagStates());
        wiz.putProperty("artifactStates", getComponent().getArtifactStates());
        wiz.putProperty("isTagsSelected", getComponent().isTaggedResultsRadioButtonSelected());
    }
}
