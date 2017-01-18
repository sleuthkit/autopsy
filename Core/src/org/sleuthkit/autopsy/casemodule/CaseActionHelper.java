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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Cursor;
import java.util.logging.Level;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * A utility class for common code for case actions.
 */
class CaseActionHelper {

    /**
     * Tries to closes the current case, if any, with a checks to see if ingest
     * is running; if it is, the user is given the opportunity to let the ingest
     * continue and stop the case action.
     *
     * @param
     * @return True if the current case, if any, is closed, false otherwise.
     */
    // RJCTODO: Be sure to test this!
    static boolean closeCaseAndContinueAction() {
        if (IngestManager.getInstance().isIngestRunning()) {
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning"), // RJCTODO
                    NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title"), // RJCTODO
                    NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object choice = DialogDisplayer.getDefault().notify(descriptor);
            if (null != choice && DialogDescriptor.YES_OPTION == choice) {
                IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.USER_CANCELLED);
                // RJCTODO; refer to JIRA here for blocking wait on cancel...
                return true;
            } else {
                return false;
            }
        }
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Case.closeCurrentCase();
        } catch (CaseActionException ex) {
            // RJCTODO: Pop up here
            Logger.getLogger(NewCaseWizardAction.class.getName()).log(Level.SEVERE, "Error closing case.", ex); //NON-NLS
        }
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        return true;
    }

    /**
     * Private contructor to prevent instantiation.
     */
    private CaseActionHelper() {
    }
}
