/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import javax.swing.Action;
import javax.swing.JFrame;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;

@ActionID(category = "Case", id = "org.sleuthkit.autopsy.casemodule.UnpackagePortableCaseAction")
@ActionRegistration(displayName = "#CTL_UnpackagePortableCaseAction", lazy = false)
@Messages({"CTL_UnpackagePortableCaseAction=Unpack and Open Portable Case"})
/**
 * Unpackage Portable Case action for the Case menu to allow the user to
 * decompress a portable case and open it.
 */
public class UnpackagePortableCaseAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;

    UnpackagePortableCaseAction() {
        putValue(Action.NAME, Bundle.CTL_UnpackagePortableCaseAction());
        this.setEnabled(true);
    }

    @Override
    public void performAction() {
        JFrame parentFrame = (JFrame) WindowManager.getDefault().getMainWindow();
        UnpackagePortableCaseDialog dialog = new UnpackagePortableCaseDialog(parentFrame);
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    @Override
    public String getName() {
        return Bundle.CTL_UnpackagePortableCaseAction();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }
}

