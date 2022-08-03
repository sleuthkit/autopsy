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

import org.openide.util.NbBundle;
import java.awt.Component;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.profile.IngestProfilePaths;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.IngestProfileSelectionWizardPanel;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;

/**
 * second panel of add image wizard, allows user to configure ingest modules.
 *
 * TODO: review this for dead code. think about moving logic of adding image to
 * 3rd panel( {@link  AddImageWizardAddingProgressPanel}) separate class -jm
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AddImageWizardIngestConfigPanel extends ShortcutWizardDescriptorPanel {

    @Messages("AddImageWizardIngestConfigPanel.name.text=Configure Ingest")
    private final IngestJobSettingsPanel ingestJobSettingsPanel;
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private Component component = null;
    private String lastProfileUsed = AddImageWizardIngestConfigPanel.class.getCanonicalName();
    private final AddImageWizardAddingProgressPanel progressPanel;

    AddImageWizardIngestConfigPanel(AddImageWizardAddingProgressPanel proPanel) {
        this.progressPanel = proPanel;
        IngestJobSettings ingestJobSettings = new IngestJobSettings(AddImageWizardIngestConfigPanel.class.getCanonicalName());
        showWarnings(ingestJobSettings);
        //When this panel is viewed by the user it will always be displaying the
        //IngestJobSettingsPanel with the AddImageWizardIngestConfigPanel.class.getCanonicalName();
        this.ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);

    }

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component the UI component of this wizard panel
     */
    @Override
    public Component getComponent() {
        if (component == null) {
            component = new AddImageWizardIngestConfigVisual(this.ingestJobSettingsPanel);
            component.setName(Bundle.AddImageWizardIngestConfigPanel_name_text());
        }
        return component;
    }

    /**
     * Help for this panel. When the panel is active, this is used as the help
     * for the wizard dialog.
     *
     * @return HelpCtx.DEFAULT_HELP the help for this panel
     */
    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx(SampleWizardPanel1.class);
    }

    /**
     * Tests whether the panel is finished. If the panel is valid, the "Finish"
     * button will be enabled.
     *
     * @return true the finish button should be always enabled at this point
     */
    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
        // and uncomment the complicated stuff below.
    }

    /**
     * Adds a listener to changes of the panel's validity.
     *
     * @param l the change listener to add
     */
    @Override
    public final void addChangeListener(ChangeListener l) {
    }

    /**
     * Removes a listener to changes of the panel's validity.
     *
     * @param l the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
    }

    // You can use a settings object to keep track of state. Normally the
    // settings object will be the WizardDescriptor, so you can use
    // WizardDescriptor.getProperty & putProperty to store information entered
    // by the user.
    /**
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        JButton cancel = new JButton(
                NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.CANCEL_BUTTON.text"));
        cancel.setEnabled(false);
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, cancel});
    }

    /**
     * Provides the wizard panel with the opportunity to update the settings
     * with its current customized state. Rather than updating its settings with
     * every change in the GUI, it should collect them, and then only save them
     * when requested to by this method. This method can be called multiple
     * times on one instance of WizardDescriptor.Panel.
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        IngestJobSettings ingestJobSettings = ingestJobSettingsPanel.getSettings();
        ingestJobSettings.save();              
        progressPanel.setIngestJobSettings(ingestJobSettings);  //prepare ingest for being started
    }

    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), warningMessage.toString());
        }
    }

    /**
     * Loads the proper settings for this panel to use the previously selected
     * Ingest profile when this panel would be skipped due to a profile being
     * chosen.
     */
    @Override
    public void processThisPanelBeforeSkipped() {
        if (!(ModuleSettings.getConfigSetting(IngestProfileSelectionWizardPanel.getLastProfilePropertiesFile(), AddImageWizardIterator.getPropLastprofileName()) == null)
                && !ModuleSettings.getConfigSetting(IngestProfileSelectionWizardPanel.getLastProfilePropertiesFile(), AddImageWizardIterator.getPropLastprofileName()).isEmpty()) {
            lastProfileUsed = ModuleSettings.getConfigSetting(IngestProfileSelectionWizardPanel.getLastProfilePropertiesFile(), AddImageWizardIterator.getPropLastprofileName());
        }
        //Because this panel kicks off ingest during the wizard we need to 
        //swap out the ingestJobSettings for the ones of the chosen profile before
        //use prefix to specify correct execution context for profiles.
        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestProfilePaths.getInstance().getIngestProfilePrefix() + lastProfileUsed);
        progressPanel.setIngestJobSettings(ingestJobSettings); //prepare ingest for being started
    }
}
