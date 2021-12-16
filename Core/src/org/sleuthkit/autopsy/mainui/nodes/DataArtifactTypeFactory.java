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
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.mainui.datamodel.CommAccountsSearchParams;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardBinSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardDAO;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardFileSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactDAO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.EmailSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.EmailsDAO;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Factory for displaying data artifact types in the tree.
 */
public class DataArtifactTypeFactory extends TreeChildFactory<DataArtifactSearchParam> {

    private static final String BANK_ICON = "org/sleuthkit/autopsy/images/bank.png";

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
            return new AccountTypeParentNode(rowData);
        } else if (rowData.getSearchParams().getArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
            return new EmailTypeParentNode(rowData);
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
     * Display name and count of email messages in the tree.
     */
    public static class EmailTypeParentNode extends TreeNode<DataArtifactSearchParam> {

        public EmailTypeParentNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    itemData,
                    Children.create(new EmailAccountTypeFactory(itemData.getSearchParams().getDataSourceId()), true),
                    getDefaultLookup(itemData)
            );
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
        public AccountTypeParentNode(TreeResultsDTO.TreeItemDTO<? extends DataArtifactSearchParam> itemData) {
            super(itemData.getSearchParams().getArtifactType().getTypeName(),
                    getIconPath(itemData.getSearchParams().getArtifactType()),
                    createTitledData(itemData),
                    Children.create(new AccountTypeFactory(itemData.getSearchParams().getDataSourceId()), true),
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

    /**
     * Factory for displaying account types.
     */
    static class EmailAccountTypeFactory extends TreeChildFactory<EmailSearchParams> {

        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source object id for which the results
         *                     should be filtered or null if no data source
         *                     filtering.
         */
        public EmailAccountTypeFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        private EmailsDAO getDAO() {
            return MainDAO.getInstance().getEmailsDAO();
        }

        @Override
        protected TreeResultsDTO<? extends EmailSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return getDAO().getEmailCounts(dataSourceId, null);
        }

        @Override
        protected TreeNode<EmailSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends EmailSearchParams> rowData) {
            return new EmailAccountTypeNode(rowData);
        }

        @Override
        protected TreeItemDTO<? extends EmailSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {

            TreeItemDTO<EmailSearchParams> originalTreeItem = getTypedTreeItem(treeEvt, EmailSearchParams.class);

            if (originalTreeItem != null
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
                EmailSearchParams originalSearchParam = originalTreeItem.getSearchParams();
                return getDAO().createEmailTreeItem(
                        originalSearchParam.getAccount(),
                        null,
                        getDAO().getAccountDisplayName(originalSearchParam.getAccount(), null),
                        dataSourceId,
                        originalTreeItem.getDisplayCount());
            }

            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends EmailSearchParams> o1, TreeItemDTO<? extends EmailSearchParams> o2) {
            boolean firstDown = o1.getSearchParams().getAccount() == null;
            boolean secondDown = o2.getSearchParams().getAccount() == null;

            if (firstDown == secondDown) {
                return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
            } else {
                return Boolean.compare(firstDown, secondDown);
            }
        }
    }

    /**
     * A node representing a single account type in the tree.
     */
    static class EmailAccountTypeNode extends TreeNode<EmailSearchParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public EmailAccountTypeNode(TreeResultsDTO.TreeItemDTO<? extends EmailSearchParams> itemData) {
            super(itemData.getSearchParams().getAccount(),
                    "org/sleuthkit/autopsy/images/account-icon-16.png",
                    itemData,
                    Children.create(new EmailFolderTypeFactory(itemData.getSearchParams().getAccount(), itemData.getSearchParams().getDataSourceId()), true),
                    getDefaultLookup(itemData)
            );

        }
    }

    /**
     * Factory for displaying account types.
     */
    static class EmailFolderTypeFactory extends TreeChildFactory<EmailSearchParams> {

        private final String account;
        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param account      The email account for the factory.
         * @param dataSourceId The data source object id for which the results
         *                     should be filtered or null if no data source
         *                     filtering.
         */
        public EmailFolderTypeFactory(String account, Long dataSourceId) {
            this.dataSourceId = dataSourceId;
            this.account = account;
        }

        private EmailsDAO getDAO() {
            return MainDAO.getInstance().getEmailsDAO();
        }

        @Override
        protected TreeResultsDTO<? extends EmailSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return getDAO().getEmailCounts(dataSourceId, account);
        }

        @Override
        protected TreeNode<EmailSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends EmailSearchParams> rowData) {
            return new EmailFolderTypeNode(rowData);
        }

        @Override
        protected TreeItemDTO<? extends EmailSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {

            TreeItemDTO<EmailSearchParams> originalTreeItem = getTypedTreeItem(treeEvt, EmailSearchParams.class);

            if (originalTreeItem != null
                    && Objects.equals(this.account, originalTreeItem.getSearchParams().getAccount())
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {
                EmailSearchParams originalSearchParam = originalTreeItem.getSearchParams();
                return getDAO().createEmailTreeItem(
                        originalSearchParam.getAccount(),
                        originalSearchParam.getFolder(),
                        getDAO().getFolderDisplayName(originalSearchParam.getFolder()),
                        dataSourceId,
                        originalTreeItem.getDisplayCount());
            }

            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends EmailSearchParams> o1, TreeItemDTO<? extends EmailSearchParams> o2) {
            boolean firstDown = o1.getSearchParams().getFolder() == null;
            boolean secondDown = o2.getSearchParams().getFolder() == null;

            if (firstDown == secondDown) {
                return o1.getSearchParams().getFolder().compareToIgnoreCase(o2.getSearchParams().getFolder());
            } else {
                return Boolean.compare(firstDown, secondDown);
            }
        }
    }

    /**
     * A node representing a single account type in the tree.
     */
    static class EmailFolderTypeNode extends TreeNode<EmailSearchParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public EmailFolderTypeNode(TreeResultsDTO.TreeItemDTO<? extends EmailSearchParams> itemData) {
            super(itemData.getSearchParams().getFolder(),
                    "org/sleuthkit/autopsy/images/folder-icon-16.png",
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayEmailMessages(super.getItemData().getSearchParams());
        }
    }

    /**
     * A node for the root credit card node.
     */
    static class CreditCardRootNode extends TreeNode<CommAccountsSearchParams> {

        public CreditCardRootNode(TreeResultsDTO.TreeItemDTO<? extends CommAccountsSearchParams> itemData) {
            super(Account.Type.CREDIT_CARD.getDisplayName(),
                    Accounts.getIconFilePath(Account.Type.CREDIT_CARD),
                    itemData,
                    Children.create(new CreditCardTypeChildren(itemData.getSearchParams().getDataSourceId()), true),
                    getDefaultLookup(itemData));
        }

    }

    
    static class CreditCardTypeChildren extends TreeChildFactory<CreditCardSearchParams> {

        private final Long dataSourceId;
        private final boolean includeRejected = true;

        CreditCardTypeChildren(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        private CreditCardDAO getDAO() {
            return MainDAO.getInstance().getCreditCardDAO();
        }

        @Override
        protected TreeResultsDTO<? extends CreditCardSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return getDAO().getCreditCardCounts(this.dataSourceId, this.includeRejected);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TreeNode<CreditCardSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends CreditCardSearchParams> rowData) {
            if (rowData.getSearchParams() instanceof CreditCardFileSearchParams) {
                return new CreditCardByFileNode(rowData);
            } else if (rowData.getSearchParams() instanceof CreditCardBinSearchParams) {
                return new CreditCardByBinParentNode(rowData);
            } else {
                return null;
            }
        }

        @Override
        protected TreeItemDTO<? extends CreditCardSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {

            TreeItemDTO<CreditCardSearchParams> originalTreeItem = getTypedTreeItem(treeEvt, CreditCardSearchParams.class);

            if (originalTreeItem != null
                    && (this.includeRejected || !originalTreeItem.getSearchParams().isRejectedIncluded())
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                if (originalTreeItem.getSearchParams() instanceof CreditCardFileSearchParams) {
                    return getDAO().createFileTreeItem(this.includeRejected, this.dataSourceId, originalTreeItem.getDisplayCount());
                } else if (originalTreeItem.getSearchParams() instanceof CreditCardBinSearchParams) {
                    return getDAO().createBinTreeItem(this.includeRejected, null, this.dataSourceId, originalTreeItem.getDisplayCount());
                }
            }

            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends CreditCardSearchParams> o1, TreeItemDTO<? extends CreditCardSearchParams> o2) {
            boolean isBin1 = o1.getSearchParams() instanceof CreditCardBinSearchParams;
            boolean isBin2 = o2.getSearchParams() instanceof CreditCardFileSearchParams;
            return Boolean.compare(isBin1, isBin2);
        }
    }

    static class CreditCardByFileNode extends TreeNode<CreditCardSearchParams> {

        CreditCardByFileNode(TreeItemDTO<? extends CreditCardSearchParams> rowData) {
            super(rowData.getDisplayName(),
                    NodeIconUtil.FILE.getPath(),
                    rowData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            CreditCardSearchParams baseParams = this.getItemData().getSearchParams();
            dataResultPanel.displayCreditCardsByFile(new CreditCardFileSearchParams(baseParams.isRejectedIncluded(), baseParams.getDataSourceId()));
        }
    }

    static class CreditCardByBinParentNode extends TreeNode<CreditCardSearchParams> {

        CreditCardByBinParentNode(TreeResultsDTO.TreeItemDTO<? extends CreditCardSearchParams> rowData) {
            super(rowData.getDisplayName(),
                    BANK_ICON,
                    rowData,
                    Children.create(new CreditCardByBinFactory(rowData.getSearchParams().getDataSourceId(), rowData.getSearchParams().isRejectedIncluded()), true),
                    getDefaultLookup(rowData));
        }
    }

    static class CreditCardByBinFactory extends TreeChildFactory<CreditCardBinSearchParams> {

        private final Long dataSourceId;
        private final boolean includeRejected = true;

        CreditCardByBinFactory(Long dataSourceId, boolean includeRejected) {
            this.dataSourceId = dataSourceId;
        }

        private CreditCardDAO getDAO() {
            return MainDAO.getInstance().getCreditCardDAO();
        }

        @Override
        protected TreeResultsDTO<? extends CreditCardBinSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return getDAO().getCreditCardBinCounts(this.dataSourceId, this.includeRejected);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TreeNode<CreditCardBinSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends CreditCardBinSearchParams> rowData) {
            return new CreditCardByBinNode(rowData);
        }

        @Override
        protected TreeItemDTO<? extends CreditCardBinSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {

            TreeItemDTO<CreditCardBinSearchParams> originalTreeItem = getTypedTreeItem(treeEvt, CreditCardBinSearchParams.class);

            if (originalTreeItem != null
                    && (this.includeRejected || !originalTreeItem.getSearchParams().isRejectedIncluded())
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))
                    && (originalTreeItem.getSearchParams().getBinPrefix() != null)) {
                return getDAO().createBinTreeItem(
                        this.includeRejected,
                        originalTreeItem.getSearchParams().getBinPrefix(),
                        this.dataSourceId,
                        originalTreeItem.getDisplayCount());
            }

            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends CreditCardBinSearchParams> o1, TreeItemDTO<? extends CreditCardBinSearchParams> o2) {
            return StringUtils.defaultString(o1.getSearchParams().getBinPrefix()).compareToIgnoreCase(StringUtils.defaultString(o2.getSearchParams().getBinPrefix()));
        }
    }

    static class CreditCardByBinNode extends TreeNode<CreditCardBinSearchParams> {

        CreditCardByBinNode(TreeResultsDTO.TreeItemDTO<? extends CreditCardBinSearchParams> rowData) {
            super(rowData.getDisplayName(),
                    BANK_ICON,
                    rowData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayCreditCardsByBin(this.getItemData().getSearchParams());
        }
    }
}
