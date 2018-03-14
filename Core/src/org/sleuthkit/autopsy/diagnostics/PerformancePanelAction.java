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
package org.sleuthkit.autopsy.diagnostics;

import javax.swing.JDialog;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;

@ActionID(category = "Help", id = "org.sleuthkit.autopsy.diagnostics.PerformancePanelAction")
@ActionRegistration(displayName = "#CTL_PerformancePanelAction", lazy = true)
@ActionReference(path = "Menu/Help", position = 1437)
public final class PerformancePanelAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;

    @Override
    public void performAction() {
        JDialog dialog = new PerformancePanel();
        dialog.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        dialog.setVisible(true);
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(PerformancePanelAction.class, "CTL_PerformancePanelAction");
    }
}
