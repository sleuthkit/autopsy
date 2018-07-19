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
package org.sleuthkit.autopsy.directorytree;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * A dialog for adding or editing an external viewer rule
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AddExternalViewerRuleDialog extends JDialog {

    private ExternalViewerRule rule;
    private final AddExternalViewerRulePanel addRulePanel;
    private BUTTON_PRESSED result;
    private JButton saveButton;
    private JButton closeButton;

    enum BUTTON_PRESSED {
        OK, CANCEL;
    }

    /**
     * Creates a dialog for creating an external viewer rule
     */
    AddExternalViewerRuleDialog() {
        super(new JFrame(NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.title")),
                NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.title"), true);
        addRulePanel = new AddExternalViewerRulePanel();
        this.display();
    }

    /**
     * Creates a dialog for editing an external viewer rule
     *
     * @param rule ExternalViewerRule to be edited
     */
    AddExternalViewerRuleDialog(ExternalViewerRule rule) {
        super(new JFrame(NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.title")),
                NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.title"), true);
        addRulePanel = new AddExternalViewerRulePanel(rule);
        this.display();
    }

    /**
     * Displays the add external viewer rule dialog.
     */
    private void display() {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());

        add(this.addRulePanel, BorderLayout.PAGE_START);

        // Add a save button.
        saveButton = new JButton(NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.saveButton.title"));
        saveButton.addActionListener((ActionEvent e) -> {
            doButtonAction(true);
        });

        // Add a close button.
        closeButton = new JButton(NbBundle.getMessage(AddExternalViewerRuleDialog.class, "AddExternalViewerRuleDialog.cancelButton.title"));
        closeButton.addActionListener((ActionEvent e) -> {
            doButtonAction(false);
        });

        // Put the buttons in their own panel, under the settings panel.
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 10), new Dimension(10, 10), new Dimension(10, 10)));
        buttonPanel.add(saveButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 10), new Dimension(10, 10), new Dimension(10, 10)));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.LINE_START);

        /**
         * Add a handler for when the dialog window is closed directly,
         * bypassing the buttons.
         */
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doButtonAction(false);
            }
        });

        /**
         * Add a listener to enable the save button when a text field in the
         * AddRulePanel is changed or modified.
         */
        this.addRulePanel.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(AddExternalViewerRulePanel.EVENT.CHANGED.toString())) {
                enableSaveButton();
            }
        });

        enableSaveButton();

        /**
         * Show the dialog.
         */
        pack();
        setResizable(false);
        setVisible(true);
    }

    /**
     * Performs actions based on whether the save button was pressed or not.
     *
     * @param savePressed Whether save was pressed.
     */
    private void doButtonAction(boolean savePressed) {
        if (savePressed) {
            ExternalViewerRule ruleFromPanel = addRulePanel.getRule();
            if (null != ruleFromPanel) {
                this.rule = ruleFromPanel;
                this.result = BUTTON_PRESSED.OK;
                setVisible(false);
            }
        } else {
            this.rule = null;
            this.result = BUTTON_PRESSED.CANCEL;
            setVisible(false);
        }
    }

    /**
     * Gets the external viewer rule of this dialog
     *
     * @return The external viewer rule
     */
    ExternalViewerRule getRule() {
        return rule;
    }

    /**
     * Gets the button pressed on this dialog
     *
     * @return The button pressed to close the dialog
     */
    BUTTON_PRESSED getResult() {
        return result;
    }

    /**
     * Enables save button once addRulePanel's fields have text in them. Maps
     * enter key to the save button.
     */
    private void enableSaveButton() {
        this.saveButton.setEnabled(addRulePanel.hasFields());
        getRootPane().setDefaultButton(saveButton);
    }
}
