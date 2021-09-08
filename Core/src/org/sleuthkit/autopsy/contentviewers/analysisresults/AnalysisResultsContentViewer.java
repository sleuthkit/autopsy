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
package org.sleuthkit.autopsy.contentviewers.analysisresults;

import java.awt.Component;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Displays a list of analysis results as a content viewer.
 */
@ServiceProvider(service = DataContentViewer.class, position = 7)
public class AnalysisResultsContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(AnalysisResultsContentPanel.class.getName());

    // isPreferred value
    private static final int PREFERRED_VALUE = ViewerPriority.viewerPriority.LevelThree.getFlag();;

    private final AnalysisResultsViewModel viewModel = new AnalysisResultsViewModel();
    private final AnalysisResultsContentPanel panel = new AnalysisResultsContentPanel();

    private SwingWorker<?, ?> worker = null;

    @NbBundle.Messages({
        "AnalysisResultsContentViewer_title=Analysis Results"
    })
    @Override
    public String getTitle() {
        return Bundle.AnalysisResultsContentViewer_title();
    }

    @NbBundle.Messages({
        "AnalysisResultsContentViewer_tooltip=Viewer for Analysis Results related to the selected node."
    })
    @Override
    public String getToolTip() {
        return Bundle.AnalysisResultsContentViewer_tooltip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new AnalysisResultsContentViewer();
    }

    @Override
    public Component getComponent() {
        return panel;
    }

    @Override
    public void resetComponent() {
        panel.reset();
    }

    @Override
    @NbBundle.Messages({
        "AnalysisResultsContentViewer_setNode_loadingMessage=Loading...",
        "AnalysisResultsContentViewer_setNode_errorMessage=There was an error loading results.",})
    public synchronized void setNode(Node node) {
        // reset the panel
        panel.reset();

        // if there is a worker running, cancel it
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        // if no node, nothing to do
        if (node == null) {
            return;
        }

        // show a loading message
        panel.showMessage(Bundle.AnalysisResultsContentViewer_setNode_loadingMessage());

        // create the worker
        worker = new DataFetchWorker<>(
                // load a view model from the node
                (selectedNode) -> viewModel.getAnalysisResults(selectedNode),
                (nodeAnalysisResults) -> {
                    if (nodeAnalysisResults.getResultType() == DataFetchResult.ResultType.SUCCESS) {
                        // if successful, display the results
                        panel.displayResults(nodeAnalysisResults.getData());
                    } else {
                        // if there was an error, display an error message
                        panel.showMessage(Bundle.AnalysisResultsContentViewer_setNode_errorMessage());
                    }
                },
                node);

        // kick off the swing worker
        worker.execute();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        // There needs to either be a file with an AnalysisResult or an AnalysisResult in the lookup.
        for (Content content : node.getLookup().lookupAll(Content.class)) {
            if (content instanceof AnalysisResult) {
                return true;
            }

            if (content == null || content instanceof BlackboardArtifact) {
                continue;
            }

            try {
                if (Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().hasAnalysisResults(content.getId())) {
                    return true;
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get analysis results for file with obj id " + content.getId(), ex);
            }
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        return PREFERRED_VALUE;
    }
}
