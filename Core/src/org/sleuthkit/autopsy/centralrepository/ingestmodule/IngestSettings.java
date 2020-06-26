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
 * Ingest job settings for the Central Repository module.
 */
final class IngestSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    private final boolean flagTaggedNotableItems;
    private final boolean flagPreviousDevices;
    private final boolean createCorrelationProperties;

    /**
     * Instantiate the ingest job settings with default values.
     */
    IngestSettings() {
        this.flagTaggedNotableItems = CentralRepoIngestModule.DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS;
        this.flagPreviousDevices = CentralRepoIngestModule.DEFAULT_FLAG_PREVIOUS_DEVICES;
        this.createCorrelationProperties = CentralRepoIngestModule.DEFAULT_CREATE_CR_PROPERTIES;
    }

    /**
     * Instantiate the ingest job settings.
     *
     * @param flagTaggedNotableItems      Flag previously tagged notable items.
     * @param flagPreviousDevices         Flag devices which exist already in
     *                                    the Central Repository
     * @param createCorrelationProperties Create correlation properties in the
     *                                    central repository
     */
    IngestSettings(boolean flagTaggedNotableItems, boolean flagPreviousDevices, boolean createCorrelationProperties) {
        this.flagTaggedNotableItems = flagTaggedNotableItems;
        this.flagPreviousDevices = flagPreviousDevices;
        this.createCorrelationProperties = createCorrelationProperties;
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
     * Are devices previously seen in other cases to be flagged?
     *
     * @return True if flagging; otherwise false.
     */
    boolean isFlagPreviousDevices() {
        return flagPreviousDevices;
    }

    /**
     * Should correlation properties be created
     *
     * @return True if creating; otherwise false.
     */
    boolean shouldCreateCorrelationProperties() {
        return createCorrelationProperties;
    }
}
