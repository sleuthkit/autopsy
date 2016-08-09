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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CREDIT_CARD_ACCOUNT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * AutopsyVisitableItem for the Accounts section of the tree. All nodes and
 * factories are inner classes.
 */
public class Accounts extends Observable implements AutopsyVisitableItem {

    private static final Logger LOGGER = Logger.getLogger(Accounts.class.getName());
    private static final BlackboardArtifact.Type CREDIT_CARD_ACCOUNT_TYPE = new BlackboardArtifact.Type(TSK_CREDIT_CARD_ACCOUNT);

    private SleuthkitCase skCase;

    private void update() {
        setChanged();
        notifyObservers();
    }

    Accounts(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
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
            deleteObserver(this);
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            addObserver(this);
        }
    }

    /**
     * Top-level node for the accounts tree
     */
    @NbBundle.Messages({"Accounts.RootNode.displayName=Accounts"})
    public class AccountsRootNode extends DisplayableItemNode {

        AccountsRootNode() {
            super(Children.create(new AccountTypeFactory(), true));
            super.setName("Accounts");    //NON-NLS
            super.setDisplayName(Bundle.Accounts_RootNode_displayName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/account_menu.png");    //NON-NLS
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
     * Creates child nodes for each account type (currently hard coded to make
     * one for Credit Cards)
     */
    private class AccountTypeFactory extends ObservingChildFactory<String> {

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
                        Case.getCurrentCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && CREDIT_CARD_ACCOUNT_TYPE.equals(eventData.getBlackboardArtifactType())) {
                            Accounts.this.update();
                        }
                    } catch (IllegalStateException notUsed) {
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
        @NbBundle.Messages({"Accounts.AccountTypeFactory.accountType.creditCards=Credit Card Numbers"})
        protected boolean createKeys(List<String> list) {
            list.add(Bundle.Accounts_AccountTypeFactory_accountType_creditCards());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new AccountTypeNode(key);
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

    /**
     * Node for an account type.
     *
     * NOTE: currently hard coded to work for Credit Card only
     */
    public class AccountTypeNode extends DisplayableItemNode {

        private AccountTypeNode(String accountTypeName) {
            super(Children.create(new ViewModeFactory(), true));
            super.setName(accountTypeName);
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
     * Enum for the children under the credit card AccountTypeNode.
     */
    static private enum CreditCardViewMode {
        BY_FILE,
        BY_BIN;
    }

    /**
     * ChildFactory that makes nodes for the different account organizations (by
     * file, by BIN)
     */
    private class ViewModeFactory extends ObservingChildFactory<CreditCardViewMode> {

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

    /**
     * Node that is the root of the "by file" accounts tree. Its children are
     * FileWithCCNNodes.
     */
    public class ByFileNode extends DisplayableItemNode implements Observer {

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
    public class ByBINNode extends DisplayableItemNode implements Observer {

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
            ArrayList<BINInfo> keys = new ArrayList<>();
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
    private static class FileWithCCN {

        private final long objID;
        private final String solrDocumentId;
        private final List<Long> artifactIDS;
        private final long hits;
        private final String status;

        private FileWithCCN(long objID, String solrDocID, List<Long> artifactIDS, long hits, String status) {
            this.objID = objID;
            this.solrDocumentId = solrDocID;
            this.artifactIDS = artifactIDS;
            this.hits = hits;
            this.status = status;
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

        public String getStatus() {
            return status;
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
                : Stream.of(groupConcat.split(","))
                .map(mapper::apply)
                .collect(Collectors.toList());

    }

    /**
     * Factory for the children of the ByFiles Node.
     */
    private class FileWithCCNFactory extends ObservingChildFactory<FileWithCCN> {

        @Override
        protected boolean createKeys(List<FileWithCCN> list) {
            String query =
                    "SELECT blackboard_artifacts.obj_id," //NON-NLS
                    + "      blackboard_attributes.value_text AS solr_document_id, " //NON-NLS
                    + "      GROUP_CONCAT(blackboard_artifacts.artifact_id) AS artifact_IDs, " //NON-NLS
                    + "      COUNT( blackboard_artifacts.artifact_id) AS hits " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + " LEFT JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                    + "                                AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SOLR_DOCUMENT_ID.getTypeID() //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_CREDIT_CARD_ACCOUNT.getTypeID() //NON-NLS
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
                            "unreviewed"));  //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for files with ccn hits.", ex); //NON-NLS
                return false;
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileWithCCN key) {
            //add all account artifacts for the file and the file itself to th elookup
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

    /**
     * Node that represents a file or chunk of an unallocated space file.
     */
    public class FileWithCCNNode extends DisplayableItemNode {

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
                    : Bundle.Accounts_FileWithCCNNode_unallocatedSpaceFile_displayName(content.getName(), StringUtils.substringAfter(key.getSolrDocmentID(), "_"));
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
                    fileKey.getStatus()));

            return s;
        }
    }

    /**
     * Node that represents a BIN (Bank Identification Number)
     */
    public class BINNode extends DisplayableItemNode implements Observer {

        private final BINInfo bin;
        private final AccountFactory accountFactory;

        private BINNode(BINInfo bin) {
            super(Children.LEAF);
            this.bin = bin;
            accountFactory = new AccountFactory(bin);
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
            ArrayList<Long> keys = new ArrayList<>();
            accountFactory.createKeys(keys);
            setDisplayName(bin.getBIN().toString() + " (" + keys.size() + ")");
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
            "Accounts.BINNode.binProperty.displayName=Bank Identifier Number",
            "Accounts.BINNode.accountsProperty.displayName=Accounts",
            "Accounts.BINNode.noDescription=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.Accounts_BINNode_binProperty_displayName(),
                    Bundle.Accounts_BINNode_binProperty_displayName(),
                    Bundle.Accounts_BINNode_noDescription(),
                    bin.getBIN()));
            ss.put(new NodeProperty<>(Bundle.Accounts_BINNode_accountsProperty_displayName(),
                    Bundle.Accounts_BINNode_accountsProperty_displayName(), Bundle.Accounts_BINNode_noDescription(),
                    bin.getCount()));

            return s;
        }
    }

    /**
     * Factory that generates the children of the ByBin node.
     */
    private class BINFactory extends ObservingChildFactory<BINInfo> {

        @Override
        protected boolean createKeys(List<BINInfo> list) {
            String query =
                    "SELECT SUBSTR(blackboard_attributes.value_text,1,6) AS BIN, " //NON-NLS
                    + "     COUNT(blackboard_artifacts.artifact_id) AS count " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_CREDIT_CARD_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_NUMBER.getTypeID() //NON-NLS
                    + " GROUP BY BIN " //NON-NLS
                    + " ORDER BY BIN "; //NON-NLS
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query)) {
                ResultSet resultSet = results.getResultSet();
                while (resultSet.next()) {
                    list.add(new BINInfo(Integer.valueOf(resultSet.getString("BIN")), //NON-NLS
                            resultSet.getLong("count"))); //NON-NLS
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying for BINs.", ex); //NON-NLS
                return false;
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(BINInfo key) {
            return new BINNode(key);
        }
    }

    /**
     * Data model item to back the BINNodes in the tree. Has basic info about a
     * BIN.
     */
    private class BINInfo {

        private final Integer bin;
        /**
         * The number of accounts with this BIN
         */
        private final Long count;

        private BINInfo(Integer bin, Long count) {
            this.bin = bin;
            this.count = count;
        }

        public Integer getBIN() {
            return bin;
        }

        public Long getCount() {
            return count;
        }
    }

    /**
     * Creates the nodes for the accounts of a given type
     */
    private class AccountFactory extends ObservingChildFactory<Long> {

        private final BINInfo bin;

        private AccountFactory(BINInfo bin) {
            this.bin = bin;
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            String query =
                    "SELECT blackboard_artifacts.artifact_id " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_CREDIT_CARD_ACCOUNT.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_NUMBER.getTypeID() //NON-NLS
                    + "     AND blackboard_attributes.value_text LIKE \"" + bin.getBIN() + "%\" " //NON-NLS
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
                return new BlackboardArtifactNode(art, "org/sleuthkit/autopsy/images/credit-card.png");   //NON-NLS 
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error creating BlackboardArtifactNode for artifact with ID " + artifactID, ex);   //NON-NLS
                return null;
            }
        }
    }
}
