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

import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalDirectoryNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.mainui.datamodel.ArtifactRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.BaseNode.backgroundTasksPool;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.nodes.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.nodes.sco.SCOSupporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.VirtualDirectory;

public abstract class ArtifactNode<T extends BlackboardArtifact, R extends ArtifactRowDTO<T>> extends BaseNode<SearchResultsDTO, ArtifactRowDTO> implements ActionContext, SCOSupporter {

    private final R rowData;
    private final List<ColumnKey> columns;
    private Node parentFileNode;

    ArtifactNode(SearchResultsDTO searchResults, R rowData, List<ColumnKey> columns, Lookup lookup, String iconPath) {
        super(Children.LEAF, lookup, searchResults, rowData);
        this.rowData = rowData;
        this.columns = columns;
        setupNodeDisplay(iconPath);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        backgroundTasksPool.submit(new SCOFetcher<>(new WeakReference<>(this)));
        return sheet;
    }

    @Override
    public Optional<Content> getSourceContent() {
        return Optional.ofNullable(rowData.getSrcContent());
    }

    @Override
    public Optional<AbstractFile> getLinkedFile() {
        return Optional.ofNullable((AbstractFile) rowData.getLinkedFile());
    }

    @Override
    public boolean supportsViewInTimeline() {
        return rowData.isTimelineSupported();
    }

    @Override
    public Optional<BlackboardArtifact> getArtifactForTimeline() {
        return Optional.ofNullable(rowData.getArtifact());
    }

    @Override
    public boolean supportsAssociatedFileActions() {
        return getLinkedFile().isPresent();
    }

    @Override
    public boolean supportsSourceContentActions() {
        Content sourceContent = rowData.getSrcContent();

        return (sourceContent instanceof DataArtifact)
                || (sourceContent instanceof OsAccount)
                || (sourceContent instanceof AbstractFile || (rowData.getArtifact() instanceof DataArtifact));
    }

    @Override
    public Optional<AbstractFile> getSourceFileForTimelineAction() {
        return Optional.ofNullable(rowData.getSrcContent() instanceof AbstractFile ? (AbstractFile) rowData.getSrcContent() : null);
    }

    @Override
    public Optional<BlackboardArtifact> getArtifact() {
        return Optional.of(rowData.getArtifact());
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return rowData.getSrcContent() != null;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.ofNullable(getParentFileNode());
    }

    @Override
    public Optional<Node> getExternalViewerActionNode() {
        return Optional.ofNullable(getParentFileNode());
    }

    @Override
    public boolean supportsExtractActions() {
        return rowData.getSrcContent() instanceof AbstractFile;
    }

    @Override
    public boolean supportsArtifactTagAction() {
        return true;
    }

    private Node getParentFileNode() {
        if (parentFileNode == null) {
            parentFileNode = getParentFileNode(rowData.getSrcContent());
        }
        return parentFileNode;
    }

    protected void setupNodeDisplay(String iconPath) {
        // use first cell value for display name
        String displayName = rowData.getCellValues().size() > 0
                ? rowData.getCellValues().get(0).toString()
                : "";

        setDisplayName(displayName);
        setShortDescription(displayName);
        setName(Long.toString(rowData.getId()));
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }

    @Override
    public Optional<Content> getContent() {
        return Optional.of(rowData.getArtifact());
    }

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }

    @Messages({
        "# {0} - occurrenceCount",
        "# {1} - attributeType",
        "ArtifactNode_createSheet_count_description=There were {0} datasource(s) found with occurrences of the correlation value of type {1}",
        "ArtifactNode_createSheet_count_noCorrelationValues_description=Unable to find other occurrences because no value exists for the available correlation property"
    })
    @Override
    public Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        Long count = -1L;
        String description = defaultDescription;
        try {
            if (attribute != null && StringUtils.isNotBlank(attribute.getCorrelationValue())) {
                count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attribute);
                description = Bundle.ArtifactNode_createSheet_count_description(count, attribute.getCorrelationType().getDisplayName());
            } else if (attribute != null) {
                description = Bundle.ArtifactNode_createSheet_count_noCorrelationValues_description();
            }
        } catch (CentralRepoException ex) {
            getLogger().log(Level.SEVERE, MessageFormat.format("Error querying central repository for other occurences count (artifact objID={0}, corrAttrType={1}, corrAttrValue={2})",
                    getRowDTO().getArtifact().getId(),
                    attribute.getCorrelationType(),
                    attribute.getCorrelationValue()), ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            getLogger().log(Level.SEVERE, MessageFormat.format("Error normalizing correlation attribute for central repository query (artifact objID={0}, corrAttrType={2}, corrAttrValue={3})",
                    getRowDTO().getArtifact().getId(),
                    attribute.getCorrelationType(),
                    attribute.getCorrelationValue()), ex);
        }
        return Pair.of(count, description);
    }
    
    @Override
    public DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        /*
         * Has a tag with a comment been applied to the artifact or its source
         * content?
         */
        DataResultViewerTable.HasCommentStatus status = tags.size() > 0 ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
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
            getLogger().log(Level.SEVERE, "Attempted to Query CR for presence of comments in a Blackboard Artifact node and was unable to perform query, comment column will only reflect caseDB", ex);
        }
        return status;
    }

    /**
     * Returns a Node representing the file content if the content is indeed
     * some sort of file. Otherwise, return null.
     *
     * @param content The content.
     *
     * @return The file node or null if not a file.
     */
    private Node getParentFileNode(Content content) {
        if (content instanceof File) {
            return new org.sleuthkit.autopsy.datamodel.FileNode((AbstractFile) content);
        } else if (content instanceof Directory) {
            return new DirectoryNode((Directory) content);
        } else if (content instanceof VirtualDirectory) {
            return new VirtualDirectoryNode((VirtualDirectory) content);
        } else if (content instanceof LocalDirectory) {
            return new LocalDirectoryNode((LocalDirectory) content);
        } else if (content instanceof LayoutFile) {
            return new LayoutFileNode((LayoutFile) content);
        } else if (content instanceof LocalFile || content instanceof DerivedFile) {
            return new LocalFileNode((AbstractFile) content);
        } else if (content instanceof SlackFile) {
            return new SlackFileNode((AbstractFile) content);
        } else {
            return null;
        }
    }
}
