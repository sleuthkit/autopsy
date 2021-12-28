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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.MediaTypeUtils;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.LayoutFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.SlackFileRowDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * A node for representing an AbstractFile.
 */
public class FileNode extends BaseNode<SearchResultsDTO, FileRowDTO> implements SCOSupporter {

    private static final Logger logger = Logger.getLogger(FileNode.class.getName());

    private final boolean directoryBrowseMode;
    private final FileRowDTO fileData;
    private final List<ColumnKey> columns;

    public FileNode(SearchResultsDTO results, FileRowDTO file) {
        this(results, file, true);
    }

    public FileNode(SearchResultsDTO results, FileRowDTO file, boolean directoryBrowseMode) {
        // GVDTODO: at some point, this leaf will need to allow for children
        super(Children.LEAF, ContentNodeUtil.getLookup(file.getAbstractFile()), results, file);
        setIcon(file);
        setName(ContentNodeUtil.getContentName(file.getId()));
        setDisplayName(ContentNodeUtil.getContentDisplayName(file.getFileName()));
        setShortDescription(ContentNodeUtil.getContentDisplayName(file.getFileName()));
        this.directoryBrowseMode = directoryBrowseMode;
        this.fileData = file;
        this.columns = results.getColumns();
    }

    /*
     * Sets the icon for the node, based on properties of the AbstractFile.
     */
    void setIcon(FileRowDTO fileData) {
        if (fileData.getAbstractFile().isDir()) {
            // This is most likely a derived file directory
            if (fileData.getAllocated()) {
                this.setIconBaseWithExtension(NodeIconUtil.FOLDER.getPath());
            } else {
                this.setIconBaseWithExtension(NodeIconUtil.DELETED_FOLDER.getPath());
            }
        } else {
            if (!fileData.getAllocated()) {
                if (TSK_DB_FILES_TYPE_ENUM.CARVED.equals(fileData.getFileType())) {
                    this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
                } else {
                    this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
                }
            } else {
                this.setIconBaseWithExtension(MediaTypeUtils.getIconForFileType(fileData.getExtensionMediaType()));
            }
        }
    }

    @Override
    public boolean supportsViewInTimeline() {
        return true;
    }

    @Override
    public Optional<AbstractFile> getFileForViewInTimelineAction() {
        return Optional.of(fileData.getAbstractFile());
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public Optional<Node> getExternalViewerActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }

    @Override
    public boolean supportsContentTagAction() {
        return true;
    }

    @Override
    public Optional<AbstractFile> getFileForDirectoryBrowseMode() {
        if (directoryBrowseMode) {
            return Optional.of(fileData.getAbstractFile());
        }

        return Optional.empty();
    }

    @Override
    public Optional<AbstractFile> getExtractArchiveWithPasswordActionFile() {
        // TODO: See JIRA-8099
        AbstractFile file = this.fileData.getAbstractFile();
        boolean isArchive = FileTypeExtensions.getArchiveExtensions().contains("." + file.getNameExtension().toLowerCase());
        boolean encryptionDetected = false;
        try {
            encryptionDetected = isArchive && file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0;
        } catch (TskCoreException ex) {
            // TODO
        }

        return encryptionDetected ? Optional.of(fileData.getAbstractFile()) : Optional.empty();
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }

    @Override
    public Optional<Content> getContent() {
        return Optional.ofNullable(fileData.getAbstractFile());
    }

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }

    @Override
    public Optional<List<Tag>> getAllTagsFromDatabase() {
        try {
            List<ContentTag> contentTags = ContentNodeUtil.getContentTagsFromDatabase(fileData.getAbstractFile());
            if (!contentTags.isEmpty()) {
                List<Tag> tags = new ArrayList<>();
                tags.addAll(contentTags);
                return Optional.of(tags);
            }

        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get content tags from database for AbstractFile id=" + fileData.getAbstractFile().getId(), ex);
        }
        return Optional.empty();
    }
    
    @NbBundle.Messages({
        "FileNode_createSheet_count.displayName=O",
        "FileNode_createSheet_count_hashLookupNotRun_description=Hash lookup had not been run on this file when the column was populated",
        "# {0} - occurrenceCount",
        "FileNode_createSheet_count.description=There were {0} datasource(s) found with occurrences of the MD5 correlation value"})
    @Override
    public Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attributeInstance, String defaultDescription) {
        Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
        String description = defaultDescription;
        try {
            //don't perform the query if there is no correlation value
            if (attributeInstance != null && StringUtils.isNotBlank(attributeInstance.getCorrelationValue())) {
                count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attributeInstance);
                description = Bundle.FileNode_createSheet_count_description(count);
            } else if (attributeInstance != null) {
                description = Bundle.FileNode_createSheet_count_hashLookupNotRun_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }
        return Pair.of(count, description);
    }

    @NbBundle.Messages({
        "FileNode.createSheet.comment.displayName=C"})
    @Override
    public DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        DataResultViewerTable.HasCommentStatus status = !tags.isEmpty() ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        /*
         * Is there a comment in the CR for anything that matches the value and
         * type of the specified attributes.
         */
        try {
            if (CentralRepoDbUtil.commentExistsOnAttributes(attributes)) {
                if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                    status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
                } else {
                    status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Attempted to Query CR for presence of comments in a file node and was unable to perform query, comment column will only reflect caseDB", ex);
        }
        return status;
    }
    
    @Override
    public Action getPreferredAction() {
        return DirectoryTreeTopComponent.getOpenChildAction(getName());
    }

    /**
     * A node for representing a LayoutFile.
     */
    public static class LayoutFileNode extends FileNode {

        private final LayoutFileRowDTO layoutFileRow;

        public LayoutFileNode(SearchResultsDTO results, LayoutFileRowDTO file) {
            super(results, file, true);
            layoutFileRow = file;
        }

        @Override
        void setIcon(FileRowDTO fileData) {
            LayoutFile lf = ((LayoutFileRowDTO) fileData).getLayoutFile();
            switch (lf.getType()) {
                case CARVED:
                    setIconBaseWithExtension(NodeIconUtil.CARVED_FILE.getPath());
                    break;
                case LAYOUT_FILE:
                    if (lf.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                        setIconBaseWithExtension(NodeIconUtil.DELETED_FILE.getPath());
                    } else {
                        setIconBaseWithExtension(MediaTypeUtils.getIconForFileType(layoutFileRow.getExtensionMediaType()));
                    }
                    break;
                default:
                    setIconBaseWithExtension(NodeIconUtil.DELETED_FILE.getPath());
            }
        }
    }

    /**
     * A node for representing a SlackFile.
     */
    public static class SlackFileNode extends FileNode {

        public SlackFileNode(SearchResultsDTO results, SlackFileRowDTO file) {
            super(results, file);
        }

        @Override
        void setIcon(FileRowDTO fileData) {
            AbstractFile file = fileData.getAbstractFile();
            if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                if (file.getType().equals(TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                    this.setIconBaseWithExtension(NodeIconUtil.CARVED_FILE.getPath()); //NON-NLS
                } else {
                    this.setIconBaseWithExtension(NodeIconUtil.DELETED_FILE.getPath()); //NON-NLS
                }
            } else {
                this.setIconBaseWithExtension(MediaTypeUtils.getIconForFileType(fileData.getExtensionMediaType()));
            }
        }
    }
}
