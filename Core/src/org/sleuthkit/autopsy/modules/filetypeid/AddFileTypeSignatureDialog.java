/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;

/**
 * A dialog box that allows a user to create a file type signature, to be added
 * to a selected file type.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AddFileTypeSignatureDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final Dimension BUTTON_SIZE = new Dimension(85, 23);
    private final AddFileTypeSignaturePanel addFileTypeSigPanel;
    private static final String TITLE = NbBundle.getMessage(RunIngestModulesAction.class, "RunIngestModulesAction.name");
    private Signature signature;
    private BUTTON_PRESSED result;

    /**
     * Enum used for letting creator of this dialog know whether or not OK was
     * pressed.
     */
    enum BUTTON_PRESSED {

        OK, CANCEL;
    }

    /**
     * Creates a file type signature dialog for a new signature.
     */
    AddFileTypeSignatureDialog() {
        super(WindowManager.getDefault().getMainWindow(), TITLE, true);
        this.addFileTypeSigPanel = new AddFileTypeSignaturePanel();
        init();
    }

    /**
     * Creates a file type signature dialog for a signature being edited.
     *
     * @param toEdit The signature to edit.
     */
    AddFileTypeSignatureDialog(Signature toEdit) {
        super(WindowManager.getDefault().getMainWindow(), TITLE, true);
        this.addFileTypeSigPanel = new AddFileTypeSignaturePanel(toEdit);
        init();
    }

    private void init() {
        setLayout(new BorderLayout());

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        add(this.addFileTypeSigPanel, BorderLayout.PAGE_START);

        // Add the add/done button.
        JButton okButton;
        okButton = new JButton(Bundle.AddFileTypeSignatureDialog_addButton_title());
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(true);
            }
        });
        //setting both max and preffered size appears to be necessary to change the button size
        okButton.setMaximumSize(BUTTON_SIZE);
        okButton.setPreferredSize(BUTTON_SIZE);

        // Add a close button.
        JButton cancelButton = new JButton(Bundle.AddFileTypeSignatureDialog_cancelButton_title());
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(false);
            }
        });
        //setting both max and preffered size appears to be necessary to change the button size
        cancelButton.setMaximumSize(BUTTON_SIZE);
        cancelButton.setPreferredSize(BUTTON_SIZE);

        // Put the buttons in their own panel, under the settings panel.
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(okButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 35), new Dimension(10, 35), new Dimension(10, 35)));
        buttonPanel.add(cancelButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 35), new Dimension(10, 35), new Dimension(10, 35)));
        buttonPanel.validate();
        add(buttonPanel, BorderLayout.LINE_END);

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
        setResizable(false);
        pack();
    }

    /**
     * Gets the signature that was created by this dialog.
     *
     * @return the signature.
     */
    public Signature getSignature() {
        return signature;
    }

    /**
     * Gets which button was pressed (OK or Cancel).
     *
     * @return The result.
     */
    public BUTTON_PRESSED getResult() {
        return result;
    }

    /**
     * Displays the add signature dialog.
     *
     * @param add Whether or not this is an edit or a new window.
     */
    @Messages({
        "AddFileTypeSignatureDialog.addButton.title=OK",
        "AddFileTypeSignatureDialog.cancelButton.title=Cancel"})
    void display(boolean add) {      
        /**
         * Center the dialog.
         */
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        /**
         * Show the dialog.
         */
        setVisible(true);
    }

    /**
     * Performs actions on the fields based on whether the ok button was pressed
     * or not.
     *
     * @param okPressed Whether ok was pressed.
     */
    @Messages({"AddFileTypeSignatureDialog.invalidSignature.message=Invalid signature"})
    private void doButtonAction(boolean okPressed) {
        if (okPressed) {
            Signature sig = addFileTypeSigPanel.getSignature();
            if (sig != null) {
                this.signature = sig;
                this.result = BUTTON_PRESSED.OK;
                setVisible(false);
            }
        } else {
            this.signature = null;
            this.result = BUTTON_PRESSED.CANCEL;
            setVisible(false);
        }
    }

}
