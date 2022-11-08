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

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.contentviewers.analysisresults.AnalysisResultsViewModel.NodeResults;
import org.sleuthkit.autopsy.contentviewers.analysisresults.AnalysisResultsViewModel.ResultDisplayAttributes;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerHtmlStyles;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Score;

/**
 * Displays a list of analysis results in a panel.
 */
public class AnalysisResultsContentPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private static final String EMPTY_HTML = "<html><head></head><body></body></html>";

    // Anchors are inserted into the navigation so that the viewer can navigate to a selection.  
    // This is the prefix of those anchors.
    private static final String RESULT_ANCHOR_PREFIX = "AnalysisResult_";

    /**
     * Creates new form AnalysisResultsContentViewer
     */
    public AnalysisResultsContentPanel() {
        initComponents();
        ContentViewerHtmlStyles.setupHtmlJTextPane(textPanel);
    }

    /**
     * Clears current text and shows text provided in the message.
     *
     * @param message The message to be displayed.
     */
    void showMessage(String message) {
        ContentViewerHtmlStyles.setStyles(textPanel);
        textPanel.setText("<html><head></head><body>"
                + MessageFormat.format("<p class=\"{0}\">{1}</p>",
                        ContentViewerHtmlStyles.getMessageClassName(),
                        message == null ? "" : EscapeUtil.escapeHtml(message))
                + "</body></html>");
    }

    /**
     * Resets the current view and displays nothing.
     */
    void reset() {
        textPanel.setText(EMPTY_HTML);
    }

    /**
     * Displays analysis results for the node in the text pane.
     *
     * @param nodeResults The analysis results data to display.
     */
    void displayResults(NodeResults nodeResults) {
        Document document = Jsoup.parse(EMPTY_HTML);
        Element body = document.getElementsByTag("body").first();

        Optional<Element> panelHeader = appendPanelHeader(body, nodeResults.getItemName(), nodeResults.getAggregateScore());

        // for each analysis result item, display the data.
        List<ResultDisplayAttributes> displayAttributes = nodeResults.getAnalysisResults();
        for (int idx = 0; idx < displayAttributes.size(); idx++) {
            AnalysisResultsViewModel.ResultDisplayAttributes resultAttrs = displayAttributes.get(idx);
            Element sectionDiv = appendResult(body, idx, resultAttrs);
            if (idx > 0 || panelHeader.isPresent()) {
                sectionDiv.attr("class", ContentViewerHtmlStyles.getSpacedSectionClassName());
            }
        }

        // set the body html
        ContentViewerHtmlStyles.setStyles(textPanel);
        textPanel.setText(document.html());

        // if there is a selected result scroll to it
        Optional<AnalysisResult> selectedResult = nodeResults.getSelectedResult();
        if (selectedResult.isPresent()) {
            textPanel.scrollToReference(getAnchor(selectedResult.get()));
        } else {
            // otherwise, scroll to the beginning.
            textPanel.setCaretPosition(0);
        }
    }

    /**
     * Appends the header to the panel.
     *
     * @param parent   The parent html element.
     * @param itemName The item whose name will be added if present.
     * @param score    The aggregate score whose significance will be added if
     *                 present.
     *
     * @return The html element.
     */
    @Messages({
        "AnalysisResultsContentPanel_aggregateScore_displayKey=Aggregate Score",
        "AnalysisResultsContentPanel_content_displayKey=Item"
    })
    private Optional<Element> appendPanelHeader(Element parent, Optional<String> itemName, Optional<Score> score) {
        // if no item name or score, don't display
        if (!itemName.isPresent() || !score.isPresent()) {
            return Optional.empty();
        }

        Element container = parent.appendElement("div");

        // if there is content append the name
        container.appendElement("p")
                .attr("class", ContentViewerHtmlStyles.getTextClassName())
                .text(MessageFormat.format("{0}: {1}",
                        Bundle.AnalysisResultsContentPanel_content_displayKey(),
                        itemName.get()));

        // if there is an aggregate score, append the value
        container.appendElement("p")
                .attr("class", ContentViewerHtmlStyles.getTextClassName())
                .text(MessageFormat.format("{0}: {1}",
                        Bundle.AnalysisResultsContentPanel_aggregateScore_displayKey(),
                        score.get().getSignificance().getDisplayName()));

        return Optional.ofNullable(container);
    }

    /**
     * Returns the anchor id to use with the analysis result (based on the id).
     *
     * @param analysisResult The analysis result.
     *
     * @return The anchor id.
     */
    private String getAnchor(AnalysisResult analysisResult) {
        return RESULT_ANCHOR_PREFIX + analysisResult.getId();
    }

    /**
     * Appends a result item to the parent element of an html document.
     *
     * @param parent The parent element.
     * @param index  The index of the item in the list of all items.
     * @param attrs  The attributes of this item.
     *
     * @return The result div.
     */
    @NbBundle.Messages({"# {0} - analysisResultsNumber",
        "AnalysisResultsContentPanel_result_headerKey=Analysis Result {0}"
    })
    private Element appendResult(Element parent, int index, AnalysisResultsViewModel.ResultDisplayAttributes attrs) {
        // create a new section with appropriate header
        Element sectionDiv = appendSection(parent,
                Bundle.AnalysisResultsContentPanel_result_headerKey(index + 1),
                Optional.ofNullable(getAnchor(attrs.getAnalysisResult())));

        // create a table
        Element table = sectionDiv.appendElement("table")
                .attr("valign", "top")
                .attr("align", "left");

        table.attr("class", ContentViewerHtmlStyles.getIndentedClassName());

        Element tableBody = table.appendElement("tbody");

        // append a row for each item
        for (Pair<String, String> keyVal : attrs.getAttributesToDisplay()) {
            Element row = tableBody.appendElement("tr");
            String keyString = keyVal.getKey() == null ? "" : keyVal.getKey() + ":";
            Element keyTd = row.appendElement("td")
                    .attr("class", ContentViewerHtmlStyles.getKeyColumnClassName());

            keyTd.appendElement("span")
                    .text(keyString)
                    .attr("class", ContentViewerHtmlStyles.getTextClassName());

            String valueString = keyVal.getValue() == null ? "" : keyVal.getValue();
            row.appendElement("td")
                    .text(valueString)
                    .attr("class", ContentViewerHtmlStyles.getTextClassName());
        }

        return sectionDiv;
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
    private Element appendSection(Element parent, String headerText, Optional<String> anchorId) {
        Element sectionDiv = parent.appendElement("div");

        // append an anchor tag if there is one
        Element anchorEl = null;
        if (anchorId.isPresent()) {
            anchorEl = sectionDiv.appendElement("a");
            anchorEl.attr("name", anchorId.get());
            anchorEl.attr("style", "padding: 0px; margin: 0px; display: inline-block;");
        }

        // append the header
        Element header = null;
        header = (anchorEl == null)
                ? sectionDiv.appendElement("h1")
                : anchorEl.appendElement("h1");

        header.text(headerText);
        header.attr("class", ContentViewerHtmlStyles.getHeaderClassName());
        header.attr("style", "display: inline-block");

        // return the section element
        return sectionDiv;
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
