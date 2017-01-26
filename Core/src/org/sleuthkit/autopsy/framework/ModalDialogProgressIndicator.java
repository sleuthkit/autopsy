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
import java.awt.event.ActionListener;
import javax.swing.SwingUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;

/**
 * A progress indicator that displays progress using a modal dialog with a
 * message label and a progress bar.
 */
public final class ModalDialogProgressIndicator implements ProgressIndicator {

    private final ProgressPanel progressPanel;
    private final ActionListener listener;
    private final Dialog dialog;

    public ModalDialogProgressIndicator(String title, Object[] options, Object initialValue, HelpCtx helpCtx, ActionListener listener) {
        progressPanel = new ProgressPanel();
        this.listener = listener;
        DialogDescriptor dialogDescriptor = new DialogDescriptor(
                progressPanel,
                title,
                true,
                options,
                initialValue,
                DialogDescriptor.BOTTOM_ALIGN,
                helpCtx,
                this.listener);
        dialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor);
    }

   public ModalDialogProgressIndicator(String title, HelpCtx helpCtx) {
        progressPanel = new ProgressPanel();
        DialogDescriptor dialogDescriptor = new DialogDescriptor(
                progressPanel,
                title,
                true,
                DialogDescriptor.NO_OPTION,
                null,
                null);
        dialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor);       
   }
    
    public void setVisible(boolean isVisible) {
        this.dialog.setVisible(isVisible);
    }

    public ActionListener getListener() {
        return listener;
    }

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

    @Override
    public void progress(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setMessage(message);
            }
        });
    }

    @Override
    public void progress(int workUnitsCompleted) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressPanel.setCurrent(workUnitsCompleted);
            }
        });
    }

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
