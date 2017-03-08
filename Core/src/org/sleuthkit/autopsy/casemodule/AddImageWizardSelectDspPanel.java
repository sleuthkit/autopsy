/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule;

import java.awt.Component;
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;

/**
 * Create a wizard panel which contains a panel allowing the selection of the DataSourceProcessor
 */
class AddImageWizardSelectDspPanel extends ShortcutWizardDescriptorPanel implements PropertyChangeListener {
 @NbBundle.Messages("SelectDataSourceProcessorPanel.name.text=Select Type of Data")
    private AddImageWizardSelectDspVisual component;
    private static final String LAST_DSP_PROPERTIES_FILE = "LastDSPUsed";
    private static final String LAST_DSP_USED_KEY = "Last_DSP_Used";

    @Override
    public Component getComponent() {
        if (component == null) {
            String lastDspUsed;
            if (!(ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY) == null)
                    && !ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY).isEmpty()) {
                lastDspUsed = ModuleSettings.getConfigSetting(LAST_DSP_PROPERTIES_FILE, LAST_DSP_USED_KEY);
            } else {
                lastDspUsed = ImageDSProcessor.getType();
                System.out.println("NO SAVED DSP WJS-TODO LOG THIS");
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
