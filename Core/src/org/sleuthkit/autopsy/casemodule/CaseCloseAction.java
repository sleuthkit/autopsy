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
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * An action to close the current case and pop up the start up window that
 * allows a user to open another case. 
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.casemodule.CaseCloseAction")
@ActionRegistration(displayName = "#CTL_CaseCloseAct", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Toolbars/Case", position = 104)
})
public final class CaseCloseAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final long serialVersionUID = 1L;
    private final JButton toolbarButton = new JButton();

    /**
     * The constructor for this class
     */
    public CaseCloseAction() {
        putValue("iconBase", "org/sleuthkit/autopsy/images/close-icon.png"); // put the icon NON-NLS
        putValue(Action.NAME, NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct")); // put the action Name
        toolbarButton.addActionListener(CaseCloseAction.this::actionPerformed);
        this.setEnabled(false);
    }

    /**
     * Closes the current opened case.
     *
     * @param e the action event for this method
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        /*
         * If ingest is running, give the user the option to abort changing
         * cases.
         */
        if (IngestManager.getInstance().isIngestRunning()) {
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning"), // RJCTODO
                    NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title"), // RJCTODO
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object response = DialogDisplayer.getDefault().notify(descriptor);
            if (DialogDescriptor.NO_OPTION == response) {
                return;
            }
        }

        /*
         * Close the case.
         */
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                Case.closeCurrentCase();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    // RJCTODO: Pop up error and log
                }
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                StartupWindowProvider.getInstance().open();
            }
        }.execute();
    }

    /**
     * This method does nothing. Use the "actionPerformed(ActionEvent e)"
     * instead of this method.
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
        return NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct");
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
     * @return component the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_close_case.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }
}
