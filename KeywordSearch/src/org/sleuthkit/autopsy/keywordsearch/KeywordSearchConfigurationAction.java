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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;

/**
 * System action to open the KeywordSearch Options panel.
 */
class KeywordSearchConfigurationAction extends CallableSystemAction {

    private static final String ACTION_NAME = org.openide.util.NbBundle.getMessage(DropdownToolbar.class, "ListBundleConfig");
    private KeywordSearchGlobalSettingsPanel panel;

    @Override
    public void performAction() {
        final KeywordSearchGlobalSettingsPanel panel = getPanel();
        panel.load();
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();
        dialog.addApplyButtonListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panel.store();
                dialog.close();
            }
        });
        WindowListener exitListener = new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                dialog.close();
                XmlKeywordSearchList.getCurrent().reload();
            }
        };
        dialog.addWindowListener(exitListener);
        dialog.display(panel);
    }

    private KeywordSearchGlobalSettingsPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchGlobalSettingsPanel();
        }
        return panel;
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
