/*
 * Central Repository
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
    static final boolean DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS = false;
    static final boolean DEFAULT_FLAG_PREVIOUS_DEVICES = false;
    static final boolean DEFAULT_FLAG_UNIQUE_DEVICES = false;
    static final boolean DEFAULT_CREATE_CR_PROPERTIES = true;
    
    private final boolean flagTaggedNotableItems;
    private final boolean flagPreviousDevices;
    private final boolean createCorrelationProperties;
    private final boolean flagUniqueArtifacts;

    /**
     * Instantiate the ingest job settings with default values.
     */
    IngestSettings() {
        this.flagTaggedNotableItems = DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS;
        this.flagPreviousDevices = DEFAULT_FLAG_PREVIOUS_DEVICES;
        this.createCorrelationProperties = DEFAULT_CREATE_CR_PROPERTIES;
        this.flagUniqueArtifacts = DEFAULT_FLAG_UNIQUE_DEVICES;
    }

    /**
     * Instantiate the ingest job settings.
     *
     * @param flagTaggedNotableItems      Flag previously tagged notable items.
     * @param flagPreviousDevices         Flag devices which exist already in
     *                                    the Central Repository
     * @param createCorrelationProperties Create correlation properties in the
     *                                    central repository
     * @param flagUniqueArtifacts         Flag unique artifacts that have not
     *                                    been seen in any other cases
     */
    IngestSettings(boolean flagTaggedNotableItems, boolean flagPreviousDevices, boolean createCorrelationProperties, boolean flagUniqueArtifacts) {
        this.flagTaggedNotableItems = flagTaggedNotableItems;
        this.flagPreviousDevices = flagPreviousDevices;
        this.createCorrelationProperties = createCorrelationProperties;
        this.flagUniqueArtifacts = flagUniqueArtifacts;
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

    /**
     * Are artifacts (apps, domains) previously unseen in other cases to be
     * flagged?
     *
     * @return True if flagging; otherwise false.
     */
    public boolean isFlagUniqueArtifacts() {
        return flagUniqueArtifacts;
    }
}
