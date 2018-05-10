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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published when an auto ingest node is started, paused,
 * resumed or shutdown.
 */
public final class AutoIngestNodeStateEvent extends AutopsyEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final AutoIngestManager.Event eventType;
    private final String nodeName;

    public AutoIngestNodeStateEvent(AutoIngestManager.Event eventType, String nodeName) {
        super(eventType.toString(), null, null);
        this.eventType = eventType;
        this.nodeName = nodeName;
    }
    
    public AutoIngestManager.Event getEventType() {
        return this.eventType;
    }
    
    public String getNodeName() {
        return this.nodeName;
    }
}
