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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;

/**
 *
 * @author dfickling
 */
class KeywordSearchConfigurationAction extends CallableSystemAction{
    
    private static final String ACTION_NAME = org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "ListBundleConfig");

    @Override
    public void performAction() {
        final KeywordSearchConfigurationPanel panel = KeywordSearchConfigurationPanel.getDefault();
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();
        dialog.addApplyButtonListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panel.save();
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
