/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;

/**
 * Key for AccountDeviceInstance node.
 * 
 * Encapsulates a AccountDeviceInstance, and CommunicationsFilter.
 */
public class AccountDeviceInstanceKey {

    private final AccountDeviceInstance accountDeviceInstance;
    private final CommunicationsFilter filter;

    AccountDeviceInstanceKey(AccountDeviceInstance accountDeviceInstance, CommunicationsFilter filter) {
        this.accountDeviceInstance = accountDeviceInstance;
        this.filter = filter;
    }

    AccountDeviceInstance getAccountDeviceInstance() {
        return accountDeviceInstance;
    }

    CommunicationsFilter getCommunicationsFilter() {
        return filter;
    }
}
