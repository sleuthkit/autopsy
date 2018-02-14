/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.progress;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.ActionListener;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;

/**
 * A progress indicator that displays progress using a modal dialog with a
 * message label, a progress bar, and optionally, a configurable set of buttons
 * with a button listener. Setting a cancelling flag which locks in a cancelling
 * message and an indeterminate progress bar is supported.
 */
@ThreadSafe
public final class ModalDialogProgressIndicator implements ProgressIndicator {

    private final Frame parent;
    private final String title;
    private final ProgressPanel progressPanel;
    private final Object[] buttonLabels;
    private final Object focusedButtonLabel;
    private final ActionListener buttonListener;
    private Dialog dialog;
    @GuardedBy("this")
    private boolean cancelling;

    /**
     * Creates a progress indicator that displays progress using a modal dialog
     * with a message label, a progress bar with a configurable set of buttons
     * with a button listener.
     *
     * @param parent             The parent frame.
     * @param title              The title for the dialog.
     * @param buttonLabels       The labels for the desired buttons.
     * @param focusedButtonLabel The label of the button that should have
     *                           initial focus.
     * @param buttonListener     An ActionListener for the buttons.
     */
    public ModalDialogProgressIndicator(Frame parent, String title, Object[] buttonLabels, Object focusedButtonLabel, ActionListener buttonListener) {
        this.parent = parent;
        this.title = title;
        progressPanel = new ProgressPanel();
        progressPanel.setIndeterminate(true);
        this.buttonLabels = buttonLabels;
        this.focusedButtonLabel = focusedButtonLabel;
        this.buttonListener = buttonListener;
    }

    /**
     * Creates a progress indicator that displays progress using a modal dialog
     * with a message label and a progress bar with no buttons.
     *
     * @param parent The parent frame.
     * @param title  The title for the dialog.
     */
    public ModalDialogProgressIndicator(Frame parent, String title) {
        this.parent = parent;
        this.title = title;
        progressPanel = new ProgressPanel();
        progressPanel.setIndeterminate(true);
        this.buttonLabels = null;
        this.focusedButtonLabel = null;
        this.buttonListener = null;
    }

    /**
     * Starts the progress indicator in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message        The initial progress message.
     * @param totalWorkUnits The total number of work units.
     */
    @Override
    public synchronized void start(String message, int totalWorkUnits) {
        cancelling = false;
        SwingUtilities.invokeLater(() -> {
            progressPanel.setIndeterminate(false);
            progressPanel.setMessage(message);
            progressPanel.setMaximum(totalWorkUnits);
            displayDialog();
        });
    }

    /**
     * Starts the progress indicator in indeterminate mode (the total number of
     * work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    @Override
    public synchronized void start(String message) {
        cancelling = false;
        SwingUtilities.invokeLater(() -> {
            progressPanel.setIndeterminate(true);
            progressPanel.setMessage(message);
            displayDialog();
        });
    }

    /**
     * Sets a cancelling message and makes the progress bar indeterminate. Once
     * cancel has been called, the progress indicator no longer accepts updates
     * unless start is called again.
     *
     * @param cancellingMessage
     */
    public synchronized void setCancelling(String cancellingMessage) {
        cancelling = true;
        SwingUtilities.invokeLater(() -> {
            progressPanel.setIndeterminate(false);
            progressPanel.setMessage(cancellingMessage);
        });
    }

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    @Override
    public synchronized void switchToIndeterminate(String message) {
        if (!cancelling) {
            SwingUtilities.invokeLater(() -> {
                progressPanel.setIndeterminate(true);
                progressPanel.setMessage(message);
            });
        }
    }

    /**
     * Switches the progress indicator to determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message            The initial progress message.
     * @param workUnitsCompleted The number of work units completed so far.
     * @param totalWorkUnits     The total number of work units to be completed.
     */
    @Override
    public synchronized void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
        if (!cancelling) {
            SwingUtilities.invokeLater(() -> {
                progressPanel.setIndeterminate(false);
                progressPanel.setMessage(message);
                progressPanel.setMaximum(totalWorkUnits);
                progressPanel.setCurrent(workUnitsCompleted);
            });
        }
    }

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message The progress message.
     */
    @Override
    public synchronized void progress(String message) {
        if (!cancelling) {
            SwingUtilities.invokeLater(() -> {
                progressPanel.setMessage(message);
            });
        }
    }

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param workUnitsCompleted Number of work units completed so far.
     */
    @Override
    public synchronized void progress(int workUnitsCompleted) {
        if (!cancelling) {
            SwingUtilities.invokeLater(() -> {
                progressPanel.setCurrent(workUnitsCompleted);
            });
        }
    }

    /**
     * Updates the progress indicator with a progress message and the number of
     * work units completed so far when in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message            The progress message.
     * @param workUnitsCompleted Number of work units completed so far.
     */
    @Override
    public synchronized void progress(String message, int workUnitsCompleted) {
        if (!cancelling) {
            SwingUtilities.invokeLater(() -> {
                progressPanel.setMessage(message);
                progressPanel.setCurrent(workUnitsCompleted);
            });
        }
    }

    /**
     * Finishes the progress indicator when the task is completed.
     */
    @Override
    public synchronized void finish() {
        SwingUtilities.invokeLater(() -> {
            this.dialog.setVisible(false);
        });
    }

    /**
     * Creates and dislpays the dialog for the progress indicator.
     */
    private void displayDialog() {
        if (null != buttonLabels && null != focusedButtonLabel && null != buttonListener) {
            /*
             * Dialog with buttons.
             */
            DialogDescriptor dialogDescriptor = new DialogDescriptor(
                    progressPanel,
                    title,
                    true,
                    buttonLabels,
                    focusedButtonLabel,
                    DialogDescriptor.BOTTOM_ALIGN,
                    HelpCtx.DEFAULT_HELP,
                    buttonListener);
            dialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor, parent);
        } else {
            /*
             * Dialog without buttons.
             */
            dialog = new JDialog(parent, title, true);
            dialog.add(progressPanel);
            dialog.pack();
        }
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        this.dialog.setVisible(true);
    }
}
