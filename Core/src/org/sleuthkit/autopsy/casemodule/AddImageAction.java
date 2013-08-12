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
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Image;

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
    // String[]: array of paths to each data source selected
    static final String DATASOURCEPATH_PROP = "dataSrcPath";
    // String data source type selected
    static final String DATASOURCETYPE_PROP = "dataSrcType";
    // CleanupTask: task to clean up the database file if wizard errors/is cancelled after it is created
    static final String IMAGECLEANUPTASK_PROP = "finalFileCleanup";
    // int: the next availble id for a new image
    static final String IMAGEID_PROP = "imageId";
    // AddImageProcess: the next availble id for a new image
    static final String PROCESS_PROP = "process";
    // boolean: whether or not to lookup files in the hashDB
    static final String LOOKUPFILES_PROP = "lookupFiles";
    // boolean: whether or not to skip processing orphan files on FAT filesystems
    static final String NOFATORPHANS_PROP = "nofatorphans";

    
    static final Logger logger = Logger.getLogger(AddImageAction.class.getName());

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
        Logger.noteAction(AddImageAction.class);
        
        final IngestConfigurator ingestConfig = Lookup.getDefault().lookup(IngestConfigurator.class);
        if (null != ingestConfig && ingestConfig.isIngestRunning()) {
            final String msg = "<html>Ingest is ongoing on another data source. Adding a new source now might slow down the current ingest.<br />Do you want to proceed and add a new data source now?</html>";
            if (JOptionPane.showConfirmDialog(null, msg, "Ingest in progress", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION) {
                return;
            }
        }

        iterator = new AddImageWizardIterator(this);
        wizardDescriptor = new WizardDescriptor(iterator);
        wizardDescriptor.setTitle("Add Data Source");
        wizardDescriptor.putProperty(NAME, e);

        if (dialog != null) {
            dialog.setVisible(false); // hide the old one
        }
        dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();
        
        // Do any cleanup that needs to happen (potentially: stopping the
        //add-image process, reverting an image)
        runCleanupTasks();
    }    
    
    /**
     * Closes the current dialog and wizard, and opens a new one. Used in the
     * "Add another image" action on the last panel
     */
    void restart() {
        // Simulate clicking finish for the current dialog
        wizardDescriptor.setValue(WizardDescriptor.FINISH_OPTION);
        dialog.setVisible(false);

        // let the previous call to AddImageAction.actionPerformed() finish up
        // after the wizard, this will run when its it's done
        final Runnable r = new Runnable()  {
            @Override
            public void run() {
                actionPerformed(null);
            }
        };
        
        SwingUtilities.invokeLater(r);
    }
    
    public interface IndexImageTask {
        void runTask(Image newImage);
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
        return NbBundle.getMessage(AddImageAction.class, "CTL_AddImageButton");
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
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_add_image.png"));
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
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
