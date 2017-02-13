/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * A dialog that displays ingest task progress snapshots.
 */
public final class IngestProgressSnapshotDialog extends JDialog {

    private static final String TITLE = NbBundle.getMessage(IngestProgressSnapshotDialog.class, "IngestProgressSnapshotDialog.title.text");
    private static final Dimension DIMENSIONS = new Dimension(500, 300);

    /**
     * Constructs a non-modal instance of the dialog with its own frame.
     */
    public IngestProgressSnapshotDialog() {
        this((Window) WindowManager.getDefault().getMainWindow(), false);
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
    }

    /**
     * Constructs an instance of the dialog with its own frame. Could be modal.
     *
     * @param owner         - the owner of this dialog. If this dialog should be
     *                      modal, the owner gets set to non modal.
     * @param shouldBeModal - true if this should be modal, false otherwise.
     */
    public IngestProgressSnapshotDialog(Container owner, Boolean shouldBeModal) {
        super((Window) owner, TITLE, ModalityType.MODELESS);
        if (shouldBeModal && owner instanceof JDialog) { // if called from a modal dialog, manipulate the parent be just under this in z order, and not modal.
            final JDialog pseudoOwner = (JDialog) owner;
            final ModalityType originalModality = pseudoOwner.getModalityType();
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) { // Put it back to how it was before we manipulated it.
                    pseudoOwner.setVisible(false);
                    pseudoOwner.setModalityType(originalModality);
                    pseudoOwner.toFront();
                    pseudoOwner.setVisible(true);
                }
            });
            pseudoOwner.setVisible(false);
            pseudoOwner.setModalityType(Dialog.ModalityType.MODELESS);
            pseudoOwner.toFront();
            pseudoOwner.repaint();
            pseudoOwner.setVisible(true);
        }
        setResizable(true);
        setLayout(new BorderLayout());
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(DIMENSIONS);
        int w = this.getSize().width;
        int h = this.getSize().height;
        setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);
        this.getRootPane().registerKeyboardAction(e -> {
            this.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        add(new IngestProgressSnapshotPanel(this));
        pack();
        setResizable(false);
        if (shouldBeModal) { // if called from a modal dialog, become modal, otherwise don't.
            setModal(true);
        }
        setVisible(true);
    }
}
