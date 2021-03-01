/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.Frame;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * An Action that opens the Host Management window.
 */
@ActionID(category = "Case", id = "org.sleuthkit.autopsy.datamodel.hosts.OpenHostsAction")
@ActionRegistration(displayName = "#CTL_OpenHosts", lazy = false)
@Messages({
    "CTL_OpenHosts=Manage Hosts",})
public final class OpenHostsAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;

    /**
     * Main constructor.
     */
    OpenHostsAction() {
        putValue(Action.NAME, Bundle.CTL_OpenHosts());
        this.setEnabled(false);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            setEnabled(null != evt.getNewValue());
        });
    }

    @Override
    public void performAction() {
        Frame parent = WindowManager.getDefault().getMainWindow();
        ManageHostsDialog dialog = new ManageHostsDialog(parent);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    @NbBundle.Messages("OpenHostsAction_displayName=Hosts")
    public String getName() {
        return Bundle.OpenHostsAction_displayName();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
