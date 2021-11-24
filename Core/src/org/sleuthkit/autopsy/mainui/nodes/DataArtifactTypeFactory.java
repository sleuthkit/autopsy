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

import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Factory for displaying data artifact types in the tree.
 */
public class DataArtifactTypeFactory extends TreeChildFactory<DataArtifactSearchParam> {

    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param dataSourceId The data source id to filter on or null if no filter.
     */
    public DataArtifactTypeFactory(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    @Override
    protected TreeResultsDTO<? extends DataArtifactSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
        return MainDAO.getInstance().getDataArtifactsDAO().getDataArtifactCounts(dataSourceId);
    }

    @Override
    protected TreeNode<DataArtifactSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> rowData) {
        return new DataArtifactTypeTreeNode(rowData);
    }

    @Override
    protected TreeItemDTO<DataArtifactSearchParam> getInvalidatedChild(TreeEvent daoEvt) {
        if (daoEvt.getItemRecord().getTypeData() instanceof DataArtifactSearchParam) {
            TreeItemDTO<DataArtifactSearchParam> originalTreeItem = (TreeItemDTO<DataArtifactSearchParam>) daoEvt.getItemRecord();
            DataArtifactSearchParam searchParam = originalTreeItem.getTypeData();
            if (this.dataSourceId == null || this.dataSourceId == searchParam.getDataSourceId()) {
                return TreeChildFactory.getUpdatedTreeData(originalTreeItem, new DataArtifactSearchParam(searchParam.getArtifactType(), searchParam.getDataSourceId()));
            }
        }
        return null;
    }

    @Override
    public int compare(DataArtifactSearchParam o1, DataArtifactSearchParam o2) {
        return o1.getArtifactType().getDisplayName().compareTo(o2.getArtifactType().getDisplayName());
    }

    /**
     * Display name and count of a data artifact type in the tree.
     */
    public static class DataArtifactTypeTreeNode extends TreeNode<DataArtifactSearchParam> {

        private static String getIconPath(BlackboardArtifact.Type artType) {
            String iconPath = IconsUtil.getIconFilePath(artType.getTypeID());
            return iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath;
        }

        public DataArtifactTypeTreeNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayDataArtifact(this.getItemData().getTypeData());
        }
    }
}
