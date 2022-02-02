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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.DirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskData;

/**
 * A node representing a row for a Directory in the results table.
 */
public class DirectoryNode extends BaseNode<SearchResultsDTO, DirectoryRowDTO> implements SCOSupporter {

    /**
     * Simple node constructor.
     *
     * @param results The search result DTO.
     * @param row     The table row DTO.
     */
    public DirectoryNode(SearchResultsDTO results, DirectoryRowDTO row, ExecutorService backgroundTasksPool) {
        super(Children.LEAF, ContentNodeUtil.getLookup(row.getContent()), results, row, backgroundTasksPool);
        setName(ContentNodeUtil.getContentName(row.getContent().getId()));
        setDisplayName(ContentNodeUtil.getContentDisplayName(row.getContent().getName()));
        setShortDescription(ContentNodeUtil.getContentDisplayName(row.getContent().getName()));
        setIcon();
    }

    /**
     * Sets the Icon that appears for the directory based on the FLAG state.
     *
     * @param dir
     */
    private void setIcon() {
        // set name, display name, and icon
        if (getRowDTO().getContent().isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            this.setIconBaseWithExtension(NodeIconUtil.DELETED_FOLDER.getPath()); //NON-NLS
        } else {
            this.setIconBaseWithExtension(NodeIconUtil.FOLDER.getPath()); //NON-NLS
        }
    }

    @Override
    public boolean supportsViewInTimeline() {
        return true;
    }

    @Override
    public Optional<Long> getFileForViewInTimelineAction() {
        return Optional.of(getRowDTO().getContent().getId());
    }

    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }

    @Override
    public Optional<Long> getContentForRunIngestionModuleAction() {
        return Optional.of(getRowDTO().getContent().getId());
    }

    @Override
    public boolean supportsContentTagAction() {
        return true;
    }

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }

    @Override
    public Optional<Content> getContent() {
        return Optional.ofNullable(getRowDTO().getContent());
    }

    @Override
    public Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        return SCOUtils.getCountPropertyAndDescription(attribute, defaultDescription);
    }

    @Override
    public DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        return SCOUtils.getCommentProperty(tags, attributes);
    }
    
    @Override
    public Action getPreferredAction() {
        if (getDisplayName().equals(org.sleuthkit.autopsy.datamodel.DirectoryNode.DOTDOTDIR)
                || getDisplayName().equals("..")) {
            return DirectoryTreeTopComponent.getOpenParentAction();
        }
        return DirectoryTreeTopComponent.getOpenChildAction(getName());
    }
}
