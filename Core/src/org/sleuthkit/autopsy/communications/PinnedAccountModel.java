/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import java.util.HashSet;
import java.util.Set;

/**
 * Model of what accounts are pinned to a visualization.
 */
class PinnedAccountModel {

    /**
     * Set of AccountDeviceInstanceKeys that are 'Pinned' to the graph. Pinned
     * accounts are shown regardless of filters, and accounts that are related
     * to pinned accounts and pass the filters are show. Pinning accounts is the
     * primary way to populate the graph.
     */
    private final Set<AccountDeviceInstanceKey> pinnedAccountDevices = new HashSet<>();

    private final EventBus eventBus = new EventBus();

    void registerhandler(Object handler) {
        eventBus.register(handler);
    }

    void unregisterhandler(Object handler) {
        eventBus.unregister(handler);
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
