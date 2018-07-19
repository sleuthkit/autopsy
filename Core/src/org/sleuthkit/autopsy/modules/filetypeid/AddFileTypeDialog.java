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
import javax.swing.JFrame;
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
    private FileType fileType;
    final private AddFileTypePanel addMimeTypePanel;
    private BUTTON_PRESSED result;
    private JButton okButton;
    private JButton closeButton;

    /**
     * Creates a dialog for creating a file type
     */
    @Messages({"AddMimeTypedialog.title=File Type"})
    public AddFileTypeDialog() {
        super(new JFrame(Bundle.AddMimeTypedialog_title()), Bundle.AddMimeTypedialog_title(), true);
        addMimeTypePanel = new AddFileTypePanel();
        this.display(true);
    }

    /**
     * Creates a dialog for editing a file type
     *
     * @param fileType The file type to edit
     */
    public AddFileTypeDialog(FileType fileType) {
        super(new JFrame(Bundle.AddMimeTypedialog_title()), Bundle.AddMimeTypedialog_title(), true);
        addMimeTypePanel = new AddFileTypePanel(fileType);
        this.display(false);
    }

    /**
     * Displays the add file type dialog.
     *
     * @param add Whether or not this is an edit or a new window.
     */
    @NbBundle.Messages({
        "AddMimeTypeDialog.addButton.title=Add",
        "AddMimeTypeDialog.addButton.title2=Done",
        "AddMimeTypeDialog.cancelButton.title=Cancel"})
    void display(boolean add) {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        add(this.addMimeTypePanel, BorderLayout.PAGE_START);

        // Add the add/done button.
        if (add) {
            okButton = new JButton(Bundle.AddMimeTypeDialog_addButton_title());
        } else {
            okButton = new JButton(Bundle.AddMimeTypeDialog_addButton_title2());
        }
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(true);
            }
        });

        // Add a close button.
        closeButton = new JButton(Bundle.AddMimeTypeDialog_cancelButton_title());
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(false);
            }
        });

        // Put the buttons in their own panel, under the settings panel.
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 10), new Dimension(10, 10), new Dimension(10, 10)));
        buttonPanel.add(okButton);
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
        this.addMimeTypePanel.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(AddFileTypePanel.EVENT.SIG_LIST_CHANGED.toString())) {
                    enableOkButton();
                }
            }
        });
        enableOkButton();
        /**
         * Show the dialog.
         */
        pack();
        setResizable(false);
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
    public FileType getFileType() {
        return fileType;
    }

    /**
     * Gets the button pressed on this dialog
     *
     * @return The button pressed to close the dialog
     */
    public BUTTON_PRESSED getResult() {
        return result;
    }

    private void enableOkButton() {
        this.okButton.setEnabled(addMimeTypePanel.hasSignature());
    }

}
