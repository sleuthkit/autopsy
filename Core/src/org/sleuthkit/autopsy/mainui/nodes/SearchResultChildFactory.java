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

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultTableSearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.BlackboardArtifactTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactTableSearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.DirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.LayoutFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.SlackFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.ImageRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.LocalDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.LocalFileDataSourceRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.OsAccountRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.PoolRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.VirtualDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.nodes.SearchResultChildFactory.ChildKey;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.RowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.VolumeRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardByFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ReportsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ScoreResultRowDTO;
import org.sleuthkit.autopsy.mainui.nodes.FileNode.LayoutFileNode;
import org.sleuthkit.autopsy.mainui.nodes.FileNode.SlackFileNode;
import org.sleuthkit.autopsy.mainui.nodes.SpecialDirectoryNode.LocalDirectoryNode;
import org.sleuthkit.autopsy.mainui.nodes.SpecialDirectoryNode.LocalFileDataSourceNode;
import org.sleuthkit.autopsy.mainui.nodes.SpecialDirectoryNode.VirtualDirectoryNode;

/**
 * Factory for populating results in a results viewer with a SearchResultsDTO.
 */
public class SearchResultChildFactory extends ChildFactory<ChildKey> {

    private static final Logger logger = Logger.getLogger(SearchResultChildFactory.class.getName());
    private SearchResultsDTO results;
    
    private final ExecutorService nodeThreadPool;

    public SearchResultChildFactory(SearchResultsDTO initialResults, ExecutorService nodeThreadPool) {
        this.results = initialResults;
        this.nodeThreadPool = nodeThreadPool;
    }

    @Override
    protected boolean createKeys(List<ChildKey> toPopulate) {
        SearchResultsDTO results = this.results;

        if (results != null) {
            List<ChildKey> childKeys = results.getItems().stream()
                    .map((item) -> new ChildKey(results, item))
                    .collect(Collectors.toList());

            toPopulate.addAll(childKeys);
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(ChildKey key) {
        String typeId = key.getRow().getTypeId();
        try {
            if (ScoreResultRowDTO.getTypeIdForClass().equals(typeId) && key.getRow() instanceof ScoreResultRowDTO scoreRow) {
                if (scoreRow.getArtifactDTO() != null) {
                    String iconPath = IconsUtil.getIconFilePath(scoreRow.getArtifactTypeId());
                    return new DataArtifactNode(key.getSearchResults(), scoreRow.getArtifactDTO(), iconPath, nodeThreadPool);
                } else if (scoreRow.getFileDTO() != null) {
                    return new FileNode(key.getSearchResults(), scoreRow.getFileDTO(), true, nodeThreadPool);
                }
            } else if (DataArtifactRowDTO.getTypeIdForClass().equals(typeId)) {
                return new DataArtifactNode((DataArtifactTableSearchResultsDTO) key.getSearchResults(), (DataArtifactRowDTO) key.getRow(), nodeThreadPool);
            } else if (FileRowDTO.getTypeIdForClass().equals(typeId)) {
                return new FileNode(key.getSearchResults(), (FileRowDTO) key.getRow(), true, nodeThreadPool);
            } else if (AnalysisResultRowDTO.getTypeIdForClass().equals(typeId)) {
                return new AnalysisResultNode((AnalysisResultTableSearchResultsDTO) key.getSearchResults(), (AnalysisResultRowDTO) key.getRow(), nodeThreadPool);
            } else if (ContentTagsRowDTO.getTypeIdForClass().equals(typeId)) {
                return new ContentTagNode(key.getSearchResults(), (ContentTagsRowDTO) key.getRow(), nodeThreadPool);
            } else if (BlackboardArtifactTagsRowDTO.getTypeIdForClass().equals(typeId)) {
                return new BlackboardArtifactTagNode(key.getSearchResults(), (BlackboardArtifactTagsRowDTO) key.getRow(), nodeThreadPool);
            } else if (ImageRowDTO.getTypeIdForClass().equals(typeId)) {
                return new ImageNode(key.getSearchResults(), (ImageRowDTO) key.getRow(), nodeThreadPool);
            } else if (LocalFileDataSourceRowDTO.getTypeIdForClass().equals(typeId)) {
                return new LocalFileDataSourceNode(key.getSearchResults(), (LocalFileDataSourceRowDTO) key.getRow(), nodeThreadPool);
            } else if (DirectoryRowDTO.getTypeIdForClass().equals(typeId)) {
                return new DirectoryNode(key.getSearchResults(), (DirectoryRowDTO) key.getRow(), nodeThreadPool);
            } else if (VolumeRowDTO.getTypeIdForClass().equals(typeId)) {
                return new VolumeNode(key.getSearchResults(), (VolumeRowDTO) key.getRow(), nodeThreadPool);
            } else if (LocalDirectoryRowDTO.getTypeIdForClass().equals(typeId)) {
                return new LocalDirectoryNode(key.getSearchResults(), (LocalDirectoryRowDTO) key.getRow(), nodeThreadPool);
            } else if (VirtualDirectoryRowDTO.getTypeIdForClass().equals(typeId)) {
                return new VirtualDirectoryNode(key.getSearchResults(), (VirtualDirectoryRowDTO) key.getRow(), nodeThreadPool);
            } else if (LayoutFileRowDTO.getTypeIdForClass().equals(typeId)) {
                return new LayoutFileNode(key.getSearchResults(), (LayoutFileRowDTO) key.getRow(), nodeThreadPool);
            } else if (PoolRowDTO.getTypeIdForClass().equals(typeId)) {
                return new PoolNode(key.getSearchResults(), (PoolRowDTO) key.getRow(), nodeThreadPool);
            } else if (SlackFileRowDTO.getTypeIdForClass().equals(typeId)) {
                return new SlackFileNode(key.getSearchResults(), (SlackFileRowDTO) key.getRow(), nodeThreadPool);
            } else if (OsAccountRowDTO.getTypeIdForClass().equals(typeId)) {
                return new OsAccountNode(key.getSearchResults(), (OsAccountRowDTO) key.getRow(), nodeThreadPool);
            } else if (CreditCardByFileRowDTO.getTypeIdForClass().equals(typeId)) {
                return new CreditCardByFileNode(key.getSearchResults(), (CreditCardByFileRowDTO) key.getRow(), nodeThreadPool);
            } else if (ReportsRowDTO.getTypeIdForClass().equals(typeId)) {
                return new ReportNode(key.getSearchResults(), (ReportsRowDTO) key.getRow(), nodeThreadPool);
            }else {
                logger.log(Level.WARNING, MessageFormat.format("No known node for type id: {0} provided by row result: {1}", typeId, key.getRow()));
            }
        } catch (ClassCastException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Could not cast item with type id: {0} to valid type.", typeId), ex);
        }

        return null;
    }

    public void update(SearchResultsDTO newResults) {
        this.results = newResults;
        this.refresh(false);
    }

    public long getResultCount() {
        return results == null ? 0 : results.getTotalResultsCount();
    }

    static class ChildKey {

        private final SearchResultsDTO searchResults;
        private final RowDTO row;

        ChildKey(SearchResultsDTO searchResults, RowDTO child) {
            this.searchResults = searchResults;
            this.row = child;
        }

        SearchResultsDTO getSearchResults() {
            return searchResults;
        }

        RowDTO getRow() {
            return row;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + (this.row == null ? 0 : Objects.hashCode(this.row.getId()));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChildKey other = (ChildKey) obj;
            return this.row.getId() == other.row.getId();
        }

    }
}
