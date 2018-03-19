/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

class TableReportGenerator {

    private final List<BlackboardArtifact.Type> artifactTypes = new ArrayList<>();
    private final HashSet<String> tagNamesFilter = new HashSet<>();

    private final Set<Content> images = new HashSet<>();
    private final ReportProgressPanel progressPanel;
    private final TableReportModule tableReport;
    private final Map<Integer, List<Column>> columnHeaderMap;
    private static final Logger logger = Logger.getLogger(TableReportGenerator.class.getName());

    private final List<String> errorList;

    TableReportGenerator(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections, ReportProgressPanel progressPanel, TableReportModule tableReport) {

        this.progressPanel = progressPanel;
        this.tableReport = tableReport;
        this.columnHeaderMap = new HashMap<>();
        errorList = new ArrayList<>();
        // Get the artifact types selected by the user.
        for (Map.Entry<BlackboardArtifact.Type, Boolean> entry : artifactTypeSelections.entrySet()) {
            if (entry.getValue()) {
                artifactTypes.add(entry.getKey());
            }
        }

        // Get the tag names selected by the user and make a tag names filter.
        if (null != tagNameSelections) {
            for (Map.Entry<String, Boolean> entry : tagNameSelections.entrySet()) {
                if (entry.getValue() == true) {
                    tagNamesFilter.add(entry.getKey());
                }
            }
        }
    }

    protected void execute() {
        // Start the progress indicators for each active TableReportModule.

        progressPanel.start();
        progressPanel.setIndeterminate(false);
        progressPanel.setMaximumProgress(this.artifactTypes.size() + 2); // +2 for content and blackboard artifact tags
        // report on the blackboard results
        if (progressPanel.getStatus() != ReportProgressPanel.ReportStatus.CANCELED) {
            makeBlackboardArtifactTables();
        }

        // report on the tagged files and artifacts
        if (progressPanel.getStatus() != ReportProgressPanel.ReportStatus.CANCELED) {
            makeContentTagsTables();
        }

        if (progressPanel.getStatus() != ReportProgressPanel.ReportStatus.CANCELED) {
            makeBlackboardArtifactTagsTables();
        }

        if (progressPanel.getStatus() != ReportProgressPanel.ReportStatus.CANCELED) {
            // report on the tagged images
            makeThumbnailTable();
        }
    }

    /**
     * Generate the tables for the selected blackboard artifacts
     */
    private void makeBlackboardArtifactTables() {
        // Make a comment string describing the tag names filter in effect. 
        String comment = "";
        if (!tagNamesFilter.isEmpty()) {
            comment += NbBundle.getMessage(this.getClass(), "ReportGenerator.artifactTable.taggedResults.text");
            comment += makeCommaSeparatedList(tagNamesFilter);
        }

        // Add a table to the report for every enabled blackboard artifact type.
        for (BlackboardArtifact.Type type : artifactTypes) {
            // Check for cancellaton.

            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                return;
            }

            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                            type.getDisplayName()));

            // Keyword hits and hashset hit artifacts get special handling.
            if (type.getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                writeKeywordHits(tableReport, comment, tagNamesFilter);
                continue;
            } else if (type.getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                writeHashsetHits(tableReport, comment, tagNamesFilter);
                continue;
            }

            List<ArtifactData> artifactList = getFilteredArtifacts(type, tagNamesFilter);

            if (artifactList.isEmpty()) {
                continue;
            }

            /*
             * TSK_ACCOUNT artifacts get grouped by their TSK_ACCOUNT_TYPE
             * attribute, and then handed off to the standard method for writing
             * tables.
             */
            if (type.getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                //Group account artifacts by their account type
                ListMultimap<String, ArtifactData> groupedArtifacts = Multimaps.index(artifactList,
                        artifactData -> {
                            try {
                                return artifactData.getArtifact().getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE)).getValueString();
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Unable to get value of TSK_ACCOUNT_TYPE attribute. Defaulting to \"unknown\"", ex);
                                return "unknown";
                            }
                        });
                for (String accountTypeStr : groupedArtifacts.keySet()) {
                    /*
                     * If the report is a ReportHTML, the data type name
                     * eventualy makes it to useDataTypeIcon which expects but
                     * does not require a artifact name, so we make a synthetic
                     * compund name by appending a ":" and the account type.
                     */
                    String accountDisplayname = accountTypeStr;
                    if (accountTypeStr != null) {
                        try {
                            Account.Type acctType = Case.getOpenCase().getSleuthkitCase().getCommunicationsManager().getAccountType(accountTypeStr);
                            if (acctType != null) {
                                accountDisplayname = acctType.getDisplayName();
                            }
                        } catch (TskCoreException | NoCurrentCaseException ex) {
                            logger.log(Level.SEVERE, "Unable to get display name for account type " + accountTypeStr, ex);
                        }
                    }

                    final String compundDataTypeName = BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getDisplayName() + ": " + accountDisplayname;
                    writeTableForDataType(new ArrayList<>(groupedArtifacts.get(accountTypeStr)), type, compundDataTypeName, comment);
                }
            } else {
                //all other artifact types are sent to writeTableForDataType directly
                writeTableForDataType(artifactList, type, type.getDisplayName(), comment);
            }
        }
    }

    /**
     *
     * Write the given list of artifacts to the table for the given type.
     *
     * @param artifactList The List of artifacts to include in the table.
     * @param type         The Type of artifacts included in the table. All the
     *                     artifacts in artifactList should be of this type.
     * @param tableName    The name of the table.
     * @param comment      A comment to put in the header.
     */
    private void writeTableForDataType(List<ArtifactData> artifactList, BlackboardArtifact.Type type, String tableName, String comment) {
        /*
         * Make a sorted set of all of the attribute types that are on any of
         * the given artifacts.
         */
        Set<BlackboardAttribute.Type> attrTypeSet = new TreeSet<>(Comparator.comparing(BlackboardAttribute.Type::getDisplayName));
        for (ArtifactData data : artifactList) {
            List<BlackboardAttribute> attributes = data.getAttributes();
            for (BlackboardAttribute attribute : attributes) {
                attrTypeSet.add(attribute.getAttributeType());
            }
        }
        /*
         * Get the columns appropriate for the artifact type. This is used to
         * get the data that will be in the cells below based on type, and
         * display the column headers.
         */
        List<Column> columns = getArtifactTableColumns(type.getTypeID(), attrTypeSet);
        if (columns.isEmpty()) {
            return;
        }
        columnHeaderMap.put(type.getTypeID(), columns);

        /*
         * The artifact list is sorted now, as getting the row data is dependent
         * on having the columns, which is necessary for sorting.
         */
        Collections.sort(artifactList);

        tableReport.startDataType(tableName, comment);
        tableReport.startTable(Lists.transform(columns, Column::getColumnHeader));

        for (ArtifactData artifactData : artifactList) {
            // Get the row data for this artifact, and has the
            // module add it.
            List<String> rowData = artifactData.getRow();
            if (rowData.isEmpty()) {
                return;
            }

            tableReport.addRow(rowData);
        }
        // Finish up this data type
        progressPanel.increment();
        tableReport.endTable();
        tableReport.endDataType();
    }

    /**
     * Make table for tagged files
     */
    @SuppressWarnings("deprecation")
    private void makeContentTagsTables() {

        // Get the content tags.
        List<ContentTag> tags;
        try {
            tags = Case.getOpenCase().getServices().getTagsManager().getAllContentTags();
        } catch (TskCoreException | NoCurrentCaseException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetContentTags"));
            logger.log(Level.SEVERE, "failed to get content tags", ex); //NON-NLS
            return;
        }

        // Tell the modules reporting on content tags is beginning.
        // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
        // @@@ Alos Using the obsolete ARTIFACT_TYPE.TSK_TAG_FILE is also an expedient hack.
        progressPanel.updateStatusLabel(
                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName()));
        ArrayList<String> columnHeaders = new ArrayList<>(Arrays.asList(
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.tag"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.file"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.comment"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeModified"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeChanged"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeAccessed"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeCreated"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.size"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.hash")));

        StringBuilder comment = new StringBuilder();
        if (!tagNamesFilter.isEmpty()) {
            comment.append(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.makeContTagTab.taggedFiles.msg"));
            comment.append(makeCommaSeparatedList(tagNamesFilter));
        }
        if (tableReport instanceof ReportHTML) {
            ReportHTML htmlReportModule = (ReportHTML) tableReport;
            htmlReportModule.startDataType(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());
            htmlReportModule.startContentTagsTable(columnHeaders);
        } else {
            tableReport.startDataType(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());
            tableReport.startTable(columnHeaders);
        }

        // Give the modules the rows for the content tags. 
        for (ContentTag tag : tags) {
            // skip tags that we are not reporting on 
            String notableString = tag.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
            if (passesTagNamesFilter(tag.getName().getDisplayName() + notableString) == false) {
                continue;
            }

            String fileName;
            try {
                fileName = tag.getContent().getUniquePath();
            } catch (TskCoreException ex) {
                fileName = tag.getContent().getName();
            }

            ArrayList<String> rowData = new ArrayList<>(Arrays.asList(tag.getName().getDisplayName() + notableString, fileName, tag.getComment()));
            Content content = tag.getContent();
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;

                // Add metadata about the file to HTML output
                rowData.add(file.getMtimeAsDate());
                rowData.add(file.getCtimeAsDate());
                rowData.add(file.getAtimeAsDate());
                rowData.add(file.getCrtimeAsDate());
                rowData.add(Long.toString(file.getSize()));
                rowData.add(file.getMd5Hash());
            }
            // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
            if (tableReport instanceof ReportHTML) {
                ReportHTML htmlReportModule = (ReportHTML) tableReport;
                htmlReportModule.addRowWithTaggedContentHyperlink(rowData, tag);
            } else {
                tableReport.addRow(rowData);
            }

            // see if it is for an image so that we later report on it
            checkIfTagHasImage(tag);
        }

        // The the modules content tags reporting is ended.
        progressPanel.increment();
        tableReport.endTable();
        tableReport.endDataType();
    }

    /**
     * Generate the tables for the tagged artifacts
     */
    @SuppressWarnings("deprecation")
    private void makeBlackboardArtifactTagsTables() {

        List<BlackboardArtifactTag> tags;
        try {
            tags = Case.getOpenCase().getServices().getTagsManager().getAllBlackboardArtifactTags();
        } catch (TskCoreException | NoCurrentCaseException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBArtifactTags"));
            logger.log(Level.SEVERE, "failed to get blackboard artifact tags", ex); //NON-NLS
            return;
        }

        // Tell the modules reporting on blackboard artifact tags data type is beginning.
        // @@@ Using the obsolete ARTIFACT_TYPE.TSK_TAG_ARTIFACT is an expedient hack.
        progressPanel.updateStatusLabel(
                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName()));
        StringBuilder comment = new StringBuilder();
        if (!tagNamesFilter.isEmpty()) {
            comment.append(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.makeBbArtTagTab.taggedRes.msg"));
            comment.append(makeCommaSeparatedList(tagNamesFilter));
        }
        tableReport.startDataType(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName(), comment.toString());
        tableReport.startTable(new ArrayList<>(Arrays.asList(
                NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.resultType"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.tag"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.comment"),
                NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.srcFile"))));

        // Give the modules the rows for the content tags. 
        for (BlackboardArtifactTag tag : tags) {
            String notableString = tag.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
            if (passesTagNamesFilter(tag.getName().getDisplayName() + notableString) == false) {
                continue;
            }

            List<String> row;
            row = new ArrayList<>(Arrays.asList(tag.getArtifact().getArtifactTypeName(), tag.getName().getDisplayName() + notableString, tag.getComment(), tag.getContent().getName()));
            tableReport.addRow(row);

            // check if the tag is an image that we should later make a thumbnail for
            checkIfTagHasImage(tag);
        }

        // The the modules blackboard artifact tags reporting is ended.
        progressPanel.increment();
        tableReport.endTable();
        tableReport.endDataType();
    }

    /**
     * Test if the user requested that this tag be reported on
     *
     * @param tagName
     *
     * @return true if it should be reported on
     */
    private boolean passesTagNamesFilter(String tagName) {
        return tagNamesFilter.isEmpty() || tagNamesFilter.contains(tagName);
    }

    /**
     * Make a report for the files that were previously found to be images.
     */
    private void makeThumbnailTable() {
        progressPanel.updateStatusLabel(
                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.createdThumb.text"));

        if (tableReport instanceof ReportHTML) {
            ReportHTML htmlModule = (ReportHTML) tableReport;
            htmlModule.startDataType(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.thumbnailTable.name"),
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.thumbnailTable.desc"));
            List<String> emptyHeaders = new ArrayList<>();
            for (int i = 0; i < ReportHTML.THUMBNAIL_COLUMNS; i++) {
                emptyHeaders.add("");
            }
            htmlModule.startTable(emptyHeaders);

            htmlModule.addThumbnailRows(images);

            htmlModule.endTable();
            htmlModule.endDataType();
        }

    }

    /**
     * Analyze artifact associated with tag and add to internal list if it is
     * associated with an image.
     *
     * @param artifactTag
     */
    private void checkIfTagHasImage(BlackboardArtifactTag artifactTag) {
        AbstractFile file;
        try {
            file = Case.getOpenCase().getSleuthkitCase().getAbstractFileById(artifactTag.getArtifact().getObjectID());
        } catch (TskCoreException | NoCurrentCaseException ex) {
            errorList.add(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.errGetContentFromBBArtifact"));
            logger.log(Level.WARNING, "Error while getting content from a blackboard artifact to report on.", ex); //NON-NLS
            return;
        }

        if (file != null) {
            checkIfFileIsImage(file);
        }
    }

    /**
     * Analyze file that tag is associated with and determine if it is an image
     * and should have a thumbnail reported for it. Images are added to internal
     * list.
     *
     * @param contentTag
     */
    private void checkIfTagHasImage(ContentTag contentTag) {
        Content c = contentTag.getContent();
        if (c instanceof AbstractFile == false) {
            return;
        }
        checkIfFileIsImage((AbstractFile) c);
    }

    /**
     * If file is an image file, add it to the internal 'images' list.
     *
     * @param file
     */
    private void checkIfFileIsImage(AbstractFile file) {

        if (file.isDir()
                || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) {
            return;
        }

        if (ImageUtils.thumbnailSupported(file)) {
            images.add(file);
        }
    }

    /**
     * Converts a collection of strings into a single string of comma-separated
     * items
     *
     * @param items A collection of strings
     *
     * @return A string of comma-separated items
     */
    private String makeCommaSeparatedList(Collection<String> items) {
        String list = "";
        for (Iterator<String> iterator = items.iterator(); iterator.hasNext();) {
            list += iterator.next() + (iterator.hasNext() ? ", " : "");
        }
        return list;
    }

    /**
     * Write the keyword hits to the provided TableReportModules.
     *
     * @param tableModule module to report on
     */
    @SuppressWarnings("deprecation")
    @NbBundle.Messages ({"ReportGenerator.errList.noOpenCase=No open case available."})
    private void writeKeywordHits(TableReportModule tableModule, String comment, HashSet<String> tagNamesFilter) {

        // Query for keyword lists-only so that we can tell modules what lists
        // will exist for their index.
        // @@@ There is a bug in here.  We should use the tags in the below code
        // so that we only report the lists that we will later provide with real
        // hits.  If no keyord hits are tagged, then we make the page for nothing.
        String orderByClause;
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            errorList.add(Bundle.ReportGenerator_errList_noOpenCase());
            logger.log(Level.SEVERE, "Exception while getting open case: ", ex); //NON-NLS
            return;
        }
        if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY list ASC"; //NON-NLS
        }
        String keywordListQuery
                = "SELECT att.value_text AS list "
                + //NON-NLS
                "FROM blackboard_attributes AS att, blackboard_artifacts AS art "
                + //NON-NLS
                "WHERE att.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " "
                + //NON-NLS
                "AND art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + " "
                + //NON-NLS
                "AND att.artifact_id = art.artifact_id "
                + //NON-NLS
                "GROUP BY list " + orderByClause; //NON-NLS

        try (SleuthkitCase.CaseDbQuery dbQuery = openCase.getSleuthkitCase().executeQuery(keywordListQuery)) {
            ResultSet listsRs = dbQuery.getResultSet();
            List<String> lists = new ArrayList<>();
            while (listsRs.next()) {
                String list = listsRs.getString("list"); //NON-NLS
                if (list.isEmpty()) {
                    list = NbBundle.getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs");
                }
                lists.add(list);
            }

            // Make keyword data type and give them set index
            tableModule.startDataType(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), comment);
            tableModule.addSetIndex(lists);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                            BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName()));
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryKWLists"));
            logger.log(Level.SEVERE, "Failed to query keyword lists: ", ex); //NON-NLS
            return;
        }

        if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att3.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(att1.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.parent_path, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.name, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(att2.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY list ASC, keyword ASC, parent_path ASC, name ASC, preview ASC"; //NON-NLS
        }
        // Query for keywords, grouped by list
        String keywordsQuery
                = "SELECT art.artifact_id, art.obj_id, att1.value_text AS keyword, att2.value_text AS preview, att3.value_text AS list, f.name AS name, f.parent_path AS parent_path "
                + //NON-NLS
                "FROM blackboard_artifacts AS art, blackboard_attributes AS att1, blackboard_attributes AS att2, blackboard_attributes AS att3, tsk_files AS f "
                + //NON-NLS
                "WHERE (att1.artifact_id = art.artifact_id) "
                + //NON-NLS
                "AND (att2.artifact_id = art.artifact_id) "
                + //NON-NLS
                "AND (att3.artifact_id = art.artifact_id) "
                + //NON-NLS
                "AND (f.obj_id = art.obj_id) "
                + //NON-NLS
                "AND (att1.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID() + ") "
                + //NON-NLS
                "AND (att2.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID() + ") "
                + //NON-NLS
                "AND (att3.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") "
                + //NON-NLS
                "AND (art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + ") "
                + //NON-NLS
                orderByClause; //NON-NLS

        try (SleuthkitCase.CaseDbQuery dbQuery = openCase.getSleuthkitCase().executeQuery(keywordsQuery)) {
            ResultSet resultSet = dbQuery.getResultSet();

            String currentKeyword = "";
            String currentList = "";
            while (resultSet.next()) {
                // Check to see if all the TableReportModules have been canceled
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    break;
                }

                // Get any tags that associated with this artifact and apply the tag filter.
                HashSet<String> uniqueTagNames = getUniqueTagNames(resultSet.getLong("artifact_id")); //NON-NLS
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                String tagsList = makeCommaSeparatedList(uniqueTagNames);

                Long objId = resultSet.getLong("obj_id"); //NON-NLS
                String keyword = resultSet.getString("keyword"); //NON-NLS
                String preview = resultSet.getString("preview"); //NON-NLS
                String list = resultSet.getString("list"); //NON-NLS
                String uniquePath = "";

                try {
                    AbstractFile f = openCase.getSleuthkitCase().getAbstractFileById(objId);
                    if (f != null) {
                        uniquePath = openCase.getSleuthkitCase().getAbstractFileById(objId).getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileByID"));
                    logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex); //NON-NLS
                }

                // If the lists aren't the same, we've started a new list
                if ((!list.equals(currentList) && !list.isEmpty()) || (list.isEmpty() && !currentList.equals(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs")))) {
                    if (!currentList.isEmpty()) {
                        tableModule.endTable();
                        tableModule.endSet();
                    }
                    currentList = list.isEmpty() ? NbBundle
                            .getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs") : list;
                    currentKeyword = ""; // reset the current keyword because it's a new list
                    tableModule.startSet(currentList);
                    progressPanel.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingList",
                                    BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), currentList));
                }
                if (!keyword.equals(currentKeyword)) {
                    if (!currentKeyword.equals("")) {
                        tableModule.endTable();
                    }
                    currentKeyword = keyword;
                    tableModule.addSetElement(currentKeyword);
                    List<String> columnHeaderNames = new ArrayList<>();
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.preview"));
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile"));
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags"));
                    tableModule.startTable(columnHeaderNames);
                }

                tableModule.addRow(Arrays.asList(new String[]{preview, uniquePath, tagsList}));
            }

            // Finish the current data type
            progressPanel.increment();
            tableModule.endDataType();
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryKWs"));
            logger.log(Level.SEVERE, "Failed to query keywords: ", ex); //NON-NLS
        }
    }

    /**
     * Write the hash set hits to the provided TableReportModules.
     *
     * @param tableModule module to report on
     */
    @SuppressWarnings("deprecation")
    private void writeHashsetHits(TableReportModule tableModule, String comment, HashSet<String> tagNamesFilter) {
        String orderByClause;
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            errorList.add(Bundle.ReportGenerator_errList_noOpenCase());
            logger.log(Level.SEVERE, "Exception while getting open case: ", ex); //NON-NLS
            return;
        }
        if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY att.value_text ASC"; //NON-NLS
        }
        String hashsetsQuery
                = "SELECT att.value_text AS list "
                + //NON-NLS
                "FROM blackboard_attributes AS att, blackboard_artifacts AS art "
                + //NON-NLS
                "WHERE att.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " "
                + //NON-NLS
                "AND art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + " "
                + //NON-NLS
                "AND att.artifact_id = art.artifact_id "
                + //NON-NLS
                "GROUP BY list " + orderByClause; //NON-NLS

        try (SleuthkitCase.CaseDbQuery dbQuery = openCase.getSleuthkitCase().executeQuery(hashsetsQuery)) {
            // Query for hashsets
            ResultSet listsRs = dbQuery.getResultSet();
            List<String> lists = new ArrayList<>();
            while (listsRs.next()) {
                lists.add(listsRs.getString("list")); //NON-NLS
            }

            tableModule.startDataType(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), comment);
            tableModule.addSetIndex(lists);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                            BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName()));
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryHashsetLists"));
            logger.log(Level.SEVERE, "Failed to query hashset lists: ", ex); //NON-NLS
            return;
        }

        if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.parent_path, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.name, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "size ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY att.value_text ASC, f.parent_path ASC, f.name ASC, size ASC"; //NON-NLS
        }
        String hashsetHitsQuery
                = "SELECT art.artifact_id, art.obj_id, att.value_text AS setname, f.name AS name, f.size AS size, f.parent_path AS parent_path "
                + //NON-NLS
                "FROM blackboard_artifacts AS art, blackboard_attributes AS att, tsk_files AS f "
                + //NON-NLS
                "WHERE (att.artifact_id = art.artifact_id) "
                + //NON-NLS
                "AND (f.obj_id = art.obj_id) "
                + //NON-NLS
                "AND (att.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") "
                + //NON-NLS
                "AND (art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + ") "
                + //NON-NLS
                orderByClause; //NON-NLS

        try (SleuthkitCase.CaseDbQuery dbQuery = openCase.getSleuthkitCase().executeQuery(hashsetHitsQuery)) {
            // Query for hashset hits
            ResultSet resultSet = dbQuery.getResultSet();
            String currentSet = "";
            while (resultSet.next()) {
                // Check to see if all the TableReportModules have been canceled
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    break;
                }

                // Get any tags that associated with this artifact and apply the tag filter.
                HashSet<String> uniqueTagNames = getUniqueTagNames(resultSet.getLong("artifact_id")); //NON-NLS
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                String tagsList = makeCommaSeparatedList(uniqueTagNames);

                Long objId = resultSet.getLong("obj_id"); //NON-NLS
                String set = resultSet.getString("setname"); //NON-NLS
                String size = resultSet.getString("size"); //NON-NLS
                String uniquePath = "";

                try {
                    AbstractFile f = openCase.getSleuthkitCase().getAbstractFileById(objId);
                    if (f != null) {
                        uniquePath = openCase.getSleuthkitCase().getAbstractFileById(objId).getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileFromID"));
                    logger.log(Level.WARNING, "Failed to get Abstract File from ID.", ex); //NON-NLS
                    return;
                }

                // If the sets aren't the same, we've started a new set
                if (!set.equals(currentSet)) {
                    if (!currentSet.isEmpty()) {
                        tableModule.endTable();
                        tableModule.endSet();
                    }
                    currentSet = set;
                    tableModule.startSet(currentSet);
                    List<String> columnHeaderNames = new ArrayList<>();
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file"));
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.size"));
                    columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags"));
                    tableModule.startTable(columnHeaderNames);
                    progressPanel.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingList",
                                    BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), currentSet));
                }

                // Add a row for this hit to every module
                tableModule.addRow(Arrays.asList(new String[]{uniquePath, size, tagsList}));
            }

            // Finish the current data type
            progressPanel.increment();
            tableModule.endDataType();
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryHashsetHits"));
            logger.log(Level.SEVERE, "Failed to query hashsets hits: ", ex); //NON-NLS
        }
    }

    /**
     * @return the errorList
     */
    List<String> getErrorList() {
        return errorList;
    }

    /**
     * Container class that holds data about an Artifact to eliminate duplicate
     * calls to the Sleuthkit database.
     */
    private class ArtifactData implements Comparable<ArtifactData> {

        private BlackboardArtifact artifact;
        private List<BlackboardAttribute> attributes;
        private HashSet<String> tags;
        private List<String> rowData = null;
        private Content content;

        ArtifactData(BlackboardArtifact artifact, List<BlackboardAttribute> attrs, HashSet<String> tags) {
            this.artifact = artifact;
            this.attributes = attrs;
            this.tags = tags;
            try {
                this.content = Case.getOpenCase().getSleuthkitCase().getContentById(artifact.getObjectID());
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Could not get content from database", ex);
            }
        }

        public BlackboardArtifact getArtifact() {
            return artifact;
        }

        public List<BlackboardAttribute> getAttributes() {
            return attributes;
        }

        public HashSet<String> getTags() {
            return tags;
        }

        public long getArtifactID() {
            return artifact.getArtifactID();
        }

        public long getObjectID() {
            return artifact.getObjectID();
        }

        /**
         * @return the content
         */
        public Content getContent() {
            return content;
        }

        /**
         * Compares ArtifactData objects by the first attribute they have in
         * common in their List<BlackboardAttribute>. Should only be used on two
         * artifacts of the same type
         *
         * If all attributes are the same, they are assumed duplicates and are
         * compared by their artifact id. Should only be used with attributes of
         * the same type.
         */
        @Override
        public int compareTo(ArtifactData otherArtifactData) {
            List<String> thisRow = getRow();
            List<String> otherRow = otherArtifactData.getRow();
            for (int i = 0; i < thisRow.size(); i++) {
                int compare = thisRow.get(i).compareTo(otherRow.get(i));
                if (compare != 0) {
                    return compare;
                }
            }
            return ((Long) this.getArtifactID()).compareTo(otherArtifactData.getArtifactID());
        }

        /**
         * Get the values for each row in the table report.
         *
         * the value types of custom artifacts
         *
         * @return A list of string representing the data for this artifact.
         */
        public List<String> getRow() {
            if (rowData == null) {
                try {
                    rowData = getOrderedRowDataAsStrings();
                    // If else is done so that row data is not set before 
                    // columns are added to the hash map.
                    if (rowData.size() > 0) {
                        // replace null values if attribute was not defined
                        for (int i = 0; i < rowData.size(); i++) {
                            if (rowData.get(i) == null) {
                                rowData.set(i, "");
                            }
                        }
                    } else {
                        rowData = null;
                        return new ArrayList<>();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.coreExceptionWhileGenRptRow"));
                    logger.log(Level.WARNING, "Core exception while generating row data for artifact report.", ex); //NON-NLS
                    rowData = Collections.<String>emptyList();
                }
            }
            return rowData;
        }

        /**
         * Get a list of Strings with all the row values for the Artifact in the
         * correct order to be written to the report.
         *
         * @return List<String> row values. Values could be null if attribute is
         *         not defined in artifact
         *
         * @throws TskCoreException
         */
        private List<String> getOrderedRowDataAsStrings() throws TskCoreException {

            List<String> orderedRowData = new ArrayList<>();
            if (BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID() == getArtifact().getArtifactTypeID()) {
                if (content != null && content instanceof AbstractFile) {
                    AbstractFile file = (AbstractFile) content;
                    orderedRowData.add(file.getName());
                    orderedRowData.add(file.getNameExtension());
                    String mimeType = file.getMIMEType();
                    if (mimeType == null) {
                        orderedRowData.add("");
                    } else {
                        orderedRowData.add(mimeType);
                    }
                    orderedRowData.add(file.getUniquePath());
                } else {
                    // Make empty rows to make sure the formatting is correct
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                }
                orderedRowData.add(makeCommaSeparatedList(getTags()));

            } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == getArtifact().getArtifactTypeID()) {
                String[] attributeDataArray = new String[3];
                // Array is used so that order of the attributes is maintained.
                for (BlackboardAttribute attr : attributes) {
                    if (attr.getAttributeType().equals(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME))) {
                        attributeDataArray[0] = attr.getDisplayString();
                    } else if (attr.getAttributeType().equals(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY))) {
                        attributeDataArray[1] = attr.getDisplayString();
                    }
                }

                attributeDataArray[2] = content.getUniquePath();
                orderedRowData.addAll(Arrays.asList(attributeDataArray));

                HashSet<String> allTags = getTags();
                try {
                    List<ContentTag> contentTags = Case.getOpenCase().getServices().getTagsManager().getContentTagsByContent(content);
                    for (ContentTag ct : contentTags) {
                        String notableString = ct.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                        allTags.add(ct.getName().getDisplayName() + notableString);
                    }
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetContentTags"));
                    logger.log(Level.SEVERE, "Failed to get content tags", ex); //NON-NLS
                }
                orderedRowData.add(makeCommaSeparatedList(allTags));

            } else if (columnHeaderMap.containsKey(this.artifact.getArtifactTypeID())) {

                for (Column currColumn : columnHeaderMap.get(this.artifact.getArtifactTypeID())) {
                    String cellData = currColumn.getCellData(this);
                    orderedRowData.add(cellData);
                }
            }

            return orderedRowData;
        }

    }

    /**
     * Get a List of the artifacts and data of the given type that pass the
     * given Tag Filter.
     *
     * @param type           The artifact type to get
     * @param tagNamesFilter The tag names that should be included.
     *
     * @return a list of the filtered tags.
     */
    private List<ArtifactData> getFilteredArtifacts(BlackboardArtifact.Type type, HashSet<String> tagNamesFilter) {
        List<ArtifactData> artifacts = new ArrayList<>();
        try {
            for (BlackboardArtifact artifact : Case.getOpenCase().getSleuthkitCase().getBlackboardArtifacts(type.getTypeID())) {
                List<BlackboardArtifactTag> tags = Case.getOpenCase().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact);
                HashSet<String> uniqueTagNames = new HashSet<>();
                for (BlackboardArtifactTag tag : tags) {
                    String notableString = tag.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                    uniqueTagNames.add(tag.getName().getDisplayName() + notableString);
                }
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                try {
                    artifacts.add(new ArtifactData(artifact, Case.getOpenCase().getSleuthkitCase().getBlackboardAttributes(artifact), uniqueTagNames));
                } catch (TskCoreException ex) {
                    errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBAttribs"));
                    logger.log(Level.SEVERE, "Failed to get Blackboard Attributes when generating report.", ex); //NON-NLS
                }
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBArtifacts"));
            logger.log(Level.SEVERE, "Failed to get Blackboard Artifacts when generating report.", ex); //NON-NLS
        }
        return artifacts;
    }

    private Boolean failsTagFilter(HashSet<String> tagNames, HashSet<String> tagsNamesFilter) {
        if (null == tagsNamesFilter || tagsNamesFilter.isEmpty()) {
            return false;
        }

        HashSet<String> filteredTagNames = new HashSet<>(tagNames);
        filteredTagNames.retainAll(tagsNamesFilter);
        return filteredTagNames.isEmpty();
    }

    /**
     * For a given artifact type ID, return the list of the columns that we are
     * reporting on.
     *
     * @param artifactTypeId   artifact type ID
     * @param attributeTypeSet The set of attributeTypeSet available for this
     *                         artifact type
     *
     * @return List<String> row titles
     */
    private List<Column> getArtifactTableColumns(int artifactTypeId, Set<BlackboardAttribute.Type> attributeTypeSet) {
        ArrayList<Column> columns = new ArrayList<>();

        // Long switch statement to retain ordering of attribute types that are 
        // attached to pre-defined artifact types.
        if (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.title"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateCreated"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.value"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.referrer"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.title"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.urlDomainDecoded"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL_DECODED)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dest"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.sourceUrl"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.path"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.progName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.instDateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeId) {
            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.preview")));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() == artifactTypeId) {
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.size")));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devMake"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devModel"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceId"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.text"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.domain"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.progName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTaken"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devManufacturer"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devModel"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumber"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumHome"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumOffice"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumMobile"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.email"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.msgType"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.direction"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.readStatus"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromPhoneNum"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromEmail"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toPhoneNum"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toEmail"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.subject"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.text"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromPhoneNum"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toPhoneNum"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.direction"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.calendarEntryType"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CALENDAR_ENTRY_TYPE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.description"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.startDateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.endDateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.location"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.shortCut"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SHORTCUT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumber"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceAddress"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.category"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userId"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.password"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.appName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.appPath"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.description"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.replytoAddress"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_REPLYTO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.mailServer"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SERVER_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID() == artifactTypeId  || 
                BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID() == artifactTypeId) {
            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.extension.text")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.mimeType.text")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.path")));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.processorArchitecture.text"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.osName.text"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.osInstallDate.text"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailTo"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailFrom"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSubject"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskDateTimeSent"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskDateTimeRcvd"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_RCVD)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskPath"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailCc"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CC)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailBcc"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_BCC)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskMsgId"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MSG_ID)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSetName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskInterestingFilesCategory"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskPath"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskGpsRouteCategory"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitudeEnd"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitudeEnd"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitudeStart"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitudeStart"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.location"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSetName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.associatedArtifact"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.associatedArtifact"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.count"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNT)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userName"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userId"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID)));

        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_REMOTE_DRIVE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.localPath"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCAL_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.remotePath"),
                    new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REMOTE_PATH)));
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
            columns.add(new StatusColumn());
            attributeTypeSet.remove(new Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE));
            attributeTypeSet.remove(new Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
            attributeTypeSet.remove(new Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
            attributeTypeSet.remove(new Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID));
        } else {
            // This is the case that it is a custom type. The reason an else is 
            // necessary is to make sure that the source file column is added
            for (BlackboardAttribute.Type type : attributeTypeSet) {
                columns.add(new AttributeColumn(type.getDisplayName(), type));
            }
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile")));
            columns.add(new TaggedResultsColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags")));

            // Short circuits to guarantee that the attribute types aren't added
            // twice.
            return columns;
        }
        // If it is an attribute column, it removes the attribute type of that 
        // column from the set, so types are not reported more than once.
        for (Column column : columns) {
            attributeTypeSet = column.removeTypeFromSet(attributeTypeSet);
        }
        // Now uses the remaining types in the set to construct columns
        for (BlackboardAttribute.Type type : attributeTypeSet) {
            columns.add(new AttributeColumn(type.getDisplayName(), type));
        }
        // Source file column is added here for ordering purposes.
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED.getTypeID()
                || artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID()) {
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile")));
        }
        columns.add(new TaggedResultsColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags")));

        return columns;
    }

    /**
     * Given a tsk_file's obj_id, return the unique path of that file.
     *
     * @param objId tsk_file obj_id
     *
     * @return String unique path
     */
    private String getFileUniquePath(Content content) {
        try {
            if (content != null) {
                return content.getUniquePath();
            } else {
                return "";
            }
        } catch (TskCoreException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileByID"));
            logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex); //NON-NLS
        }
        return "";

    }

    /**
     * Get any tags associated with an artifact
     *
     * @param artifactId
     *
     * @return hash set of tag display names
     *
     * @throws SQLException
     */
    @SuppressWarnings("deprecation")
    private HashSet<String> getUniqueTagNames(long artifactId) throws TskCoreException {
        HashSet<String> uniqueTagNames = new HashSet<>();

        String query = "SELECT display_name, artifact_id FROM tag_names AS tn, blackboard_artifact_tags AS bat "
                + //NON-NLS 
                "WHERE tn.tag_name_id = bat.tag_name_id AND bat.artifact_id = " + artifactId; //NON-NLS

        try (SleuthkitCase.CaseDbQuery dbQuery = Case.getOpenCase().getSleuthkitCase().executeQuery(query)) {
            ResultSet tagNameRows = dbQuery.getResultSet();
            while (tagNameRows.next()) {
                uniqueTagNames.add(tagNameRows.getString("display_name")); //NON-NLS
            }
        } catch (TskCoreException | SQLException | NoCurrentCaseException ex) {
            throw new TskCoreException("Error getting tag names for artifact: ", ex);
        }

        return uniqueTagNames;

    }

    private interface Column {

        String getColumnHeader();

        String getCellData(ArtifactData artData);

        Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types);
    }

    private class StatusColumn implements Column {

        @NbBundle.Messages("TableReportGenerator.StatusColumn.Header=Review Status")
        @Override
        public String getColumnHeader() {
            return Bundle.TableReportGenerator_StatusColumn_Header();
        }

        @Override
        public String getCellData(ArtifactData artData) {
            return artData.getArtifact().getReviewStatus().getDisplayName();
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }

    }

    private class AttributeColumn implements Column {

        private final String columnHeader;
        private final BlackboardAttribute.Type attributeType;

        /**
         * Constructs an ArtifactCell
         *
         * @param columnHeader  The header text of this column
         * @param attributeType The attribute type associated with this column
         */
        AttributeColumn(String columnHeader, BlackboardAttribute.Type attributeType) {
            this.columnHeader = Objects.requireNonNull(columnHeader);
            this.attributeType = attributeType;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            List<BlackboardAttribute> attributes = artData.getAttributes();
            for (BlackboardAttribute attribute : attributes) {
                if (attribute.getAttributeType().equals(this.attributeType)) {
                    if (attribute.getAttributeType().getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                        return attribute.getDisplayString();
                    } else {
                        return ContentUtils.getStringTime(attribute.getValueLong(), artData.getContent());
                    }
                }
            }
            return "";
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types) {
            types.remove(this.attributeType);
            return types;
        }
    }

    private class SourceFileColumn implements Column {

        private final String columnHeader;

        SourceFileColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            return getFileUniquePath(artData.getContent());
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }

    private class TaggedResultsColumn implements Column {

        private final String columnHeader;

        TaggedResultsColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            return makeCommaSeparatedList(artData.getTags());
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }

    private class HeaderOnlyColumn implements Column {

        private final String columnHeader;

        HeaderOnlyColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            throw new UnsupportedOperationException("Cannot get cell data of unspecified column");
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }
}
