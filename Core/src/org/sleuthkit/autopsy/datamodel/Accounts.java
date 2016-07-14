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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
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
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Node support for accounts. Inner classes have all of the nodes in the tree.
 *
 */
public class Accounts implements AutopsyVisitableItem {

    private static final String ACCOUNT = BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getDisplayName();
    private static final Logger LOGGER = Logger.getLogger(HashsetHits.class.getName());
    private final SleuthkitCase skCase;

    public Accounts(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Top-level node for all accounts
     */
    @NbBundle.Messages({"Accounts.RootNode.displayName=Accounts"})
    public class AccountsNode extends DisplayableItemNode {

        AccountsNode() {
            super(Children.create(new AccountTypeFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(ACCOUNT);
            super.setDisplayName(Bundle.Accounts_RootNode_displayName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/account_menu.png"); //NON-NLS
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
        @NbBundle.Messages({"Accounts.createSheet.name=name",
            "Accounts.createSheet.displayName=name",
            "Accounts.createSheet.desc=mo description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.Accounts_createSheet_name(),
                    Bundle.Accounts_createSheet_displayName(),
                    Bundle.Accounts_createSheet_desc(),
                    getName()));

            return s;
        }
    }

    /**
     * Creates child nodes for each account type (currently hard coded to make
     * one for Credit Cards)
     */
    private class AccountTypeFactory extends ChildFactory.Detachable<String> {

        @Override
        protected boolean createKeys(List<String> list) {
            list.add("Credit Card Numbers");
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new AccountTypeNode(key);
        }
    }

    /**
     * Node for an account type, TODO: currently hard coded to work for Credit
     * Card only
     */
    public class AccountTypeNode extends DisplayableItemNode {

        private final String accountTypeName;

        private AccountTypeNode(String accountTypeName) {
            super(Children.create(new ViewModeFactory(), true), Lookups.singleton(accountTypeName));
            super.setName(accountTypeName);
            this.accountTypeName = accountTypeName;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/credit-cards.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.Accounts_createSheet_name(),
                    Bundle.Accounts_createSheet_displayName(),
                    Bundle.Accounts_createSheet_desc(),
                    getName()));

            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
    }

    enum CreditCardViewMode {
        BY_FILE,
        BY_ARTIFACT;
    }

    private class ViewModeFactory extends ChildFactory<CreditCardViewMode> {

        @Override
        protected boolean createKeys(List<CreditCardViewMode> list) {
            list.add(CreditCardViewMode.BY_FILE);
            list.add(CreditCardViewMode.BY_ARTIFACT);
            return true;
        }

        @Override
        protected Node createNodeForKey(CreditCardViewMode key) {
            switch (key) {
                case BY_ARTIFACT:
                    return new ByArtifactNode();
                case BY_FILE:
                    return new ByFileNode();
                default:
                    return null;
            }
        }

    }

    class ByFileNode extends DisplayableItemNode {

        private ByFileNode() {
            super(Children.create(new FileFactory(), true));
            setName("By File");
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png"); //NON-NLS
        }

        /**
         * TODO: Update the count in the display name
         */
        private void updateDisplayName() {
            setDisplayName("By File");
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

    class ByArtifactNode extends DisplayableItemNode {

        private ByArtifactNode() {
            super(Children.create(new AccountFactory(), true));
            setName("By Account");

            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/credit-card.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        /**
         * TODO: Update the count in the display name
         */
        private void updateDisplayName() {
            setDisplayName("By Account");
        }

    }

    private static class FileWithCCN {

        final long objID;
        final long chunkID;
        final List<Long> artifactIDS;
        final long hits;
        final long accepted;
        private final String status;

        FileWithCCN(long objID, long chunkID, List<Long> artifactIDS, long hits, long accepted, String status) {

            this.objID = objID;
            this.chunkID = chunkID;
            this.artifactIDS = artifactIDS;
            this.hits = hits;
            this.accepted = accepted;
            this.status = status;
        }

        public long getObjID() {
            return objID;
        }

        public long getChunkID() {
            return chunkID;
        }

        public List<Long> getArtifactIDS() {
            return artifactIDS;
        }

        public long getHits() {
            return hits;
        }

        public long getAccepted() {
            return accepted;
        }

        public String getStatus() {
            return status;
        }
    }

    /**
     * TODO: this was copy-pasted from timeline. Is there a single accesible
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

    private class FileFactory extends ChildFactory.Detachable<FileWithCCN> {

        @Override
        protected boolean createKeys(List<FileWithCCN> list) {
            String query =
                    "select blackboard_artifacts.obj_id as obj_id,"
                    + "	    blackboard_attributes.value_int32 as chunk_id,"
                    + "     group_concat(blackboard_artifacts.artifact_id),"
                    + "     count(blackboard_artifacts.artifact_id) as hits "
                    //                    + "     count (case when blackboard_artifacts.status like \"accepted\" then 1 else Null end) as accepted"
                    + " from t blackboard_artifacts, "
                    + "     blackboard_attributes "
                    + " where tsk_files.obj_id = blackboard_artifacts.obj_id "
                    + "     and blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()
                    //                    + "     and not (blackboard_artifacts.status like  \"rejected\")  "
                    + "     and blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id "
                    + "     and blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CHUNK_ID.getTypeID()
                    + " group by blackboard_artifacts.obj_id, chunk_id"
                    + " order by hits desc";//, accepted desc";
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
                    ResultSet rs = results.getResultSet();) {
                while (rs.next()) {
                    list.add(new FileWithCCN(
                            rs.getLong("obj_id"),
                            rs.getLong("chunk_id"),
                            unGroupConcat(rs.getString("group_concat(blackboard_artifacts.artifact_id)"), Long::valueOf),
                            rs.getLong("hits"),
                            0,
                            "unreviewed"));

                }
            } catch (TskCoreException | SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileWithCCN key) {
            try {
                return new FileWithCCNNode(key, skCase.getAbstractFileById(key.getObjID()));
            } catch (TskCoreException ex) {
                return null;
            }
        }
    }

    class FileWithCCNNode extends DisplayableItemNode {

        private final FileWithCCN key;
        private final Content content;

        FileWithCCNNode(FileWithCCN key, Content content) {
            super(Children.LEAF, Lookups.singleton(content));

            setName(content.getName() + "_" + key.getChunkID());
            this.key = key;
            this.content = content;
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
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>("File Name", "File Name", "no description", content.getName() + "_" + key.getChunkID()));
            ss.put(new NodeProperty<>("Hits", "Hits", "no description", key.getHits()));
            ss.put(new NodeProperty<>("Accepted", "Accepted", "no description", key.getAccepted()));
            ss.put(new NodeProperty<>("Status", "Status", "no description", key.getStatus()));

            return s;
        }
    }

    /**
     * Creates the nodes for the accounts of a given type
     */
    private class AccountFactory extends ChildFactory.Detachable<Long> {

        @Override
        protected boolean createKeys(List<Long> list) {
            String query = "select artifact_id from blackboard_artifacts where artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID();
            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query)) {
                ResultSet rs = results.getResultSet();
                while (rs.next()) {
                    list.add(rs.getLong("artifact_id"));
                }
            } catch (TskCoreException | SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Long id) {
            if (skCase == null) {
                return null;
            }

            try {
                BlackboardArtifact art = skCase.getBlackboardArtifact(id);
                return new BlackboardArtifactNode(art);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "TSK Exception occurred", ex); //NON-NLS
            }
            return null;
        }
    }
}
