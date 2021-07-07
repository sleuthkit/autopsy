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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.OsAccountsUpdatedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.backgroundTasksPool;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Implements the OS Accounts subnode of Results in the Autopsy tree.
 */
public final class OsAccounts implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(OsAccounts.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/os-account.png";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final String REALM_DATA_AVAILABLE_EVENT = "REALM_DATA_AVAILABLE_EVENT";

    private SleuthkitCase skCase;
    private final long filteringDSObjId;

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
            setName(Bundle.OsAccount_listNode_name());
            setDisplayName(Bundle.OsAccount_listNode_name());
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
                        Host host = skCase.getHostManager().getHostByDataSource(skCase.getDataSource(filteringDSObjId));
                        list.addAll(skCase.getOsAccountManager().getOsAccounts(host));
                    }
                } catch (TskCoreException | TskDataException ex) {
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
                } else if (evt.getPropertyName().equals(REALM_DATA_AVAILABLE_EVENT)) {
                    OsAccountRealm realm = (OsAccountRealm) evt.getNewValue();

                    // Currently only 0 or 1 names are supported, this will need
                    // to be modified if that changes.
                    List<String> realmNames = realm.getRealmNames();
                    if (!realmNames.isEmpty()) {
                        updateSheet(new NodeProperty<>(
                                Bundle.OsAccounts_accountRealmNameProperty_name(),
                                Bundle.OsAccounts_accountRealmNameProperty_displayName(),
                                Bundle.OsAccounts_accountRealmNameProperty_desc(),
                                ""));
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

        @Messages({
            "OsAccounts_accountNameProperty_name=Name",
            "OsAccounts_accountNameProperty_displayName=Name",
            "OsAccounts_accountNameProperty_desc=Os Account name",
            "OsAccounts_accountRealmNameProperty_name=RealmName",
            "OsAccounts_accountRealmNameProperty_displayName=Realm Name",
            "OsAccounts_accountRealmNameProperty_desc=OS Account Realm Name",
            "OsAccounts_createdTimeProperty_name=creationTime",
            "OsAccounts_createdTimeProperty_displayName=Creation Time",
            "OsAccounts_createdTimeProperty_desc=OS Account Creation Time",
            "OsAccounts_loginNameProperty_name=loginName",
            "OsAccounts_loginNameProperty_displayName=Login Name",
            "OsAccounts_loginNameProperty_desc=Os Account login name"
        })

        /**
         * Refreshes this node's property sheet.
         */
        void updateSheet() {
            this.setSheet(createSheet());
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

            Optional<String> optional = account.getLoginName();
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_loginNameProperty_name(),
                    Bundle.OsAccounts_loginNameProperty_displayName(),
                    Bundle.OsAccounts_loginNameProperty_desc(),
                    optional.isPresent() ? optional.get() : ""));
            // Fill with empty string, fetch on background task.
            String realmName = "";
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountRealmNameProperty_name(),
                    Bundle.OsAccounts_accountRealmNameProperty_displayName(),
                    Bundle.OsAccounts_accountRealmNameProperty_desc(),
                    realmName));

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

        @Override
        public Action[] getActions(boolean popup) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.addAll(Arrays.asList(super.getActions(popup)));
            actionsList.addAll(DataModelActionsFactory.getActions(account));

            return actionsList.toArray(new Action[actionsList.size()]);
        }

        @Override
        protected List<Tag> getAllTagsFromDatabase() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected CorrelationAttributeInstance getCorrelationAttributeInstance() {
            return null;
        }

        @Override
        protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, CorrelationAttributeInstance attribute) {
            return DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        }

        @Override
        protected Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance.Type attributeType, String attributeValue, String defaultDescription) {
            return null;
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
                    long realmId = node.getOsAccount().getRealmId();
                    OsAccountRealm realm = Case.getCurrentCase().getSleuthkitCase().getOsAccountRealmManager().getRealmByRealmId(realmId);

                    if (listener != null && realm != null) {
                        listener.propertyChange(new PropertyChangeEvent(
                                AutopsyEvent.SourceType.LOCAL.toString(),
                                REALM_DATA_AVAILABLE_EVENT,
                                null, realm));
                    }

                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}
