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
 * Event published when auto ingest manager (AIM) starts processing an auto
 * ingest job.
 */
public final class AutoIngestJobStartedEvent extends AutoIngestJobEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public AutoIngestJobStartedEvent(AutoIngestJob job) {
        super(AutoIngestManager.Event.JOB_STARTED, job);
    }

}