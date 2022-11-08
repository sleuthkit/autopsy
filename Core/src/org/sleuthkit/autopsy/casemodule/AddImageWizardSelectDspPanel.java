/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;

/**
 * Create a wizard panel which contains a panel allowing the selection of the
 * DataSourceProcessor
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AddImageWizardSelectDspPanel extends ShortcutWizardDescriptorPanel implements PropertyChangeListener {

    @NbBundle.Messages("SelectDataSourceProcessorPanel.name.text=Select Data Source Type")
    private AddImageWizardSelectDspVisual component;
    private static final String LAST_DSP_PROPERTIES_FILE = "LastDspUsed"; //NON-NLS
    private static final String LAST_DSP_USED_KEY = "Last_Dsp_Used"; //NON-NLS
    private static final Logger logger = Logger.getLogger(AddImageWizardSelectDspVisual.class.getName());

    @Override
    public Component getComponent() {
        if (component == null) {
            String lastDspUsed;
            if (!(ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY) == null)
                    && !ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY).isEmpty()) {
                lastDspUsed = ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY);
            } else {
                lastDspUsed = ImageDSProcessor.getType();
                logger.log(Level.WARNING, "There was no properties file containing the last DataSourceProcessor used, Disk Image or VM will be selected as default selection"); //NON-NLS
            }
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            component = new AddImageWizardSelectDspVisual(lastDspUsed);
            component.setName(Bundle.SelectDataSourceProcessorPanel_name_text());
        }
        component.addPropertyChangeListener(this);
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void readSettings(WizardDescriptor data) {
    }

    @Override
    public void storeSettings(WizardDescriptor data) {
        String lastDspUsed = component.getSelectedDsp();
        ModuleSettings.setConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY, lastDspUsed);
        data.putProperty("SelectedDsp", lastDspUsed);  //NON-NLS magic string necesary to AddImageWizardChooseDataSourcePanel
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }
}
