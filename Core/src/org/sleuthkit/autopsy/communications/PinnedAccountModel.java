/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

class PinnedAccountModel {

    /**
     * Set of AccountDeviceInstanceKeys that are 'Pinned' to this graph. Pinned
     * accounts are shown regardless of filters, and accounts that are related
     * to pinned accounts and pass the filters are show. Pinning accounts is the
     * primary way to populate the graph.
     */
    private final Set<AccountDeviceInstanceKey> pinnedAccountDevices = new HashSet<>();
    private final CommunicationsGraph graph;

    PinnedAccountModel(CommunicationsGraph graph) {
        this.graph = graph;
    }

    boolean isAccountPinned(AccountDeviceInstanceKey account) {
        return pinnedAccountDevices.contains(account);
    }

    /**
     * Unpin the given accounts from the graph. Pinned accounts will always be
     * shown regardless of the filter state. Furthermore, accounts with
     * relationships that pass the filters will also be shown.
     *
     * @param accountDeviceInstances The accounts to unpin.
     */
    void unpinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.removeAll(accountDeviceInstances);
    }

    /**
     * Pin the given accounts to the graph. Pinned accounts will always be shown
     * regardless of the filter state. Furthermore, accounts with relationships
     * that pass the filters will also be shown.
     *
     * @param accountDeviceInstances The accounts to pin.
     */
    void pinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.addAll(accountDeviceInstances);
    }

    /**
     * Are there any accounts in this graph? If there are no pinned accounts the
     * graph will be empty.
     *
     * @return True if this graph is empty.
     */
    boolean isEmpty() {
        return pinnedAccountDevices.isEmpty();
    }

    void clear() {
        pinnedAccountDevices.clear();
    }

    Iterable<AccountDeviceInstanceKey> getPinnedAccounts() {
        return ImmutableSet.copyOf(pinnedAccountDevices);
    }

}
