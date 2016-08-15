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
 * Event published when a case is deleted by the automated ingest manager.
 */
public final class AutoIngestCaseDeletedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final AutoIngestManager.CaseDeletionResult result;
    private final String nodeName;

    /**
     * Constructs an event that is published when a case is deleted by the
     * automated ingest manager.
     *
     * @param result   The deletion result // RJCTODO: Get rid of logical
     *                 deletion
     * @param nodeName The host name of the node that deleted the case.
     */
    public AutoIngestCaseDeletedEvent(AutoIngestManager.CaseDeletionResult result, String nodeName) {
        super(AutoIngestManager.Event.CASE_DELETED.toString(), null, null);
        this.result = result;
        this.nodeName = nodeName;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public AutoIngestManager.CaseDeletionResult getResult() {
        return result;
    }

}
