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
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.LocalFileDataSourceRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.LocalDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.VirtualDirectoryRowDTO;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SpecialDirectory;

/**
 * Abstract Node class for SpecialDirectory row results.
 */
abstract class SpecialDirectoryNode extends BaseNode<SearchResultsDTO, FileSystemRowDTO<? extends SpecialDirectory>> {

    /**
     * An abstract base class for FileSystem objects that are subclasses of
     * SpecialDirectory.
     *
     * @param results The search result DTO.
     * @param row     The table row DTO.
     */
    private SpecialDirectoryNode(SearchResultsDTO results, FileSystemRowDTO<? extends SpecialDirectory> row) {
        super(Children.LEAF, ContentNodeUtil.getLookup(row.getContent()), results, row);
        setDisplayName(row.getContent().getName());
        setShortDescription(row.getContent().getName());
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
    public boolean supportsExtractActions() {
        return true;
    }

    @Override
    public Optional<Content> getContentForRunIngestionModuleAction() {
        return Optional.of(getRowDTO().getContent());
    }

    @Override
    public Optional<Content> getContentForFileSearchAction() {
        return Optional.of(getRowDTO().getContent());
    }

    @Override
    public Optional<Content> getDataSourceForActions() {
        return getRowDTO().getContent().isDataSource()
                ? Optional.of(getRowDTO().getContent())
                : Optional.empty();
    }

    /**
     * A node representing a LocalDirectory.
     */
    public static class LocalDirectoryNode extends SpecialDirectoryNode {

        /**
         * Simple node constructor.
         *
         * @param results The search result DTO.
         * @param row     The table row DTO.
         */
        public LocalDirectoryNode(SearchResultsDTO results, LocalDirectoryRowDTO row) {
            super(results, row);
            setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
        }
    }

    /**
     * A node representing a VirtualDirectoryNode.
     */
    public static class VirtualDirectoryNode extends SpecialDirectoryNode {

        /**
         * Simple node constructor.
         *
         * @param results The search result DTO.
         * @param row     The table row DTO.
         */
        public VirtualDirectoryNode(SearchResultsDTO results, VirtualDirectoryRowDTO row) {
            super(results, row);
            setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-virtual.png");
        }
    }

    /**
     * Node representing a LocalFileDataSource.
     *
     * The previous class hierarchy is maintained, but probably not need.
     */
    public static class LocalFileDataSourceNode extends VirtualDirectoryNode {

        /**
         * Simple node constructor.
         *
         * @param results The search result DTO.
         * @param row     The table row DTO.
         */
        public LocalFileDataSourceNode(SearchResultsDTO results, LocalFileDataSourceRowDTO row) {
            super(results, row);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
        }
    }
}
