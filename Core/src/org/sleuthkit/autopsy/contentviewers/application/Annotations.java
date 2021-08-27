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
package org.sleuthkit.autopsy.contentviewers.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerHtmlStyles;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The business logic for the Annotations content panel.
 */
public class Annotations {

    @NbBundle.Messages({
        "Annotations.title=Annotations",
        "Annotations.toolTip=Displays tags and comments associated with the selected content.",
        "Annotations.centralRepositoryEntry.title=Central Repository Comments",
        "Annotations.centralRepositoryEntryDataLabel.case=Case:",
        "Annotations.centralRepositoryEntryDataLabel.type=Type:",
        "Annotations.centralRepositoryEntryDataLabel.comment=Comment:",
        "Annotations.centralRepositoryEntryDataLabel.path=Path:",
        "Annotations.tagEntry.title=Tags",
        "Annotations.tagEntryDataLabel.tag=Tag:",
        "Annotations.tagEntryDataLabel.tagUser=Examiner:",
        "Annotations.tagEntryDataLabel.comment=Comment:",
        "Annotations.fileHitEntry.artifactCommentTitle=Artifact Comment",
        "Annotations.fileHitEntry.hashSetHitTitle=Hash Set Hit Comments",
        "Annotations.fileHitEntry.interestingFileHitTitle=Interesting File Hit Comments",
        "Annotations.fileHitEntry.setName=Set Name:",
        "Annotations.fileHitEntry.comment=Comment:",
        "Annotations.sourceFile.title=Source File",
        "Annotations.onEmpty=No annotations were found for this particular item."
    })

    private static final Logger logger = Logger.getLogger(Annotations.class.getName());

    private static final String EMPTY_HTML = "<html><head></head><body></body></html>";

    // describing table values for a tag
    private static final List<ItemEntry<Tag>> TAG_ENTRIES = Arrays.asList(
            new ItemEntry<>(Bundle.Annotations_tagEntryDataLabel_tag(),
                    (tag) -> (tag.getName() != null) ? tag.getName().getDisplayName() : null),
            new ItemEntry<>(Bundle.Annotations_tagEntryDataLabel_tagUser(), (tag) -> tag.getUserName()),
            new ItemEntry<>(Bundle.Annotations_tagEntryDataLabel_comment(), (tag) -> tag.getComment())
    );

    private static final SectionConfig<Tag> TAG_CONFIG
            = new SectionConfig<>(Bundle.Annotations_tagEntry_title(), TAG_ENTRIES);

    // file set attributes and table configurations
    private static final List<ItemEntry<BlackboardArtifact>> FILESET_HIT_ENTRIES = Arrays.asList(
            new ItemEntry<>(Bundle.Annotations_fileHitEntry_setName(),
                    (bba) -> tryGetAttribute(bba, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)),
            new ItemEntry<>(Bundle.Annotations_fileHitEntry_comment(),
                    (bba) -> tryGetAttribute(bba, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT))
    );

    private static final SectionConfig<BlackboardArtifact> INTERESTING_FILE_CONFIG
            = new SectionConfig<>(Bundle.Annotations_fileHitEntry_interestingFileHitTitle(), FILESET_HIT_ENTRIES);

    private static final SectionConfig<BlackboardArtifact> HASHSET_CONFIG
            = new SectionConfig<>(Bundle.Annotations_fileHitEntry_hashSetHitTitle(), FILESET_HIT_ENTRIES);

    private static final SectionConfig<BlackboardArtifact> ARTIFACT_COMMENT_CONFIG
            = new SectionConfig<>(Bundle.Annotations_fileHitEntry_artifactCommentTitle(), FILESET_HIT_ENTRIES);

    // central repository attributes and table configuration
    private static final List<ItemEntry<CorrelationAttributeInstance>> CR_COMMENTS_ENTRIES = Arrays.asList(
            new ItemEntry<>(Bundle.Annotations_centralRepositoryEntryDataLabel_case(),
                    cai -> (cai.getCorrelationCase() != null) ? cai.getCorrelationCase().getDisplayName() : null),
            new ItemEntry<>(Bundle.Annotations_centralRepositoryEntryDataLabel_comment(), cai -> cai.getComment()),
            new ItemEntry<>(Bundle.Annotations_centralRepositoryEntryDataLabel_path(), cai -> cai.getFilePath())
    );

    private static final SectionConfig<CorrelationAttributeInstance> CR_COMMENTS_CONFIG
            = new SectionConfig<>(Bundle.Annotations_centralRepositoryEntry_title(), CR_COMMENTS_ENTRIES);

    /*
     * Private constructor for this utility class.
     */
    private Annotations() {

    }

    /**
     * Returns the formatted Annotation information for the given node. If no
     * data was found the method will return null;
     *
     * @param node Node to get data for.
     *
     * @return A formatted document of annotation information for the given node
     *         or null.
     */
    public static Document buildDocument(Node node) {
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

        boolean somethingWasRendered = false;
        if (artifact != null) {
            somethingWasRendered = renderArtifact(body, artifact, sourceFile);
        } else {
            somethingWasRendered = renderContent(body, sourceFile, false);
        }

        if (!somethingWasRendered) {
            return null;
        }

        return html;
    }

    /**
     * Renders annotations for an artifact.
     *
     * @param parent        The html element to render content int.
     * @param bba           The blackboard artifact to render.
     * @param sourceContent The content from which the blackboard artifact
     *                      comes.
     *
     * @return If any content was actually rendered.
     */
    private static boolean renderArtifact(Element parent, BlackboardArtifact bba, Content sourceContent) {
        boolean contentRendered = appendEntries(parent, TAG_CONFIG, getTags(bba), false, true);

        if (CentralRepository.isEnabled()) {
            List<CorrelationAttributeInstance> centralRepoComments = getCentralRepositoryData(bba);
            boolean crRendered = appendEntries(parent, CR_COMMENTS_CONFIG, centralRepoComments, false, !contentRendered);
            contentRendered = contentRendered || crRendered;
        }

        // if artifact is a hashset hit or interesting file and has a non-blank comment
        if ((BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() == bba.getArtifactTypeID()
                || BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == bba.getArtifactTypeID())
                && (hasTskComment(bba))) {

            boolean filesetRendered = appendEntries(parent, ARTIFACT_COMMENT_CONFIG, Arrays.asList(bba), false, !contentRendered);
            contentRendered = contentRendered || filesetRendered;
        }

        Element sourceFileSection = appendSection(parent, Bundle.Annotations_sourceFile_title());
        sourceFileSection.attr("class", ContentViewerHtmlStyles.getSpacedSectionClassName());

        Element sourceFileContainer = sourceFileSection.appendElement("div");
        sourceFileContainer.attr("class", ContentViewerHtmlStyles.getIndentedClassName());

        boolean sourceFileRendered = renderContent(sourceFileContainer, sourceContent, true);

        if (!sourceFileRendered) {
            sourceFileSection.remove();
        }

        return contentRendered || sourceFileRendered;
    }

    /**
     * Renders annotations for a content item.
     *
     * @param parent        The parent within which to render.
     * @param sourceContent The content for which annotations will be gathered.
     * @param isSubheader   True if this section should be rendered as a
     *                      subheader as opposed to a top-level header.
     *
     * @return If any content was actually rendered.
     */
    private static boolean renderContent(Element parent, Content sourceContent, boolean isSubheader) {
        boolean contentRendered = appendEntries(parent, TAG_CONFIG, getTags(sourceContent), isSubheader, true);

        if (sourceContent instanceof AbstractFile) {
            AbstractFile sourceFile = (AbstractFile) sourceContent;

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> centralRepoComments = getCentralRepositoryData(sourceFile);
                boolean crRendered = appendEntries(parent, CR_COMMENTS_CONFIG, centralRepoComments, isSubheader,
                        !contentRendered);
                contentRendered = contentRendered || crRendered;
            }

            boolean hashsetRendered = appendEntries(parent, HASHSET_CONFIG,
                    getFileSetHits(sourceFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT),
                    isSubheader,
                    !contentRendered);

            boolean interestingFileRendered = appendEntries(parent, INTERESTING_FILE_CONFIG,
                    getFileSetHits(sourceFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT),
                    isSubheader,
                    !contentRendered);

            contentRendered = contentRendered || hashsetRendered || interestingFileRendered;
        }
        return contentRendered;
    }

    /**
     * Retrieves tags associated with a content item.
     *
     * @param sourceContent The content for which to gather content.
     *
     * @return The Tags associated with this item.
     */
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

    /**
     * Retrieves tags for blackboard artifact tags.
     *
     * @param bba The blackboard artifact for which to retrieve tags.
     *
     * @return The found tags.
     */
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

    /**
     * Retrieves the blackboard artifacts for a source file matching a certain
     * type that have a non-blank TSK_COMMENT.
     *
     * @param sourceFile The source file for which to fetch artifacts.
     * @param type       The type of blackboard artifact to fetch.
     *
     * @return The artifacts found matching this type.
     */
    private static List<BlackboardArtifact> getFileSetHits(AbstractFile sourceFile, BlackboardArtifact.ARTIFACT_TYPE type) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            return tskCase.getBlackboardArtifacts(type, sourceFile.getId()).stream()
                    .filter((bba) -> hasTskComment(bba))
                    .collect(Collectors.toList());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting file set hits from the case database.", ex); //NON-NLS
        }
        return new ArrayList<>();
    }

    /**
     * Returns true if the artifact contains a non-blank TSK_COMMENT attribute.
     *
     * @param artifact The artifact to check.
     *
     * @return True if it has a non-blank TSK_COMMENT.
     */
    private static boolean hasTskComment(BlackboardArtifact artifact) {
        return StringUtils.isNotBlank(tryGetAttribute(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT));
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
     * Gets the "Central Repository Comments" section with data for the
     * blackboard artifact.
     *
     * @param artifact The selected artifact.
     *
     * @return The Correlation Attribute Instances associated with the artifact
     *         that have comments.
     */
    private static List<CorrelationAttributeInstance> getCentralRepositoryData(BlackboardArtifact artifact) {
        if (artifact == null) {
            return new ArrayList<>();
        }

        List<Pair<CorrelationAttributeInstance.Type, String>> lookupKeys = CorrelationAttributeUtil.makeCorrAttrsForSearch(artifact)
                .stream()
                .map(cai -> Pair.of(cai.getCorrelationType(), cai.getCorrelationValue()))
                .collect(Collectors.toList());

        return getCorrelationAttributeComments(lookupKeys);
    }

    /**
     * Gets the "Central Repository Comments" section with data.
     *
     * @param sourceFile A selected file, or a source file of the selected
     *                   artifact.
     *
     * @return The Correlation Attribute Instances associated with the
     *         sourcefile that have comments.
     */
    private static List<CorrelationAttributeInstance> getCentralRepositoryData(AbstractFile sourceFile) {
        if (sourceFile == null || StringUtils.isEmpty(sourceFile.getMd5Hash())) {
            return new ArrayList<>();
        }

        List<CorrelationAttributeInstance.Type> artifactTypes = null;
        try {
            artifactTypes = CentralRepository.getInstance().getDefinedCorrelationTypes();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error connecting to the Central Repository database.", ex); // NON-NLS
        }

        if (artifactTypes == null || artifactTypes.isEmpty()) {
            return new ArrayList<>();
        }

        String md5 = sourceFile.getMd5Hash();

        // get key lookups for a file attribute types and the md5 hash
        List<Pair<CorrelationAttributeInstance.Type, String>> lookupKeys = artifactTypes.stream()
                .filter((attributeType) -> attributeType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID)
                .map((attributeType) -> Pair.of(attributeType, md5))
                .collect(Collectors.toList());

        return getCorrelationAttributeComments(lookupKeys);
    }

    /**
     * Given a type and a value for that type, does a lookup in the Central
     * Repository for matching values that have comments.
     *
     * @param lookupKeys The type and value to lookup.
     *
     * @return The found correlation attribute instances.
     */
    private static List<CorrelationAttributeInstance> getCorrelationAttributeComments(List<Pair<CorrelationAttributeInstance.Type, String>> lookupKeys) {
        List<CorrelationAttributeInstance> instancesToRet = new ArrayList<>();

        try {
            // use lookup instances to find the actual correlation attributes for the items selected
            for (Pair<CorrelationAttributeInstance.Type, String> typeVal : lookupKeys) {
                instancesToRet.addAll(CentralRepository.getInstance()
                        .getArtifactInstancesByTypeValue(typeVal.getKey(), typeVal.getValue())
                        .stream()
                        // for each one found, if it has a comment, return
                        .filter((cai) -> StringUtils.isNotBlank(cai.getComment()))
                        .collect(Collectors.toList()));
            }

        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error connecting to the Central Repository database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error normalizing instance from Central Repository database.", ex); // NON-NLS
        }

        return instancesToRet;
    }

    /**
     * Append entries to the parent element in the annotations viewer. Entries
     * will be formatted as a table in the format specified in the
     * SectionConfig.
     *
     * @param parent         The parent element for which the entries will be
     *                       attached.
     * @param config         The display configuration for this entry type (i.e.
     *                       table type, name, if data is not present).
     * @param items          The items to display.
     * @param isSubsection   Whether or not this should be displayed as a
     *                       subsection. If not displayed as a top-level
     *                       section.
     * @param isFirstSection Whether or not this is the first section appended.
     *
     * @return If there was actual content rendered for this set of entries.
     */
    private static <T> boolean appendEntries(Element parent, Annotations.SectionConfig<T> config, List<? extends T> items,
            boolean isSubsection, boolean isFirstSection) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        Element sectionDiv = (isSubsection) ? appendSubsection(parent, config.getTitle()) : appendSection(parent, config.getTitle());
        if (!isFirstSection) {
            sectionDiv.attr("class", ContentViewerHtmlStyles.getSpacedSectionClassName());
        }

        Element sectionContainer = sectionDiv.appendElement("div");

        if (!isSubsection) {
            sectionContainer.attr("class", ContentViewerHtmlStyles.getIndentedClassName());
        }

        appendVerticalEntryTables(sectionContainer, items, config.getAttributes());
        return true;
    }

    /**
     * Appends a table where items are displayed in rows of key-value pairs.
     *
     * @param parent     The parent to append the table.
     * @param items      The items to process into a series of tables.
     * @param rowHeaders The keys and the means to process items in order to get
     *                   key-value pairs.
     *
     * @return The parent element provided as parameter.
     */
    private static <T> Element appendVerticalEntryTables(Element parent, List<? extends T> items, List<ItemEntry<T>> rowHeaders) {
        boolean isFirst = true;
        for (T item : items) {
            if (item == null) {
                continue;
            }

            List<List<String>> tableData = rowHeaders.stream()
                    .map(row -> Arrays.asList(row.getItemName(), row.retrieveValue(item)))
                    .collect(Collectors.toList());

            Element childTable = appendTable(parent, 2, tableData, null);

            if (isFirst) {
                isFirst = false;
            } else {
                childTable.attr("class", ContentViewerHtmlStyles.getSpacedSectionClassName());
            }
        }

        return parent;
    }

    /**
     * Appends a generic table to the parent element.
     *
     * @param parent        The parent element that will have a table appended
     *                      to it.
     * @param columnNumber  The number of columns to append.
     * @param content       The content in content.get(row).get(column) format.
     * @param columnHeaders The column headers or null if no column headers
     *                      should be created.
     *
     * @return The created table.
     */
    private static Element appendTable(Element parent, int columnNumber, List<List<String>> content, List<String> columnHeaders) {
        Element table = parent.appendElement("table")
                .attr("valign", "top")
                .attr("align", "left");

        if (columnHeaders != null && !columnHeaders.isEmpty()) {
            Element header = table.appendElement("thead");
            appendRow(header, columnHeaders, columnNumber, true);
        }
        Element tableBody = table.appendElement("tbody");

        content.forEach((rowData) -> appendRow(tableBody, rowData, columnNumber, false));
        return table;
    }

    /**
     * Appends a row to the parent element (should be thead or tbody).
     *
     * @param rowParent    The parent table element.
     * @param data         The data to place in columns within the table.
     * @param columnNumber The number of columns to append.
     * @param isHeader     Whether or not this should have header cells ('th')
     *                     instead of regular cells ('td').
     *
     * @return The row created.
     */
    private static Element appendRow(Element rowParent, List<String> data, int columnNumber, boolean isHeader) {
        String cellType = isHeader ? "th" : "td";
        Element row = rowParent.appendElement("tr");
        for (int i = 0; i < columnNumber; i++) {
            Element cell = row.appendElement(cellType);

            if (i == 0) {
                cell.attr("class", ContentViewerHtmlStyles.getKeyColumnClassName());
            }

            if (data != null && i < data.size()) {
                cell.appendElement("span")
                        .attr("class", ContentViewerHtmlStyles.getTextClassName())
                        .text(StringUtils.isEmpty(data.get(i)) ? "" : data.get(i));
            }
        }
        return row;
    }

    /**
     * Appends a new section with a section header to the parent element.
     *
     * @param parent     The element to append this section to.
     * @param headerText The text for the section.
     *
     * @return The div for the new section.
     */
    private static Element appendSection(Element parent, String headerText) {
        Element sectionDiv = parent.appendElement("div");
        Element header = sectionDiv.appendElement("h1");
        header.text(headerText);
        header.attr("class", ContentViewerHtmlStyles.getHeaderClassName());
        return sectionDiv;
    }

    /**
     * Appends a new subsection with a subsection header to the parent element.
     *
     * @param parent     The element to append this subsection to.
     * @param headerText The text for the subsection.
     *
     * @return The div for the new subsection.
     */
    private static Element appendSubsection(Element parent, String headerText) {
        Element subsectionDiv = parent.appendElement("div");
        Element header = subsectionDiv.appendElement("h2");
        header.text(headerText);
        header.attr("class", ContentViewerHtmlStyles.getHeaderClassName());
        return subsectionDiv;
    }

    /**
     * Appends a message to the parent element. This is typically used in the
     * event that no data exists for a certain type.
     *
     * @param parent  The parent element that will have this message appended to
     *                it.
     * @param message The message to append.
     *
     * @return The paragraph element for the new message.
     */
    private static Element appendMessage(Element parent, String message) {
        Element messageEl = parent.appendElement("p");
        messageEl.text(message);
        messageEl.attr("class", ContentViewerHtmlStyles.getMessageClassName());
        return messageEl;
    }

    /**
     * Describes a key value pair for an item of type T where the key is the
     * field name to display and the value is retrieved from item of type T
     * using a provided Function<T, string>.
     *
     * @param <T> The item type.
     */
    static class ItemEntry<T> {

        private final String itemName;
        private final Function<T, String> valueRetriever;

        ItemEntry(String itemName, Function<T, String> valueRetriever) {
            this.itemName = itemName;
            this.valueRetriever = valueRetriever;
        }

        String getItemName() {
            return itemName;
        }

        Function<T, String> getValueRetriever() {
            return valueRetriever;
        }

        String retrieveValue(T object) {
            return valueRetriever.apply(object);
        }
    }

    /**
     * Describes a section that will be appended to the annotations view panel.
     *
     * @param <T> The item type for items to display.
     */
    static class SectionConfig<T> {

        private final String title;
        private final List<ItemEntry<T>> attributes;

        SectionConfig(String title, List<ItemEntry<T>> attributes) {
            this.title = title;
            this.attributes = attributes;
        }

        /**
         * @return The title for the section.
         */
        String getTitle() {
            return title;
        }

        /**
         * @return Describes key-value pairs on the object to display to the
         *         user.
         */
        List<ItemEntry<T>> getAttributes() {
            return attributes;
        }
    }

}
