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

import java.awt.Color;
import java.awt.Component;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.text.html.HTMLEditorKit;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult.ResultType;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Displays a list of analysis results as a content viewer.
 */
@ServiceProvider(service = DataContentViewer.class, position = 7)
public class AnalysisResultsContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static Logger logger = Logger.getLogger(AnalysisResultsContentViewer.class.getName());

    /**
     * isPreferred value.
     */
    private static final int PREFERRED_VALUE = 6;

    private static final String EMPTY_HTML = "<html><head></head><body></body></html>";

    private static final String DEFAULT_FONT_FAMILY = new JLabel().getFont().getFamily();
    private static final int DEFAULT_FONT_SIZE = new JLabel().getFont().getSize();
    private static final Color DEFAULT_BACKGROUND = new JLabel().getBackground();

    // html stylesheet classnames for components
    private static final String SPACED_SECTION_CLASSNAME = "spacedSection";
    private static final String HEADER_CLASSNAME = "header";
    public static final String MESSAGE_CLASSNAME = "message";

    private static final String RESULT_ANCHOR_PREFIX = "AnalysisResult_";

    // how big the header should be
    private static final int HEADER_FONT_SIZE = DEFAULT_FONT_SIZE + 2;

    // spacing occurring after an item
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_SIZE / 2;
    private static final int CELL_SPACING = DEFAULT_FONT_SIZE / 2;

    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-size: %dpx;font-style:italic; margin: 0px; padding: 0px; } ", MESSAGE_CLASSNAME, DEFAULT_FONT_SIZE)
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px; } ",
                    HEADER_CLASSNAME, DEFAULT_FONT_FAMILY, HEADER_FONT_SIZE)
            + String.format(" td { vertical-align: top; font-family: %s; font-size: %dpt; text-align: left; margin: 0pt; padding: 0px %dpt 0px 0px;} ",
                    DEFAULT_FONT_FAMILY, DEFAULT_FONT_SIZE, CELL_SPACING)
            + String.format(" .%s { margin-top: %dpt; } ", SPACED_SECTION_CLASSNAME, DEFAULT_SECTION_SPACING);

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
                        normalizeAttr(type)),
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
        private final Optional<Score> aggregateScore;

        public NodeAnalysisResults(Collection<AnalysisResult> analysisResults, Optional<AnalysisResult> selectedResult, Optional<Score> aggregateScore) {
            this.analysisResults = analysisResults;
            this.selectedResult = selectedResult;
            this.aggregateScore = aggregateScore;
        }

        public Collection<AnalysisResult> getAnalysisResults() {
            return analysisResults;
        }

        public Optional<AnalysisResult> getSelectedResult() {
            return selectedResult;
        }

        public Optional<Score> getAggregateScore() {
            return aggregateScore;
        }
    }

    private static NodeAnalysisResults getAnalysisResults(Node node) {
        if (node == null) {
            return new NodeAnalysisResults(Collections.emptyList(), Optional.empty(), Optional.empty());
        }

        Optional<Score> aggregateScore = Optional.empty();
        Map<Long, AnalysisResult> allAnalysisResults = new HashMap<>();
        Optional<AnalysisResult> selectedResult = Optional.empty();

        for (Content content : node.getLookup().lookupAll(Content.class)) {
            if (content == null || content instanceof BlackboardArtifact) {
                continue;
            }
            
            try {
                aggregateScore = Optional.ofNullable(content.getAggregateScore());

                content.getAllAnalysisResults().stream()
                        .filter(ar -> ar != null)
                        .forEach((ar) -> allAnalysisResults.put(ar.getArtifactID(), ar));
                
                break;
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get analysis results for content with obj id " + content.getId(), ex);
            }
        }

        Collection<? extends AnalysisResult> analysisResults = node.getLookup().lookupAll(AnalysisResult.class);
        if (analysisResults.size() > 0) {

            List<AnalysisResult> filteredResults = analysisResults.stream()
                    .filter(ar -> ar != null && ar.getScore() != null)
                    .collect(Collectors.toList());

            filteredResults.forEach((ar) -> allAnalysisResults.put(ar.getArtifactID(), ar));

            selectedResult = filteredResults.stream()
                    .max((a, b) -> a.getScore().compareTo(b.getScore()));

            if (!aggregateScore.isPresent()) {
                aggregateScore = selectedResult.flatMap(selectedRes -> Optional.ofNullable(selectedRes.getScore()));
            }
        }

        return new NodeAnalysisResults(getScoreOrderedResults(allAnalysisResults.values()), selectedResult, aggregateScore);
    }

    private static Document render(List<ResultDisplayAttributes> displayAttributes, Optional<Score> aggregateScore) {
        Document html = Jsoup.parse(EMPTY_HTML);
        Element body = html.getElementsByTag("body").first();

        if (aggregateScore.isPresent()) {
            appendAggregateScore(body, aggregateScore.get());
        }

        for (int idx = 0; idx < displayAttributes.size(); idx++) {
            ResultDisplayAttributes resultAttrs = displayAttributes.get(idx);
            appendResult(body, idx, resultAttrs);
        }

        return html;
    }

    @Messages("AnalysisResultsContentViewer_appendAggregateScore_displayKey=Aggregate Score")
    private static void appendAggregateScore(Element body, Score score) {
        appendSection(body,
                MessageFormat.format("{0}: {1}",
                        Bundle.AnalysisResultsContentViewer_appendAggregateScore_displayKey(),
                        score.getSignificance().getDisplayName()),
                null);
    }

    private static String getAnchor(AnalysisResult analysisResult) {
        return RESULT_ANCHOR_PREFIX + analysisResult.getId();
    }

    @Messages({"# {0} - analysisResultsNumber",
        "AnalysisResultsContentViewer_appendResult_headerKey=Analysis Result {0}"
    })
    private static void appendResult(Element parent, int index, ResultDisplayAttributes attrs) {
        Element sectionDiv = appendSection(parent,
                Bundle.AnalysisResultsContentViewer_appendResult_headerKey(index + 1),
                Optional.ofNullable(getAnchor(attrs.getAnalysisResult())));
        Element table = sectionDiv.appendElement("table");
        Element tableBody = table.appendElement("tbody");

        for (Pair<String, String> keyVal : attrs.getAttributesToDisplay()) {
            Element row = tableBody.appendElement("tr");
            String keyString = keyVal.getKey() == null ? "" : keyVal.getKey() + ":";
            row.appendElement("td").text(keyString);
            String valueString = keyVal.getValue() == null ? "" : keyVal.getValue();
            row.appendElement("td").text(valueString);
        }
    }

    /**
     * Appends a new section with a section header to the parent element.
     *
     * @param parent     The element to append this section to.
     * @param headerText The text for the section.
     * @param anchorId   The anchor id for this section.
     *
     * @return The div for the new section.
     */
    private static Element appendSection(Element parent, String headerText, Optional<String> anchorId) {
        Element sectionDiv = parent.appendElement("div");
        if (anchorId.isPresent()) {
            Element anchorEl = sectionDiv.appendElement("a");
            anchorEl.attr("name", anchorId.get());
        }

        sectionDiv.attr("class", SPACED_SECTION_CLASSNAME);
        Element header = sectionDiv.appendElement("h1");
        header.text(headerText);
        header.attr("class", HEADER_CLASSNAME);
        return sectionDiv;
    }

    private SwingWorker<?, ?> worker = null;

    /**
     * Creates new form AnalysisResultsContentViewer
     */
    public AnalysisResultsContentViewer() {
        initComponents();

        textPanel.setContentType("text/html;charset=UTF-8"); //NON-NLS
        HTMLEditorKit kit = new HTMLEditorKit();
        textPanel.setEditorKit(kit);
        kit.getStyleSheet().addRule(STYLE_SHEET_RULE);
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
        textPanel.setText("");
    }

    @Override
    @Messages({
        "AnalysisResultsContentViewer_setNode_loadingMessage=Loading...",
        "AnalysisResultsContentViewer_setNode_errorMessage=There was an error loading results.",})
    public synchronized void setNode(Node node) {
        resetComponent();

        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        if (node == null) {
            return;
        }

        showMessage(Bundle.AnalysisResultsContentViewer_setNode_loadingMessage());

        worker = new DataFetchWorker<Node, NodeAnalysisResults>(
                (selectedNode) -> getAnalysisResults(selectedNode),
                (nodeAnalysisResults) -> {
                    if (nodeAnalysisResults.getResultType() == ResultType.SUCCESS) {
                        displayResults(nodeAnalysisResults.getData());
                    } else {
                        showMessage(Bundle.AnalysisResultsContentViewer_setNode_errorMessage());
                    }
                },
                node);

        worker.execute();
    }

    private void showMessage(String message) {
        textPanel.setText("<html><head></head><body>"
                + MessageFormat.format("<p class='{0}'>{1}</p>", MESSAGE_CLASSNAME, message)
                + "</body></html>");
    }

    private void displayResults(NodeAnalysisResults nodeResults) {
        List<ResultDisplayAttributes> displayAttributes = getDisplayAttributes(nodeResults.getAnalysisResults());
        Document document = render(displayAttributes, nodeResults.getAggregateScore());
        Optional<AnalysisResult> selectedResult = nodeResults.getSelectedResult();
        textPanel.setText(document.html());

        if (selectedResult.isPresent()) {
            textPanel.scrollToReference(getAnchor(selectedResult.get()));
        }
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        AbstractFile abstractFile = node.getLookup().lookup(AbstractFile.class);
        if (abstractFile != null) {
            try {
                if (Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().hasAnalysisResults(abstractFile.getId())) {
                    return true;
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get analysis results for file with obj id " + abstractFile.getId(), ex);
            }
        }

        Collection<? extends AnalysisResult> analysisResults = node.getLookup().lookupAll(AnalysisResult.class);
        return (!analysisResults.isEmpty());
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

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        textPanel = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(100, 58));

        textPanel.setEditable(false);
        textPanel.setBackground(DEFAULT_BACKGROUND);
        textPanel.setName(""); // NOI18N
        textPanel.setPreferredSize(new java.awt.Dimension(600, 52));
        scrollPane.setViewportView(textPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 907, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane textPanel;
    // End of variables declaration//GEN-END:variables
}
