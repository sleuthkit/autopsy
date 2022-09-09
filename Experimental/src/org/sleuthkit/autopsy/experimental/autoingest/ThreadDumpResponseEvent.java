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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published to send thread dump.
 */
public final class ThreadDumpResponseEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String originalNodeName;
    private final String targetNodeName;
    private final String threadDump;

    public ThreadDumpResponseEvent(String originalNodeName, String targetNodeName, String threadDump) {
        super(AutoIngestManager.Event.GENERATE_THREAD_DUMP_RESPONSE.toString(), null, null);
        this.originalNodeName = originalNodeName;
        this.targetNodeName = targetNodeName;
        this.threadDump = threadDump;
    }
    
    String getOriginalNodeName() {
        return originalNodeName;
    }

    String getTargetNodeName() {
        return targetNodeName;
    }
    
    /**
     * @return Thread dump
     */
    public String getThreadDump() {
        return threadDump;
    }    
}
