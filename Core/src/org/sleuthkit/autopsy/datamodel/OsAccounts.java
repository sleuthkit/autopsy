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

import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.SelectionResponder;
import org.sleuthkit.autopsy.mainui.datamodel.OsAccountsSearchParams;

/**
 * Implements the OS Accounts subnode of Results in the Autopsy tree.
 */
public final class OsAccounts {

    private static final String LIST_NAME = Bundle.OsAccount_listNode_name();

    private final long filteringDSObjId;

    /**
     * Returns the name of the OsAccountListNode to be used for id purposes.
     *
     * @return The name of the OsAccountListNode to be used for id purposes.
     */
    public static String getListName() {
        return LIST_NAME;
    }

    public OsAccounts() {
        this(0);
    }

    public OsAccounts(long objId) {
        this.filteringDSObjId = objId;
    }

    @Messages({
        "OsAccount_listNode_name=OS Accounts"
    })
    /**
     * The root node of the OS Accounts subtree.
     */
    public final class OsAccountListNode extends DisplayableItemNode implements SelectionResponder {

        /**
         * Construct a new OsAccountListNode.
         */
        public OsAccountListNode() {
            super(Children.LEAF);
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

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayOsAccounts(new OsAccountsSearchParams(filteringDSObjId == 0 ? null : filteringDSObjId));
        }
    }
}
