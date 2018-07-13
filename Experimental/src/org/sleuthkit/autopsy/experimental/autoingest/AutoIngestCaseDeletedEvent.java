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
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published when a case is deleted by the automated ingest manager.
 */
@Immutable
final class AutoIngestCaseDeletedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String caseName;
    private final String nodeName;
    private final String userName;

    /**
     * Constructs an event that is published when a case is deleted by the
     * automated ingest manager.
     *
     * @param caseName The case name.
     * @param nodeName The host name of the node that deleted the case.
     * @param userName The user that deleted the case
     */
    AutoIngestCaseDeletedEvent(String caseName, String nodeName, String userName) {
        super(AutoIngestManager.Event.CASE_DELETED.toString(), null, null);
        this.caseName = caseName;
        this.nodeName = nodeName;
        this.userName = userName;
    }

    String getCaseName() {
        return caseName;
    }

    String getNodeName() {
        return nodeName;
    }
    
    String getUserName() {
        return userName;
    }

}
