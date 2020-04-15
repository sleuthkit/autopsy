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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

/**
 * Dialog used for editing or adding file types.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AddFileTypeDialog extends JDialog {

    /**
     * Enum used for letting creator of this dialog know whether or not OK was
     * pressed.
     */
    enum BUTTON_PRESSED {

        OK, CANCEL;
    }

    private static final long serialVersionUID = 1L;
    private static final Dimension BUTTON_SIZE = new Dimension(85, 23);
    private FileType fileType;
    final private AddFileTypePanel addMimeTypePanel;
    private BUTTON_PRESSED result;
    private JButton okButton;
    private JButton cancelButton;

    /**
     * Creates a dialog for creating a file type
     */
    @Messages({"AddMimeTypedialog.title=File Type"})
    AddFileTypeDialog() {
        super(WindowManager.getDefault().getMainWindow(), Bundle.AddMimeTypedialog_title(), true);
        addMimeTypePanel = new AddFileTypePanel();
        init();
    }

    /**
     * Creates a dialog for editing a file type
     *
     * @param fileType The file type to edit
     */
    AddFileTypeDialog(FileType fileType) {
        super(WindowManager.getDefault().getMainWindow(), Bundle.AddMimeTypedialog_title(), true);
        addMimeTypePanel = new AddFileTypePanel(fileType);
        init();
    }

    /**
     * Do initialization of dialog components.
     */
        @NbBundle.Messages({
        "AddMimeTypeDialog.addButton.title=OK",
        "AddMimeTypeDialog.cancelButton.title=Cancel"})
    private void init() {
        setLayout(new BorderLayout());

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        add(this.addMimeTypePanel, BorderLayout.PAGE_START);

        // Add the OK button
        okButton = new JButton(Bundle.AddMimeTypeDialog_addButton_title());
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
        cancelButton = new JButton(Bundle.AddMimeTypeDialog_cancelButton_title());
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
        this.addMimeTypePanel.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(AddFileTypePanel.EVENT.SIG_LIST_CHANGED.toString())) {
                    enableOkButton();
                }
            }
        });
        enableOkButton();
        setResizable(false);
        pack();
    }

    /**
     * Displays the add file type dialog.
     *
     */
    void display() {
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
    private void doButtonAction(boolean okPressed) {
        if (okPressed) {
            FileType fType = addMimeTypePanel.getFileType();
            if (fType != null) {
                this.fileType = fType;
                this.result = BUTTON_PRESSED.OK;
                setVisible(false);
            }
        } else {
            this.fileType = null;
            this.result = BUTTON_PRESSED.CANCEL;
            setVisible(false);
        }
    }

    /**
     * Gets the file type of this dialog
     *
     * @return The file type
     */
    FileType getFileType() {
        return fileType;
    }

    /**
     * Gets the button pressed on this dialog
     *
     * @return The button pressed to close the dialog
     */
    BUTTON_PRESSED getResult() {
        return result;
    }

    private void enableOkButton() {
        this.okButton.setEnabled(addMimeTypePanel.hasSignature());
    }

}
