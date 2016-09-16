/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.awt.Toolkit;
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
import org.openide.util.NbBundle.Messages;

/**
 * A dialog for adding or editing an external viewer rule
 */
public class AddRuleDialog extends JDialog {

    private ExternalViewerRule rule;
    private final AddRulePanel addRulePanel;
    private BUTTON_PRESSED result;
    private JButton saveButton;
    private JButton closeButton;

    enum BUTTON_PRESSED {
        OK, CANCEL;
    }

    /**
     * Creates a dialog for creating an external viewer rule
     */
    @Messages({"AddRuleDialog.title=External Viewer Rule"})
    AddRuleDialog() {
        super(new JFrame(Bundle.AddRuleDialog_title()), Bundle.AddRuleDialog_title(), true);
        addRulePanel = new AddRulePanel();
        this.display();
    }

    /**
     * Creates a dialog for editing an external viewer rule
     *
     * @param rule ExternalViewerRule to be edited
     */
    AddRuleDialog(ExternalViewerRule rule) {
        super(new JFrame(Bundle.AddRuleDialog_title()), Bundle.AddRuleDialog_title(), true);
        addRulePanel = new AddRulePanel(rule);
        this.display();
    }

    /**
     * Displays the add external viewer rule dialog.
     */
    @NbBundle.Messages({
        "AddRuleDialog.addButton.title=Save",
        "AddRuleDialog.cancelButton.title=Cancel"})
    private void display() {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int width = this.getSize().width;
        int height = this.getSize().height;
        setLocation((screenDimension.width - width) / 2, (screenDimension.height - height) / 2);

        add(this.addRulePanel, BorderLayout.PAGE_START);

        // Add a save button.
        saveButton = new JButton(Bundle.AddRuleDialog_addButton_title());
        saveButton.addActionListener((ActionEvent e) -> {
            doButtonAction(true);
        });

        // Add a close button.
        closeButton = new JButton(Bundle.AddRuleDialog_cancelButton_title());
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
            if (evt.getPropertyName().equals(AddRulePanel.EVENT.CHANGED.toString())) {
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
        getRootPane().setDefaultButton(saveButton);
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
