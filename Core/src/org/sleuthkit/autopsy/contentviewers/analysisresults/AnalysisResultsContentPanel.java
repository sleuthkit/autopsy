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
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.text.html.HTMLEditorKit;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.contentviewers.analysisresults.AnalysisResultsViewModel.NodeResults;
import org.sleuthkit.autopsy.contentviewers.analysisresults.AnalysisResultsViewModel.ResultDisplayAttributes;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Score;

/**
 * Displays a list of analysis results in a panel.
 */
public class AnalysisResultsContentPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
        
    private static final String EMPTY_HTML = "<html><head></head><body></body></html>";

    private static final String DEFAULT_FONT_FAMILY = new JLabel().getFont().getFamily();
    private static final int DEFAULT_FONT_SIZE = new JLabel().getFont().getSize();
    private static final Color DEFAULT_BACKGROUND = new JLabel().getBackground();

    // html stylesheet classnames for components
    private static final String ANALYSIS_RESULTS_CLASS_PREFIX = "analysisResult_";
    private static final String SPACED_SECTION_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "spacedSection";
    private static final String SUBSECTION_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "subsection";
    private static final String HEADER_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "header";
    public static final String MESSAGE_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "message";
    public static final String TD_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "td";

    // Anchors are inserted into the navigation so that the viewer can navigate to a selection.  
    // This is the prefix of those anchors.
    private static final String RESULT_ANCHOR_PREFIX = "AnalysisResult_";

    // how big the header should be
    private static final int HEADER_FONT_SIZE = DEFAULT_FONT_SIZE + 2;

    // spacing occurring after an item
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_SIZE / 2;
    private static final int CELL_SPACING = DEFAULT_FONT_SIZE / 2;

    // the subsection indent
    private static final int DEFAULT_SUBSECTION_LEFT_PAD = DEFAULT_FONT_SIZE;

    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-size: %dpt;font-style:italic; margin: 0px; padding: 0px; } ", MESSAGE_CLASSNAME, DEFAULT_FONT_SIZE)
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px; } ",
                    HEADER_CLASSNAME, DEFAULT_FONT_FAMILY, HEADER_FONT_SIZE)
            + String.format(" .%s { vertical-align: top; font-family: %s; font-size: %dpt; text-align: left; margin: 0pt; padding: 0px %dpt 0px 0px;} ",
                    TD_CLASSNAME, DEFAULT_FONT_FAMILY, DEFAULT_FONT_SIZE, CELL_SPACING)
            + String.format(" .%s { margin-top: %dpt; } ", SPACED_SECTION_CLASSNAME, DEFAULT_SECTION_SPACING)
            + String.format(" .%s { padding-left: %dpt; }", SUBSECTION_CLASSNAME, DEFAULT_SUBSECTION_LEFT_PAD);
    


    /**
     * Creates new form AnalysisResultsContentViewer
     */
    public AnalysisResultsContentPanel() {
        initComponents();

        textPanel.setContentType("text/html;charset=UTF-8"); //NON-NLS
        HTMLEditorKit kit = new HTMLEditorKit();
        textPanel.setEditorKit(kit);
        kit.getStyleSheet().addRule(STYLE_SHEET_RULE);
    }

    /**
     * Clears current text and shows text provided in the message.
     *
     * @param message The message to be displayed.
     */
    void showMessage(String message) {
        textPanel.setText("<html><head></head><body>"
                + MessageFormat.format("<p class='{0}'>{1}</p>",
                        MESSAGE_CLASSNAME,
                        message == null ? "" : message)
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
    @NbBundle.Messages("AnalysisResultsContentPanel_aggregateScore_displayKey=Aggregate Score")
    void displayResults(NodeResults nodeResults) {
        Document document = Jsoup.parse(EMPTY_HTML);
        Element body = document.getElementsByTag("body").first();

        // if there is an aggregate score, append a section with the value
        Optional<Score> aggregateScore = nodeResults.getAggregateScore();
        if (aggregateScore.isPresent()) {
            appendSection(body,
                    MessageFormat.format("{0}: {1}",
                            Bundle.AnalysisResultsContentPanel_aggregateScore_displayKey(),
                            aggregateScore.get().getSignificance().getDisplayName()),
                    Optional.empty());
        }

        // for each analysis result item, display the data.
        List<ResultDisplayAttributes> displayAttributes = nodeResults.getAnalysisResults();
        for (int idx = 0; idx < displayAttributes.size(); idx++) {
            AnalysisResultsViewModel.ResultDisplayAttributes resultAttrs = displayAttributes.get(idx);
            appendResult(body, idx, resultAttrs);
        }

        // set the body html
        textPanel.setText(document.html());
        
        // if there is a selected result scroll to it
        Optional<AnalysisResult> selectedResult = nodeResults.getSelectedResult();
        if (selectedResult.isPresent()) {
            textPanel.scrollToReference(getAnchor(selectedResult.get()));
        }
    }

    /**
     * Returns the anchor id to use with the analysis result (based on the id).
     * @param analysisResult The analysis result.
     * @return The anchor id.
     */
    private String getAnchor(AnalysisResult analysisResult) {
        return RESULT_ANCHOR_PREFIX + analysisResult.getId();
    }


    /**
     * Appends a result item to the parent element of an html document.
     * @param parent The parent element.
     * @param index The index of the item in the list of all items.
     * @param attrs The attributes of this item.
     */
    @NbBundle.Messages({"# {0} - analysisResultsNumber",
        "AnalysisResultsContentPanel_result_headerKey=Analysis Result {0}"
    })
    private void appendResult(Element parent, int index, AnalysisResultsViewModel.ResultDisplayAttributes attrs) {
        // create a new section with appropriate header
        Element sectionDiv = appendSection(parent,
                Bundle.AnalysisResultsContentPanel_result_headerKey(index + 1),
                Optional.ofNullable(getAnchor(attrs.getAnalysisResult())));
        
        // create a table
        Element table = sectionDiv.appendElement("table");
        table.attr("class", SUBSECTION_CLASSNAME);

        Element tableBody = table.appendElement("tbody");

        // append a row for each item
        for (Pair<String, String> keyVal : attrs.getAttributesToDisplay()) {
            Element row = tableBody.appendElement("tr");
            String keyString = keyVal.getKey() == null ? "" : keyVal.getKey() + ":";
            row.appendElement("td")
                    .text(keyString)
                    .attr("class", TD_CLASSNAME);

            String valueString = keyVal.getValue() == null ? "" : keyVal.getValue();
            row.appendElement("td")
                    .text(valueString)
                    .attr("class", TD_CLASSNAME);
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
    private Element appendSection(Element parent, String headerText, Optional<String> anchorId) {
        Element sectionDiv = parent.appendElement("div");
        
        // append an anchor tag if there is one
        if (anchorId.isPresent()) {
            Element anchorEl = sectionDiv.appendElement("a");
            anchorEl.attr("name", anchorId.get());
        }

        // set the class for the section
        sectionDiv.attr("class", SPACED_SECTION_CLASSNAME);
        
        // append the header
        Element header = sectionDiv.appendElement("h1");
        header.text(headerText);
        header.attr("class", HEADER_CLASSNAME);
        
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
