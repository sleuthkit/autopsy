/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.ingestmodule;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the Correlation Engine module.
 */
final class IngestSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    private boolean ignorePreviousNotableItems;

    /**
     * Instantiate the ingest job settings with default values.
     */
    IngestSettings() {
        this.ignorePreviousNotableItems = IngestModule.DEFAULT_IGNORE_PREVIOUS_NOTABLE_ITEMS;
    }

    /**
     * Instantiate the ingest job settings.
     *
     * @param ignorePreviousNotableItems Ignore previously seen notable items.
     */
    IngestSettings(boolean ignorePreviousNotableItems) {
        this.ignorePreviousNotableItems = ignorePreviousNotableItems;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Are previously identified notable items ignored?
     *
     * @return True if ignored; otherwise false.
     */
    boolean isIgnorePreviousNotableItems() {
        return ignorePreviousNotableItems;
    }

    /**
     * Consider or ignore previously identified notable items.
     *
     * @param ignorePreviousNotableItems Are previously identified notable items
     *                                   ignored?
     */
    void setIgnorePreviousNotableItems(boolean ignorePreviousNotableItems) {
        this.ignorePreviousNotableItems = ignorePreviousNotableItems;
    }
}
