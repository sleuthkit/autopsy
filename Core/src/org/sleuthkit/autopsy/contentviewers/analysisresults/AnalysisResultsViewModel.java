/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.AnalysisResultItem;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Creates a representation of a list of analysis results gathered from a node.
 */
public class AnalysisResultsViewModel {

    private static final Logger logger = Logger.getLogger(AnalysisResultsViewModel.class.getName());

    /**
     * The attributes to display for a particular Analysis Result.
     */
    static class ResultDisplayAttributes {

        private final AnalysisResult analysisResult;
        private final List<Pair<String, String>> attributesToDisplay;

        /**
         * Constructor.
         *
         * @param analysisResult      The analysis result which these attributes
         *                            describe.
         * @param attributesToDisplay The attributes to display in the order
         *                            they should be displayed.
         */
        ResultDisplayAttributes(AnalysisResult analysisResult, List<Pair<String, String>> attributesToDisplay) {
            this.analysisResult = analysisResult;
            this.attributesToDisplay = attributesToDisplay;
        }

        /**
         * Returns the attributes to display.
         *
         * @return The attributes to display.
         */
        List<Pair<String, String>> getAttributesToDisplay() {
            return Collections.unmodifiableList(attributesToDisplay);
        }

        /**
         * Returns the analysis result which these attributes describe.
         *
         * @return The analysis result.
         */
        AnalysisResult getAnalysisResult() {
            return analysisResult;
        }
    }

    /**
     * The analysis results relating to a node (i.e. belonging to source content
     * or directly in the lookup) to be displayed.
     */
    static class NodeResults {

        private final List<ResultDisplayAttributes> analysisResults;
        private final Optional<AnalysisResult> selectedResult;
        private final Optional<Score> aggregateScore;
        private final Optional<String> itemName;

        /**
         * Constructor.
         *
         * @param analysisResults The analysis results to be displayed.
         * @param selectedResult  The selected analysis result or empty if none
         *                        selected.
         * @param aggregateScore  The aggregate score or empty if no score.
         * @param content         The content associated with these results.
         * @param itemName        The name of the selected item to display to
         *                        the user.
         */
        NodeResults(List<ResultDisplayAttributes> analysisResults, Optional<AnalysisResult> selectedResult,
                Optional<Score> aggregateScore, Optional<String> itemName) {
            this.analysisResults = analysisResults;
            this.selectedResult = selectedResult;
            this.aggregateScore = aggregateScore;
            this.itemName = itemName;
        }

        /**
         * Returns the analysis results to be displayed.
         *
         * @return The analysis results to be displayed.
         */
        List<ResultDisplayAttributes> getAnalysisResults() {
            return Collections.unmodifiableList(analysisResults);
        }

        /**
         * Returns the selected analysis result or empty if none selected.
         *
         * @return The selected analysis result or empty if none selected.
         */
        Optional<AnalysisResult> getSelectedResult() {
            return selectedResult;
        }

        /**
         * Returns the aggregate score or empty if no score.
         *
         * @return The aggregate score or empty if no score.
         */
        Optional<Score> getAggregateScore() {
            return aggregateScore;
        }

        /**
         * Returns the name of the selected item to display to the user.
         *
         * @return The name of the selected item to display to the user.
         */
        Optional<String> getItemName() {
            return itemName;
        }
    }

    /**
     * Normalizes the value of an attribute of an analysis result for display
     * purposes.
     *
     * @param originalAttrStr The original attribute value.
     *
     * @return The normalized value for display.
     */
    private String normalizeAttr(String originalAttrStr) {
        return (originalAttrStr == null) ? "" : originalAttrStr.trim();
    }

    /**
     * Returns the attributes to be displayed for an analysis result.
     *
     * @param analysisResult The analysis result.
     *
     * @return The attributes to be displayed.
     */
    @NbBundle.Messages({
        "AnalysisResultsViewModel_displayAttributes_score=Score",
        "AnalysisResultsViewModel_displayAttributes_type=Type",
        "AnalysisResultsViewModel_displayAttributes_configuration=Configuration",
        "AnalysisResultsViewModel_displayAttributes_conclusion=Conclusion"
    })
    private ResultDisplayAttributes getDisplayAttributes(AnalysisResult analysisResult) {
        // The type of BlackboardArtifact.Type of the analysis result.
        String type = "";
        try {
            type = normalizeAttr(analysisResult.getType().getDisplayName());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get type for analysis result with id: " + analysisResult.getArtifactID(), ex);
        }

        // The standard attributes to display (score, type, configuration, conclusion)
        Stream<Pair<String, String>> baseAnalysisAttrs = Stream.of(
                Pair.of(Bundle.AnalysisResultsViewModel_displayAttributes_score(),
                        normalizeAttr(analysisResult.getScore().getSignificance().getDisplayName())),
                Pair.of(Bundle.AnalysisResultsViewModel_displayAttributes_type(),
                        normalizeAttr(type)),
                Pair.of(Bundle.AnalysisResultsViewModel_displayAttributes_configuration(),
                        normalizeAttr(analysisResult.getConfiguration())),
                Pair.of(Bundle.AnalysisResultsViewModel_displayAttributes_conclusion(),
                        normalizeAttr(analysisResult.getConclusion()))
        );

        // The BlackboardAttributes sorted by type display name.
        Stream<Pair<String, String>> blackboardAttributes = Stream.empty();
        try {

            blackboardAttributes = analysisResult.getAttributes().stream()
                    .filter(attr -> attr != null && attr.getAttributeType() != null && attr.getAttributeType().getDisplayName() != null)
                    .map(attr -> Pair.of(attr.getAttributeType().getDisplayName(), normalizeAttr(attr.getDisplayString())))
                    .sorted((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get attributes for analysis result with id: " + analysisResult.getArtifactID(), ex);
        }

        // return the standard attributes along with the key value pairs of the BlackboardAttribute values.
        List<Pair<String, String>> allDisplayAttributes = Stream.concat(baseAnalysisAttrs, blackboardAttributes)
                .collect(Collectors.toList());

        return new ResultDisplayAttributes(analysisResult, allDisplayAttributes);
    }

    private List<ResultDisplayAttributes> getOrderedDisplayAttributes(Collection<AnalysisResult> analysisResults) {
        return analysisResults.stream()
                .filter(ar -> ar != null && ar.getScore() != null)
                // reverse order to push more important scores to the top
                .sorted((a, b) -> -a.getScore().compareTo(b.getScore()))
                .map((ar) -> getDisplayAttributes(ar))
                .collect(Collectors.toList());
    }

    /**
     * Returns the item's name to display in the header for the panel.
     *
     * @param selectedContent The selected content.
     *
     * @return The name to use for the content.
     *
     * @throws TskCoreException
     */
    private String getName(Content selectedContent) throws TskCoreException {
        if (selectedContent == null) {
            return null;
        } else if (selectedContent instanceof BlackboardArtifact) {
            return ((BlackboardArtifact) selectedContent).getShortDescription();
        } else {
            return selectedContent.getName();
        }
    }

    /**
     * Returns the view model data representing the analysis results to be
     * displayed for the node.
     *
     * @param node The node.
     *
     * @return The analysis results view model data to display.
     */
    NodeResults getAnalysisResults(Node node) {
        if (node == null) {
            return new NodeResults(Collections.emptyList(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        String contentName = null;
        AnalysisResult selectedAnalysisResult = null;
        Score aggregateScore = null;
        List<AnalysisResult> analysisResults = Collections.emptyList();
        long selectedObjectId = 0;
        try {
            AnalysisResultItem analysisResultItem = node.getLookup().lookup(AnalysisResultItem.class);
            Content analyzedContent;
            if (Objects.nonNull(analysisResultItem)) {
                /*
                 * The content represented by the Node is an analysis result.
                 * Set this analysis result as the analysis result to be
                 * selected in the content viewer and get the analyzed content
                 * as the source of the analysis results to display.
                 */
                selectedAnalysisResult = analysisResultItem.getTskContent();
                selectedObjectId = selectedAnalysisResult.getId();
                analyzedContent = selectedAnalysisResult.getParent();
            } else {
                /*
                 * The content represented by the Node is something other than
                 * an analysis result. Use it as the source of the analysis
                 * results to display.
                 */
                TskContentItem<?> contentItem = node.getLookup().lookup(TskContentItem.class);
                analyzedContent = contentItem.getTskContent();
                selectedObjectId = analyzedContent.getId();
            }
            aggregateScore = analyzedContent.getAggregateScore();
            analysisResults = analyzedContent.getAllAnalysisResults();
            contentName = getName(analyzedContent);

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting analysis result data for selected Content (object ID=%d)", selectedObjectId), ex);
        }

        /*
         * Use the data collected above to construct the view model.
         */
        List<ResultDisplayAttributes> displayAttributes = getOrderedDisplayAttributes(analysisResults);
        return new NodeResults(displayAttributes,
                Optional.ofNullable(selectedAnalysisResult),
                Optional.ofNullable(aggregateScore),
                Optional.ofNullable(contentName));
    }

}
