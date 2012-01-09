/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI;

/**
 * The "Add Image" wizard panel3. No major functionality, just presents the
 * options to finish/cancel image-add or to add another image.
 */
class AddImageWizardPanel3 implements WizardDescriptor.Panel<WizardDescriptor> {

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private Component component;

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component  the UI component of this wizard panel
     */
    @Override
    public Component getComponent() {
        if (component == null) {
            component = new AddImageVisualPanel3();
        }
        return component;
    }

    /**
     * Help for this panel. When the panel is active, this is used as the help
     * for the wizard dialog.
     *
     * @return HelpCtx.DEFAULT_HELP  the help for this panel
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
     * @return true  the finish button should be always enabled at this point
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
     * @param l  the change listener to add
     */
    @Override
    public final void addChangeListener(ChangeListener l) {
    }

    /**
     * Removes a listener to changes of the panel's validity.
     *
     * @param l  the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
    }
    
    /*
    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0
    public final void addChangeListener(ChangeListener l) {
    synchronized (listeners) {
    listeners.add(l);
    }
    }
    public final void removeChangeListener(ChangeListener l) {
    synchronized (listeners) {
    listeners.remove(l);
    }
    }
    protected final void fireChangeEvent() {
    Iterator<ChangeListener> it;
    synchronized (listeners) {
    it = new HashSet<ChangeListener>(listeners).iterator();
    }
    ChangeEvent ev = new ChangeEvent(this);
    while (it.hasNext()) {
    it.next().stateChanged(ev);
    }
    }
     */

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
     * @param settings  the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        if ((SleuthkitJNI.CaseDbHandle.AddImageProcess) settings.getProperty(AddImageAction.PROCESS_PROP) != null) {
            // commit anything
            try {
                commitImage(settings);
            } catch (Exception ex) {
                // Log error/display warning
                Logger logger = Logger.getLogger(AddImageAction.class.getName());
                logger.log(Level.SEVERE, "Error adding image to case.", ex);
            }
        }else{
            Logger logger = Logger.getLogger(AddImageAction.class.getName());
            logger.log(Level.SEVERE, "Missing image process object");
        }
    }

    /**
     * Provides the wizard panel with the opportunity to update the settings
     * with its current customized state. Rather than updating its settings
     * with every change in the GUI, it should collect them, and then only save
     * them when requested to by this method. This method can be called multiple
     * times on one instance of WizardDescriptor.Panel.
     *
     * @param settings  the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        // Cancel all cleanup tasks
        settings.putProperty(AddImageAction.IMAGECLEANUPTASK_PROP, null);
    }
    
    

    /**
     * Commit the finished AddImageProcess, and cancel the CleanupTask that
     * would have reverted it.
     * @param settings property set to get AddImageProcess and CleanupTask from
     * @throws Exception if commit or adding the image to the case failed
     */
    private void commitImage(WizardDescriptor settings) throws Exception {
        
        String[] imgPaths = (String[]) settings.getProperty(AddImageAction.IMGPATHS_PROP);
        String timezone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
        boolean indexImage = (Boolean) settings.getProperty(AddImageAction.SOLR_PROP);
        
        SleuthkitJNI.CaseDbHandle.AddImageProcess process = (SleuthkitJNI.CaseDbHandle.AddImageProcess) settings.getProperty(AddImageAction.PROCESS_PROP);
        
        try {
            long imageId = process.commit();
            Image newImage = Case.getCurrentCase().addImage(imgPaths, imageId, timezone);
            
            if (indexImage) {
                // Must use a Lookup here to prevent a circular dependency
                // between Case and KeywordSearch...
                Lookup.getDefault().lookup(AddImageAction.IndexImageTask.class).runTask(newImage);
            }
        } finally {
            // Can't bail and revert image add after commit, so disable image cleanup
            // task
            AddImageAction.CleanupTask cleanupImage = (AddImageAction.CleanupTask) settings.getProperty(AddImageAction.IMAGECLEANUPTASK_PROP);
            cleanupImage.disable();
        }
    }
}
