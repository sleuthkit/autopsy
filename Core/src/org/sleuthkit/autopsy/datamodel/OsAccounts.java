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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.OsAccountChangedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Implements the OS Accounts subnode of Results in the Autopsy tree.
 */
public final class OsAccounts implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(OsAccounts.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/os-account.png";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private final SleuthkitCase skCase;
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
                refresh(true);
            }
        };
        
        @Override
        protected void addNotify() {
            Case.addEventTypeSubscriber(Collections.singleton(Case.Events.OS_ACCOUNT_ADDED), listener);
        }
        
        @Override
        protected void removeNotify() {
            Case.removeEventTypeSubscriber(Collections.singleton(Case.Events.OS_ACCOUNT_ADDED), listener);
        }
        
        @Override
        protected boolean createKeys(List<OsAccount> list) {
            try {
                if (filteringDSObjId == 0) {
                    list.addAll(skCase.getOsAccountManager().getAccounts());
                } else {
                    Host host = skCase.getHostManager().getHost(skCase.getDataSource(filteringDSObjId));
                    list.addAll(skCase.getOsAccountManager().getAccounts(host));
                }
            } catch (TskCoreException | TskDataException ex) {
                logger.log(Level.SEVERE, "Unable to retrieve list of OsAccounts for case", ex);
                return false;
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
    public static final class OsAccountNode extends DisplayableItemNode {

        private OsAccount account;
        
        private final PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(((OsAccountChangedEvent)evt).getOsAccount().getId() == account.getId()) {
                    // Update the account node to the new one
                    account = ((OsAccountChangedEvent)evt).getOsAccount();
                    updateSheet();
                }
            }
        };

        /**
         * Constructs a new OsAccountNode.
         *
         * @param account Node object.
         */
        OsAccountNode(OsAccount account) {
            super(Children.LEAF, Lookups.fixed(account));
            this.account = account;

            setName(account.getName());
            setDisplayName(account.getName());
            setIconBaseWithExtension(ICON_PATH);
            
            Case.addEventTypeSubscriber(Collections.singleton(Case.Events.OS_ACCOUNT_CHANGED), listener);
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

            optional = account.getRealm().getRealmName();
            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_accountRealmNameProperty_name(),
                    Bundle.OsAccounts_accountRealmNameProperty_displayName(),
                    Bundle.OsAccounts_accountRealmNameProperty_desc(),
                    optional.isPresent() ? optional.get() : ""));

            Optional<Long> creationTimeValue = account.getCreationTime();
            String timeDisplayStr
                    = creationTimeValue.isPresent() ? DATE_FORMATTER.format(new java.util.Date(creationTimeValue.get() * 1000)) : "";

            propertiesSet.put(new NodeProperty<>(
                    Bundle.OsAccounts_createdTimeProperty_name(),
                    Bundle.OsAccounts_createdTimeProperty_displayName(),
                    Bundle.OsAccounts_createdTimeProperty_desc(),
                    timeDisplayStr));

            return sheet;
        }
        
        @Override
        public Action[] getActions(boolean popup) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.addAll(Arrays.asList(super.getActions(popup)));
            actionsList.addAll(DataModelActionsFactory.getActions(account));
            
            return actionsList.toArray(new Action[actionsList.size()]);
        }
    }
}
