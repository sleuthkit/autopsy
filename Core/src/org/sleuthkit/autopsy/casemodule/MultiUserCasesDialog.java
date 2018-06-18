/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import org.openide.windows.WindowManager;

/**
 * This class extends a JDialog and maintains the MultiUserCasesPanel.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class MultiUserCasesDialog extends JDialog {
    
    private static final long serialVersionUID = 1L;
    private static final String REVIEW_MODE_TITLE = "Open Multi-User Case";
    private static MultiUserCasesPanel multiUserCasesPanel;
    private static MultiUserCasesDialog instance;

    /**
     * Gets the instance of the MultiuserCasesDialog.
     *
     * @return The instance.
     */
    static public MultiUserCasesDialog getInstance() {
        if(instance == null) {
            instance = new MultiUserCasesDialog();
            instance.init();
        }
        return instance;
    }
    
    /**
     * Constructs a MultiUserCasesDialog object.
     */
    private MultiUserCasesDialog() {
        super(WindowManager.getDefault().getMainWindow(),
                REVIEW_MODE_TITLE,
                Dialog.ModalityType.APPLICATION_MODAL);
    }
    
    /**
     * Initializes the multi-user cases panel.
     */
    private void init() {
        getRootPane().registerKeyboardAction(
                e -> {
                    setVisible(false);
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        multiUserCasesPanel = new MultiUserCasesPanel(this);
        add(multiUserCasesPanel);
        pack();
        setResizable(false);
    }
    
    /**
     * Set the dialog visibility. When setting it to visible, the contents will
     * refresh.
     * 
     * @param value True or false. 
     */
    @Override
    public void setVisible(boolean value) {
        if(value) {
            multiUserCasesPanel.refresh();
        }
        super.setVisible(value);
    }
}
