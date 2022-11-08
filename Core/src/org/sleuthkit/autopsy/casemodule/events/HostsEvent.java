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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A base class for application events published when hosts in the Sleuth Kit
 * data model for a case have been added or updated.
 */
public class HostsEvent extends TskDataModelChangedEvent<Host, Host> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the base class part of an application event published when
     * hosts in the Sleuth Kit data model for a case have been added or updated.
     *
     * @param eventName The name of the Case.Events enum value for the event
     *                  type.
     * @param hosts     The hosts.
     */
    HostsEvent(String eventName, List<Host> hosts) {
        super(eventName, null, null, hosts, Host::getHostId);
    }

    /**
     * Gets the hosts that have been added or updated.
     *
     * @return The hosts.
     */
    public List<Host> getHosts() {
        return getNewValue();
    }

    @Override
    protected List<Host> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<Host> hosts = new ArrayList<>();
        for (Long id : ids) {
            Optional<Host> host = caseDb.getHostManager().getHostById(id);
            if (host.isPresent()) {
                hosts.add(host.get());
            }
        }
        return hosts;
    }

}
