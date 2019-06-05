/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import net.htmlparser.jericho.Attribute;
import net.htmlparser.jericho.OutputDocument;
import net.htmlparser.jericho.Source;
import org.openide.util.NbBundle.Messages;

/**
 * A file content viewer for HTML files.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class HtmlPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(HtmlPanel.class.getName());
    private static final long serialVersionUID = 1L;

    private String htmlText;

    /**
     * Creates new form HtmlViewerPanel
     */
    HtmlPanel() {
        initComponents();

        Utilities.configureTextPaneAsHtml(htmlbodyTextPane);
    }

    /**
     * Set the text pane's HTML text and refresh the view to display it.
     *
     * @param htmlText The HTML text to be applied to the text pane.
     */
    void setHtmlText(String htmlText) {
        this.htmlText = htmlText;
        refresh();
    }

    /**
     * Clear the HTML in the text pane and disable the show/hide button.
     */
    void reset() {
        htmlbodyTextPane.setText("");
        showImagesToggleButton.setEnabled(false);
    }

    /**
     * Cleans out input HTML string so it will not access resources over the internet
     *
     * @param htmlInString The HTML string to cleanse
     *
     * @return The cleansed HTML String
     */
    private String cleanseHTML(String htmlInString) {
        String returnString = "";
        try {
            Source source = new Source(new StringReader(htmlInString));
            OutputDocument document = new OutputDocument(source);
            //remove background images
            source.getAllTags().stream().filter((tag) -> (tag.toString().contains("background-image"))).forEachOrdered((tag) -> {
                document.remove(tag);
            });
            //remove images
            source.getAllElements("img").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove frames
            source.getAllElements("frame").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove iframes
            source.getAllElements("iframe").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove pictures
            source.getAllElements("picture").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove svg
            source.getAllElements("svg").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove audio
            source.getAllElements("audio").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove video
            source.getAllElements("video").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove tracks
            source.getAllElements("track").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove embeded external elements
            source.getAllElements("embed").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove linked elements
            source.getAllElements("link").forEach((element) -> {
                document.remove(element.getAllTags());
            });
            //remove other URI elements such as input boxes
            List<Attribute> attributesToRemove = source.getURIAttributes();
            document.remove(attributesToRemove);
            returnString = document.toString();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read html for cleaning out URI elements with Jericho", ex);
        }
        return returnString;
    }

    /**
     * Refresh the panel to reflect the current show/hide images setting.
     */
    @Messages({
        "HtmlPanel_showImagesToggleButton_show=Download Images",
        "HtmlPanel_showImagesToggleButton_hide=Hide Images",
        "Html_text_display_error=The HTML text cannot be displayed, it may not be correctly formed HTML.",
    })
    private void refresh() {
        if (false == htmlText.isEmpty()) {
            try {
                if (showImagesToggleButton.isSelected()) {
                    showImagesToggleButton.setText(Bundle.HtmlPanel_showImagesToggleButton_hide());
                    this.htmlbodyTextPane.setText(wrapInHtmlBody(htmlText));
                } else {
                    showImagesToggleButton.setText(Bundle.HtmlPanel_showImagesToggleButton_show());
                    this.htmlbodyTextPane.setText(wrapInHtmlBody(cleanseHTML(htmlText)));
                }
                showImagesToggleButton.setEnabled(true);
                htmlbodyTextPane.setCaretPosition(0);
            } catch(Exception ex) {
                this.htmlbodyTextPane.setText(wrapInHtmlBody(Bundle.Html_text_display_error()));
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        htmlScrollPane = new javax.swing.JScrollPane();
        htmlbodyTextPane = new javax.swing.JTextPane();
        showImagesToggleButton = new javax.swing.JToggleButton();

        htmlScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        htmlbodyTextPane.setEditable(false);
        htmlScrollPane.setViewportView(htmlbodyTextPane);

        org.openide.awt.Mnemonics.setLocalizedText(showImagesToggleButton, org.openide.util.NbBundle.getMessage(HtmlPanel.class, "HtmlPanel.showImagesToggleButton.text")); // NOI18N
        showImagesToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showImagesToggleButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(htmlScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(showImagesToggleButton)
                .addGap(0, 75, Short.MAX_VALUE))
            .addComponent(htmlJPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(showImagesToggleButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(htmlScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void showImagesToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showImagesToggleButtonActionPerformed
        refresh();
    }//GEN-LAST:event_showImagesToggleButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane htmlScrollPane;
    private javax.swing.JTextPane htmlbodyTextPane;
    private javax.swing.JToggleButton showImagesToggleButton;
    // End of variables declaration//GEN-END:variables
}
