/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configuration;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import java.awt.Cursor;
import org.openide.windows.WindowManager;

@ActionID(category = "Tools", id = "viking.configuration.OpenVikingOptionsPanelAction")
@ActionRegistration(
        displayName = "#CTL_OpenVikingOptionsPanelAction",
        lazy = false)
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 1600)
})
@NbBundle.Messages(value = "CTL_OpenVikingOptionsPanelAction=Viking Options")

/**
 * Custom Viking options action that opens up the Viking options panel.
 * Contributes an entry in Tools menu
 */
public final class OpenOptionsPanelAction extends SystemAction implements ActionListener {

    private static final String ACTION_NAME = NbBundle.getMessage(OpenOptionsPanelAction.class, "OpenVikingOptionsPanelAction.name");

    @Override
    public void actionPerformed(ActionEvent e) {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        OptionsDialog dialog = new OptionsDialog(null, true);
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
