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
package org.sleuthkit.autopsy.ingest;

import java.util.List;
import java.util.Map;

/**
 * Interface that provides a snapshot of ingest progress.
 */
public interface IngestProgressSnapshotProvider {

    /**
     * Get a snapshot of the state of ingest threads.
     *
     * @return A list of IngestThreadActivitySnapshot
     */
    List<IngestManager.IngestThreadActivitySnapshot> getIngestThreadActivitySnapshots();

    /**
     * Get a snapshot of the state of ingest jobs.
     *
     * @return A list of ingest job snapshots.
     */
    List<IngestJobProgressSnapshot> getIngestJobSnapshots();

    /**
     * Gets the cumulative run times for the ingest module.
     *
     * @return Map of module name to run time (in milliseconds)
     */
    Map<String, Long> getModuleRunTimes();
}
