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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import org.openide.LifecycleManager;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * An action wired in to the Case/Exit menu item.
 */
@ActionRegistration(displayName = "Exit", iconInMenu = true)
@ActionReference(path = "Menu/Case", position = 1000, separatorBefore = 999)
@ActionID(id = "org.sleuthkit.autopsy.casemodule.ExitAction", category = "Case")
final public class ExitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        new Thread(() -> {
            try {
                Case.closeCurrentCase();
            } catch (CaseActionException ex) {
                // RJCTODO: Pop up here
                Logger.getLogger(ExitAction.class.getName()).log(Level.SEVERE, "Error closing the current case", ex); //NON-NLS
            } finally {
                LifecycleManager.getDefault().exit();
            }
        }).start();
    }
}
