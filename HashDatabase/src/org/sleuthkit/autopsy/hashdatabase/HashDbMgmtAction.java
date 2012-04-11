/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Level;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 * The HashDbMgmtAction opens the HashDbMgmtPanel in a dialog, and saves the
 * settings of the panel if the Apply button is clicked.
 * @author pmartel
 */
class HashDbMgmtAction extends CallableSystemAction {

    private static final String ACTION_NAME = "Hash Database Configuration";

    @Override
    public void performAction() {
        Log.noteAction(this.getClass());

        // initialize panel with loaded settings
        final HashDbMgmtPanel panel = HashDbMgmtPanel.getDefault();
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();
        dialog.addApplyButtonListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (panel.indexesExist()) {
                    try {
                        panel.saveSettings();
                    } catch (IOException ex) {
                        Log.get(HashDbMgmtAction.class).log(Level.WARNING, "Couldn't save hash database settings.", ex);
                    }
                    dialog.close();
                } else {
                    NotifyDescriptor d = new NotifyDescriptor.Message("All selected databases must have indexes.", NotifyDescriptor.INFORMATION_MESSAGE);
                    DialogDisplayer.getDefault().notify(d);
                }
            }
        });
        dialog.display(panel);
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    @Override
    protected boolean asynchronous() {
        return false;
    }
}
