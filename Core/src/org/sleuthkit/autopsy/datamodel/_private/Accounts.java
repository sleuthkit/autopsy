/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel._private;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
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
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
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

/**
 * AutopsyVisitableItem for the Accounts section of the tree. All nodes,
 * factories, and data objects related to accounts are inner classes.
 */
final public class Accounts extends Observable implements AutopsyVisitableItem {

    private static final Logger LOGGER = Logger.getLogger(Accounts.class.getName());
    @NbBundle.Messages("AccountsRootNode.name=Accounts")
    final public static String NAME = Bundle.AccountsRootNode_name();

    private SleuthkitCase skCase;

    /**
     * Should rejected accounts be shown in the accounts section of the tree.
     */
    private boolean showRejected = false;

    /**
     * Constructor
     *
     * @param skCase The SleuthkitCase object to use for db queries.
     */
    public Accounts(SleuthkitCase skCase) {
        this.skCase = skCase;
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
        return showRejected ? " " : " AND blackboard_artifacts.review_status_id != " + BlackboardArtifact.ReviewStatus.REJECTED.getID(); //NON-NLS
    }

    /**
     * Notify all observers that something has changed, causing a refresh of the
     * accounts section of the tree.
     */
    private void update() {
        setChanged();
        notifyObservers();
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
     * Base class for factories that are also observers.
     *
     * @param <X> The type of keys used by this factory.
     */
    private abstract class ObservingChildFactory<X> extends ChildFactory.Detachable<X> implements Observer {

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            Accounts.this.deleteObserver(this);
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            Accounts.this.addObserver(this);
        }
    }

    /**
     * Top-level node for the accounts tree
     */
    @NbBundle.Messages({"Accounts.RootNode.displayName=Accounts"})
    final public class AccountsRootNode extends DisplayableItemNode {

        /**
         * Creates child nodes for each account type (currently hard coded to
         * make one for Credit Cards)
         */
        final private class AccountTypeFactory extends ObservingChildFactory<String> {

            /*
             * The pcl is in this class because it has the easiest mechanisms to
             * add and remove itself during its life cycles.
             */
            private final PropertyChangeListener pcl = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String eventType = evt.getPropertyName();
                    if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                        /**
                         * Checking for a current case is a stop gap measure
                         * until a different way of handling the closing of
                         * cases is worked out. Currently, remote events may be
                         * received for a case that is already closed.
                         */
                        try {
                            Case.getCurrentCase();
                            /**
                             * Even with the check above, it is still possible
                             * that the case will be closed in a different
                             * thread before this code executes. If that
                             * happens, it is possible for the event to have a
                             * null oldValue.
                             */
                            ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                            if (null != eventData
                                    && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                                Accounts.this.update();
                            }
                        } catch (IllegalStateException notUsed) {
                            // Case is closed, do nothing.
                        }
                    } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                        /**
                         * Checking for a current case is a stop gap measure
                         * until a different way of handling the closing of
                         * cases is worked out. Currently, remote events may be
                         * received for a case that is already closed.
                         */
                        try {
                            Case.getCurrentCase();
                            Accounts.this.update();
                        } catch (IllegalStateException notUsed) {
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
                    Exceptions.printStackTrace(ex);
                    return false;
                }
                return true;
            }

            @Override
            protected Node createNodeForKey(String key) {
                try {
                    Account.Type accountType = Account.Type.valueOf(key);
                    switch (accountType) {
                        case CREDIT_CARD:
                            return new CreditCardNumberAccountTypeNode();
                        default:
                            return new DefaultAccountTypeNode(key);
                    }
                } catch (IllegalArgumentException ex) {
                    LOGGER.log(Level.WARNING, "Unknown account type: " + key);
                    //Flesh out what happens with other account types here.
                    return new DefaultAccountTypeNode(key);
                }
            }

            @Override
            protected void removeNotify() {
                IngestManager.getInstance().removeIngestJobEventListener(pcl);
                IngestManager.getInstance().removeIngestModuleEventListener(pcl);
                Case.removePropertyChangeListener(pcl);
                super.removeNotify();
            }

            @Override
            protected void addNotify() {
                IngestManager.getInstance().addIngestJobEventListener(pcl);
                IngestManager.getInstance().addIngestModuleEventListener(pcl);
                Case.addPropertyChangeListener(pcl);
                super.addNotify();
                Accounts.this.update();
            }
        }

        public AccountsRootNode() {
            super(Children.LEAF, Lookups.singleton(Accounts.this));
            setChildren(Children.create(new AccountTypeFactory(), true));
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

    }

    final public class DefaultAccountTypeNode extends DisplayableItemNode {

        final private class DefaultAccountFactory extends ChildFactory.Detachable<Long> {

            private final String accountTypeName;

            public DefaultAccountFactory(String accountTypeName) {
                this.accountTypeName = accountTypeName;
            }

            @Override
            protected boolean createKeys(List<Long> list) {

                String query
                        = "SELECT blackboard_artifacts.artifact_id " //NON-NLS
                        + " FROM blackboard_artifacts " //NON-NLS
                        + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                        + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                        + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                        + "     AND blackboard_attributes.value_text = '" + accountTypeName + "'" //NON-NLS
                        + getRejectedArtifactFilterClause(); //NON-NLS
                try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                        ResultSet rs = results.getResultSet();) {
                    while (rs.next()) {
                        list.add(rs.getLong("artifact_id")); //NON-NLS
                    }
                } catch (TskCoreException | SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS
                    return false;
                }
                return true;
            }

            @Override
            protected Node createNodeForKey(Long t) {
                try {
                    return new BlackboardArtifactNode(skCase.getBlackboardArtifact(t));
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error get black board artifact with id " + t, ex);
                    return null;
                }
            }
        }

        private DefaultAccountTypeNode(String accountTypeName) {
            super(Children.LEAF);
            setChildren(Children.create(new DefaultAccountFactory(accountTypeName), true));
            setName(accountTypeName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/credit-cards.png");   //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
    }

    /**
     * Enum for the children under the credit card AccountTypeNode.
     */
    private enum CreditCardViewMode {
        BY_FILE,
        BY_BIN;
    }

    /**
     * Node for an account type.
     *
     * NOTE: currently hard coded to work for Credit Card only
     */
    final public class CreditCardNumberAccountTypeNode extends DisplayableItemNode {

        /**
         * ChildFactory that makes nodes for the different account organizations
         * (by file, by BIN)
         */
        final private class ViewModeFactory extends ObservingChildFactory<CreditCardViewMode> {

            @Override
            protected boolean createKeys(List<CreditCardViewMode> list) {
                list.addAll(Arrays.asList(CreditCardViewMode.values()));
                return true;
            }

            @Override
            protected Node createNodeForKey(CreditCardViewMode key) {
                switch (key) {
                    case BY_BIN:
                        return new ByBINNode();
                    case BY_FILE:
                        return new ByFileNode();
                    default:
                        return null;
                }
            }
        }

        private CreditCardNumberAccountTypeNode() {
            super(Children.LEAF);
            setChildren(Children.create(new ViewModeFactory(), true));
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
    }

    /**
     * Node that is the root of the "by file" accounts tree. Its children are
     * FileWithCCNNodes.
     */
    final public class ByFileNode extends DisplayableItemNode implements Observer {

        /**
         * Factory for the children of the ByFiles Node.
         */
        final private class FileWithCCNFactory extends ObservingChildFactory<FileWithCCN> {

            @Override
            protected boolean createKeys(List<FileWithCCN> list) {
                String query
                        = "SELECT blackboard_artifacts.obj_id," //NON-NLS
                        + "      solr_attribute.value_text AS solr_document_id, " //NON-NLS
                        + "      GROUP_CONCAT(blackboard_artifacts.artifact_id) AS artifact_IDs, " //NON-NLS
                        + "      COUNT( blackboard_artifacts.artifact_id) AS hits,  " //NON-NLS
                        + "      GROUP_CONCAT(blackboard_artifacts.review_status_id) AS review_status_ids "
                        + " FROM blackboard_artifacts " //NON-NLS
                        + " LEFT JOIN blackboard_attributes as solr_attribute ON blackboard_artifacts.artifact_id = solr_attribute.artifact_id " //NON-NLS
                        + "                                AND solr_attribute.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() //NON-NLS
                        + " LEFT JOIN blackboard_attributes as account_type ON blackboard_artifacts.artifact_id = account_type.artifact_id " //NON-NLS
                        + "                                AND account_type.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                        + "                                AND account_type.value_text = '" + Account.Type.CREDIT_CARD.name() + "'" //NON-NLS
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
                    return false;
                }
                return true;
            }

            @Override
            protected Node createNodeForKey(FileWithCCN key) {
                //add all account artifacts for the file and the file itself to the lookup
                try {
                    List<Object> lookupContents = new ArrayList<>();
                    for (long artId : key.artifactIDS) {
                        lookupContents.add(skCase.getBlackboardArtifact(artId));
                    }
                    AbstractFile abstractFileById = skCase.getAbstractFileById(key.getObjID());
                    lookupContents.add(abstractFileById);
                    return new FileWithCCNNode(key, abstractFileById, lookupContents.toArray());
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting content for file with ccn hits.", ex); //NON-NLS
                    return null;
                }
            }

            @Override
            public void update(Observable o, Object arg) {
                refresh(true);
            }
        }
        private final FileWithCCNFactory fileFactory;

        private ByFileNode() {
            super(Children.LEAF);
            fileFactory = new FileWithCCNFactory();
            setChildren(Children.create(fileFactory, true));
            setName("By File");   //NON-NLS
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png");   //NON-NLS
            Accounts.this.addObserver(this);
        }

        @NbBundle.Messages({
            "# {0} - number of children",
            "Accounts.ByFileNode.displayName=By File ({0})"})
        private void updateDisplayName() {
            ArrayList<FileWithCCN> keys = new ArrayList<>();
            fileFactory.createKeys(keys);
            setDisplayName(Bundle.Accounts_ByFileNode_displayName(keys.size()));
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
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }
    }

    /**
     * Node that is the root of the "By BIN" accounts tree. Its children are
     * BINNodes.
     */
    final public class ByBINNode extends DisplayableItemNode implements Observer {

        /**
         * Factory that generates the children of the ByBin node.
         */
        final private class BINFactory extends ObservingChildFactory<BinResult> {

            @Override
            protected boolean createKeys(List<BinResult> list) {
                RangeMap<Integer, BinResult> ranges = TreeRangeMap.create();

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
                    while (resultSet.next()) {
                        final Integer bin = Integer.valueOf(resultSet.getString("BIN"));
                        long count = resultSet.getLong("count");

                        BINRange binRange = (BINRange) CreditCards.getBINInfo(bin);
                        BinResult previousResult = ranges.get(bin);

                        if (previousResult != null) {
                            ranges.remove(Range.closed(previousResult.getBINStart(), previousResult.getBINEnd()));
                            count += previousResult.getCount();
                        }

                        if (binRange != null) {
                            ranges.put(Range.closed(binRange.getBINstart(), binRange.getBINend()), new BinResult(count, binRange));
                        } else {
                            ranges.put(Range.closed(bin, bin), new BinResult(count, bin, bin));
                        }
                    }
                    ranges.asMapOfRanges().values().forEach(list::add);
                } catch (TskCoreException | SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error querying for BINs.", ex); //NON-NLS
                    return false;
                }
                return true;
            }

            @Override
            protected Node createNodeForKey(BinResult key) {
                return new BINNode(key);
            }
        }

        private final BINFactory binFactory;

        private ByBINNode() {
            super(Children.LEAF);
            binFactory = new BINFactory();
            setChildren(Children.create(binFactory, true));
            setName("By BIN");  //NON-NLS
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/bank.png");   //NON-NLS
            Accounts.this.addObserver(this);
        }

        @NbBundle.Messages({
            "# {0} - number of children",
            "Accounts.ByBINNode.displayName=By BIN ({0})"})
        private void updateDisplayName() {
            ArrayList<BinResult> keys = new ArrayList<>();
            binFactory.createKeys(keys);
            setDisplayName(Bundle.Accounts_ByBINNode_displayName(keys.size()));
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
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }
    }

    /**
     * DataModel for a child of the ByFileNode. Represents a file(chunk) and its
     * associated accounts.
     */
    @Immutable
    final private static class FileWithCCN {

        private final long objID;
        private final String solrDocumentId;
        private final List<Long> artifactIDS;
        private final long hits;
        private final Set<BlackboardArtifact.ReviewStatus> statuses;

        private FileWithCCN(long objID, String solrDocID, List<Long> artifactIDS, long hits, Set<BlackboardArtifact.ReviewStatus> statuses) {
            this.objID = objID;
            this.solrDocumentId = solrDocID;
            this.artifactIDS = artifactIDS;
            this.hits = hits;
            this.statuses = statuses;
        }

        public long getObjID() {
            return objID;
        }

        public String getSolrDocmentID() {
            return solrDocumentId;
        }

        public List<Long> getArtifactIDS() {
            return artifactIDS;
        }

        public long getHits() {
            return hits;
        }

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
         *                       contain the content object and the account
         *                       artifacts.
         */
        @NbBundle.Messages({
            "# {0} - raw file name",
            "# {1} - solr chunk id",
            "Accounts.FileWithCCNNode.unallocatedSpaceFile.displayName={0}_chunk_{1}"})
        private FileWithCCNNode(FileWithCCN key, Content content, Object[] lookupContents) {
            super(Children.LEAF, Lookups.fixed(lookupContents));
            this.fileKey = key;
            this.fileName = (key.getSolrDocmentID() == null)
                    ? content.getName()
                    : Bundle.Accounts_FileWithCCNNode_unallocatedSpaceFile_displayName(content.getName(), StringUtils.substringAfter(key.getSolrDocmentID(), "_")); //NON-NLS
            setName(fileName);
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

        @NbBundle.Messages({
            "ApproveAccountsAction.name=Approve Accounts",
            "RejectAccountsAction.name=Reject Accounts"})
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
            arrayList.add(new ApproveAccounts(getLookup().lookupAll(BlackboardArtifact.class)));
            arrayList.add(new RejectAccounts(getLookup().lookupAll(BlackboardArtifact.class)));
            return arrayList.toArray(new Action[arrayList.size()]);
        }
    }

    final public class BINNode extends DisplayableItemNode implements Observer {

        /**
         * Creates the nodes for the accounts of a given type
         */
        final private class CreditCardNumberFactory extends ObservingChildFactory<Long> {

            private final BinResult bin;

            private CreditCardNumberFactory(BinResult bin) {
                this.bin = bin;
            }

            @Override
            protected boolean createKeys(List<Long> list) {

                String query
                        = "SELECT blackboard_artifacts.artifact_id " //NON-NLS
                        + " FROM blackboard_artifacts " //NON-NLS
                        + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                        + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID() //NON-NLS
                        + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
                        + "     AND blackboard_attributes.value_text >= \"" + bin.getBINStart() + "\" AND  blackboard_attributes.value_text < \"" + (bin.getBINEnd() + 1) + "\"" //NON-NLS
                        + getRejectedArtifactFilterClause()
                        + " ORDER BY blackboard_attributes.value_text"; //NON-NLS
                try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                        ResultSet rs = results.getResultSet();) {
                    while (rs.next()) {
                        list.add(rs.getLong("artifact_id")); //NON-NLS
                    }
                } catch (TskCoreException | SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS
                    return false;
                }
                return true;
            }

            @Override
            protected Node createNodeForKey(Long artifactID) {
                if (skCase == null) {
                    return null;
                }

                try {
                    BlackboardArtifact art = skCase.getBlackboardArtifact(artifactID);
                    return new AccountArtifactNode(art);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "Error creating BlackboardArtifactNode for artifact with ID " + artifactID, ex);   //NON-NLS
                    return null;
                }
            }
        }
        private final BinResult bin;
        private final CreditCardNumberFactory accountFactory;

        private BINNode(BinResult bin) {
            super(Children.LEAF);
            this.bin = bin;

            accountFactory = new CreditCardNumberFactory(bin);
            setChildren(Children.create(accountFactory, true));
            setName(bin.toString());
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/bank.png");   //NON-NLS
            Accounts.this.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        private void updateDisplayName() {
            setDisplayName(getBinRangeString() + " (" + bin.getCount() + ")"); //NON-NLS
        }

        private String getBinRangeString() {
            if (bin.getBINStart() == bin.getBINEnd()) {
                return Integer.toString(bin.getBINStart());
            } else {
                return bin.getBINStart() + "-" + StringUtils.difference(bin.getBINStart() + "", bin.getBINEnd() + "");
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
                    getBinRangeString()));
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
    }

    /**
     * Data model item to back the BINNodes in the tree. Has the number of
     * accounts found with the BIN.
     */
    @Immutable
    final static private class BinResult implements CreditCards.BankIdentificationNumber {

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
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.addAll(Arrays.asList(super.getActions(context)));
            actionsList.add(new ApproveAccounts(Collections.singleton(artifact)));
            actionsList.add(new RejectAccounts(Collections.singleton(artifact)));
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
    }

    private final class ToggleShowRejected extends AbstractAction {

        @NbBundle.Messages("ToggleShowRejected.name=Show Rejcted Results")
        ToggleShowRejected() {
            super(Bundle.ToggleShowRejected_name());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showRejected = !showRejected;
            update();
        }
    }

    final private class ApproveAccounts extends AbstractAction {

        private final Collection<? extends BlackboardArtifact> artifacts;

        ApproveAccounts(Collection<? extends BlackboardArtifact> artifacts) {
            super(Bundle.ApproveAccountsAction_name());
            this.artifacts = artifacts;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                for (BlackboardArtifact artifact : artifacts) {
                    skCase.setReviewStatus(artifact, BlackboardArtifact.ReviewStatus.APPROVED);
                }
                Accounts.this.update();
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error approving artifacts.", ex); //NON-NLS
            }
        }
    }

    final private class RejectAccounts extends AbstractAction {

        private final Collection<? extends BlackboardArtifact> artifacts;

        RejectAccounts(Collection<? extends BlackboardArtifact> artifacts) {
            super(Bundle.RejectAccountsAction_name());
            this.artifacts = artifacts;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                for (BlackboardArtifact artifact : artifacts) {
                    skCase.setReviewStatus(artifact, BlackboardArtifact.ReviewStatus.REJECTED);
                }
                Accounts.this.update();

            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error approving artifacts.", ex); //NON-NLS
            }
        }
    }
}
