/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.actions;

import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * A helper for actions that checks to see if ingest is running. If it is,
 * prompts the user to confirm they want to proceed with whatever operation was
 * initiated (e.g., closing the case).
 */
public class IngestRunningCheck {

    /**
     * Checks to see if ingest is running. If it is, prompts the user to confirm
     * they want to proceed with whatever operation was initiated (e.g., closing
     * the case).
     *
     * @param optionsDlgTitle   The title for the options dialog used to confirm
     *                          the iser's intentions.
     * @param optionsDlgMessage The message for the options dialog used to
     *                          confirm the iser's intentions.
     *
     * @return True to proceed, false otherwise.
     */
    public static boolean checkAndConfirmProceed(String optionsDlgTitle, String optionsDlgMessage) {
        if (IngestManager.getInstance().isIngestRunning()) {
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    optionsDlgMessage,
                    optionsDlgTitle,
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object response = DialogDisplayer.getDefault().notify(descriptor);
            return (DialogDescriptor.YES_OPTION == response);
        } else {
            return true;
        }
    }

    /**
     * Private contructor to prevent instantiation of a utility class.
     */
    private IngestRunningCheck() {
    }

}
