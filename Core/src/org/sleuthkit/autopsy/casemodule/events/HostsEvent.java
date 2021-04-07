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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.HostManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Base event class for when something pertaining to hosts changes.
 */
public class HostsEvent extends TskDataModelChangeEvent<Host> {

    /**
     * Retrieves a list of ids from a list of hosts.
     *
     * @param hosts The hosts.
     * @return The list of ids.
     */
    private static List<Long> getIds(List<Host> hosts) {
        return getSafeList(hosts).stream()
                .filter(h -> h != null)
                .map(h -> h.getHostId()).collect(Collectors.toList());
    }

    /**
     * Returns the hosts or an empty list.
     *
     * @param hosts The host list.
     * @return The host list or an empty list if the parameter is null.
     */
    private static List<Host> getSafeList(List<Host> hosts) {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    /**
     * Main constructor.
     *
     * @param eventName The name of the Case.Events enum value for the event
     * type.
     * @param dataModelObjects The list of hosts for the event.
     */
    protected HostsEvent(String eventName, List<Host> dataModelObjects) {
        super(eventName, getIds(dataModelObjects), new ArrayList<>(getSafeList(dataModelObjects)));
    }

    @Override
    protected List<Host> getDataModelObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        HostManager hostManager = caseDb.getHostManager();
        List<Host> toRet = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }

                Optional<Host> thisHostOpt = hostManager.getHostById(id);
                thisHostOpt.ifPresent((h) -> toRet.add(h));
            }
        }

        return toRet;
    }

}
