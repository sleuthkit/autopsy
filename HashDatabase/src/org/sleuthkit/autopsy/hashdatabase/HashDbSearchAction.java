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
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationCleanDialog;

/**
 * The HashDbSearchAction opens the HashDbSearchPanel in a dialog.
 */
class HashDbSearchAction extends CallableSystemAction {

    static final String ACTION_NAME = "Hash File Search";

    @Override
    public void performAction() {
        final HashDbSearchPanel panel = HashDbSearchPanel.getDefault();
        final AdvancedConfigurationCleanDialog dialog = new AdvancedConfigurationCleanDialog();
        panel.cancelButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.close();
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
