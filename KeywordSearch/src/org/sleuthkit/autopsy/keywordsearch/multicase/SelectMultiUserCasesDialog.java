/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.keywordsearch.multicase;
import java.awt.Dialog;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import org.openide.nodes.Node;
import org.openide.windows.WindowManager;

/**
 * Dialog that will display the SelectMultiUserCasesPanel
 */
public class SelectMultiUserCasesDialog extends javax.swing.JDialog {
    private static final long serialVersionUID = 1L;
    private static SelectMultiUserCasesDialog instance;
    private static SelectMultiUserCasesPanel multiUserCasesPanel;

    /**
     * Gets the singleton JDialog that allows a user to open a multi-user case.
     *
     * @return The singleton JDialog instance.
     */
    public synchronized static SelectMultiUserCasesDialog getInstance() {
        if (instance == null) {
            instance = new SelectMultiUserCasesDialog();
            instance.init();
        }
        return instance;
    }
    
    /**
     * Listen for new case selections from the user.
     * 
     * @param l Listener on new case selection events
     */
    void subscribeToNewCaseSelections(ActionListener l) {
        multiUserCasesPanel.subscribeToNewCaseSelections(l);
    }
    
    /**
     * Set the node selections for the window
     * 
     * @param selections Nodes to be automatically selected in the explorer view
     */
    void setNodeSelections(Node[] selections) {
        try {
            multiUserCasesPanel.setSelections(selections);
        } catch (PropertyVetoException ex) {
            
        }
    } 

    /**
     * Constructs a singleton JDialog that allows a user to open a multi-user
     * case.
     */
    private SelectMultiUserCasesDialog() {
        super(WindowManager.getDefault().getMainWindow(), "Select Multi-User Cases", Dialog.ModalityType.APPLICATION_MODAL);
    }
    
    

    /**
     * Registers a keyboard action to hide the dialog when the escape key is
     * pressed and adds a OpenMultiUserCasePanel child component.
     */
    private void init() {
        multiUserCasesPanel = new SelectMultiUserCasesPanel(this);
        add(multiUserCasesPanel);
        pack();
        setResizable(false);
        multiUserCasesPanel.refreshDisplay();
    }
}