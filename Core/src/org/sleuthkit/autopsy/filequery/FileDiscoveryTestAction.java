/*
 * Autopsy
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
package org.sleuthkit.autopsy.filequery;

import java.util.logging.Level;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Class to test the file search API. Allows the user to run searches and see
 * results.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.newpackage.FileDiscoveryTestAction")
@ActionReference(path = "Menu/Tools", position = 1854, separatorBefore = 1853)
@ActionRegistration(displayName = "#CTL_FileDiscoveryTestAction", lazy = false)
@NbBundle.Messages({"CTL_FileDiscoveryTestAction=Test file discovery"})
public final class FileDiscoveryTestAction extends CallableSystemAction {

    private final static Logger logger = Logger.getLogger(FileDiscoveryTestAction.class.getName());
    private static final String DISPLAY_NAME = "Test file discovery";
    private static final long serialVersionUID = 1L;

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {

        // Load the central repository database.
        EamDb crDb = null;
        if (EamDb.isEnabled()) {
            try {
                crDb = EamDb.getInstance();
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error loading central repository database", ex);
                return;
            }
        }
        //modal set to true currently to prevent multiple dialogs being opened because messaging uses a shared eventbus
        FileDiscoveryDialog dialog = new FileDiscoveryDialog(WindowManager.getDefault().getMainWindow(), true, Case.getCurrentCase().getSleuthkitCase(), crDb);
        // Display the dialog
        dialog.display();

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
