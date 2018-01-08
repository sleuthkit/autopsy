/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;

/**
 *
 */
class PinAccountEvent {

    private final ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances;

    public ImmutableSet<AccountDeviceInstanceKey> getAccountDeviceInstances() {
        return accountDeviceInstances;
    }

    PinAccountEvent(Collection<? extends AccountDeviceInstanceKey> accountDeviceInstances) {
        this.accountDeviceInstances = ImmutableSet.copyOf(accountDeviceInstances);
    }
}
