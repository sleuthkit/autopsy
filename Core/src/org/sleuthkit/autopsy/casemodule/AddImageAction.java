/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Image;

/**
 * An action that invokes the Add Data Source wizard.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.casemodule.AddImageAction")
@ActionRegistration(displayName = "#CTL_AddImage", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Toolbars/Case", position = 100)})
@ServiceProvider(service = AddImageAction.class)
public final class AddImageAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final long serialVersionUID = 1L;
    private static final Dimension SIZE = new Dimension(875, 550);
    private final ChangeSupport cleanupSupport = new ChangeSupport(this);
    //  private final static MouseAdapter mouseDisabler = new MouseAdapter() {};

    // Keys into the WizardDescriptor properties that pass information between stages of the wizard
    // <TYPE>: <DESCRIPTION>
    // String: time zone that the image is from
    static final String TIMEZONE_PROP = "timeZone"; //NON-NLS
    // String[]: array of paths to each data source selected
    static final String DATASOURCEPATH_PROP = "dataSrcPath"; //NON-NLS
    // String data source type selected
    static final String DATASOURCETYPE_PROP = "dataSrcType"; //NON-NLS
    // CleanupTask: task to clean up the database file if wizard errors/is cancelled after it is created
    static final String IMAGECLEANUPTASK_PROP = "finalFileCleanup"; //NON-NLS
    // int: the next availble id for a new image
    static final String IMAGEID_PROP = "imageId"; //NON-NLS
    // AddImageProcess: the next availble id for a new image
    static final String PROCESS_PROP = "process"; //NON-NLS
    // boolean: whether or not to lookup files in the hashDB
    static final String LOOKUPFILES_PROP = "lookupFiles"; //NON-NLS
    // boolean: whether or not to skip processing orphan files on FAT filesystems
    static final String NOFATORPHANS_PROP = "nofatorphans"; //NON-NLS

    static final Logger logger = Logger.getLogger(AddImageAction.class.getName());
    private WizardDescriptor wizardDescriptor;
    private WizardDescriptor.Iterator<WizardDescriptor> iterator;
    private Dialog dialog;
    private final JButton toolbarButton = new JButton();

    /**
     * Constructs an action that invokes the Add Data Source wizard.
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

        /*
         * Disable this action until a case is opened. Currently, the Case class
         * enables the action.
         */
        this.setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String optionsDlgTitle = NbBundle.getMessage(this.getClass(), "AddImageAction.ingestConfig.ongoingIngest.title");
        String optionsDlgMessage = NbBundle.getMessage(this.getClass(), "AddImageAction.ingestConfig.ongoingIngest.msg");
        if (IngestRunningCheck.checkAndConfirmProceed(optionsDlgTitle, optionsDlgMessage)) {
            RootPaneContainer root = (RootPaneContainer) WindowManager.getDefault().getMainWindow();
            root.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            root.getGlassPane().setVisible(true);
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            iterator = new AddImageWizardIterator(this);
            wizardDescriptor = new WizardDescriptor(iterator);
            wizardDescriptor.setTitle(NbBundle.getMessage(this.getClass(), "AddImageAction.wizard.title"));
            wizardDescriptor.putProperty(NAME, e);
            wizardDescriptor.setTitleFormat(new MessageFormat("{0}"));

            if (dialog != null) {
                dialog.setVisible(false); // hide the old one
            }
            dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
            Dimension d = dialog.getSize();
            dialog.setSize(SIZE);
            root.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            root.getGlassPane().setVisible(false);
            dialog.setVisible(true);
            dialog.toFront();

            // Do any cleanup that needs to happen (potentially: stopping the
            //add-image process, reverting an image)
            runCleanupTasks();
        }
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
        final Runnable r = new Runnable() {
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

    @Override
    public void performAction() {
        actionPerformed(null);
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
     * Gets the help context for this action.
     *
     * @return The help context.
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Gets the toolbar component for this action.
     *
     * @return The toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_add_image.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
    }

    /**
     * Enables and disables this action.
     *
     * @param value whether to enable this action or not
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
     * @param buttonText the text of the button
     */
    public void requestFocusButton(String buttonText) {
        // get all buttons on this wizard panel
        Object[] wizardButtons = wizardDescriptor.getOptions();
        for (Object wizardButton : wizardButtons) {
            JButton tempButton = (JButton) wizardButton;
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
                logger.log(Level.WARNING, "Error cleaning up from wizard.", ex); //NON-NLS
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
         *
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
