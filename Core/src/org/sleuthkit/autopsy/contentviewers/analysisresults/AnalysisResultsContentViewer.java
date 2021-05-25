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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Displays a list of analysis results as a content viewer.
 */
public class AnalysisResultsContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static Logger logger = Logger.getLogger(AnalysisResultsContentViewer.class.getName());
    private static final int PREFERRED_VALUE = 6;
    
    private static Optional<Score> getAggregateScore(Collection<AnalysisResult> analysisResults) {
        return analysisResults.stream()
                .map(AnalysisResult::getScore)
                .max(Comparator.naturalOrder());
    }

    private static String normalizeAttr(String originalAttrStr) {
        return (originalAttrStr == null) ? "" : originalAttrStr.trim();
    }
    

    private static class ResultDisplayAttributes {

        private final AnalysisResult analysisResult;
        private final List<Pair<String, String>> attributesToDisplay;

        ResultDisplayAttributes(AnalysisResult analysisResult, List<Pair<String, String>> attributesToDisplay) {
            this.analysisResult = analysisResult;
            this.attributesToDisplay = attributesToDisplay;
        }

        List<Pair<String, String>> getAttributesToDisplay() {
            return attributesToDisplay;
        }

        AnalysisResult getAnalysisResult() {
            return analysisResult;
        }
    }

    @Messages({
        "AnalysisResultsContentViewer_displayAttributes_score=Score",
        "AnalysisResultsContentViewer_displayAttributes_type=Type",
        "AnalysisResultsContentViewer_displayAttributes_configuration=Configuration",
        "AnalysisResultsContentViewer_displayAttributes_conclusion=Conclusion"
    })
    private static ResultDisplayAttributes getDisplayAttributes(AnalysisResult analysisResult) {

        String type = "";
        try {
            type = normalizeAttr(analysisResult.getType().getDisplayName());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get type for analysis result with id: " + analysisResult.getArtifactID(), ex);
        }

        Stream<Pair<String, String>> baseAnalysisAttrs = Stream.of(
                Pair.of(Bundle.AnalysisResultsContentViewer_displayAttributes_score(),
                        normalizeAttr(analysisResult.getScore().getSignificance().getDisplayName())),
                Pair.of(Bundle.AnalysisResultsContentViewer_displayAttributes_type(),
                        normalizeAttr(analysisResult.getScore().getSignificance().getDisplayName())),
                Pair.of(Bundle.AnalysisResultsContentViewer_displayAttributes_configuration(),
                        normalizeAttr(analysisResult.getConfiguration())),
                Pair.of(Bundle.AnalysisResultsContentViewer_displayAttributes_conclusion(),
                        normalizeAttr(analysisResult.getConclusion()))
        );

        Stream<Pair<String, String>> blackboardAttributes = Stream.empty();
        try {

            blackboardAttributes = analysisResult.getAttributes().stream()
                    .filter(attr -> attr != null && attr.getAttributeType() != null && attr.getAttributeType().getDisplayName() != null)
                    .map(attr -> Pair.of(attr.getAttributeType().getDisplayName(), normalizeAttr(attr.getDisplayString())))
                    .sorted((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get attributes for analysis result with id: " + analysisResult.getArtifactID(), ex);
        }

        List<Pair<String, String>> allDisplayAttributes = Stream.concat(baseAnalysisAttrs, blackboardAttributes)
                .collect(Collectors.toList());

        return new ResultDisplayAttributes(analysisResult, allDisplayAttributes);
    }

    private static List<AnalysisResult> getScoreOrderedResults(Collection<AnalysisResult> analysisResults) {
        return analysisResults.stream()
                .filter(ar -> ar != null && ar.getScore() != null)
                // reverse order to push more important scores to the top
                .sorted((a, b) -> -a.getScore().compareTo(b.getScore()))
                .collect(Collectors.toList());
    }

    private static List<ResultDisplayAttributes> getDisplayAttributes(Collection<AnalysisResult> analysisResults) {
        return analysisResults.stream()
                .map(AnalysisResultsContentViewer::getDisplayAttributes)
                .collect(Collectors.toList());
    }

    private static class NodeAnalysisResults {

        private final Collection<AnalysisResult> analysisResults;
        private final Optional<AnalysisResult> selectedResult;

        NodeAnalysisResults(Collection<AnalysisResult> analysisResults, Optional<AnalysisResult> selectedResult) {
            this.analysisResults = analysisResults;
            this.selectedResult = selectedResult;
        }

        Collection<AnalysisResult> getAnalysisResults() {
            return analysisResults;
        }

        Optional<AnalysisResult> getSelectedResult() {
            return selectedResult;
        }
    }

    private static NodeAnalysisResults getAnalysisResults(Node node) {
        if (node == null) {
            return new NodeAnalysisResults(Collections.emptyList(), Optional.empty());
        }

        Map<Long, AnalysisResult> allAnalysisResults = new HashMap<>();
        Optional<AnalysisResult> selectedResult = Optional.empty();
        
        AbstractFile abstractFile = node.getLookup().lookup(AbstractFile.class);
        if (abstractFile != null) {
            try {
                abstractFile.getAllAnalysisResults().stream()
                        .filter(ar -> ar != null)
                        .forEach((ar) -> allAnalysisResults.put(ar.getArtifactID(), ar));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get analysis results for file with obj id " + abstractFile.getId(), ex);
            }
        }

        Collection<? extends AnalysisResult> analysisResults = node.getLookup().lookupAll(AnalysisResult.class);
        if (analysisResults.size() > 0) {
            
            List<AnalysisResult> filteredResults =  analysisResults.stream()
                    .filter(ar -> ar != null && ar.getScore() != null)
                    .collect(Collectors.toList());
            
            filteredResults.forEach((ar) -> allAnalysisResults.put(ar.getArtifactID(), ar));
            
            selectedResult = filteredResults.stream()
                    .max((a,b) -> a.getScore().compareTo(b.getScore()));
        }
        
        return new NodeAnalysisResults(allAnalysisResults.values(), selectedResult);
    }
    
    private static void render(Node node) {
        NodeAnalysisResults nodeResults = getAnalysisResults(node);
        List<AnalysisResult> orderedAnalysisResults = getScoreOrderedResults(nodeResults.getAnalysisResults());
        List<ResultDisplayAttributes> displayAttributes = getDisplayAttributes(orderedAnalysisResults);
        
        // GVDTODO
        
        Optional<AnalysisResult> selectedResult = nodeResults.getSelectedResult();
        if (selectedResult.isPresent()) {
            // GVDTODO
        }
    }

    private Node selectedNode;
    
    
    /**
     * Creates new form AnalysisResultsContentViewer
     */
    public AnalysisResultsContentViewer() {
        initComponents();
    }

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
        return this;
    }

    @Override
    public void resetComponent() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setNode(Node selectedNode) {
        this.selectedNode = selectedNode;
    }

    @Override
    public boolean isSupported(Node node) {
        return getAnalysisResults(node).getAnalysisResults().size() > 0;
    }

    @Override
    public int isPreferred(Node node) {
        return PREFERRED_VALUE;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
