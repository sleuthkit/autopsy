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
import java.util.List;
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
    private final List<String> configurations;
    private final Long dsID;
    
    public DeleteAnalysisResultSetAction(BlackboardArtifact.Type type, List<String> configurations, Long dsID) {
        super(Bundle.DeleteAnalysisResultsAction_label());
        this.type = type;
        this.configurations = configurations;
        this.dsID = dsID;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        
        String warningMessage;
        if (configurations == null || configurations.isEmpty() || configurations.size() > 1 
                || type == BlackboardArtifact.Type.TSK_KEYWORD_HIT) {
            // either no configuration or multiple configurations. 
            // do not display configuration for KWS hits as it contains (KW term, search type, KW list name).
            warningMessage = Bundle.DeleteAnalysisResultsAction_warning_allResults(type.getDisplayName());            
        } else {
            warningMessage = Bundle.DeleteAnalysisResultsAction_warning_allResultsWithConfiguration(type.getDisplayName(), configurations.get(0));
        }
        int response = JOptionPane.showConfirmDialog(
                WindowManager.getDefault().getMainWindow(),
                warningMessage,
                Bundle.DeleteAnalysisResultsAction_title(),
                JOptionPane.YES_NO_OPTION);
        if (response != JOptionPane.YES_OPTION) {
            return;
        }
        
        AppFrameProgressBar progress = new AppFrameProgressBar(Bundle.DeleteAnalysisResultsAction_title());
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {

                progress.start(Bundle.DeleteAnalysisResultsAction_title());
                try {
                    if (configurations == null || configurations.isEmpty()) {
                        progress.switchToIndeterminate(Bundle.DeleteAnalysisResultsAction_progress_allResults(type.getDisplayName()));
                        if (!isCancelled()) {
                            delete(type, "", dsID);
                        }
                    } else {
                        for (String configuration : configurations) {
                            progress.switchToIndeterminate(Bundle.DeleteAnalysisResultsAction_progress_allResultsWithConfiguration(type.getDisplayName(), configuration));
                            if (!isCancelled()) {
                                delete(type, configuration, dsID);
                            }
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
    
    private static void delete(BlackboardArtifact.Type type, String configuration, Long dsID) {
        try {
            logger.log(Level.INFO, "Deleting Analysis Results type = {0}, data source ID = {1}, configuration = {2}", new Object[]{type, dsID, configuration});
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().deleteAnalysisResults(type, dsID, configuration);
            logger.log(Level.INFO, "Deleted Analysis Results type = {0}, data source ID = {1}, configuration = {2}", new Object[]{type, dsID, configuration});
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to delete analysis results of type = " + type + ", data source ID = " + dsID + ", configuration = " + configuration, ex);
        }
    }  
}
