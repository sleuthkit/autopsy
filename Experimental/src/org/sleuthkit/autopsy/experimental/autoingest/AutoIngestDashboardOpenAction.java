/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.experimental.autoingest.AutoIngestDashboardOpenAction")
@ActionReference(path = "Menu/Tools", position = 104)
@ActionRegistration(displayName = "#CTL_AutoIngestDashboardOpenAction", lazy = false)
@Messages({"CTL_AutoIngestDashboardOpenAction=Auto Ingest Dashboard"})
public final class AutoIngestDashboardOpenAction extends CallableSystemAction {
    
    private static final Logger LOGGER = Logger.getLogger(AutoIngestDashboardOpenAction.class.getName());
    private static final String DISPLAY_NAME = Bundle.CTL_AutoIngestDashboardOpenAction();
    
    @Override
    public boolean isEnabled() {
        return (UserPreferences.getIsMultiUserModeEnabled());
    }
    
    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {
        AutoIngestDashboardTopComponent.openTopComponent();
    }
    
    @Override
    public String getName() {
        return DISPLAY_NAME;
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