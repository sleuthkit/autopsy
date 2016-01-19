/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Image;

/**
 * An action that adds a data source to the current case.
 *
 * RC: This action needs to be enabled and disabled as cases are opened and
 * closed. Currently this is done using
 * CallableSystemAction.get(AddImageAction.class).setEnabled().
 */
public final class AddImageAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddImageAction.class.getName());
    private final ChangeSupport cleanupSupport;
    private final JButton toolbarButton;
    private WizardDescriptor wizardDescriptor;
    private WizardDescriptor.Iterator<WizardDescriptor> iterator;

    /**
     * Constructs an action that adds a data source to the current case.
     */
    public AddImageAction() {
        cleanupSupport = new ChangeSupport(this);
        putValue(Action.NAME, NbBundle.getMessage(AddImageAction.class, "CTL_AddImage"));
        toolbarButton = new JButton();
        toolbarButton.addActionListener(AddImageAction.this::actionPerformed);
        setEnabled(false);
    }

    /**
     * Displays the first panel of the add data source wizard.
     *
     * @param notUsed An action event, may be null.
     */
    @Override
    public void actionPerformed(ActionEvent notUsed) {
        /*
         * If ingest is running, confirm that the user wants to add another data
         * source at this time, instead of waiting for the current ingest job to
         * complete.
         */
        if (IngestManager.getInstance().isIngestRunning()) {
            if (JOptionPane.showConfirmDialog(null,
                    NbBundle.getMessage(this.getClass(), "AddImageAction.ingestConfig.ongoingIngest.msg"),
                    NbBundle.getMessage(this.getClass(), "AddImageAction.ingestConfig.ongoingIngest.title"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION) {
                return;
            }
        }

        /*
         * Construct and display the wizard.
         */
        iterator = new AddImageWizardIterator();
        wizardDescriptor = new WizardDescriptor(iterator);
        wizardDescriptor.setTitle(NbBundle.getMessage(this.getClass(), "AddImageAction.wizard.title"));
        Dialog dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();

        /*
         * Run any registered cleanup tasks by firing a change event. This will
         * cause the stateChanged method of any implementations of the inner,
         * abstract CleanupTask class to call their cleanup methods (assuming
         * they have not done an override of stateChanged), after which the
         * CleanupTasks are unregistered.
         *
         * RC: This is a convoluted and error-prone way to implement clean up.
         * Fortunately, it is confined to this package.
         */
        cleanupSupport.fireChange();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void performAction() {
        actionPerformed(null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(AddImageAction.class, "CTL_AddImageButton");
    }

    /**
     * @inheritDoc
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_add_image.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }

    /**
     * Does nothing, do not use.
     *
     * @deprecated Classes in this package may call requestFocusForWizardButton
     * instead.
     */
    @Deprecated
    public void requestFocusButton(String buttonText) {
    }

    /**
     * Requests focus for an add data source wizard button.
     *
     * @param buttonText The text of the button.
     */
    void requestFocusForWizardButton(String buttonText) {
        for (Object wizardButton : wizardDescriptor.getOptions()) {
            JButton button = (JButton) wizardButton;
            if (button.getText().equals(buttonText)) {
                button.setDefaultCapable(true);
                button.requestFocus();
            }
        }
    }

    /**
     * Enabled instances of this class are called to do clean up after the add
     * data source wizard is closed. The instances are disabled after the
     * cleanUp method is called. Implementations should not override
     * stateChanged, and should not re-enable themselves after cleanUp is
     * called. To stop cleanUp being called, call disable before the wizard is
     * dismissed.
     *
     * Instances must be constructed using a reference to an AddImageAction
     * object because this is a non-static inner class.
     *
     * RC: This is a convoluted and error-prone way to implement clean up.
     * Fortunately, it is confined to this package.
     */
    abstract class CleanupTask implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            /*
             * actionPerformed fires this event after the add data source wizard
             * is closed.
             */
            try {
                cleanup();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error cleaning in add data source wizard clean up task", ex); //NON-NLS
            } finally {
                /*
                 * Clean up tasks should only be done exactly once.
                 */
                disable();
            }
        }

        /**
         * Adds this task to the list of tasks to be done when the wizard
         * closes.
         */
        void enable() {
            cleanupSupport.addChangeListener(this);
        }

        /**
         * Removes this task from the list of tasks to be done when the wizard
         * closes.
         */
        void disable() {
            cleanupSupport.removeChangeListener(this);
        }

        /**
         * Performs cleanup action when called
         *
         * @throws Exception
         */
        abstract void cleanup() throws Exception;

    }

    @Deprecated
    public interface IndexImageTask {

        void runTask(Image newImage);
    }

}
