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

import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Host;

/**
 * Event fired when hosts are changed.
 */
public class HostsChangedEvent extends HostsEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Main constructor.
     *
     * @param dataModelObjects The new values for the hosts that have been
     * changed.
     */
    public HostsChangedEvent(List<Host> dataModelObjects) {
        super(Case.Events.HOSTS_CHANGED.name(), dataModelObjects);
    }
}
