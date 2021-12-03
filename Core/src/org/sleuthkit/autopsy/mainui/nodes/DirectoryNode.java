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

import java.util.Optional;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.DirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskData;

/**
 * A node representing a row for a Directory in the results table.
 */
public class DirectoryNode extends BaseNode<SearchResultsDTO, DirectoryRowDTO> {

    /**
     * Simple node constructor.
     *
     * @param results The search result DTO.
     * @param row     The table row DTO.
     */
    public DirectoryNode(SearchResultsDTO results, DirectoryRowDTO row) {
        super(Children.LEAF, ContentNodeUtil.getLookup(row.getContent()), results, row);
        setName(ContentNodeUtil.getContentName(row.getContent().getId()));
        setDisplayName(row.getContent().getName());
        setShortDescription(row.getContent().getName());
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
    public Optional<AbstractFile> getFileForViewInTimelineAction() {
        return Optional.of(getRowDTO().getContent());
    }

    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }

    @Override
    public Optional<Content> getContentForRunIngestionModuleAction() {
        return Optional.of(getRowDTO().getContent());
    }

    @Override
    public boolean supportsContentTagAction() {
        return true;
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
