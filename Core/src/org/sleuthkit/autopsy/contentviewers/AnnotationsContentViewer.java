/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.lang3.StringEscapeUtils;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Annotations view of file contents.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 8)
@NbBundle.Messages({
    "AnnotationsContentViewer.title=Annotations",
    "AnnotationsContentViewer.toolTip=Displays tags and comments associated with the selected content."
})
public class AnnotationsContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(AnnotationsContentViewer.class.getName());

    /**
     * Creates an instance of AnnotationsContentViewer.
     */
    public AnnotationsContentViewer() {
        initComponents();
        Utilities.configureTextPaneAsHtml(jTextPane1);
    }

    @Override
    public void setNode(Node node) {
        if ((node == null) || (!isSupported(node))) {
            resetComponent();
            return;
        }

        StringBuilder html = new StringBuilder();

        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        Content sourceFile = null;

        try {
            if (artifact != null) {
                /*
                 * Get the source content based on the artifact to ensure we
                 * display the correct data instead of whatever was in the node.
                 */
                sourceFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
            } else {
                /*
                 * No artifact is present, so get the content based on what's
                 * present in the node. In this case, the selected item IS the
                 * source file.
                 */
                sourceFile = node.getLookup().lookup(AbstractFile.class);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format(
                    "Exception while trying to retrieve a Content instance from the BlackboardArtifact '%s' (id=%d).",
                    artifact.getDisplayName(), artifact.getArtifactID()), ex);
        }

        if (artifact != null) {
            populateTagData(html, artifact, sourceFile);
        } else {
            populateTagData(html, sourceFile);
        }

        if (sourceFile instanceof AbstractFile) {
            populateCentralRepositoryData(html, artifact, (AbstractFile) sourceFile);
        }

        setText(html.toString());
        jTextPane1.setCaretPosition(0);
    }

    /**
     * Populate the "Selected Item" sections with tag data for the supplied
     * content.
     *
     * @param html    The HTML text to update.
     * @param content Selected content.
     */
    private void populateTagData(StringBuilder html, Content content) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            startSection(html, "Selected Item");
            List<ContentTag> fileTagsList = tskCase.getContentTagsByContent(content);
            if (fileTagsList.isEmpty()) {
                addMessage(html, "There are no tags for the selected content.");
            } else {
                for (ContentTag tag : fileTagsList) {
                    addTagEntry(html, tag);
                }
            }
            endSection(html);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting tags from the case database.", ex); //NON-NLS
        }
    }

    /**
     * Populate the "Selected Item" and "Source File" sections with tag data for
     * a supplied artifact.
     *
     * @param html       The HTML text to update.
     * @param artifact   A selected artifact.
     * @param sourceFile The source content of the selected artifact.
     */
    private void populateTagData(StringBuilder html, BlackboardArtifact artifact, Content sourceFile) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            startSection(html, "Selected Item");
            List<BlackboardArtifactTag> artifactTagsList = tskCase.getBlackboardArtifactTagsByArtifact(artifact);
            if (artifactTagsList.isEmpty()) {
                addMessage(html, "There are no tags for the selected artifact.");
            } else {
                for (BlackboardArtifactTag tag : artifactTagsList) {
                    addTagEntry(html, tag);
                }
            }
            endSection(html);

            if (sourceFile != null) {
                startSection(html, "Source File");
                List<ContentTag> fileTagsList = tskCase.getContentTagsByContent(sourceFile);
                if (fileTagsList.isEmpty()) {
                    addMessage(html, "There are no tags for the source content.");
                } else {
                    for (ContentTag tag : fileTagsList) {
                        addTagEntry(html, tag);
                    }
                }
                endSection(html);
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting tags from the case database.", ex); //NON-NLS
        }
    }

    /**
     * Populate the "Central Repository Comments" section with data.
     *
     * @param html       The HTML text to update.
     * @param artifact   A selected artifact (can be null).
     * @param sourceFile A selected file, or a source file of the selected
     *                   artifact.
     */
    private void populateCentralRepositoryData(StringBuilder html, BlackboardArtifact artifact, AbstractFile sourceFile) {
        if (EamDb.isEnabled()) {
            startSection(html, "Central Repository Comments");
            List<CorrelationAttributeInstance> instancesList = new ArrayList<>();
            if (artifact != null) {
                instancesList.addAll(EamArtifactUtil.makeInstancesFromBlackboardArtifact(artifact, false));
            }
            try {
                List<CorrelationAttributeInstance.Type> artifactTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                String md5 = sourceFile.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttributeInstance.Type attributeType : artifactTypes) {
                        if (attributeType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCase());
                            instancesList.add(new CorrelationAttributeInstance(
                                    attributeType,
                                    md5,
                                    correlationCase,
                                    CorrelationDataSource.fromTSKDataSource(correlationCase, sourceFile.getDataSource()),
                                    sourceFile.getParentPath() + sourceFile.getName(),
                                    "",
                                    sourceFile.getKnown(),
                                    sourceFile.getId()));
                            break;
                        }
                    }
                }

                boolean commentDataFound = false;

                for (CorrelationAttributeInstance instance : instancesList) {
                    List<CorrelationAttributeInstance> correlatedInstancesList
                            = EamDb.getInstance().getArtifactInstancesByTypeValue(instance.getCorrelationType(), instance.getCorrelationValue());
                    for (CorrelationAttributeInstance correlatedInstance : correlatedInstancesList) {
                        if (correlatedInstance.getComment() != null && correlatedInstance.getComment().isEmpty() == false) {
                            commentDataFound = true;
                            addCentralRepositoryEntry(html, correlatedInstance);
                        }
                    }
                }

                if (commentDataFound == false) {
                    addMessage(html, "There is no comment data for the selected content in the Central Repository.");
                }
            } catch (EamDbException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Error connecting to the Central Repository database.", ex); // NON-NLS
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.SEVERE, "Error normalizing instance from Central Repository database.", ex); // NON-NLS
            }
            endSection(html);
        }
    }

    /**
     * Set the text of the text panel.
     *
     * @param text The text to set to the text panel.
     */
    private void setText(String text) {
        jTextPane1.setText("<html><body>" + text + "</body></html>"); //NON-NLS
    }

    /**
     * Start a new data section.
     *
     * @param html        The HTML text to add the section to.
     * @param sectionName The name of the section.
     */
    private void startSection(StringBuilder html, String sectionName) {
        html.append("<p style=\"font-size:14px;font-weight:bold;\">")
                .append(sectionName)
                .append("</p><br>"); //NON-NLS
    }

    /**
     * Add a message.
     *
     * @param html    The HTML text to add the message to.
     * @param message The message text.
     */
    private void addMessage(StringBuilder html, String message) {
        html.append("<p style=\"font-size:11px;font-style:italic;\">")
                .append(message)
                .append("</p><br>"); //NON-NLS
    }

    /**
     * Add a data table containing information about a tag.
     *
     * @param html The HTML text to add the table to.
     * @param tag  The tag whose information will be used to populate the table.
     */
    @NbBundle.Messages({
        "AnnotationsContentViewer.tagEntryDataLabel.tag=Tag:",
        "AnnotationsContentViewer.tagEntryDataLabel.tagUser=Tag User:",
        "AnnotationsContentViewer.tagEntryDataLabel.comment=Comment:"
    })
    private void addTagEntry(StringBuilder html, Tag tag) {
        startTable(html);
        addRow(html, Bundle.AnnotationsContentViewer_tagEntryDataLabel_tag(), tag.getName().getDisplayName());
        addRow(html, Bundle.AnnotationsContentViewer_tagEntryDataLabel_tagUser(), tag.getUserName());
        addRow(html, Bundle.AnnotationsContentViewer_tagEntryDataLabel_comment(), formatHtmlString(tag.getComment()));
        endTable(html);
    }

    /**
     * Add a data table containing information about a correlation attribute
     * instance in the Central Repository.
     *
     * @param html              The HTML text to add the table to.
     * @param attributeInstance The attribute instance whose information will be
     *                          used to populate the table.
     */
    @NbBundle.Messages({
        "AnnotationsContentViewer.centralRepositoryEntryDataLabel.case=Case:",
        "AnnotationsContentViewer.centralRepositoryEntryDataLabel.type=Type:",
        "AnnotationsContentViewer.centralRepositoryEntryDataLabel.comment=Comment:",
        "AnnotationsContentViewer.centralRepositoryEntryDataLabel.path=Path:"
    })
    private void addCentralRepositoryEntry(StringBuilder html, CorrelationAttributeInstance attributeInstance) {
        startTable(html);
        addRow(html, Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_case(), attributeInstance.getCorrelationCase().getDisplayName());
        addRow(html, Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_type(), attributeInstance.getCorrelationType().getDisplayName());
        addRow(html, Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_comment(), formatHtmlString(attributeInstance.getComment()));
        addRow(html, Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_path(), attributeInstance.getFilePath());
        endTable(html);
    }

    /**
     * Start a data table.
     *
     * @param html The HTML text to add the table to.
     */
    private void startTable(StringBuilder html) {
        html.append("<table>"); //NON-NLS
    }

    /**
     * Add a data row to a table.
     *
     * @param html  The HTML text to add the row to.
     * @param key   The key for the left column of the data row.
     * @param value The value for the right column of the data row.
     */
    private void addRow(StringBuilder html, String key, String value) {
        html.append("<tr><td valign=\"top\">"); //NON-NLS
        html.append(key);
        html.append("</td><td>"); //NON-NLS
        html.append(value);
        html.append("</td></tr>"); //NON-NLS
    }

    /**
     * End a data table.
     *
     * @param html The HTML text on which to end a table.
     */
    private void endTable(StringBuilder html) {
        html.append("</table><br><br>"); //NON-NLS
    }

    /**
     * End a data section.
     *
     * @param html The HTML text on which to end a section.
     */
    private void endSection(StringBuilder html) {
        html.append("<br>"); //NON-NLS
    }

    /**
     * Apply escape sequence to special characters. Line feed and carriage
     * return character combinations will be converted to HTML line breaks.
     *
     * @param text The text to format.
     *
     * @return The formatted text.
     */
    private String formatHtmlString(String text) {
        String formattedString = StringEscapeUtils.escapeHtml4(text);
        return formattedString.replaceAll("(\r\n|\r|\n|\n\r)", "<br>");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane5 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(100, 58));

        jTextPane1.setEditable(false);
        jTextPane1.setName(""); // NOI18N
        jTextPane1.setPreferredSize(new java.awt.Dimension(600, 52));
        jScrollPane5.setViewportView(jTextPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 907, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane5, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTextPane jTextPane1;
    // End of variables declaration//GEN-END:variables

    @Override
    public String getTitle() {
        return Bundle.AnnotationsContentViewer_title();
    }

    @Override
    public String getToolTip() {
        return Bundle.AnnotationsContentViewer_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new AnnotationsContentViewer();
    }

    @Override
    public boolean isSupported(Node node) {
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);

        try {
            if (artifact != null) {
                if (artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID()) != null) {
                    return true;
                }
            } else {
                if (node.getLookup().lookup(AbstractFile.class) != null) {
                    return true;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format(
                    "Exception while trying to retrieve a Content instance from the BlackboardArtifact '%s' (id=%d).",
                    artifact.getDisplayName(), artifact.getArtifactID()), ex);
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        return 1;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        setText("");
    }
}
