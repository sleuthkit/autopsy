/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import static org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Annotations view of file contents.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 8)
@Messages({
    "AnnotationsContentViewer.title=Annotations",
    "AnnotationsContentViewer.toolTip=Displays tags and comments associated with the selected content.",
    "AnnotationsContentViewer.centralRepositoryEntry.title=Central Repository Comments",
    "AnnotationsContentViewer.centralRepositoryEntry.onEmpty=There is no comment data for the selected content in the Central Repository.",
    "AnnotationsContentViewer.centralRepositoryEntryDataLabel.case=Case:",
    "AnnotationsContentViewer.centralRepositoryEntryDataLabel.type=Type:",
    "AnnotationsContentViewer.centralRepositoryEntryDataLabel.comment=Comment:",
    "AnnotationsContentViewer.centralRepositoryEntryDataLabel.path=Path:",
    "AnnotationsContentViewer.tagEntry.title=Tag Comments",
    "AnnotationsContentViewer.tagEntry.onContentEmpty=There are no tags for the selected content.",
    "AnnotationsContentViewer.tagEntry.onArtifactEmpty=There are no tags for the selected artifact.",
    "AnnotationsContentViewer.tagEntryDataLabel.tag=Tag:",
    "AnnotationsContentViewer.tagEntryDataLabel.tagUser=Examiner:",
    "AnnotationsContentViewer.tagEntryDataLabel.comment=Comment:",
    "AnnotationsContentViewer.fileHitEntry.hashSetHitTitle=Hash Set Hit Comments",
    "AnnotationsContentViewer.fileHitEntry.onHashSetHitEmpty=There are no hash set hits for the selected content.",
    "AnnotationsContentViewer.fileHitEntry.interestingFileHitTitle=Interesting File Hit Comments",
    "AnnotationsContentViewer.fileHitEntry.onInterestingFileHitEmpty=There are no interesting file hits for the selected content.",
    "AnnotationsContentViewer.fileHitEntry.setName=Set Name",
    "AnnotationsContentViewer.fileHitEntry.comment=Comment",
    "AnnotationsContentViewer.sourceFile.title=Source File"
})
public class AnnotationsContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(AnnotationsContentViewer.class.getName());

    private static final String EMPTY_HTML = "<html><head></head><body></body></html>";

    private static final int DEFAULT_FONT_SIZE = new JLabel().getFont().getSize();
    private static final int SUBHEADER_FONT_SIZE = DEFAULT_FONT_SIZE * 12 / 11;
    private static final int HEADER_FONT_SIZE = DEFAULT_FONT_SIZE * 14 / 11;
    
    private static final int DEFAULT_SUBSECTION_LEFT_PAD = DEFAULT_FONT_SIZE;

    private static final String HEADER_STYLE = "font-size:" + HEADER_FONT_SIZE + "px;font-weight:bold;";
    private static final String SUBHEADER_STYLE = "font-size:" + SUBHEADER_FONT_SIZE + "px;font-weight:bold;";
    private static final String MESSAGE_STYLE = "font-size:" + DEFAULT_FONT_SIZE + "px;font-style:italic;";
    private static final String CONTENT_STYLE = "font-size:" + DEFAULT_FONT_SIZE + "px;";

    private static final int DEFAULT_TABLE_SPACING = DEFAULT_FONT_SIZE * 2;
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_SIZE;
    private static final int DEFAULT_SUBSECTION_SPACING = DEFAULT_FONT_SIZE;

    private static final List<ColumnEntry<Tag>> TAG_COLUMNS = Arrays.asList(
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_tagEntryDataLabel_tag(),
                    (tag) -> (tag.getName() != null) ? tag.getName().getDisplayName() : null),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_tagEntryDataLabel_tagUser(), (tag) -> tag.getUserName()),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_tagEntryDataLabel_comment(), (tag) -> tag.getComment())
    );

    private static final List<ColumnEntry<BlackboardArtifact>> FILESET_HIT_COLUMNS = Arrays.asList(
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_fileHitEntry_setName(),
                    (bba) -> tryGetAttribute(bba, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_fileHitEntry_comment(),
                    (bba) -> tryGetAttribute(bba, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT))
    );

    private static final List<ColumnEntry<CorrelationAttributeInstance>> CENTRAL_REPO_COMMENTS_COLUMNS = Arrays.asList(
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_case(),
                    cai -> (cai.getCorrelationCase() != null) ? cai.getCorrelationCase().getDisplayName() : null),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_type(),
                    cai -> (cai.getCorrelationType() != null) ? cai.getCorrelationType().getDisplayName() : null),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_comment(), cai -> cai.getComment()),
            new ColumnEntry<>(Bundle.AnnotationsContentViewer_centralRepositoryEntryDataLabel_path(), cai -> cai.getFilePath())
    );

    private static class ColumnEntry<T> {

        private final String columnName;
        private final Function<T, String> valueRetriever;

        ColumnEntry(String columnName, Function<T, String> valueRetriever) {
            this.columnName = columnName;
            this.valueRetriever = valueRetriever;
        }

        String getColumnName() {
            return columnName;
        }

        Function<T, String> getValueRetriever() {
            return valueRetriever;
        }

        String retrieveValue(T object) {
            return valueRetriever.apply(object);
        }
    }

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

        Document html = Jsoup.parse(EMPTY_HTML);
        Element body = html.getElementsByTag("body").first();

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
            renderArtifact(body, artifact, sourceFile);
        } else {
            renderAbstractFile(body, sourceFile, false);
        }

        jTextPane1.setText(html.html());
        jTextPane1.setCaretPosition(0);
    }

    private static void renderArtifact(Element parent, BlackboardArtifact bba, Content sourceContent) {
        appendEntries(parent,
            Bundle.AnnotationsContentViewer_tagEntry_title(),
            Bundle.AnnotationsContentViewer_tagEntry_onArtifactEmpty(),
            getTags(bba), TAG_COLUMNS, false, true);

        if (sourceContent instanceof AbstractFile) {
            AbstractFile sourceFile = (AbstractFile) sourceContent;

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> centralRepoComments = getCentralRepositoryData(bba, sourceFile);
                appendEntries(parent,
                    Bundle.AnnotationsContentViewer_centralRepositoryEntry_title(),
                    Bundle.AnnotationsContentViewer_centralRepositoryEntry_onEmpty(),
                    centralRepoComments, CENTRAL_REPO_COMMENTS_COLUMNS, false, true);
            }
        }

        if (BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() == bba.getArtifactTypeID()) {
            appendEntries(parent,
                Bundle.AnnotationsContentViewer_fileHitEntry_hashSetHitTitle(),
                Bundle.AnnotationsContentViewer_fileHitEntry_onHashSetHitEmpty(),
                Arrays.asList(bba), FILESET_HIT_COLUMNS, false, false);
        }

        if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == bba.getArtifactTypeID()) {
            appendEntries(parent,
                Bundle.AnnotationsContentViewer_fileHitEntry_interestingFileHitTitle(),
                Bundle.AnnotationsContentViewer_fileHitEntry_onInterestingFileHitEmpty(),
                Arrays.asList(bba), FILESET_HIT_COLUMNS, false, false);
        }

        Element sourceFileSection = appendSection(parent, Bundle.AnnotationsContentViewer_sourceFile_title());
        renderAbstractFile(sourceFileSection, sourceContent, true);
    }

    private static void renderAbstractFile(Element parent, Content sourceContent, boolean isSubheader) {
        appendEntries(parent,
            Bundle.AnnotationsContentViewer_tagEntry_title(),
            Bundle.AnnotationsContentViewer_tagEntry_onContentEmpty(),
            getTags(sourceContent), TAG_COLUMNS, isSubheader, true);

        if (sourceContent instanceof AbstractFile) {
            AbstractFile sourceFile = (AbstractFile) sourceContent;

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> centralRepoComments = getCentralRepositoryData(null, sourceFile);
                appendEntries(parent,
                    Bundle.AnnotationsContentViewer_centralRepositoryEntry_title(),
                    Bundle.AnnotationsContentViewer_centralRepositoryEntry_onEmpty(),
                    centralRepoComments, CENTRAL_REPO_COMMENTS_COLUMNS, isSubheader, true);
            }

            appendEntries(parent,
                Bundle.AnnotationsContentViewer_fileHitEntry_hashSetHitTitle(),
                Bundle.AnnotationsContentViewer_fileHitEntry_onHashSetHitEmpty(),
                getFileSetHits(sourceFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT),
                FILESET_HIT_COLUMNS, isSubheader, false);

            appendEntries(parent,
                Bundle.AnnotationsContentViewer_fileHitEntry_interestingFileHitTitle(),
                Bundle.AnnotationsContentViewer_fileHitEntry_onInterestingFileHitEmpty(),
                getFileSetHits(sourceFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT),
                FILESET_HIT_COLUMNS, isSubheader, false);
        }

    }

    private static List<ContentTag> getTags(Content sourceContent) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            return tskCase.getContentTagsByContent(sourceContent);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting tags from the case database.", ex); //NON-NLS
        }
        return new ArrayList<>();
    }

    private static List<BlackboardArtifactTag> getTags(BlackboardArtifact bba) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            return tskCase.getBlackboardArtifactTagsByArtifact(bba);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting tags from the case database.", ex); //NON-NLS
        }
        return new ArrayList<>();
    }

    private static List<BlackboardArtifact> getFileSetHits(AbstractFile sourceFile, BlackboardArtifact.ARTIFACT_TYPE type) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            return tskCase.getBlackboardArtifacts(type, sourceFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting file set hits from the case database.", ex); //NON-NLS
        }
        return new ArrayList<>();
    }



    /**
     * Attempts to retrieve the attribute of a particular type from a blackboard
     * artifact.
     *
     * @param artifact      The artifact from which to retrieve the information.
     * @param attributeType The attribute type to retrieve from the artifact.
     *
     * @return The string value of the attribute or null if not found.
     */
    private static String tryGetAttribute(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) {
        if (artifact == null) {
            return null;
        }

        BlackboardAttribute attr = null;
        try {
            attr = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to fetch attribute of type %s for artifact %s", attributeType, artifact), ex);
        }

        if (attr == null) {
            return null;
        }

        return attr.getValueString();
    }

    /**
     * Populate the "Central Repository Comments" section with data.
     *
     * @param artifact   A selected artifact (can be null).
     * @param sourceFile A selected file, or a source file of the selected
     *                   artifact.
     */
    private static List<CorrelationAttributeInstance> getCentralRepositoryData(BlackboardArtifact artifact, AbstractFile sourceFile) {
        List<CorrelationAttributeInstance> toReturn = new ArrayList<>();

        List<CorrelationAttributeInstance> instancesList = new ArrayList<>();
        if (artifact != null) {
            instancesList.addAll(CorrelationAttributeUtil.makeCorrAttrsForCorrelation(artifact));
        }

        try {
            List<CorrelationAttributeInstance.Type> artifactTypes = CentralRepository.getInstance().getDefinedCorrelationTypes();
            String md5 = sourceFile.getMd5Hash();
            if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                for (CorrelationAttributeInstance.Type attributeType : artifactTypes) {
                    if (attributeType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                        CorrelationCase correlationCase = CentralRepository.getInstance().getCase(Case.getCurrentCase());
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

            for (CorrelationAttributeInstance instance : instancesList) {
                List<CorrelationAttributeInstance> correlatedInstancesList
                        = CentralRepository.getInstance().getArtifactInstancesByTypeValue(instance.getCorrelationType(), instance.getCorrelationValue());
                for (CorrelationAttributeInstance correlatedInstance : correlatedInstancesList) {
                    if (StringUtils.isNotEmpty(correlatedInstance.getComment())) {
                        toReturn.add(correlatedInstance);
                    }
                }
            }

        } catch (CentralRepoException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error connecting to the Central Repository database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error normalizing instance from Central Repository database.", ex); // NON-NLS
        }

        return toReturn;
    }
    
    
    private static <T> void appendEntries(Element parent, String title, String errorMessage, List<? extends T> items,
            List<ColumnEntry<T>> fields, boolean isSubsection, boolean isVerticalTable) {

        Element sectionDiv = (isSubsection) ? appendSubsection(parent, title) : appendSection(parent, title);

        if (items == null || items.isEmpty()) {
            appendMessage(sectionDiv, errorMessage);
        } else if (isVerticalTable) {
            appendVerticalEntryTables(sectionDiv, items, fields);
        } else {
            appendEntryTable(sectionDiv, items, fields);
        }
    }

    private static <T> Element appendVerticalEntryTables(Element parent, List<? extends T> items, List<ColumnEntry<T>> rowHeaders) {
        items.stream()
                .filter(item -> item != null)
                .forEach((item) -> {
                    List<List<String>> tableData = rowHeaders.stream()
                            .map(row -> Arrays.asList(row.getColumnName(), row.retrieveValue(item)))
                            .collect(Collectors.toList());

                    Element childTable = appendTable(parent, 2, tableData, null);
                    childTable.attr("style", String.format("margin-bottom: %dpx", DEFAULT_TABLE_SPACING));
                });

        return parent;
    }

    private static <T> Element appendEntryTable(Element parent, List<? extends T> items, List<ColumnEntry<T>> columns) {
        int columnNumber = columns.size();
        List<String> columnHeaders = columns.stream().map(c -> c.getColumnName()).collect(Collectors.toList());
        List<List<String>> rows = items.stream()
                .filter(r -> r != null)
                .map(r -> {
                    return columns.stream()
                            .map(c -> c.retrieveValue(r))
                            .collect(Collectors.toList());
                })
                .collect(Collectors.toList());

        Element table = appendTable(parent, columnNumber, rows, columnHeaders);
        table.attr("style", String.format("margin-bottom: %dpx", DEFAULT_TABLE_SPACING));
        return table;
    }

    private static Element appendTable(Element parent, int columnNumber, List<List<String>> content, List<String> columnHeaders) {
        Element table = parent.appendElement("table");
        if (columnHeaders != null && !columnHeaders.isEmpty()) {
            Element header = parent.appendElement("thead");
            appendRow(header, columnHeaders, columnNumber, true);
        }
        Element tableBody = table.appendElement("tbody");

        content.forEach((rowData) -> appendRow(tableBody, rowData, columnNumber, false));
        return table;
    }

    // TODO test sanitizing string
    private static Element appendRow(Element rowParent, List<String> data, int columnNumber, boolean isHeader) {
        String cellType = isHeader ? "th" : "td";
        Element row = rowParent.appendElement("tr");
        for (int i = 0; i < columnNumber; i++) {
            Element cell = row.appendElement(cellType);
            if (data != null && i < data.size()) {
                cell.attr("valign", "top");
                cell.attr("style", CONTENT_STYLE);
                cell.text(StringUtils.isEmpty(data.get(i)) ? "" : data.get(i));
            }
        }
        return row;
    }

    private static Element appendSection(Element parent, String headerText) {
        Element sectionDiv = parent.appendElement("div");
        sectionDiv.attr("style", String.format("margin-bottom: %dpx;", DEFAULT_SECTION_SPACING));
        Element header = sectionDiv.appendElement("h1");
        header.text(headerText);
        header.attr("style", HEADER_STYLE);
        return sectionDiv;
    }

    private static Element appendSubsection(Element parent, String headerText) {
        Element subsectionDiv = parent.appendElement("div");
        subsectionDiv.attr("style", String.format("margin-bottom: %dpx; padding-left: %dpx;", DEFAULT_SUBSECTION_SPACING, DEFAULT_SUBSECTION_LEFT_PAD));
        Element header = subsectionDiv.appendElement("h2");
        header.text(headerText);
        header.attr("style", SUBHEADER_STYLE);
        return subsectionDiv;
    }

    private static Element appendMessage(Element parent, String message) {
        Element messageEl = parent.appendElement("p");
        messageEl.text(message);
        messageEl.attr("style", MESSAGE_STYLE);
        return messageEl;
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
        jTextPane1.setText(EMPTY_HTML);
    }
}
