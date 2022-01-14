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

package org.sleuthkit.autopsy.mainui.nodes.actions;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.KeywordSearchTermParams;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action class for Deleting Analysis Result objects.
 */
public class DeleteAnalysisResultSetAction extends AbstractAction {
    
    @Messages({
        "DeleteKeywordSetAction_label=Delete Analysis Results"
    })
    
    private static final Logger logger = Logger.getLogger(DeleteAnalysisResultSetAction.class.getName());
    private static final long serialVersionUID = 1L;
    
    private BlackboardArtifact.Type type;
    private String configuration;
    private Long dsID;
    
    public DeleteAnalysisResultSetAction(BlackboardArtifact.Type type, String configuration, Long dsID) {
        super(Bundle.DeleteKeywordSetAction_label());
        this.type = type;
        this.configuration = configuration;
        this.dsID = dsID;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //ExplorerManager em = DirectoryTreeTopComponent.findInstance().getExplorerManager();
        //Node[] selectedNode = em.getSelectedNodes();
        //Children c = selectedNode[0].getChildren();
        //for (Node next : c.getNodes()) {
        //}
                
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {

//                    for (AnalysisResult result : selectedNode[0].getChildren()) {
//                        if (!isCancelled()) {
//                            try {
//                                Case.getCurrentCase().getSleuthkitCase().getBlackboard().deleteAnalysisResult(result);
//                                logger.log(Level.INFO, "Deleted Analysis Result id = " + result.getId());
//                            } catch (TskCoreException ex) {
//                                logger.log(Level.SEVERE, "Failed to delete analysis result id = " + result.getId(), ex);
//                            }
//                        }
//                    }
                
                return null;
            }
        };
        
        worker.execute();
        
    }
    
}
