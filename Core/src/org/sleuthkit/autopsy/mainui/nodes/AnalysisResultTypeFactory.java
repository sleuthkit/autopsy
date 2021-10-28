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

import java.beans.PropertyChangeEvent;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultDAO;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.HashHitSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;

/**
 * Factory for displaying data artifact types in the tree.
 */
public class AnalysisResultTypeFactory extends TreeChildFactory<AnalysisResultSearchParam> {

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
        if (BlackboardArtifact.Type.TSK_HASHSET_HIT.equals(rowData.getTypeData().getArtifactType())) {
            return new HashHitTypeNode(rowData);
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

    private static String getIconPath(BlackboardArtifact.Type artType) {
        String iconPath = IconsUtil.getIconFilePath(artType.getTypeID());
        return iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath;
    }

    /**
     * Display name and count of a data artifact type in the tree.
     */
    public static class AnalysisResultTypeTreeNode extends TreeNode<AnalysisResultSearchParam> {

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

    public static class HashHitTypeNode extends TreeNode<AnalysisResultSearchParam> {

        public HashHitTypeNode(TreeResultsDTO.TreeItemDTO<? extends AnalysisResultSearchParam> itemData) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData,
                    Children.create(new HashHitSetFactory(itemData.getTypeData().getDataSourceId()), true),
                    getDefaultLookup(itemData));
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            // GVDTODO...NO OP???
        }
    }

    public static class HashHitSetFactory extends TreeChildFactory<HashHitSearchParam> {

        private final Long dataSourceId;

        public HashHitSetFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<HashHitSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends HashHitSearchParam> rowData) {
            return new HashHitSetNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends HashHitSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getHashSetCounts(dataSourceId);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    public static class HashHitSetNode extends TreeNode<HashHitSearchParam> {

        public HashHitSetNode(TreeResultsDTO.TreeItemDTO<? extends HashHitSearchParam> itemData) {
            super(itemData.getTypeData().getSetName(), getIconPath(BlackboardArtifact.Type.TSK_HASHSET_HIT), itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayHashHits(this.getItemData().getTypeData());
        }

    }
}
