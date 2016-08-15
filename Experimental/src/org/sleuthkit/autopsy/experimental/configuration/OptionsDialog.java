/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Cursor;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import org.openide.util.ImageUtilities;

/**
 */
public class OptionsDialog extends JDialog {

    private static final String TITLE = "Options";
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of the dialog with its own frame. Could be modal.
     *
     * @param owner         - the owner of this dialog. If this dialog should be
     *                      modal, the owner gets set to non modal.
     * @param shouldBeModal - true if this should be modal, false otherwise.
     */
    public OptionsDialog(Container owner, Boolean shouldBeModal) {
        super((Window) owner, TITLE, Dialog.ModalityType.MODELESS);
        setIconImage(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/frame.gif", false)); //NON-NLS
        if (shouldBeModal && owner instanceof JDialog) { // if called from a modal dialog, manipulate the parent be just under this in z order, and not modal.
            final JDialog pseudoOwner = (JDialog) owner;
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) { // Put it back to how it was before we manipulated it.
                    pseudoOwner.setVisible(false);
                    pseudoOwner.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
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
        this.getRootPane().registerKeyboardAction(e -> {
            this.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        add(new OptionsPanel(this));
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
        if (shouldBeModal) { // if called from a modal dialog, become modal, otherwise don't.
            setModal(true);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        setVisible(true);
    }
}
