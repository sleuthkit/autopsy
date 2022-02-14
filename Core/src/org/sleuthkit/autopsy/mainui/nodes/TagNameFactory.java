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
import org.openide.util.Lookup;
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

    // GVDTODO
    private static final String TAG_ICON = TBD;
    
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
            
            // GVDTODO
            return MainDAO.getInstance().getTagsDAO().getTagNameTreeItem(originalTreeItem);
        }
        return null;
    }

    @Override
    public int compare(TreeItemDTO<? extends TagNameSearchParams> o1, TreeItemDTO<? extends TagNameSearchParams> o2) {
        return Comparator.comparing(tagTreeItem -> tagTreeItem.getSearchParams().getTagName().getDisplayName())
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
    
    
    static class TagNameNode extends TreeNode<TagNameSearchParams> {

        public TagNameNode(TreeResultsDTO.TreeItemDTO<? extends TagNameSearchParams> rowData) {
            super(rowData.getName(),
                    TAG_ICON,
                    rowData, 
                    Children.create(new TagTypeFactory(rowData.getSearchParams()), false),
                    getDefaultLookup(rowData));
        }
        
    }
    
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
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public int compare(TreeItemDTO<? extends TagsSearchParams> o1, TreeItemDTO<? extends TagsSearchParams> o2) {
            return Comparator.comparing(rowData -> rowData.getSearchParams().getTagType() == TagsSearchParams.TagType.FILE ? 0 : 1)
                    .compare(o1, o2);
        }
        
    }
    
    static class TagsTypeNode extends TreeNode<TagsSearchParams> {

        private TagsTypeNode(TreeItemDTO<? extends TagsSearchParams> rowData) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
    }

//    /**
//     * Display name and count of an analysis result type in the tree.
//     */
//    static class AnalysisResultTypeTreeNode extends TreeNode<AnalysisResultSearchParam> {
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data to display.
//         */
//        AnalysisResultTypeTreeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData) {
//            super(itemData.getSearchParams().getArtifactType().getTypeName(),
//                    getIconPath(itemData.getSearchParams().getArtifactType()),
//                    itemData);
//        }
//
//        @Override
//        public void respondSelection(DataResultTopComponent dataResultPanel) {
//            dataResultPanel.displayAnalysisResult(this.getItemData().getSearchParams());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//        
//         @Override
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
//            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
//
//            Optional<BlackboardArtifact.Type> type = getAnalysisResultType();
//            Optional<Long> dsId = getDataSourceIdForActions();
//            if (type.isPresent()) {
//                group.add(new DeleteAnalysisResultSetAction(type.get(), () -> Collections.emptyList(), dsId.isPresent() ? dsId.get() : null));
//            }
//
//            return Optional.of(group);
//        }
//
//    }
//
//    /**
//     * An analysis result type node that has nested children.
//     */
//    static class TreeTypeNode extends TreeNode<AnalysisResultSearchParam> {
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data to display.
//         */
//        TreeTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData, ChildFactory<?> childFactory) {
//            super(itemData.getSearchParams().getArtifactType().getTypeName(),
//                    getIconPath(itemData.getSearchParams().getArtifactType()),
//                    itemData,
//                    Children.create(childFactory, true),
//                    getDefaultLookup(itemData));
//        }
//
//        @Override
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//    }
//
//    /**
//     * Factory displaying all analysis result configurations with count in the
//     * tree.
//     */
//    static class TreeConfigFactory extends AbstractAnalysisResultTreeFactory<AnalysisResultSearchParam> {
//
//        private final BlackboardArtifact.Type artifactType;
//        private final Long dataSourceId;
//        private final String nullSetName;
//
//        /**
//         * Main constructor.
//         *
//         * @param artifactType The type of artifact.
//         * @param dataSourceId The data source object id for which the results
//         *                     should be filtered or null if no data source
//         *                     filtering.
//         * @param nullSetName  The name of the set for artifacts with no
//         *                     configuration value. If null, items are omitted.
//         */
//        TreeConfigFactory(BlackboardArtifact.Type artifactType, Long dataSourceId, String nullSetName) {
//            this.artifactType = artifactType;
//            this.dataSourceId = dataSourceId;
//            this.nullSetName = nullSetName;
//        }
//
//        protected BlackboardArtifact.Type getArtifactType() {
//            return artifactType;
//        }
//
//        protected Long getDataSourceId() {
//            return dataSourceId;
//        }
//
//        protected String getNullSetName() {
//            return nullSetName;
//        }
//
//        @Override
//        protected TreeResultsDTO<? extends AnalysisResultSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
//            return MainDAO.getInstance().getAnalysisResultDAO().getConfigurationCounts(this.artifactType, this.dataSourceId, this.nullSetName);
//        }
//
//        @Override
//        protected TreeNode<AnalysisResultSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> rowData) {
//            return new TreeConfigTypeNode(rowData);
//        }
//
//        @Override
//        protected TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
//            TreeResultsDTO.TreeItemDTO<AnalysisResultSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, AnalysisResultSearchParam.class);
//
//            if (originalTreeItem != null
//                    && originalTreeItem.getSearchParams().getArtifactType().equals(this.artifactType)
//                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
//
//                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
//                AnalysisResultSearchParam searchParam = originalTreeItem.getSearchParams();
//                return new TreeResultsDTO.TreeItemDTO<>(
//                        AnalysisResultSearchParam.getTypeId(),
//                        new AnalysisResultSearchParam(this.artifactType, searchParam.getConfiguration(), this.dataSourceId),
//                        searchParam.getConfiguration() == null ? 0 : searchParam.getConfiguration(),
//                        searchParam.getConfiguration() == null ? nullSetName : searchParam.getConfiguration(),
//                        originalTreeItem.getDisplayCount());
//            }
//            return null;
//        }
//
//        @Override
//        public int compare(TreeItemDTO<? extends AnalysisResultSearchParam> o1, TreeItemDTO<? extends AnalysisResultSearchParam> o2) {
//            return STRING_COMPARATOR.compare(o1.getSearchParams().getConfiguration(), o2.getSearchParams().getConfiguration());
//        }
//
//        @Override
//        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
//            for (DAOEvent evt : aggEvt.getEvents()) {
//                if (evt instanceof DeleteAnalysisResultEvent && evt.getType() == DAOEvent.Type.TREE) {
//                    super.update();
//                    return;
//                }
//            }
//
//            super.handleDAOAggregateEvent(aggEvt);
//        }
//
//    }
//
//    /**
//     * A node for a set within an artifact type.
//     */
//    static class TreeConfigTypeNode extends TreeNode<AnalysisResultSearchParam> {
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data to display.
//         */
//        TreeConfigTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData) {
//            super(itemData.getSearchParams().getArtifactType().getTypeName() + "_SET_" + itemData.getSearchParams().getConfiguration(),
//                    getIconPath(itemData.getSearchParams().getArtifactType()),
//                    itemData,
//                    Children.LEAF,
//                    getDefaultLookup(itemData));
//        }
//
//        @Override
//        public void respondSelection(DataResultTopComponent dataResultPanel) {
//            dataResultPanel.displayAnalysisResultConfig(this.getItemData().getSearchParams());
//        }
//
//        @Override
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//
//        @Override
//        public boolean hasAnalysisResultConfigurations() {
//            return true;
//        }
//
//        @Override
//        public List<String> getAnalysisResultConfigurations() {
//            return Collections.singletonList(this.getItemData().getSearchParams().getConfiguration());
//        }
//
//        @Override
//        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
//            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
//
//            Optional<BlackboardArtifact.Type> type = getAnalysisResultType();
//            Optional<Long> dsId = getDataSourceIdForActions();
//            if (type.isPresent()) {
//                group.add(new DeleteAnalysisResultSetAction(
//                        type.get(), 
//                        () -> this.getAnalysisResultConfigurations(), 
//                        dsId.isPresent() ? dsId.get() : null));
//            }
//
//            return Optional.of(group);
//        }
//    }
//
//    /**
//     * A factory that shows all sets in keyword hits.
//     */
//    @Messages({
//        "AnalysisResultTypeFactory_adHocName=Ad Hoc Results"
//    })
//    public static class KeywordSetFactory extends AbstractAnalysisResultTreeFactory<KeywordListSearchParam> {
//
//        private final Long dataSourceId;
//
//        public KeywordSetFactory(Long dataSourceId) {
//            this.dataSourceId = dataSourceId;
//        }
//
//        @Override
//        protected TreeResultsDTO<? extends KeywordListSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
//            return MainDAO.getInstance().getAnalysisResultDAO().getKwSetCounts(this.dataSourceId, Bundle.AnalysisResultTypeFactory_adHocName());
//        }
//
//        @Override
//        protected TreeResultsDTO.TreeItemDTO<? extends KeywordListSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
//            TreeResultsDTO.TreeItemDTO<KeywordListSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordListSearchParam.class);
//
//            if (originalTreeItem != null
//                    && originalTreeItem.getSearchParams().getArtifactType().equals(BlackboardArtifact.Type.TSK_KEYWORD_HIT)
//                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
//
//                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
//                KeywordListSearchParam searchParam = originalTreeItem.getSearchParams();
//                return new TreeResultsDTO.TreeItemDTO<>(
//                        KeywordListSearchParam.getTypeId(),
//                        new KeywordListSearchParam(this.dataSourceId, searchParam.getConfiguration(), searchParam.getSetName()),
//                        searchParam.getSetName() == null ? 0 : searchParam.getSetName(),
//                        searchParam.getSetName() == null ? Bundle.AnalysisResultTypeFactory_adHocName() : searchParam.getSetName(),
//                        originalTreeItem.getDisplayCount());
//            }
//            return null;
//        }
//
//        @Override
//        public int compare(TreeItemDTO<? extends KeywordListSearchParam> o1, TreeItemDTO<? extends KeywordListSearchParam> o2) {
//            return STRING_COMPARATOR.compare(o1.getSearchParams().getSetName(), o2.getSearchParams().getSetName());
//        }
//
//        @Override
//        protected TreeNode<KeywordListSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordListSearchParam> rowData) {
//            return new KeywordSetNode(rowData);
//        }
//    }
//
//    static class KeywordSetNode extends TreeNode<KeywordListSearchParam> {
//
//        private static final Logger logger = Logger.getLogger(KeywordSetNode.class.getName());
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data to display.
//         */
//        public KeywordSetNode(TreeResultsDTO.TreeItemDTO<? extends KeywordListSearchParam> itemData) {
//            super("TSK_KEYWORD_HIT_SET_" + itemData.getSearchParams().getSetName(),
//                    getIconPath(itemData.getSearchParams().getArtifactType()),
//                    itemData,
//                    Children.create(new KeywordSearchTermFactory(itemData.getSearchParams()), true),
//                    getDefaultLookup(itemData));
//        }
//
//        @Override
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//
//        @Override
//        public boolean hasAnalysisResultConfigurations() {
//            return true;
//        }
//
//        @Override
//        public List<String> getAnalysisResultConfigurations() {
//            try {
//                return MainDAO.getInstance().getAnalysisResultDAO().getKeywordHitConfigurations(
//                        this.getItemData().getSearchParams().getSetName(),
//                        this.getItemData().getSearchParams().getDataSourceId());
//            } catch (ExecutionException ex) {
//                logger.log(Level.WARNING, "An exception occurred while fetching configurations.", ex);
//                return Collections.emptyList();
//            }
//        }
//        
//        @Override
//        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
//            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
//
//            Optional<BlackboardArtifact.Type> type = getAnalysisResultType();
//            Optional<Long> dsId = getDataSourceIdForActions();
//            if (type.isPresent()) {
//                group.add(new DeleteAnalysisResultSetAction(
//                        type.get(), 
//                        () -> this.getAnalysisResultConfigurations(), 
//                        dsId.isPresent() ? dsId.get() : null));
//            }
//
//            return Optional.of(group);
//        }        
//    }
//
//    /**
//     * Factory for displaying all search terms (regex or exact) for a specific
//     * set.
//     */
//    static class KeywordSearchTermFactory extends AbstractAnalysisResultTreeFactory<KeywordSearchTermParams> {
//
//        private final KeywordListSearchParam setParams;
//
//        /**
//         * Main constructor.
//         *
//         * @param setParams The parameters for the set.
//         */
//        KeywordSearchTermFactory(KeywordListSearchParam setParams) {
//            this.setParams = setParams;
//        }
//
//        @Override
//        protected TreeNode<KeywordSearchTermParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> rowData) {
//            return new KeywordSearchTermNode(rowData);
//        }
//
//        @Override
//        protected TreeResultsDTO<? extends KeywordSearchTermParams> getChildResults() throws IllegalArgumentException, ExecutionException {
//            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordSearchTermCounts(this.setParams.getSetName(), this.setParams.getDataSourceId());
//        }
//
//        @Override
//        protected TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
//            TreeResultsDTO.TreeItemDTO<KeywordSearchTermParams> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordSearchTermParams.class);
//
//            if (originalTreeItem != null
//                    && Objects.equals(originalTreeItem.getSearchParams().getSetName(), this.setParams.getSetName())
//                    && (this.setParams.getDataSourceId() == null
//                    || Objects.equals(this.setParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {
//
//                KeywordSearchTermParams searchParam = originalTreeItem.getSearchParams();
//                String searchTermDisplayName = MainDAO.getInstance().getAnalysisResultDAO()
//                        .getSearchTermDisplayName(searchParam.getRegex(), searchParam.getSearchType());
//
//                return new TreeResultsDTO.TreeItemDTO<>(
//                        KeywordSearchTermParams.getTypeId(),
//                        new KeywordSearchTermParams(
//                                this.setParams.getSetName(),
//                                searchParam.getRegex(),
//                                searchParam.getSearchType(),
//                                searchParam.getConfiguration(),
//                                searchParam.hasChildren(),
//                                this.setParams.getDataSourceId()
//                        ),
//                        searchTermDisplayName,
//                        searchTermDisplayName,
//                        originalTreeItem.getDisplayCount()
//                );
//            }
//            return null;
//        }
//
//        @Override
//        public int compare(TreeItemDTO<? extends KeywordSearchTermParams> o1, TreeItemDTO<? extends KeywordSearchTermParams> o2) {
//            return STRING_COMPARATOR.compare(o1.getSearchParams().getRegex(), o2.getSearchParams().getRegex());
//        }
//
//        @Override
//        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
//            for (DAOEvent evt : aggEvt.getEvents()) {
//                if (evt instanceof DeleteAnalysisResultEvent && evt.getType() == DAOEvent.Type.TREE) {
//                    super.update();
//                    return;
//                }
//            }
//
//            super.handleDAOAggregateEvent(aggEvt);
//        }
//
//    }
//
//    /**
//     * A node for an individual search term.
//     */
//    static class KeywordSearchTermNode extends TreeNode<KeywordSearchTermParams> {
//
//        private static final Logger logger = Logger.getLogger(KeywordSearchTermNode.class.getName());
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data for the search term.
//         */
//        KeywordSearchTermNode(TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> itemData) {
//            super(itemData.getSearchParams().getRegex(),
//                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
//                    itemData,
//                    (itemData.getSearchParams().hasChildren() || itemData.getSearchParams().getSearchType() == TskData.KeywordSearchQueryType.REGEX
//                    // for regex queries always create a subtree, even if there is only one child
//                    ? Children.create(new KeywordFoundMatchFactory(itemData.getSearchParams()), true)
//                    : Children.LEAF),
//                    getDefaultLookup(itemData));
//        }
//
//        @Override
//        public void respondSelection(DataResultTopComponent dataResultPanel) {
//            KeywordSearchTermParams searchTermParams = this.getItemData().getSearchParams();
//
//            if (!searchTermParams.hasChildren()) {
//                KeywordHitSearchParam searchParams = new KeywordHitSearchParam(searchTermParams.getDataSourceId(),
//                        searchTermParams.getConfiguration(),
//                        // if literal, keyword is regex
//                        TskData.KeywordSearchQueryType.LITERAL.equals(searchTermParams.getSearchType()) ? searchTermParams.getRegex() : null,
//                        // if literal, no regex
//                        TskData.KeywordSearchQueryType.LITERAL.equals(searchTermParams.getSearchType()) ? null : searchTermParams.getRegex(),
//                        searchTermParams.getSearchType(),
//                        searchTermParams.getConfiguration());
//                dataResultPanel.displayKeywordHits(searchParams);
//            } else {
//                super.respondSelection(dataResultPanel);
//            }
//        }
//
//        @Override
//        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
//            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
//
//            Optional<BlackboardArtifact.Type> type = getAnalysisResultType();
//            Optional<Long> dsId = getDataSourceIdForActions();
//            if (type.isPresent()) {
//                group.add(new DeleteAnalysisResultSetAction(
//                        type.get(), 
//                        () -> this.getAnalysisResultConfigurations(), 
//                        dsId.isPresent() ? dsId.get() : null));
//            }
//
//            return Optional.of(group);
//        }
//
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//
//        @Override
//        public boolean hasAnalysisResultConfigurations() {
//            return true;
//        }
//
//        @Override
//        public List<String> getAnalysisResultConfigurations() {
//            return Collections.singletonList(this.getItemData().getSearchParams().getConfiguration());
//        }
//    }
//
//    /**
//     * A factory for found keyword matches based on the search term (for
//     * regex/substring).
//     */
//    public static class KeywordFoundMatchFactory extends AbstractAnalysisResultTreeFactory<KeywordHitSearchParam> {
//
//        private final KeywordSearchTermParams searchTermParams;
//
//        /**
//         * Main constructor.
//         *
//         * @param params The search term parameters.
//         */
//        public KeywordFoundMatchFactory(KeywordSearchTermParams params) {
//            this.searchTermParams = params;
//        }
//
//        @Override
//        protected TreeNode<KeywordHitSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> rowData) {
//            return new KeywordFoundMatchNode(rowData);
//        }
//
//        @Override
//        protected TreeResultsDTO<? extends KeywordHitSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
//            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordMatchCounts(
//                    this.searchTermParams.getSetName(),
//                    this.searchTermParams.getRegex(),
//                    this.searchTermParams.getSearchType(),
//                    this.searchTermParams.getDataSourceId());
//        }
//
//        @Override
//        protected TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
//            TreeResultsDTO.TreeItemDTO<KeywordHitSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordHitSearchParam.class);
//
//            if (originalTreeItem != null
//                    && Objects.equals(originalTreeItem.getSearchParams().getRegex(), this.searchTermParams.getRegex())
//                    && Objects.equals(originalTreeItem.getSearchParams().getSearchType(), this.searchTermParams.getSearchType())
//                    && Objects.equals(originalTreeItem.getSearchParams().getConfiguration(), this.searchTermParams.getConfiguration())
//                    && (this.searchTermParams.getDataSourceId() == null
//                    || Objects.equals(this.searchTermParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {
//
//                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
//                KeywordHitSearchParam searchParam = originalTreeItem.getSearchParams();
//                return new TreeResultsDTO.TreeItemDTO<>(
//                        KeywordHitSearchParam.getTypeId(),
//                        new KeywordHitSearchParam(
//                                this.searchTermParams.getDataSourceId(),
//                                this.searchTermParams.getSetName(),
//                                searchParam.getKeyword(),
//                                this.searchTermParams.getRegex(),
//                                this.searchTermParams.getSearchType(),
//                                this.searchTermParams.getConfiguration()
//                        ),
//                        searchParam.getKeyword() == null ? "" : searchParam.getKeyword(),
//                        searchParam.getKeyword() == null ? "" : searchParam.getKeyword(),
//                        originalTreeItem.getDisplayCount()
//                );
//            }
//            return null;
//        }
//
//        @Override
//        public int compare(TreeItemDTO<? extends KeywordHitSearchParam> o1, TreeItemDTO<? extends KeywordHitSearchParam> o2) {
//            return STRING_COMPARATOR.compare(o1.getSearchParams().getKeyword(), o2.getSearchParams().getKeyword());
//        }
//
//        @Override
//        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
//            for (DAOEvent evt : aggEvt.getEvents()) {
//                if (evt instanceof DeleteAnalysisResultEvent && evt.getType() == DAOEvent.Type.TREE) {
//                    super.update();
//                    return;
//                }
//            }
//
//            super.handleDAOAggregateEvent(aggEvt);
//        }
//    }
//
//    /**
//     * A node signifying a match for a specific keyword given a regex/substring
//     * search term.
//     */
//    static class KeywordFoundMatchNode extends TreeNode<KeywordHitSearchParam> {
//
//        /**
//         * Main constructor.
//         *
//         * @param itemData The data for the match parameters.
//         */
//        public KeywordFoundMatchNode(TreeResultsDTO.TreeItemDTO<? extends KeywordHitSearchParam> itemData) {
//            super(itemData.getSearchParams().getKeyword(),
//                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
//                    itemData,
//                    Children.LEAF,
//                    getDefaultLookup(itemData));
//        }
//
//        @Override
//        public void respondSelection(DataResultTopComponent dataResultPanel) {
//            dataResultPanel.displayKeywordHits(this.getItemData().getSearchParams());
//        }
//
//        @Override
//        public Optional<Long> getDataSourceIdForActions() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getDataSourceId());
//        }
//
//        @Override
//        public Optional<BlackboardArtifact.Type> getAnalysisResultType() {
//            return Optional.ofNullable(this.getItemData().getSearchParams().getArtifactType());
//        }
//
//        @Override
//        public boolean hasAnalysisResultConfigurations() {
//            return true;
//        }
//
//        @Override
//        public List<String> getAnalysisResultConfigurations() {
//            return Collections.singletonList(this.getItemData().getSearchParams().getConfiguration());
//        }
//    }

}
