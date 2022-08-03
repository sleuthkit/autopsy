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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.Action;
import org.apache.commons.lang3.tuple.Pair;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.OsAccountsUpdatedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.NO_DESCR;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.VALUE_LOADING;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.backgroundTasksPool;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Implements the OS Accounts subnode of Results in the Autopsy tree.
 */
public final class OsAccounts implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(OsAccounts.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/os-account.png";
    private static final String OS_ACCOUNT_DATA_AVAILABLE_EVENT = "OS_ACCOUNT_DATA_AVAILABLE_EVENT";

    private static final String LIST_NAME = Bundle.OsAccount_listNode_name();

    private SleuthkitCase skCase;
    private final long filteringDSObjId;

    /**
     * Returns the name of the OsAccountListNode to be used for id purposes.
     *
     * @return The name of the OsAccountListNode to be used for id purposes.
     */
    public static String getListName() {
        return LIST_NAME;
    }

    public OsAccounts(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    public OsAccounts(SleuthkitCase skCase, long objId) {
        this.skCase = skCase;
        this.filteringDSObjId = objId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Messages({
        "OsAccount_listNode_name=OS Accounts"
    })
    /**
     * The root node of the OS Accounts subtree.
     */
    public final class OsAccountListNode extends DisplayableItemNode {

        /**
         * Construct a new OsAccountListNode.
         */
        public OsAccountListNode() {
            super(Children.create(new OsAccountNodeFactory(), true));
            setName(LIST_NAME);
            setDisplayName(LIST_NAME);
            setIconBaseWithExtension("org/sleuthkit/autopsy/images/os-account.png");
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * The child node factory that creates the OsAccountNode children for a
     * OsAccountListNode.
     */
    private final class OsAccountNodeFactory extends ChildFactory.Detachable<OsAccount> {

        private final PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.OS_ACCOUNTS_ADDED.toString())
                        || eventType.equals(Case.Events.OS_ACCOUNTS_DELETED.toString())) {
                    refresh(true);
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(listener, null);

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            Case.removeEventTypeSubscriber(Collections.singleton(Case.Events.OS_ACCOUNTS_ADDED), weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
        }

        @Override
        protected void addNotify() {
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.OS_ACCOUNTS_ADDED, Case.Events.OS_ACCOUNTS_DELETED), listener);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), listener);
        }

        @Override
        protected boolean createKeys(List<OsAccount> list) {
            if (skCase != null) {
                try {
                    if (filteringDSObjId == 0) {
                        list.addAll(skCase.getOsAccountManager().getOsAccounts());
                    } else {
                        list.addAll(skCase.getOsAccountManager().getOsAccountsByDataSourceObjId(filteringDSObjId));
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Unable to retrieve list of OsAccounts for case", ex);
                    return false;
                }
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(OsAccount key) {
            return new OsAccountNode(key);
        }
    }

    /**
     * An OsAccount leaf Node.
     */
    public static final class OsAccountNode extends AbstractContentNode<OsAccount> {

        private OsAccount account;

        @Messages({
            "OsAccounts_accountNameProperty_name=Name",
            "OsAccounts_accountNameProperty_displayName=Name",
            "OsAccounts_accountNameProperty_desc=Os Account name",
            "OsAccounts_accountRealmNameProperty_name=RealmName",
            "OsAccounts_accountRealmNameProperty_displayName=Realm Name",
            "OsAccounts_accountRealmNameProperty_desc=OS Account Realm Name",
            "OsAccounts_accountHostNameProperty_name=HostName",
            "OsAccounts_accountHostNameProperty_displayName=Host",
            "OsAccounts_accountHostNameProperty_desc=OS Account Host Name",
            "OsAccounts_accountScopeNameProperty_name=ScopeName",
            "OsAccounts_accountScopeNameProperty_displayName=Scope",
            "OsAccounts_accountScopeNameProperty_desc=OS Account Scope Name",
            "OsAccounts_createdTimeProperty_name=creationTime",
            "OsAccounts_createdTimeProperty_displayName=Creation Time",
            "OsAccounts_createdTimeProperty_desc=OS Account Creation Time",
            "OsAccounts_loginNameProperty_name=loginName",
            "OsAccounts_loginNameProperty_displayName=Login Name",
            "OsAccounts_loginNameProperty_desc=OS Account login name",
            "OsAccounts.createSheet.score.name=S",
            "OsAccounts.createSheet.score.displayName=S",
            "OsAccounts.createSheet.count.name=O",
            "OsAccounts.createSheet.count.displayName=O",
            "OsAccounts.createSheet.comment.name=C",
            "OsAccounts.createSheet.comment.displayName=C"
        })
        private final PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(Case.Events.OS_ACCOUNTS_UPDATED.name())) {
                    OsAccountsUpdatedEvent updateEvent = (OsAccountsUpdatedEvent) evt;
                    for (OsAccount acct : updateEvent.getOsAccounts()) {
                        if (acct.getId() == account.getId()) {
                            account = acct;
                            updateSheet();
                            break;
                        }
                    }
                } else if (evt.getPropertyName().equals(OS_ACCOUNT_DATA_AVAILABLE_EVENT)
                        && evt.getNewValue() instanceof AsynchOsAcctData
                        && ((AsynchOsAcctData) evt.getNewValue()).getOsAccountId() == account.getId()) {

                    List<NodeProperty<?>> propertiesToUpdate = new ArrayList<>();

                    AsynchOsAcctData osAcctData = (AsynchOsAcctData) evt.getNewValue();

                    List<String> realmNames = osAcctData.getOsAcctRealm().getRealmNames();
                    if (!realmNames.isEmpty()) {
                        String realmNamesStr = realmNames.stream()
                                .map(String::trim)
                                .distinct()
                                .sorted((a, b) -> a.compareToIgnoreCase(b))
                                .collect(Collectors.joining(", "));

                        propertiesToUpdate.add(new NodeProperty<>(
                                Bundle.OsAccounts_accountRealmNameProperty_name(),
                                Bundle.OsAccounts_accountRealmNameProperty_displayName(),
                                Bundle.OsAccounts_accountRealmNameProperty_desc(),
                                realmNamesStr));
                    }

                    String scopeName = osAcctData.getOsAcctRealm().getScope().getName();
                    if (StringUtils.isNotBlank(scopeName)) {
                        propertiesToUpdate.add(new NodeProperty<>(
                                Bundle.OsAccounts_accountScopeNameProperty_name(),
                                Bundle.OsAccounts_accountScopeNameProperty_displayName(),
                                Bundle.OsAccounts_accountScopeNameProperty_desc(),
                                scopeName));
                    }

                    List<Host> hosts = osAcctData.getHosts();
                    if (!hosts.isEmpty()) {
                        String hostsString = hosts.stream()
                                .map(h -> h.getName().trim())
                                .distinct()
                                .sorted((a, b) -> a.compareToIgnoreCase(b))
                                .collect(Collectors.joining(", "));

                        propertiesToUpdate.add(new NodeProperty<>(
                                Bundle.OsAccounts_accountHostNameProperty_name(),
                                Bundle.OsAccounts_accountHostNameProperty_displayName(),
                                Bundle.OsAccounts_accountHostNameProperty_desc(),
                                hostsString));
                    }
                    updateSheet(propertiesToUpdate.toArray(new NodeProperty<?>[propertiesToUpdate.size()]));
                } else if (evt.getPropertyName().equals(NodeSpecificEvents.SCO_AVAILABLE.toString()) && !UserPreferences.getHideSCOColumns()) {
                    SCOData scoData = (SCOData) evt.getNewValue();
                    if (scoData.getScoreAndDescription() != null) {
                        updateSheet(new NodeProperty<>(
                                Bundle.OsAccounts_createSheet_score_name(),
                                Bundle.OsAccounts_createSheet_score_displayName(),
                                scoData.getScoreAndDescription().getRight(),
                                scoData.getScoreAndDescription().getLeft()));
                    }
                    if (scoData.getComment() != null) {
                        updateSheet(new NodeProperty<>(
                                Bundle.OsAccounts_createSheet_comment_name(),
                                Bundle.OsAccounts_createSheet_comment_displayName(),
                                NO_DESCR, scoData.getComment()));
                    }
                    if (scoData.getCountAndDescription() != null) {
                        updateSheet(new NodeProperty<>(
                                Bundle.OsAccounts_createSheet_count_name(),
                                Bundle.OsAccounts_createSheet_count_displayName(),
                                scoData.getCountAndDescription().getRight(),
                                scoData.getCountAndDescription().getLeft()));
                    }
                }
            }
        };

        private final PropertyChangeListener weakListener = WeakListeners.propertyChange(listener, null);

        /**
         * Constructs a new OsAccountNode.
         *
         * @param account Node object.
         */
        OsAccountNode(OsAccount account) {
            super(account);
            this.account = account;

            setName(account.getName());
            setDisplayName(account.getName());
            setIconBaseWithExtension(ICON_PATH);

            Case.addEventTypeSubscriber(Collections.singleton(Case.Events.OS_ACCOUNTS_UPDATED), weakListener);
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        /**
         * Returns the OsAccount associated with this node.
         *
         * @return
         */
        OsAccount getOsAccount() {
            return account;
        }

        /**
         * Refreshes this node's property sheet.
         */
        void updateSheet() {
            SwingUtilities.invokeLater(() -> {
                this.setSheet(createSheet());
            });
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set propertiesSet = sheet.get(Sheet.PROPERTIES);
            if (propertiesSet == null) {
                propertiesSet = Sheet.createPropertiesSet();
                sheet.put(propertiesSet);
            }
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountNameProperty_name(),
                    Bundle.OsAccounts_accountNameProperty_displayName(),
                    Bundle.OsAccounts_accountNameProperty_desc(),
                    account.getName() != null ? account.getName() : ""));
            addSCOColumns(propertiesSet);
            Optional<String> optional = account.getLoginName();
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_loginNameProperty_name(),
                    Bundle.OsAccounts_loginNameProperty_displayName(),
                    Bundle.OsAccounts_loginNameProperty_desc(),
                    optional.isPresent() ? optional.get() : ""));

            // Fill with empty string, fetch on background task.
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountHostNameProperty_name(),
                    Bundle.OsAccounts_accountHostNameProperty_displayName(),
                    Bundle.OsAccounts_accountHostNameProperty_desc(),
                    ""));

            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountScopeNameProperty_name(),
                    Bundle.OsAccounts_accountScopeNameProperty_displayName(),
                    Bundle.OsAccounts_accountScopeNameProperty_desc(),
                    ""));

            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountRealmNameProperty_name(),
                    Bundle.OsAccounts_accountRealmNameProperty_displayName(),
                    Bundle.OsAccounts_accountRealmNameProperty_desc(),
                    ""));

            Optional<Long> creationTimeValue = account.getCreationTime();
            String timeDisplayStr
                    = creationTimeValue.isPresent() ? TimeZoneUtils.getFormattedTime(creationTimeValue.get()) : "";

            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_createdTimeProperty_name(),
                    Bundle.OsAccounts_createdTimeProperty_displayName(),
                    Bundle.OsAccounts_createdTimeProperty_desc(),
                    timeDisplayStr));

            backgroundTasksPool.submit(new GetOsAccountRealmTask(new WeakReference<>(this), weakListener));
            return sheet;
        }

        private void addSCOColumns(Sheet.Set sheetSet) {
            if (!UserPreferences.getHideSCOColumns()) {
                /*
                 * Add S(core), C(omments), and O(ther occurences) columns to
                 * the sheet and start a background task to compute the value of
                 * these properties for the artifact represented by this node.
                 * The task will fire a PropertyChangeEvent when the computation
                 * is completed and this node's PropertyChangeListener will
                 * update the sheet.
                 */
                sheetSet.put(new NodeProperty<>(
                        Bundle.OsAccounts_createSheet_score_name(),
                        Bundle.OsAccounts_createSheet_score_displayName(),
                        VALUE_LOADING,
                        ""));
                sheetSet.put(new NodeProperty<>(
                        Bundle.OsAccounts_createSheet_comment_name(),
                        Bundle.OsAccounts_createSheet_comment_displayName(),
                        VALUE_LOADING,
                        ""));
                if (CentralRepository.isEnabled()) {
                    sheetSet.put(new NodeProperty<>(
                            Bundle.OsAccounts_createSheet_count_name(),
                            Bundle.OsAccounts_createSheet_count_displayName(),
                            VALUE_LOADING,
                            ""));
                }
                backgroundTasksPool.submit(new GetSCOTask(new WeakReference<>(this), weakListener));
            }
        }

        @Override
        public Action[] getActions(boolean popup) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.addAll(DataModelActionsFactory.getActions(account));
            actionsList.add(null);
            actionsList.addAll(Arrays.asList(super.getActions(popup)));
            return actionsList.toArray(new Action[actionsList.size()]);
        }

        @Override
        protected List<Tag> getAllTagsFromDatabase() {
            return new ArrayList<>();
        }

        @Override
        public <T> T accept(ContentNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        /**
         * Task for grabbing the osAccount realm.
         */
        static class GetOsAccountRealmTask implements Runnable {

            private final WeakReference<OsAccountNode> weakNodeRef;
            private final PropertyChangeListener listener;

            /**
             * Construct a new task.
             *
             * @param weakContentRef
             * @param listener
             */
            GetOsAccountRealmTask(WeakReference<OsAccountNode> weakContentRef, PropertyChangeListener listener) {
                this.weakNodeRef = weakContentRef;
                this.listener = listener;
            }

            @Override
            public void run() {
                OsAccountNode node = weakNodeRef.get();
                if (node == null) {
                    return;
                }

                try {
                    SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
                    OsAccount osAcct = node.getOsAccount();
                    long realmId = osAcct.getRealmId();
                    OsAccountRealm realm = skCase.getOsAccountRealmManager().getRealmByRealmId(realmId);

                    List<Host> hosts = skCase.getOsAccountManager().getHosts(osAcct);

                    AsynchOsAcctData evtData = new AsynchOsAcctData(osAcct.getId(), realm, hosts);

                    if (listener != null && realm != null) {
                        listener.propertyChange(new PropertyChangeEvent(
                                AutopsyEvent.SourceType.LOCAL.toString(),
                                OS_ACCOUNT_DATA_AVAILABLE_EVENT,
                                null, evtData));
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error occurred getting realm information for Os Account Node from case db, for account: " + node.getOsAccount().getName(), ex);
                }
            }
        }

        @NbBundle.Messages({
            "OsAccounts.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this file when the column was populated",
            "# {0} - occurrenceCount",
            "OsAccounts.createSheet.count.description=There were {0} datasource(s) found with occurrences of the OS Account correlation value"})
        @Override

        protected Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attributeInstance, String defaultDescription) {
            Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
            String description = defaultDescription;
            try {
                //don't perform the query if there is no correlation value
                if (attributeInstance != null && StringUtils.isNotBlank(attributeInstance.getCorrelationValue())) {
                    count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attributeInstance);
                    description = Bundle.OsAccounts_createSheet_count_description(count);
                } else if (attributeInstance != null) {
                    description = Bundle.OsAccounts_createSheet_count_hashLookupNotRun_description();
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, String.format("Error getting count of data sources with %s correlation attribute %s", attributeInstance.getCorrelationType().getDisplayName(), attributeInstance.getCorrelationValue()), ex);
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.WARNING, String.format("Unable to normalize %s correlation attribute %s", attributeInstance.getCorrelationType().getDisplayName(), attributeInstance.getCorrelationValue()), ex);
            }
            return Pair.of(count, description);
        }

        /**
         * Returns comment property for the node.
         *
         * @param tags       The list of tags.
         * @param attributes The list of correlation attribute instances.
         *
         * @return Comment property for the underlying content of the node.
         */
        @Override
        protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
            /*
             * Has a tag with a comment been applied to the OsAccount or its
             * source content?
             */
            DataResultViewerTable.HasCommentStatus status = tags.size() > 0 ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;
            for (Tag tag : tags) {
                if (!StringUtils.isBlank(tag.getComment())) {
                    status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                    break;
                }
            }
            /*
             * Is there a comment in the CR for anything that matches the value
             * and type of the specified attributes.
             */
            try {
                if (CentralRepoDbUtil.commentExistsOnAttributes(attributes)) {
                    if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                        status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
                    } else {
                        status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
                    }
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Attempted to Query CR for presence of comments in an OS Account node and was unable to perform query, comment column will only reflect caseDB", ex);
            }
            return status;
        }

        /**
         * Data concerning an OS Account loaded asynchronously (and not at sheet
         * creation).
         */
        private static class AsynchOsAcctData {

            private final long osAccountId;
            private final OsAccountRealm osAcctRealm;
            private final List<Host> hosts;

            /**
             * Main constructor.
             *
             * @param osAccountId The id of the os account.
             * @param osAcctRealm The realm of the os account.
             * @param hosts       The hosts that the os account belongs to.
             */
            AsynchOsAcctData(long osAccountId, OsAccountRealm osAcctRealm, List<Host> hosts) {
                this.osAccountId = osAccountId;
                this.osAcctRealm = osAcctRealm;
                this.hosts = hosts;
            }

            /**
             * @return The id of the os account.
             */
            long getOsAccountId() {
                return osAccountId;
            }

            /**
             * @return The realm of the os account.
             */
            OsAccountRealm getOsAcctRealm() {
                return osAcctRealm;
            }

            /**
             * @return The hosts that the os account belongs to.
             */
            List<Host> getHosts() {
                return hosts;
            }

        }
    }
}
