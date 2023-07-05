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

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.DeletedContentSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.ScoreViewFilter;
import org.sleuthkit.autopsy.mainui.datamodel.ScoreViewSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode.StaticTreeNode;

/**
 *
 * Factories for displaying views.
 */
public class ScoreTypeFactory {
    private static final String SCORE_ICON = "org/sleuthkit/autopsy/images/red-circle-exclamation.png";
    /**
     * Children of 'Score' in the tree.
     */
    public static class ScoreChildren extends Children.Array {

        public ScoreChildren(Long dataSourceId) {
            super(ImmutableList.of(
                    new ScoreParentNode(dataSourceId)
            ));
        }
    }

    /**
     * Parent of score nodes in the tree.
     */
    @Messages({"ScoreTypeFactory_ScoreParentNode_displayName=Score"})
    public static class ScoreParentNode extends StaticTreeNode {

        ScoreParentNode(Long dataSourceId) {
            super(
                    "FILE_VIEW_SCORE_PARENT",
                    Bundle.ScoreTypeFactory_ScoreParentNode_displayName(),
                    SCORE_ICON,
                    new ScoreContentFactory(dataSourceId)
            );
        }
    }

    /**
     * The factory for creating deleted content tree nodes.
     */
    public static class ScoreContentFactory extends TreeChildFactory<ScoreViewSearchParams> {

        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source to filter files to or null.
         */
        public ScoreContentFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<ScoreViewSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends ScoreViewSearchParams> rowData) {
            return new ScoreContentTypeNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends ScoreViewSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getScoreDAO().getScoreContentCounts(dataSourceId);
        }

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof TreeEvent) {
                    TreeResultsDTO.TreeItemDTO<DeletedContentSearchParams> treeItem = super.getTypedTreeItem((TreeEvent) evt, DeletedContentSearchParams.class);
                    // if search params has null filter, trigger full refresh
                    if (treeItem != null && treeItem.getSearchParams().getFilter() == null) {
                        super.update();
                        return;
                    }
                }
            }

            super.handleDAOAggregateEvent(aggEvt);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends ScoreViewSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<ScoreViewSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, ScoreViewSearchParams.class);

            if (originalTreeItem != null
                    // only create child if size filter is present (if null, update should be triggered separately)
                    && originalTreeItem.getSearchParams().getFilter() != null
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                ScoreViewSearchParams searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        ScoreViewSearchParams.getTypeId(),
                        new ScoreViewSearchParams(searchParam.getFilter(), this.dataSourceId),
                        searchParam.getFilter(),
                        searchParam.getFilter().getDisplayName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends ScoreViewSearchParams> o1, TreeItemDTO<? extends ScoreViewSearchParams> o2) {
            return Integer.compare(o1.getSearchParams().getFilter().getId(), o2.getSearchParams().getFilter().getId());
        }

        /**
         * Shows a deleted content tree node.
         */
        static class ScoreContentTypeNode extends TreeNode<ScoreViewSearchParams> {

            private static final String BAD_SCORE_ICON = SCORE_ICON;
            private static final String SUS_SCORE_ICON = "org/sleuthkit/autopsy/images/yellow-circle-yield.png";

            private static String getIcon(ScoreViewFilter filter) {
                switch (filter) {
                    case SUSPICIOUS:
                        return SUS_SCORE_ICON;
                    case BAD:
                    default:
                        return BAD_SCORE_ICON;
                }
            }

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            ScoreContentTypeNode(TreeResultsDTO.TreeItemDTO<? extends ScoreViewSearchParams> itemData) {
                super("SCORE_CONTENT_" + itemData.getSearchParams().getFilter().name(), getIcon(itemData.getSearchParams().getFilter()), itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayScoreContent(this.getItemData().getSearchParams());
            }

        }
    }

}
