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
import org.sleuthkit.autopsy.mainui.datamodel.KeywordMatchParams;
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
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Factory for displaying analysis result types in the tree.
 */
public class AnalysisResultTypeFactory extends TreeChildFactory<AnalysisResultSearchParam> {

    private final static Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());

    @SuppressWarnings("deprecation")
    private static Set<Integer> SET_TREE_ARTIFACTS = ImmutableSet.of(
            BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID()
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
    public int compare(AnalysisResultSearchParam o1, AnalysisResultSearchParam o2) {
        return o1.getArtifactType().getDisplayName().compareTo(o2.getArtifactType().getDisplayName());
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
        public AnalysisResultTypeTreeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData) {
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
        public TreeTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData, ChildFactory<?> childFactory) {
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
        public TreeSetFactory(BlackboardArtifact.Type artifactType, Long dataSourceId, String nullSetName) {
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
                        searchParam.getSetName(),
                        searchParam.getSetName() == null ? nullSetName : searchParam.getSetName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(AnalysisResultSetSearchParam o1, AnalysisResultSetSearchParam o2) {
            return STRING_COMPARATOR.compare(o1.getSetName(), o2.getSetName());
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
        public TreeSetTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
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
        "AnalysisResultTypeFactory_adHocName=Adhoc Results"
    })
    static class KeywordSetFactory extends TreeSetFactory {

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
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
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
        public KeywordSearchTermFactory(AnalysisResultSetSearchParam setParams) {
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
                        .getSearchTermDisplayName(searchParam.getSearchTerm(), searchParam.getSearchType());

                return new TreeResultsDTO.TreeItemDTO<>(
                        KeywordSearchTermParams.getTypeId(),
                        new KeywordSearchTermParams(
                                this.setParams.getSetName(),
                                searchParam.getSearchTerm(),
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
        public int compare(KeywordSearchTermParams o1, KeywordSearchTermParams o2) {
            return STRING_COMPARATOR.compare(o1.getSearchTerm(), o2.getSearchTerm());
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
        public KeywordSearchTermNode(TreeResultsDTO.TreeItemDTO<? extends KeywordSearchTermParams> itemData) {
            super(itemData.getSearchParams().getSearchTerm(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    itemData.getSearchParams().hasChildren() ? Children.create(new KeywordFoundMatchFactory(itemData.getSearchParams()), true) : Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            KeywordSearchTermParams searchParams = this.getItemData().getSearchParams();

            if (!searchParams.hasChildren()) {
                dataResultPanel.displayKeywordHits(
                        new KeywordHitSearchParam(
                                searchParams.getDataSourceId(),
                                searchParams.getSetName(),
                                null,
                                searchParams.getSearchTerm()));
            } else {
                super.respondSelection(dataResultPanel);
            }
        }

    }

    /**
     * A factory for found keyword matches based on the search term (for
     * regex/substring).
     */
    public static class KeywordFoundMatchFactory extends TreeChildFactory<KeywordMatchParams> {

        private final KeywordSearchTermParams setParams;

        /**
         * Main constructor.
         *
         * @param params The search term parameters.
         */
        public KeywordFoundMatchFactory(KeywordSearchTermParams params) {
            this.setParams = params;
        }

        @Override
        protected TreeNode<KeywordMatchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends KeywordMatchParams> rowData) {
            return new KeywordFoundMatchNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends KeywordMatchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordMatchCounts(
                    this.setParams.getSetName(),
                    this.setParams.getSearchTerm(),
                    this.setParams.getSearchType(),
                    this.setParams.getDataSourceId());
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends KeywordMatchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<KeywordMatchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, KeywordMatchParams.class);

            if (originalTreeItem != null
                    && Objects.equals(originalTreeItem.getSearchParams().getSetName(), this.setParams.getSetName())
                    && (this.setParams.getDataSourceId() == null
                    || Objects.equals(this.setParams.getDataSourceId(), originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                KeywordMatchParams searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        KeywordMatchParams.getTypeId(),
                        new KeywordMatchParams(
                                this.setParams.getSetName(),
                                this.setParams.getSearchTerm(),
                                searchParam.getKeywordMatch(),
                                this.setParams.getSearchType(),
                                this.setParams.getDataSourceId()
                        ),
                        searchParam.getKeywordMatch(),
                        searchParam.getKeywordMatch() == null ? "" : searchParam.getKeywordMatch(),
                        originalTreeItem.getDisplayCount()
                );
            }
            return null;
        }

        @Override
        public int compare(KeywordMatchParams o1, KeywordMatchParams o2) {
            return STRING_COMPARATOR.compare(o1.getKeywordMatch(), o2.getKeywordMatch());
        }
    }

    /**
     * A node signifying a match for a specific keyword given a regex/substring
     * search term.
     */
    static class KeywordFoundMatchNode extends TreeNode<KeywordMatchParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data for the match parameters.
         */
        public KeywordFoundMatchNode(TreeResultsDTO.TreeItemDTO<? extends KeywordMatchParams> itemData) {
            super(itemData.getSearchParams().getKeywordMatch(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            KeywordMatchParams searchParams = this.getItemData().getSearchParams();
            dataResultPanel.displayKeywordHits(new KeywordHitSearchParam(
                    searchParams.getDataSourceId(),
                    searchParams.getSetName(),
                    searchParams.getKeywordMatch(),
                    searchParams.getSearchTerm()));
        }

    }

}
