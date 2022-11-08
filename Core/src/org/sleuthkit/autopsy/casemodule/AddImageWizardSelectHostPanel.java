/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;

/**
 * Create a wizard panel which contains a panel allowing the selection of a host
 * for a data source.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@Messages("AddImageWizardSelectHostPanel_title=Select Host To Add The Data Source To")
final class AddImageWizardSelectHostPanel extends ShortcutWizardDescriptorPanel implements PropertyChangeListener {

    private final AddImageWizardSelectHostVisual component = new AddImageWizardSelectHostVisual();
    private final ChangeSupport changeSupport = new ChangeSupport(this);
    
    AddImageWizardSelectHostPanel() {
        component.addListener(this);
    }
    
    @Override
    public Component getComponent() {
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void readSettings(WizardDescriptor data) {
    }

    /**
     * Returns or generates the selected host. If user specifies 'generate
     * new...', then null will be returned.
     *
     * @return The selected host or null if to be auto generated.
     */
    Host getSelectedHost() {
        return component.getSelectedHost();
    }

    @Override
    public void storeSettings(WizardDescriptor data) {
    }

    @Override
    public boolean isValid() {
        return component.hasValidData();
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        changeSupport.addChangeListener(cl);
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        changeSupport.removeChangeListener(cl);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        changeSupport.fireChange();
    }
    
    
}
