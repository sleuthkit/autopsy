/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;

public class RunIngestModuleWizardWizardPanel2 extends ShortCircuitableWizardPanel  {

    /**f
 The visual ingestJobSettingsPanel that displays this panel. If you need to access the
 ingestJobSettingsPanel from this class, just use getComponent().
     */
    private IngestJobSettingsPanel ingestJobSettingsPanel;

    // Get the visual ingestJobSettingsPanel for the panel. In this template, the ingestJobSettingsPanel
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override
    public IngestJobSettingsPanel getComponent() {
        if (ingestJobSettingsPanel == null) {
            ingestJobSettingsPanel = new IngestJobSettingsPanel(new IngestJobSettings("org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModuleAction"));
        }
        return ingestJobSettingsPanel;
    }

    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx("help.key.here");
    }

    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...) and
        // this condition changes (last form field filled in...) then
        // use ChangeSupport to implement add/removeChangeListener below.
        // WizardDescriptor.ERROR/WARNING/INFORMATION_MESSAGE will also be useful.
    }
    
    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        // use wiz.getProperty to retrieve previous panel state
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        // use wiz.putProperty to remember current panel state
    }

}
