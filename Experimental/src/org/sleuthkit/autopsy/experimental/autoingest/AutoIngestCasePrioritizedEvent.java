/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published when an automated ingest manager prioritizes all or part of a
 * case.
 */
public final class AutoIngestCasePrioritizedEvent extends AutopsyEvent implements Serializable { // RJCTODO: Rename to AutoIngestPrioritizationEvent

    private static final long serialVersionUID = 1L;
    private final String caseName;
    private final String nodeName;

    /**
     * Constructs an event published when an automated ingest manager
     * prioritizes all or part of a case.
     *
     * @param caseName The name of the case.
     * @param nodeName The host name of the node that prioritized the case.
     */
    public AutoIngestCasePrioritizedEvent(String nodeName, String caseName) {
        super(AutoIngestManager.Event.CASE_PRIORITIZED.toString(), null, null);
        this.caseName = caseName;
        this.nodeName = nodeName;
    }

    /**
     * Gets the name of the prioritized case.
     *
     * @return The case name.
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * Gets the host name of the node that prioritized the case.
     *
     * @return The host name of the node.
     */
    public String getNodeName() {
        return nodeName;
    }

}
