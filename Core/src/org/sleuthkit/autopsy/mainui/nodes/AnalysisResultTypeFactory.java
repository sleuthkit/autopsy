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

import org.sleuthkit.autopsy.mainui.datamodel.KeywordSearchTermParams;
import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultDAO;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSetSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.KeywordHitSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DeleteAnalysisResultEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;

/**
 * Factory for displaying analysis result types in the tree.
 */
public class AnalysisResultTypeFactory extends TreeChildFactory<AnalysisResultSearchParam> {

    private final static Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());

    @SuppressWarnings("deprecation")
    private static Set<Integer> SET_TREE_ARTIFACTS = ImmutableSet.of(
            BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_ITEM.getTypeID()
    );

    /**
     * Returns the path to the icon to use for this artifact type.
     *
     * @param artType The artifact type.
     *
     * @return The path to the icon to use for this artifact type.
     */
    private static String getIconPath(BlackboardArtifact.Type artType) {
        String iconPath = IconsUtil.getIconFilePath(artType.getTypeID());
        return iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath;
    }

    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param dataSourceId The data source id to filter on or null if no filter.
     */
    public AnalysisResultTypeFactory(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    @Override
    protected TreeResultsDTO<? extends AnalysisResultSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
        return MainDAO.getInstance().getAnalysisResultDAO().getAnalysisResultCounts(dataSourceId);
    }

    @Override
    protected TreeNode<AnalysisResultSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> rowData) {
        if (SET_TREE_ARTIFACTS.contains(rowData.getSearchParams().getArtifactType().getTypeID())) {
            return new TreeTypeNode(rowData, new TreeSetFactory(rowData.getSearchParams().getArtifactType(), dataSourceId, null));
        } else if (BlackboardArtifact.Type.TSK_KEYWORD_HIT.equals(rowData.getSearchParams().getArtifactType())) {
            return new TreeTypeNode(rowData, new KeywordSetFactory(dataSourceId));
        } else {
            return new AnalysisResultTypeTreeNode(rowData);
        }
    }

    @Override
    protected TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {

        TreeResultsDTO.TreeItemDTO<AnalysisResultSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, AnalysisResultSearchParam.class);

        if (originalTreeItem != null
                && !AnalysisResultDAO.getIgnoredTreeTypes().contains(originalTreeItem.getSearchParams().getArtifactType())
                && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

            // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
            AnalysisResultSearchParam searchParam = originalTreeItem.getSearchParams();
            return new TreeResultsDTO.TreeItemDTO<>(
                    AnalysisResultSearchParam.getTypeId(),
                    new AnalysisResultSearchParam(searchParam.getArtifactType(), this.dataSourceId),
                    searchParam.getArtifactType().getTypeID(),
                    searchParam.getArtifactType().getDisplayName(),
                    originalTreeItem.getDisplayCount());
        }
        return null;
    }

    @Override
    public int compare(TreeItemDTO<? extends AnalysisResultSearchParam> o1, TreeItemDTO<? extends AnalysisResultSearchParam> o2) {
        return o1.getSearchParams().getArtifactType().getDisplayName().compareTo(o2.getSearchParams().getArtifactType().getDisplayName());
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
     * Display name and count of an analysis result type in the tree.
     */
    static class AnalysisResultTypeTreeNode extends TreeNode<AnalysisResultSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        AnalysisResultTypeTreeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAnalysisResult(this.getItemData().getSearchParams());
        }
    }

    /**
     * An analysis result type node that has nested children.
     */
    static class TreeTypeNode extends TreeNode<AnalysisResultSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        TreeTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData, ChildFactory<?> childFactory) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData,
                    Children.create(childFactory, true),
                    getDefaultLookup(itemData));
        }
    }

    /**
     * Factory displaying all hashset sets with count in the tree.
     */
    static class TreeSetFactory extends TreeChildFactory<AnalysisResultSetSearchParam> {

        private final BlackboardArtifact.Type artifactType;
        private final Long dataSourceId;
        private final String nullSetName;

        /**
         * Main constructor.
         *
         * @param artifactType The type of artifact.
         * @param dataSourceId The data source object id for which the results
         *                     should be filtered or null if no data source
         *                     filtering.
         * @param nullSetName  The name of the set for artifacts with no
         *                     TSK_SET_NAME value. If null, items are omitted.
         */
        TreeSetFactory(BlackboardArtifact.Type artifactType, Long dataSourceId, String nullSetName) {
            this.artifactType = artifactType;
            this.dataSourceId = dataSourceId;
            this.nullSetName = nullSetName;
        }

        @Override
        protected TreeResultsDTO<? extends AnalysisResultSetSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getSetCounts(this.artifactType, this.dataSourceId, this.nullSetName);
        }

        @Override
        protected TreeNode<AnalysisResultSetSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> rowData) {
            return new TreeSetTypeNode(rowData);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<AnalysisResultSetSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, AnalysisResultSetSearchParam.class);

            if (originalTreeItem != null
                    && originalTreeItem.getSearchParams().getArtifactType().equals(this.artifactType)
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                AnalysisResultSetSearchParam searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        AnalysisResultSetSearchParam.getTypeId(),
                        new AnalysisResultSetSearchParam(this.artifactType, this.dataSourceId, searchParam.getSetName()),
                        searchParam.getSetName() == null ? 0 : searchParam.getSetName(),
                        searchParam.getSetName() == null ? nullSetName : searchParam.getSetName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends AnalysisResultSetSearchParam> o1, TreeItemDTO<? extends AnalysisResultSetSearchParam> o2) {
            return STRING_COMPARATOR.compare(o1.getSearchParams().getSetName(), o2.getSearchParams().getSetName());
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

    }

    /**
     * A node for a set within an artifact type.
     */
    static class TreeSetTypeNode extends TreeNode<AnalysisResultSetSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        TreeSetTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName() + "_SET_" + itemData.getSearchParams().getSetName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData,
                    Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAnalysisResultSet(this.getItemData().getSearchParams());
        }
    }

    /**
     * A factory that shows all sets in keyword hits.
     */
    @Messages({
        "AnalysisResultTypeFactory_adHocName=Ad Hoc Results"
    })
    public static class KeywordSetFactory extends TreeSetFactory {

        public KeywordSetFactory(Long dataSourceId) {
            super(BlackboardArtifact.Type.TSK_KEYWORD_HIT, dataSourceId, Bundle.AnalysisResultTypeFactory_adHocName());
        }

        @Override
        protected TreeNode<AnalysisResultSetSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> rowData) {
            return new KeywordSetNode(rowData);
        }
    }

    static class KeywordSetNode extends TreeNode<AnalysisResultSetSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public KeywordSetNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> itemData) {
            super("TSK_KEYWORD_HIT_SET_" + itemData.getSearchParams().getSetName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData,
                    Children.create(new KeywordSearchTermFactory(itemData.getSearchParams()), true),
                    getDefaultLookup(itemData));
        }
    }

    /**
     * Factory for displaying all search terms (regex or exact) for a specific
     * set.
     */
    static class KeywordSearchTermFactory extends TreeChildFactory<KeywordSearchTermParams> {

        private final AnalysisResultSetSearchParam setParams;

        /**
         * Main constructor.
         *
         * @param setParams The parameters for the set.
         */
        KeywordSearchTermFactory(AnalysisResultSetSearchParam setParams) {
            this.setParams = setParams;
        }

        @Override
        protected TreeNode<KeywordSearchTermParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> rowData) {
            return new KeywordSearchTermNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends KeywordSearchTermParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordSearchTermCounts(this.setParams.getSetName(), this.setParams.getDataSourceId());
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<KeywordSearchTermParams> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordSearchTermParams.class);

            if (originalTreeItem != null
                    && Objects.equals(originalTreeItem.getSearchParams().getSetName(), this.setParams.getSetName())
                    && (this.setParams.getDataSourceId() == null
                    || Objects.equals(this.setParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {

                KeywordSearchTermParams searchParam = originalTreeItem.getSearchParams();
                String searchTermDisplayName = MainDAO.getInstance().getAnalysisResultDAO()
                        .getSearchTermDisplayName(searchParam.getRegex(), searchParam.getSearchType());

                return new TreeResultsDTO.TreeItemDTO<>(
                        KeywordSearchTermParams.getTypeId(),
                        new KeywordSearchTermParams(
                                this.setParams.getSetName(),
                                searchParam.getRegex(),
                                searchParam.getSearchType(),
                                searchParam.hasChildren(),
                                this.setParams.getDataSourceId()
                        ),
                        searchTermDisplayName,
                        searchTermDisplayName,
                        originalTreeItem.getDisplayCount()
                );
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends KeywordSearchTermParams> o1, TreeItemDTO<? extends KeywordSearchTermParams> o2) {
            return STRING_COMPARATOR.compare(o1.getSearchParams().getRegex(), o2.getSearchParams().getRegex());
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

    }

    /**
     * A node for an individual search term.
     */
    static class KeywordSearchTermNode extends TreeNode<KeywordSearchTermParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data for the search term.
         */
        KeywordSearchTermNode(TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> itemData) {
            super(itemData.getSearchParams().getRegex(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    (itemData.getSearchParams().hasChildren() || itemData.getSearchParams().getSearchType() == TskData.KeywordSearchQueryType.REGEX
                    // for regex queries always create a subtree, even if there is only one child
                    ? Children.create(new KeywordFoundMatchFactory(itemData.getSearchParams()), true)
                    : Children.LEAF),
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            KeywordSearchTermParams searchTermParams = this.getItemData().getSearchParams();

            if (!searchTermParams.hasChildren()) {
                KeywordHitSearchParam searchParams = new KeywordHitSearchParam(searchTermParams.getDataSourceId(),
                        searchTermParams.getSetName(),
                        // if literal, keyword is regex
                        TskData.KeywordSearchQueryType.LITERAL.equals(searchTermParams.getSearchType()) ? searchTermParams.getRegex() : null,
                        // if literal, no regex
                        TskData.KeywordSearchQueryType.LITERAL.equals(searchTermParams.getSearchType()) ? null : searchTermParams.getRegex(),
                        searchTermParams.getSearchType());
                dataResultPanel.displayKeywordHits(searchParams);
            } else {
                super.respondSelection(dataResultPanel);
            }
        }

    }

    /**
     * A factory for found keyword matches based on the search term (for
     * regex/substring).
     */
    public static class KeywordFoundMatchFactory extends TreeChildFactory<KeywordHitSearchParam> {

        private final KeywordSearchTermParams searchTermParams;

        /**
         * Main constructor.
         *
         * @param params The search term parameters.
         */
        public KeywordFoundMatchFactory(KeywordSearchTermParams params) {
            this.searchTermParams = params;
        }

        @Override
        protected TreeNode<KeywordHitSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> rowData) {
            return new KeywordFoundMatchNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends KeywordHitSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordMatchCounts(
                    this.searchTermParams.getSetName(),
                    this.searchTermParams.getRegex(),
                    this.searchTermParams.getSearchType(),
                    this.searchTermParams.getDataSourceId());
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<KeywordHitSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordHitSearchParam.class);

            if (originalTreeItem != null
                    && Objects.equals(originalTreeItem.getSearchParams().getRegex(), this.searchTermParams.getRegex())
                    && Objects.equals(originalTreeItem.getSearchParams().getSearchType(), this.searchTermParams.getSearchType())
                    && Objects.equals(originalTreeItem.getSearchParams().getSetName(), this.searchTermParams.getSetName())
                    && (this.searchTermParams.getDataSourceId() == null
                    || Objects.equals(this.searchTermParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                KeywordHitSearchParam searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        KeywordHitSearchParam.getTypeId(),
                        new KeywordHitSearchParam(
                                this.searchTermParams.getDataSourceId(),
                                this.searchTermParams.getSetName(),
                                searchParam.getKeyword(),
                                this.searchTermParams.getRegex(),
                                this.searchTermParams.getSearchType()
                        ),
                        searchParam.getKeyword() == null ? "" : searchParam.getKeyword(),
                        searchParam.getKeyword() == null ? "" : searchParam.getKeyword(),
                        originalTreeItem.getDisplayCount()
                );
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends KeywordHitSearchParam> o1, TreeItemDTO<? extends KeywordHitSearchParam> o2) {
            return STRING_COMPARATOR.compare(o1.getSearchParams().getKeyword(), o2.getSearchParams().getKeyword());
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
    }

    /**
     * A node signifying a match for a specific keyword given a regex/substring
     * search term.
     */
    static class KeywordFoundMatchNode extends TreeNode<KeywordHitSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData The data for the match parameters.
         */
        public KeywordFoundMatchNode(TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> itemData) {
            super(itemData.getSearchParams().getKeyword(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayKeywordHits(this.getItemData().getSearchParams());
        }

    }

}
