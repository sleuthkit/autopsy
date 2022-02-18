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

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TagNameSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TagsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DeleteAnalysisResultEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;

/**
 * Factory for displaying analysis result types in the tree.
 */
public class TagNameFactory extends TreeChildFactory<TagNameSearchParams> {

    private static final String TAG_ICON = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png";

    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param dataSourceId The data source id to filter on or null if no filter.
     */
    public TagNameFactory(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    @Override
    protected TreeResultsDTO<? extends TagNameSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
        return MainDAO.getInstance().getTagsDAO().getNameCounts(dataSourceId);
    }

    @Override
    protected TreeNode<TagNameSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends TagNameSearchParams> rowData) {
        return new TagNameNode(rowData);
    }

    @Override
    protected TreeResultsDTO.TreeItemDTO<? extends TagNameSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
        TreeResultsDTO.TreeItemDTO<TagNameSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, TagNameSearchParams.class);

        if (originalTreeItem != null
                && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

            return MainDAO.getInstance().getTagsDAO().createTagNameTreeItem(
                    originalTreeItem.getSearchParams().getTagName(),
                    dataSourceId,
                    originalTreeItem.getDisplayCount());
        }
        return null;
    }

    @Override
    public int compare(TreeItemDTO<? extends TagNameSearchParams> o1, TreeItemDTO<? extends TagNameSearchParams> o2) {
        return Comparator.comparing((TreeItemDTO<? extends TagNameSearchParams> tagTreeItem) -> tagTreeItem.getDisplayName())
                .compare(o1, o2);
    }

    @Override
    protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
        for (DAOEvent evt : aggEvt.getEvents()) {
            if (evt instanceof DeleteAnalysisResultEvent && evt.getType() == DAOEvent.Type.TREE) {
                super.update();
                return;
            }
        }

        super.handleDAOAggregateEvent(aggEvt);
    }

    /**
     * A node for a tag name.
     */
    static class TagNameNode extends TreeNode<TagNameSearchParams> {

        public TagNameNode(TreeResultsDTO.TreeItemDTO<? extends TagNameSearchParams> rowData) {
            super(TagNameSearchParams.getTypeId() + "_" + Objects.toString(rowData.getId()),
                    TAG_ICON,
                    rowData,
                    Children.create(new TagTypeFactory(rowData.getSearchParams()), false),
                    getDefaultLookup(rowData));
        }

    }

    /**
     * Factory displaying file type or result type underneath a tag name node.
     */
    static class TagTypeFactory extends TreeChildFactory<TagsSearchParams> {

        private final TagNameSearchParams searchParams;

        TagTypeFactory(TagNameSearchParams searchParams) {
            this.searchParams = searchParams;
        }

        @Override
        protected TreeNode<TagsSearchParams> createNewNode(TreeItemDTO<? extends TagsSearchParams> rowData) {
            return new TagsTypeNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends TagsSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getTagsDAO().getTypeCounts(searchParams);
        }

        @Override
        protected TreeItemDTO<? extends TagsSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<TagsSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, TagsSearchParams.class);

            if (originalTreeItem != null
                    && Objects.equals(this.searchParams.getTagName(), originalTreeItem.getSearchParams().getTagName())
                    && (this.searchParams.getDataSourceId() == null || Objects.equals(this.searchParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {

                return MainDAO.getInstance().getTagsDAO().createTagTypeTreeItem(
                        searchParams.getTagName(),
                        originalTreeItem.getSearchParams().getTagType(),
                        searchParams.getDataSourceId(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends TagsSearchParams> o1, TreeItemDTO<? extends TagsSearchParams> o2) {
            return Comparator.comparing((TreeItemDTO<? extends TagsSearchParams> rowData) -> rowData.getSearchParams().getTagType() == TagsSearchParams.TagType.FILE ? 0 : 1)
                    .compare(o1, o2);
        }

    }

    /**
     * A tag type (i.e. File/Result) tree node. Clicking on this will go to
     * results.
     */
    static class TagsTypeNode extends TreeNode<TagsSearchParams> {

        private TagsTypeNode(TreeItemDTO<? extends TagsSearchParams> rowData) {
            super(TagsSearchParams.getTypeId() + "_" + Objects.toString(rowData.getId()), TAG_ICON, rowData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayTags(this.getItemData().getSearchParams());
        }
    }
}
