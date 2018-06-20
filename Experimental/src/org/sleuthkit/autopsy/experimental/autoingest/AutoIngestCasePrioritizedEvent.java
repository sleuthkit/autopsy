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
public final class AutoIngestCasePrioritizedEvent extends AutopsyEvent implements Serializable {

    /**
     * Possible event types
     */
    enum EventType {
        CASE_PRIORITIZED,
        CASE_DEPRIORITIZED,
        JOB_PRIORITIZED,
        JOB_DEPRIORITIZED
    }
    
    private static final long serialVersionUID = 1L;
    private final String caseName;
    private final String nodeName;
    private final String userName;
    private final EventType eventType;
    private final String dataSource;

    /**
     * Constructs an event published when an automated ingest manager
     * prioritizes all or part of a case.
     *
     * @param caseName The name of the case.
     * @param nodeName The host name of the node that prioritized the case.
     * @param userName The logged in user
     * @param eventType The type of prioritization event
     * @pamam dataSource List of data sources that were prioritized/deprioritized (if it wasn't the whole case)
     */
    public AutoIngestCasePrioritizedEvent(String nodeName, String caseName, String userName, EventType eventType, String dataSource) {
        super(AutoIngestManager.Event.CASE_PRIORITIZED.toString(), null, null);
        this.caseName = caseName;
        this.nodeName = nodeName;
        this.userName = userName;
        this.eventType = eventType;
        this.dataSource = dataSource;
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
    
    /**
     * Gets the user logged in to the node that prioritized the case.
     *
     * @return The user name
     */
    String getUserName() {
        return userName;
    }    

    /**
     * Gets the type of prioritization
     *
     * @return The type
     */
    EventType getEventType() {
        return eventType;
    }
    
    /**
     * Gets the list of data sources (if applicable)
     *
     * @return The data sources
     */
    String getDataSources() {
        return dataSource;
    }
}
