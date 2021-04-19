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
package org.sleuthkit.autopsy.actions;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.LifecycleManager;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.StartupWindowProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * The action associated with the Case/Exit menu item. It closes the current
 * case, if any, and shuts down the application.
 */
@ActionRegistration(displayName = "#CTL_ExitAction", iconInMenu = true)
@ActionReference(path = "Menu/Case", position = 1000, separatorBefore = 999)
@ActionID(id = "org.sleuthkit.autopsy.casemodule.ExitAction", category = "Case")
final public class ExitAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(ExitAction.class.getName());

    @NbBundle.Messages({
        "ExitAction.confirmationDialog.title=Ingest is Running",
        "ExitAction.confirmationDialog.message=Ingest is running, are you sure you want to exit?",
        "# {0} - exception message", "ExitAction.messageBox.caseCloseExceptionMessage=Error closing case: {0}"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        if (IngestRunningCheck.checkAndConfirmProceed(Bundle.ExitAction_confirmationDialog_title(), Bundle.ExitAction_confirmationDialog_message())) {
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
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, "Unexpected interrupt closing the current case", ex);
                    } catch (ExecutionException ex) {
                        logger.log(Level.SEVERE, "Error closing the current case", ex);
                        MessageNotifyUtil.Message.error(Bundle.ExitAction_messageBox_caseCloseExceptionMessage(ex.getMessage()));
                    } finally {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        LifecycleManager.getDefault().exit();
                    }
                }
            }.execute();
        }
    }
}
