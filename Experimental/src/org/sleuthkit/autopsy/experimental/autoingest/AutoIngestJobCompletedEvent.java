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

/**
 * Event published when an automated ingest manager completes processing an
 * automated ingest job.
 */
public final class AutoIngestJobCompletedEvent extends AutoIngestJobEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final boolean retry;

    /**
     * RJCTODO
     */
    public AutoIngestJobCompletedEvent(AutoIngestJob job, boolean shouldRetry) {
        super(AutoIngestManager.Event.JOB_COMPLETED, job);
        this.retry = shouldRetry;
    }
    
    /**
     * RJCTODO
     * @return 
     */
    public boolean shouldRetry() {
        return this.retry;
    }
    
}
