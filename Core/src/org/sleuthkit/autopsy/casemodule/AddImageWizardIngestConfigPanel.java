/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import org.sleuthkit.autopsy.ingest.IngestConfigurator;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
/**
 * second panel of add image wizard, allows user to configure ingest modules.
 *
 * TODO: review this for dead code. think about moving logic of adding image to
 * 3rd panel( {@link  AddImageWizardAddingProgressPanel}) separate class -jm
 */
class AddImageWizardIngestConfigPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(AddImageWizardIngestConfigPanel.class.getName());
    private IngestConfigurator ingestConfig;
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private Component component = null;
    
    private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
    private boolean ingested = false;
    private boolean readyToIngest = false;
    
    // task that will clean up the created database file if the wizard is cancelled before it finishes
    private AddImageAction.CleanupTask cleanupTask; 
   
    private AddImageAction addImageAction;
    
    private AddImageWizardAddingProgressPanel progressPanel;
    private AddImageWizardChooseDataSourcePanel dataSourcePanel;
    
    private DataSourceProcessor dsProcessor;
    

    AddImageWizardIngestConfigPanel(AddImageWizardChooseDataSourcePanel dsPanel, AddImageAction action, AddImageWizardAddingProgressPanel proPanel) {
        this.addImageAction = action;
        this.progressPanel = proPanel;
        this.dataSourcePanel = dsPanel;
        
        ingestConfig = Lookup.getDefault().lookup(IngestConfigurator.class);
        List<String> messages = ingestConfig.setContext(AddImageWizardIngestConfigPanel.class.getCanonicalName());
        if (messages.isEmpty() == false) {
            StringBuilder warning = new StringBuilder();
            for (String message : messages) {
                warning.append(message).append("\n");
            }
            JOptionPane.showMessageDialog(null, warning.toString());
        }
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
            component = new AddImageWizardIngestConfigVisual(ingestConfig.getIngestConfigPanel());
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
        JButton cancel = new JButton("Cancel");
        cancel.setEnabled(false);
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, cancel});
        cleanupTask = null;
        readyToIngest = false;

        newContents.clear();
       
        // Start processing the data source by handing it off to the selected DSP, 
        // so it gets going in the background while the user is still picking the Ingest modules
        startDataSourceProcessing(settings);
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
        //save previously selected config
        ingestConfig.save();
        // Start ingest if it hasn't already been started
        readyToIngest = true;
        startIngest();
    }

    /**
     * Start ingest after verifying we have a new image, we are ready to ingest,
     * and we haven't already ingested.
     */
    private void startIngest() {
        if (!newContents.isEmpty() && readyToIngest && !ingested) {
            ingested = true;
            ingestConfig.setContent(newContents);
            ingestConfig.start();
            progressPanel.setStateFinished();

        }
    }
    
     /**
     * Starts the Data source processing by kicking off the selected DataSourceProcessor
     */
    private void startDataSourceProcessing(WizardDescriptor settings) {
        
       
       
        // Add a cleanup task to interrupt the background process if the
        // wizard exits while the background process is running.
        cleanupTask = addImageAction.new CleanupTask() {
            @Override
            void cleanup() throws Exception {
                cancelDataSourceProcessing();
            }
        };
        
        cleanupTask.enable();
       
         // get the selected DSProcessor
        dsProcessor =  dataSourcePanel.getComponent().getCurrentDSProcessor();
        
        DataSourceProcessorCallback cbObj = new DataSourceProcessorCallback () {
            @Override
            public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList,  List<Content> contents)  {
                dataSourceProcessorDone(result, errList, contents );
            }
            
        };
        
        progressPanel.setStateStarted();
        
        // Kick off the DSProcessor 
        dsProcessor.run(progressPanel.getDSPProgressMonitorImpl(), cbObj);
     
    }

    /*
     * Cancels the data source processing - in case the users presses 'Cancel'
     */
    private void cancelDataSourceProcessing() {
         dsProcessor.cancel();
    }
    
    /*
     * Callback for the data source processor. 
     * Invoked by the DSP on the EDT thread, when it finishes processing the data source.
     */
    private void dataSourceProcessorDone(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList,  List<Content> contents) {
         
         // disable the cleanup task
        cleanupTask.disable();
       
        // Get attention for the process finish
        java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
        AddImageWizardAddingProgressVisual panel = progressPanel.getComponent();
        if (panel != null) {
            Window w = SwingUtilities.getWindowAncestor(panel);
            if (w != null) {
                w.toFront();
            }
        }
        // Tell the panel we're done
        progressPanel.setStateFinished();
                
      
        //check the result and display to user
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS)
            progressPanel.getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.noErrs.text"), 100, Color.black);
        else 
            progressPanel.getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.errs.text"), 100, Color.red);
       
        
        //if errors, display them on the progress panel
        boolean critErr = false;
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS) {
            critErr = true;
        }
        for ( String err: errList ) {
            //  TBD: there probably should be an error level for each error
            progressPanel.addErrors(err, critErr);
        }
      
        newContents.clear();
        newContents.addAll(contents);
        
        //notify the UI of the new content added to the case
        if (!newContents.isEmpty()) {
            
             Case.getCurrentCase().notifyNewDataSource(newContents.get(0));
        }

        
       // Start ingest if we can
        progressPanel.setStateStarted();
        startIngest();
        
    }
}
