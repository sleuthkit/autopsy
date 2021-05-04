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
public final class AutoIngestNodeControlEvent extends AutopsyEvent implements Serializable {

    /**
     * The set of available controls.
     */
    public enum ControlEventType {
        PAUSE,
        RESUME,
        SHUTDOWN,
        GENERATE_THREAD_DUMP_REQUEST
    }

    private static final long serialVersionUID = 1L;
    private final String targetNodeName;
    private final String originatingNodeName;
    private final String userName;
    private final ControlEventType eventType;

    public AutoIngestNodeControlEvent(ControlEventType eventType, String targetNode, String originatingNode, String userName) {
        super(eventType.toString(), null, null);
        this.eventType = eventType;
        this.targetNodeName = targetNode;
        this.originatingNodeName = originatingNode;
        this.userName = userName;
    }

    String getTargetNodeName() {
        return targetNodeName;
    }

    String getOriginatingNodeName() {
        return originatingNodeName;
    }

    String getUserName() {
        return userName;
    }

    ControlEventType getControlEventType() {
        return eventType;
    }
}
