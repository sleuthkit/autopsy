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

    private boolean flagTaggedNotableItems;

    /**
     * Instantiate the ingest job settings with default values.
     */
    IngestSettings() {
        this.flagTaggedNotableItems = IngestModule.DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS;
    }

    /**
     * Instantiate the ingest job settings.
     *
     * @param flagTaggedNotableItems Flag previously tagged notable items.
     */
    IngestSettings(boolean flagTaggedNotableItems) {
        this.flagTaggedNotableItems = flagTaggedNotableItems;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Are previously tagged notable items to be flagged?
     *
     * @return True if flagging; otherwise false.
     */
    boolean isFlagTaggedNotableItems() {
        return flagTaggedNotableItems;
    }

    /**
     * Flag or ignore previously identified notable items.
     *
     * @param ignorePreviousNotableItems Are previously tagged notable items to
     *                                   be flagged?
     */
    void setFlagTaggedNotableItems(boolean flagTaggedNotableItems) {
        this.flagTaggedNotableItems = flagTaggedNotableItems;
    }
}
