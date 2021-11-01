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
import java.beans.PropertyChangeEvent;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultDAO;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSetSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.KeywordHitSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;

/**
 * Factory for displaying analysis result types in the tree.
 */
public class AnalysisResultTypeFactory extends TreeChildFactory<AnalysisResultSearchParam> {

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
        if (SET_TREE_ARTIFACTS.contains(rowData.getTypeData().getArtifactType().getTypeID())) {
            return new TreeTypeNode(rowData, new TreeSetFactory(rowData.getTypeData().getArtifactType(), dataSourceId, null));
        } else if (BlackboardArtifact.Type.TSK_KEYWORD_HIT.equals(rowData.getTypeData().getArtifactType())) {
            return new TreeTypeNode(rowData, new TreeSetFactory(rowData.getTypeData().getArtifactType(), dataSourceId, null));
        } else {
            return new AnalysisResultTypeTreeNode(rowData);
        }
    }

    @Override
    public boolean isRefreshRequired(PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
            /**
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, remote events may be
             * received for a case that is already closed.
             */
            try {
                Case.getCurrentCaseThrows();
                /**
                 * Due to some unresolved issues with how cases are closed, it
                 * is possible for the event to have a null oldValue if the
                 * event is a remote event.
                 */
                final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                if (null != event && Category.ANALYSIS_RESULT.equals(event.getBlackboardArtifactType().getCategory())
                        && !(AnalysisResultDAO.getIgnoredTreeTypes().contains(event.getBlackboardArtifactType()))) {
                    return true;
                }
            } catch (NoCurrentCaseException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
            }
        }
        return false;
    }

    /**
     * See if expected blackboard type matches event.
     *
     * @param expectedType The expected artifact type.
     * @param evt          The event.
     *
     * @return If the event is a data added event and contains the provided
     *         type.
     */
    private static boolean isRefreshRequired(BlackboardArtifact.Type expectedType, PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
            /**
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, remote events may be
             * received for a case that is already closed.
             */
            try {
                Case.getCurrentCaseThrows();
                /**
                 * Due to some unresolved issues with how cases are closed, it
                 * is possible for the event to have a null oldValue if the
                 * event is a remote event.
                 */
                final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                // GVDTODO it may be necessary to have more fine-grained check for refresh here.
                if (null != event && expectedType.equals(event.getBlackboardArtifactType())) {
                    return true;
                }
            } catch (NoCurrentCaseException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
            }
        }
        return false;
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
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAnalysisResult(this.getItemData().getTypeData());
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
        public TreeTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData, ChildFactory childFactory) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData,
                    Children.create(childFactory, true),
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            // GVDTODO...NO OP???
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
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            return AnalysisResultTypeFactory.isRefreshRequired(artifactType, evt);
        }

        @Override
        protected TreeNode<AnalysisResultSetSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> rowData) {
            return new TreeSetTypeNode(rowData, Children.LEAF);
        }
    }

    /**
     * A node for a set within an artifact type.
     */
    static class TreeSetTypeNode extends TreeNode<AnalysisResultSetSearchParam> {

        /**
         * Main constructor.
         *
         * @param artifactType The type of artifact.
         * @param itemData     The data to display.
         */
        public TreeSetTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSetSearchParam> itemData, Children children) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData,
                    children,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAnalysisResultSet(this.getItemData().getTypeData());
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
            return new TreeSetTypeNode(rowData, Children.create(new KeywordSearchTermFactory(rowData.getTypeData()), true));
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
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordSearchTermCounts(this.setParams);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            return AnalysisResultTypeFactory.isRefreshRequired(BlackboardArtifact.Type.TSK_KEYWORD_HIT, evt);
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
            super(itemData.getTypeData().getSearchTerm(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    itemData.getTypeData().hasChildren() ? Children.create(new KeywordFoundMatchFactory(itemData.getTypeData()), true) : Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            KeywordSearchTermParams searchParams = this.getItemData().getTypeData();

            if (!searchParams.hasChildren()) {
                dataResultPanel.displayKeywordHits(
                        new KeywordHitSearchParam(
                                searchParams.getDataSourceId(),
                                searchParams.getSetName(),
                                null,
                                searchParams.getSearchTerm()));
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
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordMatchCounts(this.setParams);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            return AnalysisResultTypeFactory.isRefreshRequired(BlackboardArtifact.Type.TSK_KEYWORD_HIT, evt);
        }
    }

    /**
     * A node signifying a match for a specific keyword given a regex/substring
     * search term.
     */
    static class KeywordFoundMatchNode extends TreeNode<KeywordMatchParams> {

        /**
         * Main constructor.
         * @param itemData The data for the match parameters.
         */
        public KeywordFoundMatchNode(TreeResultsDTO.TreeItemDTO<? extends KeywordMatchParams> itemData) {
            super(itemData.getTypeData().getKeywordMatch(),
                    getIconPath(BlackboardArtifact.Type.TSK_KEYWORD_HIT),
                    itemData,
                    Children.LEAF,
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            KeywordMatchParams searchParams = this.getItemData().getTypeData();
            dataResultPanel.displayKeywordHits(new KeywordHitSearchParam(
                    searchParams.getDataSourceId(),
                    searchParams.getSetName(),
                    searchParams.getKeywordMatch(),
                    searchParams.getSearchTerm()));
        }

    }

}
