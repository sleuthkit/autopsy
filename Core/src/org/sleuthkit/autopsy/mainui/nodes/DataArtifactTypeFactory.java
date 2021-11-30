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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.AccountSearchParams;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactDAO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.datamodel.Account;
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
        if (rowData.getTypeData().getArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
            return new AccountTypeParentNode(rowData, this.dataSourceId);
        } else {
            return new DataArtifactTypeTreeNode(rowData);
        }
    }

    @Override
    protected TreeItemDTO<DataArtifactSearchParam> getOrCreateRelevantChild(TreeEvent daoEvt) {
        if (daoEvt.getItemRecord().getSearchParams() instanceof DataArtifactSearchParam) {
            @SuppressWarnings("unchecked")
            TreeItemDTO<DataArtifactSearchParam> originalTreeItem = (TreeItemDTO<DataArtifactSearchParam>) daoEvt.getItemRecord();
            DataArtifactSearchParam searchParam = originalTreeItem.getSearchParams();
            if ((this.dataSourceId == null || Objects.equals(this.dataSourceId, searchParam.getDataSourceId())) && 
                    !DataArtifactDAO.getIgnoredTreeTypes().contains(searchParam.getArtifactType())) {
                return TreeChildFactory.createTreeItemDTO(originalTreeItem, new DataArtifactSearchParam(searchParam.getArtifactType(), searchParam.getDataSourceId()));
            }
        }
        return null;
    }

    @Override
    public int compare(DataArtifactSearchParam o1, DataArtifactSearchParam o2) {
        return o1.getArtifactType().getDisplayName().compareTo(o2.getArtifactType().getDisplayName());
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
    static class AccountTypeParentNode extends TreeNode<DataArtifactSearchParam> {

        /**
         * Main constructor.
         *
         * @param itemData     The data to display.
         * @param dataSourceId The data source id to filter on or null if no
         *                     data source filter.
         */
        public AccountTypeParentNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData, Long dataSourceId) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getIconPath(itemData.getTypeData().getArtifactType()),
                    itemData,
                    Children.create(new AccountTypeFactory(dataSourceId), true),
                    getDefaultLookup(itemData));
        }
    }

    /**
     * Factory for displaying account types.
     */
    static class AccountTypeFactory extends TreeChildFactory<AccountSearchParams> {

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
        protected TreeResultsDTO<? extends AccountSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getDataArtifactsDAO().getAccountsCounts(this.dataSourceId);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            // GVDTODO
            return false;
        }

        @Override
        protected TreeNode<AccountSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends AccountSearchParams> rowData) {
            return new AccountTypeNode(rowData);
        }
    }

    /**
     * A node representing a single account type in the tree.
     */
    static class AccountTypeNode extends TreeNode<AccountSearchParams> {

        private static final String ICON_BASE_PATH = "org/sleuthkit/autopsy/images/"; //NON-NLS

        /**
         * Get the path of the icon for the given Account Type.
         *
         * @return The path of the icon for the given Account Type.
         */
        public static String getAccountIconPath(String accountType) {

            if (accountType.equals(Account.Type.CREDIT_CARD.getTypeName())) {
                return ICON_BASE_PATH + "credit-card.png";
            } else if (accountType.equals(Account.Type.DEVICE.getTypeName())) {
                return ICON_BASE_PATH + "image.png";
            } else if (accountType.equals(Account.Type.EMAIL.getTypeName())) {
                return ICON_BASE_PATH + "email.png";
            } else if (accountType.equals(Account.Type.FACEBOOK.getTypeName())) {
                return ICON_BASE_PATH + "facebook.png";
            } else if (accountType.equals(Account.Type.INSTAGRAM.getTypeName())) {
                return ICON_BASE_PATH + "instagram.png";
            } else if (accountType.equals(Account.Type.MESSAGING_APP.getTypeName())) {
                return ICON_BASE_PATH + "messaging.png";
            } else if (accountType.equals(Account.Type.PHONE.getTypeName())) {
                return ICON_BASE_PATH + "phone.png";
            } else if (accountType.equals(Account.Type.TWITTER.getTypeName())) {
                return ICON_BASE_PATH + "twitter.png";
            } else if (accountType.equals(Account.Type.WEBSITE.getTypeName())) {
                return ICON_BASE_PATH + "web-file.png";
            } else if (accountType.equals(Account.Type.WHATSAPP.getTypeName())) {
                return ICON_BASE_PATH + "WhatsApp.png";
            } else if (accountType.equals(Account.Type.CREDIT_CARD.getTypeName())) {
                return ICON_BASE_PATH + "credit-cards.png";
            } else {
                return ICON_BASE_PATH + "face.png";
            }
        }

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public AccountTypeNode(TreeResultsDTO.TreeItemDTO<? extends AccountSearchParams> itemData) {
            super(itemData.getTypeData().getArtifactType().getTypeName(),
                    getAccountIconPath(itemData.getTypeData().getAccountType()),
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayAccounts(super.getItemData().getTypeData());
        }
    }
}
