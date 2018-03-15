/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.accounts;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeNotFoundException;
import org.openide.nodes.NodeOp;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.AutopsyItemVisitor;
import org.sleuthkit.autopsy.datamodel.AutopsyVisitableItem;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * AutopsyVisitableItem for the Accounts section of the tree. All nodes,
 * factories, and custom key class related to accounts are inner classes.
 */
final public class Accounts implements AutopsyVisitableItem {

    private static final Logger LOGGER = Logger.getLogger(Accounts.class.getName());
    private static final String iconBasePath = "/org/sleuthkit/autopsy/images/"; //NON-NLS

    @NbBundle.Messages("AccountsRootNode.name=Accounts")
    final public static String NAME = Bundle.AccountsRootNode_name();

    private SleuthkitCase skCase;
    private final EventBus reviewStatusBus = new EventBus("ReviewStatusBus");

    /**
     * Should rejected accounts be shown in the accounts section of the tree.
     */
    private boolean showRejected = false;

    private final RejectAccounts rejectActionInstance;
    private final ApproveAccounts approveActionInstance;

    /**
     * Constructor
     *
     * @param skCase The SleuthkitCase object to use for db queries.
     */
    public Accounts(SleuthkitCase skCase) {
        this.skCase = skCase;

        this.rejectActionInstance = new RejectAccounts();
        this.approveActionInstance = new ApproveAccounts();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Get the clause that should be used in order to (not) filter out rejected
     * results from db queries.
     *
     * @return A clause that will or will not filter out rejected artifacts
     *         based on the state of showRejected.
     */
    private String getRejectedArtifactFilterClause() {
        return showRejected ? " " : " AND blackboard_artifacts.review_status_id != " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + " "; //NON-NLS
    }

    /**
     * Gets a new Action that when invoked toggles showing rejected artifacts on
     * or off.
     *
     * @return An Action that will toggle whether rejected artifacts are shown
     *         in the tree rooted by this Accounts instance.
     */
    public Action newToggleShowRejectedAction() {
        return new ToggleShowRejected();
    }

    /**
     * Base class for children that are also observers of the reviewStatusBus.
     * It
     *
     * @param <X> The type of keys used by this factory.
     */
    private abstract class ObservingChildren<X> extends ChildFactory.Detachable<X> {

        /**
         * Override of default constructor to force lazy creation of nodes, by
         * concrete instances of ObservingChildren
         */
        ObservingChildren() {
            super();
        }

        /**
         * Create of keys used by this Children object to represent the child
         * nodes.
         */
        abstract protected boolean createKeys(List<X> list);

        /**
         * Handle a ReviewStatusChangeEvent
         *
         * @param event the ReviewStatusChangeEvent to handle.
         */
        @Subscribe
        abstract void handleReviewStatusChange(ReviewStatusChangeEvent event);

        @Subscribe
        abstract void handleDataAdded(ModuleDataEvent event);

        @Override
        protected void removeNotify() {
            super.removeNotify();
            reviewStatusBus.unregister(ObservingChildren.this);
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refresh(true);
            reviewStatusBus.register(ObservingChildren.this);
        }
    }

    /**
     * Top-level node for the accounts tree
     */
    @NbBundle.Messages({"Accounts.RootNode.displayName=Accounts"})
    final public class AccountsRootNode extends DisplayableItemNode {

        public AccountsRootNode() {
            super(Children.create(new AccountTypeFactory(), true), Lookups.singleton(Accounts.this));
            setName(Accounts.NAME);
            setDisplayName(Bundle.Accounts_RootNode_displayName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/accounts.png");    //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates child nodes for each account type in the db.
     */
    private class AccountTypeFactory extends ObservingChildren<String> {

        /*
         * The pcl is in this class because it has the easiest mechanisms to add
         * and remove itself during its life cycles.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData
                                && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                            reviewStatusBus.post(eventData);
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        refresh(true);
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            try (SleuthkitCase.CaseDbQuery executeQuery = skCase.executeQuery(
                    "SELECT DISTINCT blackboard_attributes.value_text as account_type "
                    + " FROM blackboard_attributes "
                    + " WHERE blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID());
                    ResultSet resultSet = executeQuery.getResultSet()) {
                while (resultSet.next()) {
                    String accountType = resultSet.getString("account_type");
                    list.add(accountType);
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for account_types", ex);
            }

            return true;
        }

        @Override
        protected Node[] createNodesForKey(String acountTypeName) {

            if (Account.Type.CREDIT_CARD.getTypeName().equals(acountTypeName)) {
                return new Node[]{new CreditCardNumberAccountTypeNode()};
            } else {

                try {
                    Account.Type accountType = skCase.getCommunicationsManager().getAccountType(acountTypeName);
                    return new Node[]{new DefaultAccountTypeNode(accountType)};
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting display name for account type. ", ex);
                }

                return new Node[]{};
            }
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            super.removeNotify();
        }

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            super.addNotify();
            refresh(true);
        }

    }

    final private class DefaultAccountFactory extends ObservingChildren<Long> {

        private final Account.Type accountType;

        private DefaultAccountFactory(Account.Type accountType) {
            this.accountType = accountType;
        }

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData
                                && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                            reviewStatusBus.post(eventData);
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        refresh(true);

                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            super.removeNotify();
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            String query =
                    "SELECT blackboard_artifacts.artifact_id " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.value_text = '" + accountType.getTypeName() + "'" //NON-NLS
                    + getRejectedArtifactFilterClause(); //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    list.add(rs.getLong("artifact_id")); //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS
            }

            return true;
        }

        @Override
        protected Node[] createNodesForKey(Long t) {
            try {
                return new Node[]{new BlackboardArtifactNode(skCase.getBlackboardArtifact(t))};
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error get black board artifact with id " + t, ex);
                return new Node[0];
            }
        }

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }
    }

    /**
     * Default Node class for unknown account types and account types that have
     * no special behavior.
     */
    final public class DefaultAccountTypeNode extends DisplayableItemNode {

        private DefaultAccountTypeNode(Account.Type accountType) {
            super(Children.create(new DefaultAccountFactory(accountType), true), Lookups.singleton(accountType));
            setName(accountType.getDisplayName());
            this.setIconBaseWithExtension(getIconFilePath(accountType));   //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Enum for the children under the credit card AccountTypeNode.
     */
    private enum CreditCardViewMode {
        BY_FILE,
        BY_BIN;
    }

    final private class ViewModeFactory extends ObservingChildren<CreditCardViewMode> {

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData
                                && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                            reviewStatusBus.post(eventData);
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        refresh(true);

                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            super.removeNotify();
        }

        /**
         *
         */
        @Override
        protected boolean createKeys(List<CreditCardViewMode> list) {
            list.addAll(Arrays.asList(CreditCardViewMode.values()));

            return true;
        }

        @Override
        protected Node[] createNodesForKey(CreditCardViewMode key) {
            switch (key) {
                case BY_BIN:
                    return new Node[]{new ByBINNode()};
                case BY_FILE:
                    return new Node[]{new ByFileNode()};
                default:
                    return new Node[0];
            }
        }
    }

    /**
     * Node for the Credit Card account type. *
     */
    final public class CreditCardNumberAccountTypeNode extends DisplayableItemNode {

        /**
         * ChildFactory that makes nodes for the different account organizations
         * (by file, by BIN)
         */
        private CreditCardNumberAccountTypeNode() {
            super(Children.create(new ViewModeFactory(), true), Lookups.singleton(Account.Type.CREDIT_CARD.getDisplayName()));
            setName(Account.Type.CREDIT_CARD.getDisplayName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/credit-cards.png");   //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    final private class FileWithCCNFactory extends ObservingChildren<FileWithCCN> {

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData
                                && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                            reviewStatusBus.post(eventData);
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        refresh(true);

                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            super.removeNotify();
        }

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<FileWithCCN> list) {
            String query
                    = "SELECT blackboard_artifacts.obj_id," //NON-NLS
                    + "      solr_attribute.value_text AS solr_document_id, "; //NON-NLS
            if (skCase.getDatabaseType().equals(DbType.POSTGRESQL)) {
                query += "      string_agg(blackboard_artifacts.artifact_id::character varying, ',') AS artifact_IDs, " //NON-NLS
                        + "      string_agg(blackboard_artifacts.review_status_id::character varying, ',') AS review_status_ids, ";
            } else {
                query += "      GROUP_CONCAT(blackboard_artifacts.artifact_id) AS artifact_IDs, " //NON-NLS
                        + "      GROUP_CONCAT(blackboard_artifacts.review_status_id) AS review_status_ids, ";
            }
            query += "      COUNT( blackboard_artifacts.artifact_id) AS hits  " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + " LEFT JOIN blackboard_attributes as solr_attribute ON blackboard_artifacts.artifact_id = solr_attribute.artifact_id " //NON-NLS
                    + "                                AND solr_attribute.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() //NON-NLS
                    + " LEFT JOIN blackboard_attributes as account_type ON blackboard_artifacts.artifact_id = account_type.artifact_id " //NON-NLS
                    + "                                AND account_type.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                    + "                                AND account_type.value_text = '" + Account.Type.CREDIT_CARD.getTypeName() + "'" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + getRejectedArtifactFilterClause()
                    + " GROUP BY blackboard_artifacts.obj_id, solr_document_id " //NON-NLS
                    + " ORDER BY hits DESC ";  //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    list.add(new FileWithCCN(
                            rs.getLong("obj_id"), //NON-NLS
                            rs.getString("solr_document_id"), //NON-NLS
                            unGroupConcat(rs.getString("artifact_IDs"), Long::valueOf), //NON-NLS
                            rs.getLong("hits"), //NON-NLS
                            new HashSet<>(unGroupConcat(rs.getString("review_status_ids"), id -> BlackboardArtifact.ReviewStatus.withID(Integer.valueOf(id))))));  //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for files with ccn hits.", ex); //NON-NLS

            }
            return true;
        }

        @Override
        protected Node[] createNodesForKey(FileWithCCN key) {
            //add all account artifacts for the file and the file itself to the lookup
            try {
                List<Object> lookupContents = new ArrayList<>();
                for (long artId : key.artifactIDs) {
                    lookupContents.add(skCase.getBlackboardArtifact(artId));
                }
                AbstractFile abstractFileById = skCase.getAbstractFileById(key.getObjID());
                lookupContents.add(abstractFileById);
                return new Node[]{new FileWithCCNNode(key, abstractFileById, lookupContents.toArray())};
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error getting content for file with ccn hits.", ex); //NON-NLS
                return new Node[0];
            }
        }
    }

    /**
     * Node that is the root of the "by file" accounts tree. Its children are
     * FileWithCCNNodes.
     */
    final public class ByFileNode extends DisplayableItemNode {

        /**
         * Factory for the children of the ByFiles Node.
         */
        private ByFileNode() {
            super(Children.create(new FileWithCCNFactory(), true), Lookups.singleton("By File"));
            setName("By File");   //NON-NLS
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png");   //NON-NLS
            reviewStatusBus.register(this);
        }

        @NbBundle.Messages({
            "# {0} - number of children",
            "Accounts.ByFileNode.displayName=By File ({0})"})
        private void updateDisplayName() {
            String query
                    = "SELECT count(*) FROM ( SELECT count(*) AS documents "
                    + " FROM blackboard_artifacts " //NON-NLS
                    + " LEFT JOIN blackboard_attributes as solr_attribute ON blackboard_artifacts.artifact_id = solr_attribute.artifact_id " //NON-NLS
                    + "                                AND solr_attribute.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() //NON-NLS
                    + " LEFT JOIN blackboard_attributes as account_type ON blackboard_artifacts.artifact_id = account_type.artifact_id " //NON-NLS
                    + "                                AND account_type.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                    + "                                AND account_type.value_text = '" + Account.Type.CREDIT_CARD.getTypeName() + "'" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + getRejectedArtifactFilterClause()
                    + " GROUP BY blackboard_artifacts.obj_id, solr_attribute.value_text ) AS foo";
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    if (skCase.getDatabaseType().equals(DbType.POSTGRESQL)) {
                        setDisplayName(Bundle.Accounts_ByFileNode_displayName(rs.getLong("count")));
                    } else {
                        setDisplayName(Bundle.Accounts_ByFileNode_displayName(rs.getLong("count(*)")));
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for files with ccn hits.", ex); //NON-NLS

            }
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Subscribe
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            updateDisplayName();
        }

        @Subscribe
        void handleDataAdded(ModuleDataEvent event) {
            updateDisplayName();
        }
    }

    final private class BINFactory extends ObservingChildren<BinResult> {

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData
                                && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                            reviewStatusBus.post(eventData);
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();

                        refresh(true);
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            super.removeNotify();
        }

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<BinResult> list) {

            RangeMap<Integer, BinResult> binRanges = TreeRangeMap.create();

            String query
                    = "SELECT SUBSTR(blackboard_attributes.value_text,1,8) AS BIN, " //NON-NLS
                    + "     COUNT(blackboard_artifacts.artifact_id) AS count " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
                    + getRejectedArtifactFilterClause()
                    + " GROUP BY BIN " //NON-NLS
                    + " ORDER BY BIN "; //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query)) {
                ResultSet resultSet = results.getResultSet();
                //sort all te individual bins in to the ranges
                while (resultSet.next()) {
                    final Integer bin = Integer.valueOf(resultSet.getString("BIN"));
                    long count = resultSet.getLong("count");

                    BINRange binRange = (BINRange) CreditCards.getBINInfo(bin);
                    BinResult previousResult = binRanges.get(bin);

                    if (previousResult != null) {
                        binRanges.remove(Range.closed(previousResult.getBINStart(), previousResult.getBINEnd()));
                        count += previousResult.getCount();
                    }

                    if (binRange != null) {
                        binRanges.put(Range.closed(binRange.getBINstart(), binRange.getBINend()), new BinResult(count, binRange));
                    } else {
                        binRanges.put(Range.closed(bin, bin), new BinResult(count, bin, bin));
                    }
                }
                binRanges.asMapOfRanges().values().forEach(list::add);
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for BINs.", ex); //NON-NLS

            }

            return true;
        }

        @Override
        protected Node[] createNodesForKey(BinResult key) {
            return new Node[]{new BINNode(key)};
        }
    }

    /**
     * Node that is the root of the "By BIN" accounts tree. Its children are
     * BINNodes.
     */
    final public class ByBINNode extends DisplayableItemNode {

        /**
         * Factory that generates the children of the ByBin node.
         */
        @NbBundle.Messages("Accounts.ByBINNode.name=By BIN")
        private ByBINNode() {
            super(Children.create(new BINFactory(), true), Lookups.singleton(Bundle.Accounts_ByBINNode_name()));
            setName(Bundle.Accounts_ByBINNode_name());  //NON-NLS
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/bank.png");   //NON-NLS
            reviewStatusBus.register(this);
        }

        @NbBundle.Messages({
            "# {0} - number of children",
            "Accounts.ByBINNode.displayName=By BIN ({0})"})
        private void updateDisplayName() {
            String query
                    = "SELECT count(distinct SUBSTR(blackboard_attributes.value_text,1,8)) AS BINs " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
                    + getRejectedArtifactFilterClause(); //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query)) {
                ResultSet resultSet = results.getResultSet();
                while (resultSet.next()) {
                    setDisplayName(Bundle.Accounts_ByBINNode_displayName(resultSet.getLong("BINs")));
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for BINs.", ex); //NON-NLS
            }
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Subscribe
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            updateDisplayName();
        }

        @Subscribe
        void handleDataAdded(ModuleDataEvent event) {
            updateDisplayName();
        }
    }

    /**
     * DataModel for a child of the ByFileNode. Represents a file(chunk) and its
     * associated accounts.
     */
    @Immutable
    final private static class FileWithCCN {

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + (int) (this.objID ^ (this.objID >>> 32));
            hash = 79 * hash + Objects.hashCode(this.keywordSearchDocID);
            hash = 79 * hash + Objects.hashCode(this.artifactIDs);
            hash = 79 * hash + (int) (this.hits ^ (this.hits >>> 32));
            hash = 79 * hash + Objects.hashCode(this.statuses);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FileWithCCN other = (FileWithCCN) obj;
            if (this.objID != other.objID) {
                return false;
            }
            if (this.hits != other.hits) {
                return false;
            }
            if (!Objects.equals(this.keywordSearchDocID, other.keywordSearchDocID)) {
                return false;
            }
            if (!Objects.equals(this.artifactIDs, other.artifactIDs)) {
                return false;
            }
            if (!Objects.equals(this.statuses, other.statuses)) {
                return false;
            }
            return true;
        }

        private final long objID;
        private final String keywordSearchDocID;
        private final List<Long> artifactIDs;
        private final long hits;
        private final Set<BlackboardArtifact.ReviewStatus> statuses;

        private FileWithCCN(long objID, String solrDocID, List<Long> artifactIDs, long hits, Set<BlackboardArtifact.ReviewStatus> statuses) {
            this.objID = objID;
            this.keywordSearchDocID = solrDocID;
            this.artifactIDs = artifactIDs;
            this.hits = hits;
            this.statuses = statuses;
        }

        /**
         * Get the object ID of the file.
         *
         * @return the object ID of the file.
         */
        public long getObjID() {
            return objID;
        }

        /**
         * Get the keyword search docuement id. This is used for unnalocated
         * files to limit results to one chunk/page
         *
         * @return the keyword search document id.
         */
        public String getkeywordSearchDocID() {
            return keywordSearchDocID;
        }

        /**
         * Get the artifact ids of the account artifacts from this file.
         *
         * @return the artifact ids of the account artifacts from this file.
         */
        public List<Long> getArtifactIDs() {
            return artifactIDs;
        }

        /**
         * Get the number of account artifacts from this file.
         *
         * @return the number of account artifacts from this file.
         */
        public long getHits() {
            return hits;
        }

        /**
         * Get the status(s) of the account artifacts from this file.
         *
         * @return the status(s) of the account artifacts from this file.
         */
        public Set<BlackboardArtifact.ReviewStatus> getStatuses() {
            return statuses;
        }
    }

    /**
     * TODO: this was copy-pasted from timeline. Is there a single accessible
     * place it should go?
     *
     *
     * take the result of a group_concat SQLite operation and split it into a
     * set of X using the mapper to to convert from string to X
     *
     * @param <X>         the type of elements to return
     * @param groupConcat a string containing the group_concat result ( a comma
     *                    separated list)
     * @param mapper      a function from String to X
     *
     * @return a Set of X, each element mapped from one element of the original
     *         comma delimited string
     */
    static <X> List<X> unGroupConcat(String groupConcat, Function<String, X> mapper) {
        return StringUtils.isBlank(groupConcat) ? Collections.emptyList()
                : Stream.of(groupConcat.split(",")) //NON-NLS
                        .map(mapper::apply)
                        .collect(Collectors.toList());
    }

    /**
     * Node that represents a file or chunk of an unallocated space file.
     */
    final public class FileWithCCNNode extends DisplayableItemNode {

        private final FileWithCCN fileKey;
        private final String fileName;

        /**
         * Constructor
         *
         * @param key            The FileWithCCN that backs this node.
         * @param content        The Content object the key represents.
         * @param lookupContents The contents of this Node's lookup. It should
         * contain the content object and the account artifacts.
         */
        @NbBundle.Messages({
            "# {0} - raw file name",
            "# {1} - solr chunk id",
            "Accounts.FileWithCCNNode.unallocatedSpaceFile.displayName={0}_chunk_{1}"})
        private FileWithCCNNode(FileWithCCN key, Content content, Object[] lookupContents) {
            super(Children.LEAF, Lookups.fixed(lookupContents));
            this.fileKey = key;
            this.fileName = (key.getkeywordSearchDocID() == null)
                    ? content.getName()
                    : Bundle.Accounts_FileWithCCNNode_unallocatedSpaceFile_displayName(content.getName(), StringUtils.substringAfter(key.getkeywordSearchDocID(), "_")); //NON-NLS
            setName(fileName + key.getObjID());
            setDisplayName(fileName);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Override
        @NbBundle.Messages({
            "Accounts.FileWithCCNNode.nameProperty.displayName=File",
            "Accounts.FileWithCCNNode.accountsProperty.displayName=Accounts",
            "Accounts.FileWithCCNNode.statusProperty.displayName=Status",
            "Accounts.FileWithCCNNode.noDescription=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.Accounts_FileWithCCNNode_nameProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_nameProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_noDescription(),
                    fileName));
            ss.put(new NodeProperty<>(Bundle.Accounts_FileWithCCNNode_accountsProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_accountsProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_noDescription(),
                    fileKey.getHits()));
            ss.put(new NodeProperty<>(Bundle.Accounts_FileWithCCNNode_statusProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_statusProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_noDescription(),
                    fileKey.getStatuses().stream()
                            .map(BlackboardArtifact.ReviewStatus::getDisplayName)
                            .collect(Collectors.joining(", ")))); //NON-NLS

            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            Action[] actions = super.getActions(context);
            ArrayList<Action> arrayList = new ArrayList<>();
            arrayList.addAll(Arrays.asList(actions));
            try {
                arrayList.addAll(DataModelActionsFactory.getActions(Accounts.this.skCase.getContentById(fileKey.getObjID()), false));
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error gettung content by id", ex);
            }

            arrayList.add(approveActionInstance);
            arrayList.add(rejectActionInstance);

            return arrayList.toArray(new Action[arrayList.size()]);
        }
    }

    final private class CreditCardNumberFactory extends ObservingChildren<Long> {

        private final BinResult bin;

        private CreditCardNumberFactory(BinResult bin) {
            this.bin = bin;
        }

        @Subscribe
        @Override
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            refresh(true);
        }

        @Subscribe
        @Override
        void handleDataAdded(ModuleDataEvent event) {
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<Long> list) {

            String query
                    = "SELECT blackboard_artifacts.artifact_id " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.value_text >= '" + bin.getBINStart() + "' AND  blackboard_attributes.value_text < '" + (bin.getBINEnd() + 1) + "'" //NON-NLS
                    + getRejectedArtifactFilterClause()
                    + " ORDER BY blackboard_attributes.value_text"; //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    list.add(rs.getLong("artifact_id")); //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS

            }
            return true;
        }

        @Override
        protected Node[] createNodesForKey(Long artifactID) {
            if (skCase == null) {
                return new Node[0];
            }

            try {
                BlackboardArtifact art = skCase.getBlackboardArtifact(artifactID);
                return new Node[]{new AccountArtifactNode(art)};
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error creating BlackboardArtifactNode for artifact with ID " + artifactID, ex);   //NON-NLS
                return new Node[0];
            }
        }
    }

    private String getBinRangeString(BinResult bin) {
        if (bin.getBINStart() == bin.getBINEnd()) {
            return Integer.toString(bin.getBINStart());
        } else {
            return bin.getBINStart() + "-" + StringUtils.difference(bin.getBINStart() + "", bin.getBINEnd() + "");
        }
    }

    final public class BINNode extends DisplayableItemNode {

        /**
         * Creates the nodes for the credit card numbers
         */
        private final BinResult bin;

        private BINNode(BinResult bin) {
            super(Children.create(new CreditCardNumberFactory(bin), true), Lookups.singleton(getBinRangeString(bin)));
            this.bin = bin;
            setName(getBinRangeString(bin));
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/bank.png");   //NON-NLS
            reviewStatusBus.register(this);
        }

        @Subscribe
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {
            updateDisplayName();
            updateSheet();
        }

        @Subscribe
        void handleDataAdded(ModuleDataEvent event) {
            updateDisplayName();
        }

        private void updateDisplayName() {
            String query
                    = "SELECT count(blackboard_artifacts.artifact_id ) AS count" //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.value_text >= '" + bin.getBINStart() + "' AND  blackboard_attributes.value_text < '" + (bin.getBINEnd() + 1) + "'" //NON-NLS
                    + getRejectedArtifactFilterClause();
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    setDisplayName(getBinRangeString(bin) + " (" + rs.getLong("count") + ")"); //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS

            }

        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        private Sheet.Set getPropertySet(Sheet s) {
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            return ss;
        }

        @Override
        @NbBundle.Messages({
            "Accounts.BINNode.binProperty.displayName=Bank Identifier Number",
            "Accounts.BINNode.accountsProperty.displayName=Accounts",
            "Accounts.BINNode.cardTypeProperty.displayName=Payment Card Type",
            "Accounts.BINNode.schemeProperty.displayName=Credit Card Scheme",
            "Accounts.BINNode.brandProperty.displayName=Brand",
            "Accounts.BINNode.bankProperty.displayName=Bank",
            "Accounts.BINNode.bankCityProperty.displayName=Bank City",
            "Accounts.BINNode.bankCountryProperty.displayName=Bank Country",
            "Accounts.BINNode.bankPhoneProperty.displayName=Bank Phone #",
            "Accounts.BINNode.bankURLProperty.displayName=Bank URL",
            "Accounts.BINNode.noDescription=no description"})
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set properties = getPropertySet(sheet);

            properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_binProperty_displayName(),
                    Bundle.Accounts_BINNode_binProperty_displayName(),
                    Bundle.Accounts_BINNode_noDescription(),
                    getBinRangeString(bin)));
            properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_accountsProperty_displayName(),
                    Bundle.Accounts_BINNode_accountsProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                    bin.getCount()));

            //add optional properties if they are available
            if (bin.hasDetails()) {
                bin.getCardType().ifPresent(cardType -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_cardTypeProperty_displayName(),
                        Bundle.Accounts_BINNode_cardTypeProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        cardType)));
                bin.getScheme().ifPresent(scheme -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_schemeProperty_displayName(),
                        Bundle.Accounts_BINNode_schemeProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        scheme)));
                bin.getBrand().ifPresent(brand -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_brandProperty_displayName(),
                        Bundle.Accounts_BINNode_brandProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        brand)));
                bin.getBankName().ifPresent(bankName -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_bankProperty_displayName(),
                        Bundle.Accounts_BINNode_bankProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        bankName)));
                bin.getBankCity().ifPresent(bankCity -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_bankCityProperty_displayName(),
                        Bundle.Accounts_BINNode_bankCityProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        bankCity)));
                bin.getCountry().ifPresent(country -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_bankCountryProperty_displayName(),
                        Bundle.Accounts_BINNode_bankCountryProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        country)));
                bin.getBankPhoneNumber().ifPresent(phoneNumber -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_bankPhoneProperty_displayName(),
                        Bundle.Accounts_BINNode_bankPhoneProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        phoneNumber)));
                bin.getBankURL().ifPresent(url -> properties.put(new NodeProperty<>(Bundle.Accounts_BINNode_bankURLProperty_displayName(),
                        Bundle.Accounts_BINNode_bankURLProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                        url)));
            }
            return sheet;
        }

        private void updateSheet() {
            this.setSheet(createSheet());
        }

    }

    /**
     * Data model item to back the BINNodes in the tree. Has the number of
     * accounts found with the BIN.
     */
    @Immutable
    final static private class BinResult implements CreditCards.BankIdentificationNumber {

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + this.binEnd;
            hash = 97 * hash + this.binStart;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BinResult other = (BinResult) obj;
            if (this.binEnd != other.binEnd) {
                return false;
            }
            if (this.binStart != other.binStart) {
                return false;
            }
            return true;
        }

        /**
         * The number of accounts with this BIN
         */
        private final long count;

        private final BINRange binRange;
        private final int binEnd;
        private final int binStart;

        private BinResult(long count, @Nonnull BINRange binRange) {
            this.count = count;
            this.binRange = binRange;
            binStart = binRange.getBINstart();
            binEnd = binRange.getBINend();
        }

        private BinResult(long count, int start, int end) {
            this.count = count;
            this.binRange = null;
            binStart = start;
            binEnd = end;
        }

        int getBINStart() {
            return binStart;
        }

        int getBINEnd() {
            return binEnd;
        }

        long getCount() {
            return count;
        }

        boolean hasDetails() {
            return binRange != null;
        }

        @Override
        public Optional<Integer> getNumberLength() {
            return binRange.getNumberLength();
        }

        @Override
        public Optional<String> getBankCity() {
            return binRange.getBankCity();
        }

        @Override
        public Optional<String> getBankName() {
            return binRange.getBankName();
        }

        @Override
        public Optional<String> getBankPhoneNumber() {
            return binRange.getBankPhoneNumber();
        }

        @Override
        public Optional<String> getBankURL() {
            return binRange.getBankURL();
        }

        @Override
        public Optional<String> getBrand() {
            return binRange.getBrand();
        }

        @Override
        public Optional<String> getCardType() {
            return binRange.getCardType();
        }

        @Override
        public Optional<String> getCountry() {
            return binRange.getCountry();
        }

        @Override
        public Optional<String> getScheme() {
            return binRange.getScheme();
        }
    }

    final private class AccountArtifactNode extends BlackboardArtifactNode {

        private final BlackboardArtifact artifact;

        private AccountArtifactNode(BlackboardArtifact artifact) {
            super(artifact, "org/sleuthkit/autopsy/images/credit-card.png");   //NON-NLS
            this.artifact = artifact;
            setName("" + this.artifact.getArtifactID());

            reviewStatusBus.register(this);
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.addAll(Arrays.asList(super.getActions(context)));

            actionsList.add(approveActionInstance);
            actionsList.add(rejectActionInstance);

            return actionsList.toArray(new Action[actionsList.size()]);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set properties = sheet.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                sheet.put(properties);
            }
            properties.put(new NodeProperty<>(Bundle.Accounts_FileWithCCNNode_statusProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_statusProperty_displayName(),
                    Bundle.Accounts_FileWithCCNNode_noDescription(),
                    artifact.getReviewStatus().getDisplayName()));

            return sheet;
        }

        @Subscribe
        void handleReviewStatusChange(ReviewStatusChangeEvent event) {

            // Update the node if event includes this artifact
            event.artifacts.stream().filter((art) -> (art.getArtifactID() == this.artifact.getArtifactID())).map((_item) -> {
                return _item;
            }).forEachOrdered((_item) -> {
                updateSheet();
            });
        }

        private void updateSheet() {
            this.setSheet(createSheet());
        }

    }

    private final class ToggleShowRejected extends AbstractAction {

        @NbBundle.Messages("ToggleShowRejected.name=Show Rejected Results")
        ToggleShowRejected() {
            super(Bundle.ToggleShowRejected_name());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showRejected = !showRejected;
            reviewStatusBus.post(new ReviewStatusChangeEvent(Collections.emptySet(), null));
        }
    }

    private abstract class ReviewStatusAction extends AbstractAction {

        private final BlackboardArtifact.ReviewStatus newStatus;

        private ReviewStatusAction(String displayName, BlackboardArtifact.ReviewStatus newStatus) {
            super(displayName);
            this.newStatus = newStatus;

        }

        @Override
        public void actionPerformed(ActionEvent e) {

            /* get paths for selected nodes to reselect after applying review
             * status change */
            List<String[]> selectedPaths = Utilities.actionsGlobalContext().lookupAll(Node.class).stream()
                    .map(node -> {
                        String[] createPath;
                        /*
                         * If the we are rejecting and not showing rejected
                         * results, then the selected node, won't exist any
                         * more, so we select the previous one in stead.
                         */
                        if (newStatus == BlackboardArtifact.ReviewStatus.REJECTED && showRejected == false) {
                            List<Node> siblings = Arrays.asList(node.getParentNode().getChildren().getNodes());
                            if (siblings.size() > 1) {
                                int indexOf = siblings.indexOf(node);
                                //there is no previous for the first node, so instead we select the next one
                                Node sibling = indexOf > 0
                                        ? siblings.get(indexOf - 1)
                                        : siblings.get(Integer.max(indexOf + 1, siblings.size() - 1));
                                createPath = NodeOp.createPath(sibling, null);
                            } else {
                                /* if there are no other siblings to select,
                                 * just return null, but note we need to filter
                                 * this out of stream below */
                                return null;
                            }
                        } else {
                            createPath = NodeOp.createPath(node, null);
                        }
                        //for the reselect to work we need to strip off the first part of the path.
                        return Arrays.copyOfRange(createPath, 1, createPath.length);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            //change status of selected artifacts
            final Collection<? extends BlackboardArtifact> artifacts = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class);
            artifacts.forEach(artifact -> {
                try {
                    artifact.setReviewStatus(newStatus);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error changing artifact review status.", ex); //NON-NLS
                }
            });
            //post event
            reviewStatusBus.post(new ReviewStatusChangeEvent(artifacts, newStatus));

            final DataResultTopComponent directoryListing = DirectoryTreeTopComponent.findInstance().getDirectoryListing();
            final Node rootNode = directoryListing.getRootNode();

            //convert paths back to nodes
            List<Node> toArray = new ArrayList<>();
            selectedPaths.forEach(path -> {
                try {
                    toArray.add(NodeOp.findPath(rootNode, path));
                } catch (NodeNotFoundException ex) {
                    //just ingnore paths taht don't exist.  this is expected since we are rejecting
                }
            });
            //select nodes
            directoryListing.setSelectedNodes(toArray.toArray(new Node[toArray.size()]));
        }
    }

    final private class ApproveAccounts extends ReviewStatusAction {

        @NbBundle.Messages({"ApproveAccountsAction.name=Approve Accounts"})
        private ApproveAccounts() {
            super(Bundle.ApproveAccountsAction_name(), BlackboardArtifact.ReviewStatus.APPROVED);
        }
    }

    final private class RejectAccounts extends ReviewStatusAction {

        @NbBundle.Messages({"RejectAccountsAction.name=Reject Accounts"})
        private RejectAccounts() {
            super(Bundle.RejectAccountsAction_name(), BlackboardArtifact.ReviewStatus.REJECTED);
        }
    }

    static private class ReviewStatusChangeEvent {

        Collection<? extends BlackboardArtifact> artifacts;
        BlackboardArtifact.ReviewStatus newReviewStatus;

        ReviewStatusChangeEvent(Collection<? extends BlackboardArtifact> artifacts, BlackboardArtifact.ReviewStatus newReviewStatus) {
            this.artifacts = artifacts;
            this.newReviewStatus = newReviewStatus;
        }
    }

    /**
     * Get the path of the icon for the given Account Type.
     *
     * @return The path of the icon for the given Account Type.
     */
    public static String getIconFilePath(Account.Type type) {

        if (type.equals(Account.Type.CREDIT_CARD)) {
            return iconBasePath + "credit-card.png";
        } else if (type.equals(Account.Type.DEVICE)) {
            return iconBasePath + "image.png";
        } else if (type.equals(Account.Type.EMAIL)) {
            return iconBasePath + "email.png";
        } else if (type.equals(Account.Type.FACEBOOK)) {
            return iconBasePath + "facebook.png";
        } else if (type.equals(Account.Type.INSTAGRAM)) {
            return iconBasePath + "instagram.png";
        } else if (type.equals(Account.Type.MESSAGING_APP)) {
            return iconBasePath + "messaging.png";
        } else if (type.equals(Account.Type.PHONE)) {
            return iconBasePath + "phone.png";
        } else if (type.equals(Account.Type.TWITTER)) {
            return iconBasePath + "twitter.png";
        } else if (type.equals(Account.Type.WEBSITE)) {
            return iconBasePath + "web-file.png";
        } else if (type.equals(Account.Type.WHATSAPP)) {
            return iconBasePath + "WhatsApp.png";
        } else {
            //there could be a default icon instead...
            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }
    }
}
