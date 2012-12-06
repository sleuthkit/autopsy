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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationCleanDialog;

/**
 * The HashDbPanelSearchAction opens the HashDbSearchPanel in a dialog, and sets
 * some of it's ActionListeners to cooperate with the Dialog.
 */
class HashDbPanelSearchAction extends CallableSystemAction {

    static final String ACTION_NAME = "File Search by MD5 Hash";
    private static HashDbPanelSearchAction instance = null;
    
    HashDbPanelSearchAction() {
        super();
        setEnabled(false);
        
        Case.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(evt.getPropertyName().equals(Case.CASE_CURRENT_CASE)){
                    setEnabled(evt.getNewValue() != null);
                }
            }
            
        });
    }
    
    public static HashDbPanelSearchAction getDefault() {
        if(instance == null){
            instance = new HashDbPanelSearchAction();
        }
        return instance;
    }

    @Override
    public void performAction() {
        final HashDbSearchPanel panel = HashDbSearchPanel.getDefault();
        final AdvancedConfigurationCleanDialog dialog = new AdvancedConfigurationCleanDialog();
        // Set the dialog close button to clear then close the window
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.clear();
                dialog.close();
            }
        });
        // Have the search button close the window after searching
        panel.addSearchActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(panel.search()) {
                    panel.clear();
                    dialog.close();
                }
            }
        });
        // Have the search button close the window after searching
        panel.addCancelActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panel.clear();
                dialog.close();
            }
        });
        panel.refresh();
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
