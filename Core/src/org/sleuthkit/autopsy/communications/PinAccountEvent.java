/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import org.sleuthkit.autopsy.communications.AccountsRootChildren.AccountDeviceInstanceNode;

/**
 *
 */
class PinAccountEvent {

    private final AccountDeviceInstanceNode accountDeviceInstance;

    public AccountDeviceInstanceNode getAccountDeviceInstanceNode() {
        return accountDeviceInstance;
    }

    PinAccountEvent(AccountDeviceInstanceNode accountDeviceInstance) {
        this.accountDeviceInstance = accountDeviceInstance;
    }
}
