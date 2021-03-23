/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.Host;

/**
 * Menu action to merge a host into another host.
 */
@Messages({
    "MergeHostAction_onError_title=Error Merging Hosts",
    "# {0} - sourceHostName",
    "# {1} - destHostName",
    "MergeHostAction_onError_description=There was an error merging host {0} into host {1}.",})
public class MergeHostAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(MergeHostAction.class.getName());

    private final Host sourceHost;
    private final Host destHost;

    /**
     * Main constructor.
     *
     * @param sourceHost The source host.
     * @param destHost   The destination host. 
     */
    public MergeHostAction(Host sourceHost, Host destHost) {
        super(destHost.getName());

        this.sourceHost = sourceHost;
        this.destHost = destHost;
    }

    @NbBundle.Messages({
        "MergeHostAction.progressIndicatorName=Merging Hosts",
        "MergeHostAction.confirmTitle=Confirmation",
        "# {0} - sourceHost",
        "# {1} - destHost",
        "MergeHostAction.confirmText=Are you sure you want to merge {0} into {1}?\nThis may include merging OS Accounts and cannot be undone.",
        "# {0} - sourceHost",
        "# {1} - destHost",
        "MergeHostAction.progressText=Merging {0} into {1}..."    
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        
        // Display confirmation dialog
        int response = JOptionPane.showConfirmDialog(
                WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(this.getClass(), "MergeHostAction.confirmText", sourceHost.getName(), destHost.getName()),
                NbBundle.getMessage(this.getClass(), "MergeHostAction.confirmTitle"),
                JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.NO_OPTION) {
            return;
        }
        
        ModalDialogProgressIndicator progressDialog = new ModalDialogProgressIndicator(WindowManager.getDefault().getMainWindow(),
                Bundle.MergeHostAction_progressIndicatorName());
        
        MergeHostsBackgroundTask mergeTask = new MergeHostsBackgroundTask(sourceHost, destHost, progressDialog);
        progressDialog.start(NbBundle.getMessage(this.getClass(), "MergeHostAction.progressText", sourceHost.getName(), destHost.getName()));
        mergeTask.execute();
    }

    /**
     * Merges the host in a background worker.
     */
    private class MergeHostsBackgroundTask extends SwingWorker<Void, Void> {

        private final Host sourceHost;
        private final Host destHost;
        private final ProgressIndicator progress;

        public MergeHostsBackgroundTask(Host sourceHost, Host destHost, ProgressIndicator progress) {
            this.sourceHost = sourceHost;
            this.destHost = destHost;
            this.progress = progress;
        }

        @Override
        protected Void doInBackground() throws Exception {
            Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().mergeHosts(sourceHost, destHost);
            return null;
        }
        
        @NbBundle.Messages({
            "MergeHostAction.errorTitle=Error Merging Hosts",
            "MergeHostAction.errorText=An error occurred while merging hosts.\nTry again in a few minutes or check the log for details."
        })
        @Override
        protected void done() {
            progress.finish();
            try {
                get();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error merging " + sourceHost.getName() + " into " + destHost.getName(), ex);
                
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), 
                        NbBundle.getMessage(this.getClass(), "MergeHostAction.errorText"),
                        NbBundle.getMessage(this.getClass(), "MergeHostAction.errorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
}
