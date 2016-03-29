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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.RunIngestModulesDialog;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;

/**
 *
 * A dialog box that allows a user to configure and execute analysis of one or
 * more data sources with ingest modules or analysis of the contents of a
 * directory with file-level ingest modules.
 */
final class AddFileTypeSignatureDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private final AddFileTypeSignaturePanel addFileTypeSigPanel;
    private static final String TITLE = NbBundle.getMessage(RunIngestModulesDialog.class, "IngestDialog.title.text");
    private Signature signature;
    private BUTTON_PRESSED result;

    /**
     * @return the signature
     */
    public Signature getSignature() {
        return signature;
    }

    enum BUTTON_PRESSED {

        ADD, CANCEL;
    }

    /**
     * Constructs a dialog box that allows a user to configure and execute
     * analysis of the contents of a directory with file-level ingest modules.
     *
     */
    AddFileTypeSignatureDialog() {
        super(new JFrame(TITLE), TITLE, true);
        this.addFileTypeSigPanel = new AddFileTypeSignaturePanel();
        this.display();
    }

    /**
     * @return the result
     */
    public BUTTON_PRESSED getResult() {
        return result;
    }

    /**
     * Displays this dialog.
     */
    @Messages({
        "AddFileTypeSignatureDialog.addButton.title=Add",
        "AddFileTypeSignatureDialog.cancelButton.title=Cancel"})
    void display() {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int width = this.getSize().width;
        int height = this.getSize().height;
        setLocation((screenDimension.width - width) / 2, (screenDimension.height - height) / 2);

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        add(this.addFileTypeSigPanel, BorderLayout.PAGE_START);

        // Add a start ingest button.
        JButton addButton = new JButton(Bundle.AddFileTypeSignatureDialog_addButton_title());
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(true);
            }
        });

        // Add a close button.
        JButton closeButton = new JButton(Bundle.AddFileTypeSignatureDialog_cancelButton_title());
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
        buttonPanel.add(addButton);
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
         * Show the dialog.
         */
        pack();
        setResizable(false);
        setVisible(true);
    }

    /**
     * Saves the ingest job settings, optionally starts an ingest job for each
     * data source, then closes the dialog
     *
     * @param okPressed True if ingest job(s) should be started, false
     *                  otherwise.
     */
    @Messages({"AddFileTypeSignatureDialog.invalidSignature.message=Invalid signature"})
    private void doButtonAction(boolean okPressed) {
        if (okPressed) {
            Signature sig = addFileTypeSigPanel.getSignature();
            if (sig != null) {
                this.signature = sig;
                this.result = BUTTON_PRESSED.ADD;
                setVisible(false);
            }
        } else {
            this.signature = null;
            this.result = BUTTON_PRESSED.CANCEL;
            setVisible(false);
        }
    }

}
