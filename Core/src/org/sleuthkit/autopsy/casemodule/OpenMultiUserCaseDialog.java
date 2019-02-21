/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Dialog;
import javax.swing.JDialog;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * A singleton JDialog that allows a user to open a multi-user case.
 */
final class OpenMultiUserCaseDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static OpenMultiUserCaseDialog instance;
    private static OpenMultiUserCasePanel multiUserCasesPanel;

    /**
     * Gets the singleton JDialog that allows a user to open a multi-user case.
     *
     * @return The singleton JDialog instance.
     */
    public synchronized static OpenMultiUserCaseDialog getInstance() {
        if (instance == null) {
            instance = new OpenMultiUserCaseDialog();
            instance.init();
        }
        return instance;
    }

    /**
     * Constructs a singleton JDialog that allows a user to open a multi-user
     * case.
     */
    @NbBundle.Messages({
        "OpenMultiUserCaseDialog.title=Open Multi-User Case"
    })
    private OpenMultiUserCaseDialog() {
        super(WindowManager.getDefault().getMainWindow(), Bundle.OpenMultiUserCaseDialog_title(), Dialog.ModalityType.APPLICATION_MODAL);
    }

    /**
     * Registers a keyboard action to hide the dialog when the escape key is
     * pressed and adds a OpenMultiUserCasePanel child component.
     */
    private void init() {
        multiUserCasesPanel = new OpenMultiUserCasePanel(this);
        add(multiUserCasesPanel);
        pack();
        setResizable(false);
    }

    /**
     * Sets the dialog visibility. When made visible, the dialog refreshes the
     * display of its OpenMultiUserCasePanel child component.
     *
     * @param makeVisible True or false.
     */
    @Override
    public void setVisible(boolean makeVisible) {
        if (makeVisible) {
            multiUserCasesPanel.refreshDisplay();
        }
        super.setVisible(makeVisible);
    }

}
