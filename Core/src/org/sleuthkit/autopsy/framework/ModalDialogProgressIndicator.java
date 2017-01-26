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
package org.sleuthkit.autopsy.framework;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.ActionListener;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;

/**
 * A progress indicator that displays progress using a modal dialog with a
 * message label, a progress bar, and optionally, a configurable set of buttons
 * with a button listener.
 */
public final class ModalDialogProgressIndicator implements ProgressIndicator {

    private final Frame parent;
    private final ProgressPanel progressPanel;
    private final Dialog dialog;
    private final ActionListener buttonListener;

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
        progressPanel = new ProgressPanel();
        DialogDescriptor dialogDescriptor = new DialogDescriptor(
                progressPanel,
                title,
                true,
                buttonLabels,
                focusedButtonLabel,
                DialogDescriptor.BOTTOM_ALIGN,
                HelpCtx.DEFAULT_HELP,
                buttonListener);
        dialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor);
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
        progressPanel = new ProgressPanel();
        dialog = new JDialog(parent, title, true);
        dialog.add(progressPanel);
        dialog.pack();
        buttonListener = null;
    }

    /**
     * Calls setVisible on the underlying modal dialog.
     *
     * @param isVisible True or false.
     */
    public void setVisible(boolean isVisible) {
        if (isVisible) {
            dialog.setLocationRelativeTo(parent);
        }
        this.dialog.setVisible(isVisible);
    }

    /**
     * Gets the button listener for the dialog, if there is one.
     *
     * @return The button listener or null.
     */
    public ActionListener getButtonListener() {
        return buttonListener;
    }

    /**
     * Starts the progress indicator in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message        The initial progress message.
     * @param totalWorkUnits The total number of work units.
     */
    @Override
    public void start(String message, int totalWorkUnits) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setInderminate(false);
                progressPanel.setMessage(message);
                progressPanel.setMaximum(totalWorkUnits);
            }
        });
    }

    /**
     * Starts the progress indicator in indeterminate mode (the total number of
     * work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    @Override
    public void start(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setInderminate(true);
                progressPanel.setMessage(message);
            }
        });
    }

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    @Override
    public void switchToIndeterminate(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setInderminate(true);
                progressPanel.setMessage(message);
            }
        });
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
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setInderminate(false);
                progressPanel.setMessage(message);
                progressPanel.setMaximum(totalWorkUnits);
                progressPanel.setCurrent(workUnitsCompleted);
            }
        });
    }

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message The progress message.
     */
    @Override
    public void progress(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setMessage(message);
            }
        });
    }

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param workUnitsCompleted Number of work units completed so far.
     */
    @Override
    public void progress(int workUnitsCompleted) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setCurrent(workUnitsCompleted);
            }
        });
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
    public void progress(String message, int workUnitsCompleted) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setMessage(message);
                progressPanel.setCurrent(workUnitsCompleted);
            }
        });
    }

    /**
     * Finishes the progress indicator when the task is completed.
     *
     * @param message The finished message.
     */
    @Override
    public void finish(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setMessage(message);
            }
        });
    }

}
