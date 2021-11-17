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
import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
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
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
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

    @Override
    public Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        return SCOUtils.getCountPropertyAndDescription(attribute, defaultDescription);
    }

    @Override
    public DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        return SCOUtils.getCommentProperty(tags, attributes);
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
