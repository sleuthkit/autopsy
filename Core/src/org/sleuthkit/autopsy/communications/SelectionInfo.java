/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
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

import java.util.Set;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;

/**
 * Class to wrap the details of the current selection from the AccountBrowser or
 * VisualizationPane
 */
public final class SelectionInfo {

    private final Set<AccountDeviceInstance> accountDeviceInstances;
    private final CommunicationsFilter communicationFilter;

    /**
     * Wraps the details of the currently selected accounts.
     *
     * @param accountDeviceInstances Selected accountDecivedInstances
     * @param communicationFilter    Currently selected communications filters
     */
    SelectionInfo(Set<AccountDeviceInstance> accountDeviceInstances, CommunicationsFilter communicationFilter) {
        this.accountDeviceInstances = accountDeviceInstances;
        this.communicationFilter = communicationFilter;
    }

    /**
     * Returns the currently selected accountDeviceInstances
     *
     * @return Set of AccountDeviceInstance
     */
    public Set<AccountDeviceInstance> getAccountDevicesInstances() {
        return accountDeviceInstances;
    }

    /**
     * Returns the currently selected communications filters.
     *
     * @return Instance of CommunicationsFilter
     */
    public CommunicationsFilter getCommunicationsFilter() {
        return communicationFilter;
    }

}
