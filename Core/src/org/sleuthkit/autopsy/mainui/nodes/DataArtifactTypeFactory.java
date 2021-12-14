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

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.mainui.datamodel.CommAccountsSearchParams;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactDAO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
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
        if (rowData.getSearchParams().getArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
            return new AccountTypeParentNode(rowData, this.dataSourceId);
        } else {
            return new DataArtifactTypeTreeNode(rowData);
        }
    }
    
    @Override
    protected TreeItemDTO<DataArtifactSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
        
        TreeItemDTO<DataArtifactSearchParam> originalTreeItem = super.getTypedTreeItem(treeEvt, DataArtifactSearchParam.class);
        
        if (originalTreeItem != null
                && !DataArtifactDAO.getIgnoredTreeTypes().contains(originalTreeItem.getSearchParams().getArtifactType())
                && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
            
            DataArtifactSearchParam searchParam = originalTreeItem.getSearchParams();
            return new TreeItemDTO<>(
                    DataArtifactSearchParam.getTypeId(),
                    new DataArtifactSearchParam(searchParam.getArtifactType(), this.dataSourceId),
                    searchParam.getArtifactType().getTypeID(),
                    MainDAO.getInstance().getDataArtifactsDAO().getDisplayName(searchParam.getArtifactType()),
                    originalTreeItem.getDisplayCount());
        }
        return null;
    }
    
    @Override
    public int compare(TreeItemDTO<? extends DataArtifactSearchParam> o1, TreeItemDTO<? extends DataArtifactSearchParam> o2) {
        DataArtifactDAO dao = MainDAO.getInstance().getDataArtifactsDAO();
        return dao.getDisplayName(o1.getSearchParams().getArtifactType()).compareToIgnoreCase(dao.getDisplayName(o2.getSearchParams().getArtifactType()));
    }
    
    private static String getIconPath(BlackboardArtifact.Type artType) {
        String iconPath = IconsUtil.getIconFilePath(artType.getTypeID());
        return iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath;
    }

    /**
     * Display name and count of a data artifact type in the tree.
     */
    public static class DataArtifactTypeTreeNode extends TreeNode<DataArtifactSearchParam> {
        
        public DataArtifactTypeTreeNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData);
        }
        
        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayDataArtifact(this.getItemData().getSearchParams());
        }
    }

    /**
     * The account node that has nested children of account types.
     */
    @Messages({
        "DataArtifactTypeFactory_AccountTypeParentNode_displayName=Communication Accounts"
    })
    static class AccountTypeParentNode extends TreeNode<DataArtifactSearchParam> {

        /**
         * Sets correct title (not using artifact type display name).
         *
         * @param itemData The item data.
         *
         * @return The updated data.
         */
        private static TreeItemDTO<? extends DataArtifactSearchParam> createTitledData(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData) {
            return new TreeItemDTO<>(
                    itemData.getTypeId(),
                    itemData.getSearchParams(),
                    itemData.getId(),
                    Bundle.DataArtifactTypeFactory_AccountTypeParentNode_displayName(),
                    itemData.getDisplayCount()
            );
        }

        /**
         * Main constructor.
         *
         * @param itemData     The data to display.
         * @param dataSourceId The data source id to filter on or null if no
         *                     data source filter.
         */
        public AccountTypeParentNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData, Long dataSourceId) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    createTitledData(itemData),
                    Children.create(new AccountTypeFactory(dataSourceId), true),
                    getDefaultLookup(itemData)
            );
        }
        
        @Override
        protected void updateDisplayName(TreeItemDTO<? extends DataArtifactSearchParam> prevData, TreeItemDTO<? extends DataArtifactSearchParam> curData) {
            super.updateDisplayName(prevData, createTitledData(curData));
        }
        
    }

    /**
     * Factory for displaying account types.
     */
    static class AccountTypeFactory extends TreeChildFactory<CommAccountsSearchParams> {
        
        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source object id for which the results
         *                     should be filtered or null if no data source
         *                     filtering.
         */
        public AccountTypeFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }
        
        @Override
        protected TreeResultsDTO<? extends CommAccountsSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getCommAccountsDAO().getAccountsCounts(this.dataSourceId);
        }
        
        @Override
        protected TreeNode<CommAccountsSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends CommAccountsSearchParams> rowData) {
            return new AccountTypeNode(rowData);
        }
        
        @Override
        protected TreeItemDTO<? extends CommAccountsSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            
            TreeItemDTO<CommAccountsSearchParams> originalTreeItem = getTypedTreeItem(treeEvt, CommAccountsSearchParams.class);
            
            if (originalTreeItem != null
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
                CommAccountsSearchParams searchParam = originalTreeItem.getSearchParams();
                return TreeChildFactory.createTreeItemDTO(originalTreeItem,
                        new CommAccountsSearchParams(searchParam.getType(), this.dataSourceId));
            }
            
            return null;
        }
        
        @Override
        public int compare(TreeItemDTO<? extends CommAccountsSearchParams> o1, TreeItemDTO<? extends CommAccountsSearchParams> o2) {
            return o1.getSearchParams().getType().getDisplayName().compareToIgnoreCase(o2.getSearchParams().getType().getDisplayName());
        }
    }

    /**
     * A node representing a single account type in the tree.
     */
    static class AccountTypeNode extends TreeNode<CommAccountsSearchParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public AccountTypeNode(TreeResultsDTO.TreeItemDTO<? extends CommAccountsSearchParams> itemData) {
            super(itemData.getSearchParams().getType().getTypeName(),
                    Accounts.getIconFilePath(itemData.getSearchParams().getType()),
                    itemData);
        }
        
        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAccounts(super.getItemData().getSearchParams());
        }
    }
}
