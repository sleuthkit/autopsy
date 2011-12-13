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
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.logging.Log;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;

/**
 * The action to add an image to the current Case. This action should be disabled
 * on creation and it will be enabled on new case creation or case opened.
 *
 * @author jantonius
 */
// TODO: need annotation because there's a "Lookup.getDefault().lookup(AddImageAction.class)"
// used in AddImageWizardPanel1 (among other places). It really shouldn't be done like that.
@ServiceProvider(service = AddImageAction.class)
public final class AddImageAction extends CallableSystemAction implements Presenter.Toolbar {

    // Keys into the WizardDescriptor properties that pass information between stages of the wizard
    // <TYPE>: <DESCRIPTION>
    // String: time zone that the image is from
    static final String TIMEZONE_PROP = "timeZone";
    // String[]: task to clean up the database file if wizard errors/is cancelled after it is created
    static final String IMGPATHS_PROP = "imgPaths";
    // CleanupTask: task to clean up the database file if wizard errors/is cancelled after it is created
    static final String IMAGECLEANUPTASK_PROP = "finalFileCleanup";
    // int: the next availble id for a new image
    static final String IMAGEID_PROP = "imageId";
    // AddImageProcess: the next availble id for a new image
    static final String PROCESS_PROP = "process";
    // boolean: whether or not to index the image in Solr
    static final String SOLR_PROP = "indexInSolr";


    private WizardDescriptor wizardDescriptor;
    private WizardDescriptor.Iterator<WizardDescriptor> iterator;
    private Dialog dialog;
    private JButton toolbarButton = new JButton();

    /**
     * The constructor for AddImageAction class
     */
    public AddImageAction() {
        putValue(Action.NAME, NbBundle.getMessage(AddImageAction.class, "CTL_AddImage")); // set the action Name

        // set the action for the toolbar button
        toolbarButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                AddImageAction.this.actionPerformed(e);
            }
        });

        this.setEnabled(false); // disable this action class
    }

    /**
     * Pop-up the "Add Image" wizard panel.
     * 
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(AddImageAction.class);

        iterator = new AddImageWizardIterator(this);
        wizardDescriptor = new WizardDescriptor(iterator);
        wizardDescriptor.setTitle("Add Image");
        wizardDescriptor.putProperty(NAME, e);
        wizardDescriptor.putProperty(SOLR_PROP, false);


        if (dialog != null) {
            dialog.setVisible(false); // hide the old one
        }
        dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();

        boolean cancelled = wizardDescriptor.getValue() != WizardDescriptor.FINISH_OPTION;
        // @@@ Why don't we commit and revert in the same general area????
        if (!cancelled) {
            // commit anything
            try {
                commitImage(wizardDescriptor);
            } catch (Exception ex) {
                // Log error/display warning
                Logger logger = Logger.getLogger(AddImageAction.class.getName());
                logger.log(Level.SEVERE, "Error adding image to case.", ex);
            }
        }
        
        // Do any cleanup that needs to happen (potentially: stopping the
        //add-image process, reverting an image)
        runCleanupTasks();
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
        
        AddImageProcess process = (AddImageProcess) settings.getProperty(PROCESS_PROP);
        
        try {
            long imageId = process.commit();
            Image newImage = Case.getCurrentCase().addImage(imgPaths, imageId, timezone);
            
            if (indexImage) {
                // Must use a Lookup here to prevent a circular dependency
                // between Case and KeywordSearch...
                Lookup.getDefault().lookup(IndexImageTask.class).runTask(newImage);
            }
        } finally {
            // Can't bail and revert image add after commit, so disable image cleanup
            // task
            CleanupTask cleanupImage = (CleanupTask) settings.getProperty(IMAGECLEANUPTASK_PROP);
            cleanupImage.disable();
        }
    }
    
    
    /**
     * Closes the current dialog and wizard, and opens a new one. Used in the
     * "Add another image" action on the last panel
     */
    public void restart() {
        // Simulate clicking finish for the current dialog
        wizardDescriptor.setValue(WizardDescriptor.FINISH_OPTION);
        dialog.setVisible(false);


        // let the previous call to AddImageAction.actionPerformed() finish up
        // after the wizard, this will run when its it's done
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                actionPerformed(null);
            }
        });
    }
    
    public interface IndexImageTask {
        void runTask(Image newImage);
    }
    
    

    /**
     * Closes the current dialog and wizard and indexes the newly added image.
     * Used in the "Index for keyword search" action on the last panel
     */
    public void indexImage() {
        // Simulate clicking finish for the current dialog
        wizardDescriptor.setValue(WizardDescriptor.FINISH_OPTION);
        wizardDescriptor.putProperty(SOLR_PROP, true);
        dialog.setVisible(false);
    }

    /**
     * This method does nothing. Use the "actionPerformed(ActionEvent e)" instead of this method.
     */
    @Override
    public void performAction() {
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(AddImageAction.class, "CTL_AddImage");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Returns the toolbar component of this action
     * 
     * @return component  the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("addImage-icon.png"));
        toolbarButton.setIcon(icon);
        return toolbarButton;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value  whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }

    /**
     * Set the focus to the button of the given name on this wizard dialog.
     *
     * Note: the name of the buttons that available are "Next >", "< Back",
     * "Cancel", and "Finish". If you change the name of any of those buttons,
     * use the latest name instead.
     *
     * @param buttonText  the text of the button
     */
    public void requestFocusButton(String buttonText) {
        // get all buttons on this wizard panel
        Object[] wizardButtons = wizardDescriptor.getOptions();
        for (int i = 0; i < wizardButtons.length; i++) {
            JButton tempButton = (JButton) wizardButtons[i];
            if (tempButton.getText().equals(buttonText)) {
                tempButton.setDefaultCapable(true);
                tempButton.requestFocus();
            }
        }
    }
    
    /**
     * Run and clear any cleanup tasks for wizard closing that might be
     * registered. This should be run even when the wizard exits cleanly, so
     * that no cleanup actions remain the next time the wizard is run.
     */
    private void runCleanupTasks() {
        cleanupSupport.fireChange();
    }
    
    ChangeSupport cleanupSupport = new ChangeSupport(this);

    /**
     * Instances of this class implement the cleanup() method to run cleanup
     * code when the wizard exits.
     * 
     * After enable() has been called on an instance it will run once after the
     * wizard closes (on both a cancel and a normal finish).
     * 
     * If disable() is called before the wizard exits, the task will not run.
     */
    abstract class CleanupTask implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            // fired by AddImageAction.runCleanupTasks() after the wizard closes 
            try {
                cleanup();
            } catch (Exception ex) {
                Logger logger = Logger.getLogger(this.getClass().getName());
                logger.log(Level.WARNING, "Error cleaning up from wizard.", ex);
            } finally {
                disable(); // cleanup tasks should only run once.
            }
        }

        /**
         * Add task to the enabled list to run when the wizard closes.
         */
        public void enable() {
            cleanupSupport.addChangeListener(this);
        }

        /**
         * Performs cleanup action when called
         * @throws Exception 
         */
        abstract void cleanup() throws Exception;

        /**
         * Remove task from the enabled list.
         */
        public void disable() {
            cleanupSupport.removeChangeListener(this);
        }
    }
}
