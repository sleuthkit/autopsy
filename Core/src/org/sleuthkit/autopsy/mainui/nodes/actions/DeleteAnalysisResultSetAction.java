/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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

package org.sleuthkit.autopsy.mainui.nodes.actions;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.AppFrameProgressBar;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action class for Deleting Analysis Result objects.
 */
public class DeleteAnalysisResultSetAction extends AbstractAction {
    
    @Messages({
        "DeleteAnalysisResultsAction.label=Delete Analysis Results",
        "DeleteAnalysisResultsAction.title=Deleting Analysis Results",
        "# {0} - result type", 
        "DeleteAnalysisResultsAction.progress.allResults=Deleting Analysis Results type {0}",
        "# {0} - result type", "# {1} - configuration", 
        "DeleteAnalysisResultsAction.progress.allResultsWithConfiguration=Deleting Analysis Results type {0} and configuration {1}",
        "# {0} - result type", 
        "DeleteAnalysisResultsAction.warning.allResults=Are you sure you want to delete all Analysis Results of type {0}?",
        "# {0} - result type", "# {1} - configuration", 
        "DeleteAnalysisResultsAction.warning.allResultsWithConfiguration=Are you sure you want to delete all Analysis Results of type {0} and configuration {1}?"        
    })
    
    private static final Logger logger = Logger.getLogger(DeleteAnalysisResultSetAction.class.getName());
    private static final long serialVersionUID = 1L;
    
    private final BlackboardArtifact.Type type;
    private final String configuration;
    private final Long dsID;
    
    public DeleteAnalysisResultSetAction(BlackboardArtifact.Type type, String configuration, Long dsID) {
        super(Bundle.DeleteAnalysisResultsAction_label());
        this.type = type;
        this.configuration = configuration;
        this.dsID = dsID;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {

                AppFrameProgressBar progress = new AppFrameProgressBar(Bundle.DeleteAnalysisResultsAction_title());
                try {
                    String progressMessage;
                    String warningMessage;
                    if (configuration == null || configuration.isEmpty()) {
                        progressMessage = Bundle.DeleteAnalysisResultsAction_progress_allResults(type.getDisplayName());
                        warningMessage = Bundle.DeleteAnalysisResultsAction_warning_allResults(type.getDisplayName());
                    } else {
                        progressMessage = Bundle.DeleteAnalysisResultsAction_progress_allResultsWithConfiguration(type.getDisplayName(), configuration);
                        warningMessage = Bundle.DeleteAnalysisResultsAction_warning_allResultsWithConfiguration(type.getDisplayName(), configuration);
                    }
 
                    int response = JOptionPane.showConfirmDialog(
                            WindowManager.getDefault().getMainWindow(),
                            warningMessage,
                            Bundle.DeleteAnalysisResultsAction_title(),
                            JOptionPane.YES_NO_OPTION);
                    if (response != JOptionPane.YES_OPTION) {
                        return null;
                    }

                    progress.start(progressMessage);
                    progress.switchToIndeterminate(progressMessage);
                    if (!isCancelled()) {
                        try {
                            logger.log(Level.INFO, "Deleting Analysis Results type = {0}, data source ID = {1}, configuration = {2}", new Object[]{type, dsID, configuration});
                            Case.getCurrentCase().getSleuthkitCase().getBlackboard().deleteAnalysisResults(type, dsID, configuration);
                            logger.log(Level.INFO, "Deleted Analysis Results type = {0}, data source ID = {1}, configuration = {2}", new Object[]{type, dsID, configuration});
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Failed to delete analysis results of type = "+type+", data source ID = "+dsID+", configuration = "+configuration, ex);
                        }
                    }

                    return null;
                } finally {
                    progress.finish();
                }
            }
        };
        
        worker.execute();        
    }    
}
