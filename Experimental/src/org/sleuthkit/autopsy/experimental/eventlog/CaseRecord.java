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
package org.sleuthkit.autopsy.experimental.eventlog;

/**
 * A record for a case processed by auto ingest.
 */
public class CaseRecord {

    private final long id;
    private final String name;

    /**
     * Main constructor.
     *
     * @param id   The event log id of the case.
     * @param name The unique name of the case.
     */
    CaseRecord(long id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * @return The event log id of the case.
     */
    public long getId() {
        return id;
    }

    /**
     * @return The unique name of the case.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "CaseRecord{" + "id=" + id + ", name=" + name + '}';
    }
}
