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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
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
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        StringBuilder html = new StringBuilder();
        
        populateTagData(html, artifact, file);
        populateCentralRepositoryData(html, artifact, file);
        
        setText(html.toString());
        jTextPane1.setCaretPosition(0);
    }
    
    /**
     * Populate the "Selected Item" and "Source File" sections with tag data.
     * 
     * @param html     The HTML text to update.
     * @param artifact A selected artifact (can be null).
     * @param file     A selected file, or a source file of the selected
     *                 artifact.
     */
    private void populateTagData(StringBuilder html, BlackboardArtifact artifact, AbstractFile file) {
        Case openCase;
        SleuthkitCase tskCase;
        try {
            openCase = Case.getCurrentCaseThrows();
            tskCase = openCase.getSleuthkitCase();
            List<ContentTag> fileTagsList = null;
            
            startSection(html, "Selected Item");
            if (artifact != null) {
                List<BlackboardArtifactTag> artifactTagsList = tskCase.getBlackboardArtifactTagsByArtifact(artifact);
                if (artifactTagsList.isEmpty()) {
                    addMessage(html, "There are no tags for the selected artifact.");
                } else {
                    for (BlackboardArtifactTag tag : artifactTagsList) {
                        addTagEntry(html, tag);
                    }
                }
            } else {
                fileTagsList = tskCase.getContentTagsByContent(file);
                if (fileTagsList.isEmpty()) {
                    addMessage(html, "There are no tags for the selected file.");
                } else {
                    for (ContentTag tag : fileTagsList) {
                        addTagEntry(html, tag);
                    }
                }
            }
            endSection(html);
            
            if (fileTagsList == null) {
                startSection(html, "Source File");
                fileTagsList = tskCase.getContentTagsByContent(file);
                if (fileTagsList.isEmpty()) {
                    addMessage(html, "There are no tags for the source file.");
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
     * @param html     The HTML text to update.
     * @param artifact A selected artifact (can be null).
     * @param file     A selected file, or a source file of the selected artifact.
     */
    private void populateCentralRepositoryData(StringBuilder html, BlackboardArtifact artifact, AbstractFile file) {
        if (EamDbUtil.useCentralRepo()) {
            startSection(html, "Central Repository Comments");
            List<CorrelationAttribute> attributesList = new ArrayList<>();
            if (artifact != null) {
                attributesList.addAll(EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(artifact, false, false));
            }
            try {
                List<CorrelationAttribute.Type> artifactTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                String md5 = file.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttribute.Type aType : artifactTypes) {
                        if (aType.getId() == CorrelationAttribute.FILES_TYPE_ID) {
                            attributesList.add(new CorrelationAttribute(aType, md5));
                            break;
                        }
                    }
                }

                boolean commentDataFound = false;
                for (CorrelationAttribute attribute : attributesList) {
                    List<CorrelationAttributeInstance> instancesList =
                            EamDb.getInstance().getArtifactInstancesByTypeValue(attribute.getCorrelationType(), attribute.getCorrelationValue());
                    for (CorrelationAttributeInstance attributeInstance : instancesList) {
                        if (attributeInstance.getComment() != null && attributeInstance.getComment().isEmpty() == false) {
                            commentDataFound = true;
                            addCentralRepositoryEntry(html, attributeInstance, attribute.getCorrelationType());
                        }
                    }
                }

                if (commentDataFound == false) {
                    addMessage(html, "There is no comment data for the selected content in the central repository.");
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error connecting to the central repository database.", ex); // NON-NLS
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
        html.append("<p style=\"font-style:italic;\">")
                .append(message)
                .append("</p><br>"); //NON-NLS
    }
    
    /**
     * Add a data table containing information about a tag.
     * 
     * @param html The HTML text to add the table to.
     * @param tag  The tag whose information will be used to populate the table.
     */
    private void addTagEntry(StringBuilder html, Tag tag) {
        startTable(html);
        addRow(html, "Tag:", tag.getName().getDisplayName());
        addRow(html, "Tag User:", tag.getUserName());
        addRow(html, "Comment:", convertLineBreaksToHtml(tag.getComment()));
        endTable(html);
    }
    
    /**
     * Add a data table containing information about a correlation attribute
     * instance in the central repository.
     * 
     * @param html              The HTML text to add the table to.
     * @param attributeInstance The attribute instance whose information will be
     *                          used to populate the table.
     * @param correlationType   The correlation data type.
     */
    private void addCentralRepositoryEntry(StringBuilder html, CorrelationAttributeInstance attributeInstance, CorrelationAttribute.Type correlationType) {
        startTable(html);
        addRow(html, "Case:", attributeInstance.getCorrelationCase().getDisplayName());
        addRow(html, "Type:", correlationType.getDisplayName());
        addRow(html, "Comment:", convertLineBreaksToHtml(attributeInstance.getComment()));
        addRow(html, "Path:", attributeInstance.getFilePath());
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
     * Convert line feed and carriage return character combinations to HTML line
     * breaks.
     * 
     * @param text The text to apply conversions.
     * @return The converted text.
     */
    private String convertLineBreaksToHtml(String text) {
        return text.replaceAll("(\r\n|\r|\n|\n\r)", "<br>");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickMenu = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(AnnotationsContentViewer.class, "AnnotationsContentViewer.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(AnnotationsContentViewer.class, "AnnotationsContentViewer.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

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
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTextPane jTextPane1;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
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
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        return file != null;
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
