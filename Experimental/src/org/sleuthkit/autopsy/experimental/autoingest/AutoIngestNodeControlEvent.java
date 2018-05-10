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
 * Event published to pause, resume or shutdown an AIN.
 */
final class AutoIngestNodeControlEvent extends AutopsyEvent implements Serializable {

    /**
     * The set of available controls.
     */
    enum ControlEventType {
        PAUSE,
        RESUME,
        SHUTDOWN
    }
    
    private static final long serialVersionUID = 1L;
    private final String nodeName;
    private final ControlEventType eventType;

    AutoIngestNodeControlEvent(ControlEventType eventType, String nodeName) {
        super(eventType.toString(), null, null);
        this.eventType = eventType;
        this.nodeName = nodeName;
    }
    
    String getNodeName() {
        return nodeName;
    }
    
    ControlEventType getControlEventType() {
        return eventType;
    }
}
