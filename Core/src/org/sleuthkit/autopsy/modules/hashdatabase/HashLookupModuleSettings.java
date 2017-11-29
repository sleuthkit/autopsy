/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org *

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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the hash lookup module.
 */
final public class HashLookupModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private HashSet<String> namesOfEnabledKnownHashSets;
    private HashSet<String> namesOfDisabledKnownHashSets;    // Added in version 1.1
    private HashSet<String> namesOfEnabledKnownBadHashSets;
    private HashSet<String> namesOfDisabledKnownBadHashSets; // Added in version 1.1
    private boolean shouldCalculateHashes = true;

    /**
     * Constructs ingest job settings for the hash lookup module.
     *
     * @param shouldCalculateHashes          Whether or not hashes should be
     *                                       calculated.
     * @param namesOfEnabledKnownHashSets    A list of enabled known hash sets.
     * @param namesOfEnabledKnownBadHashSets A list of enabled known bad hash
     *                                       sets.
     */
    public HashLookupModuleSettings(boolean shouldCalculateHashes,
            List<String> namesOfEnabledKnownHashSets,
            List<String> namesOfEnabledKnownBadHashSets) {
        this(shouldCalculateHashes, namesOfEnabledKnownHashSets, namesOfEnabledKnownBadHashSets, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Constructs ingest job settings for the hash lookup module.
     *
     * @param shouldCalculateHashes           Whether or not hashes should be
     *                                        calculated.
     * @param namesOfEnabledKnownHashSets     A list of enabled known hash sets.
     * @param namesOfEnabledKnownBadHashSets  A list of enabled known bad hash
     *                                        sets.
     * @param namesOfDisabledKnownHashSets    A list of disabled known hash
     *                                        sets.
     * @param namesOfDisabledKnownBadHashSets A list of disabled known bad hash
     *                                        sets.
     */
    public HashLookupModuleSettings(boolean shouldCalculateHashes,
            List<String> namesOfEnabledKnownHashSets,
            List<String> namesOfEnabledKnownBadHashSets,
            List<String> namesOfDisabledKnownHashSets,
            List<String> namesOfDisabledKnownBadHashSets) {
        this.shouldCalculateHashes = shouldCalculateHashes;
        this.namesOfEnabledKnownHashSets = new HashSet<>(namesOfEnabledKnownHashSets);
        this.namesOfEnabledKnownBadHashSets = new HashSet<>(namesOfEnabledKnownBadHashSets);
        this.namesOfDisabledKnownHashSets = new HashSet<>(namesOfDisabledKnownHashSets);
        this.namesOfDisabledKnownBadHashSets = new HashSet<>(namesOfDisabledKnownBadHashSets);
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        this.upgradeFromOlderVersions();
        return HashLookupModuleSettings.serialVersionUID;
    }

    /**
     * Checks the setting that specifies whether or not hashes are to be
     * calculated.
     *
     * @return True if hashes are to be calculated, false otherwise.
     */
    boolean shouldCalculateHashes() {
        this.upgradeFromOlderVersions();
        return this.shouldCalculateHashes;
    }

    /**
     * Checks whether or not a hash set is enabled. If there is no setting for
     * the requested hash set, it is deemed to be enabled.
     *
     * @param hashSetName The name of the hash set to check.
     *
     * @return True if the hash set is enabled, false otherwise.
     */
    boolean isHashSetEnabled(String hashSetName) {
        this.upgradeFromOlderVersions();
        return !(this.namesOfDisabledKnownHashSets.contains(hashSetName) || this.namesOfDisabledKnownBadHashSets.contains(hashSetName));
    }

    /**
     * Get the names of all explicitly enabled known files hash sets.
     *
     * @return The list of names.
     */
    List<String> getNamesOfEnabledKnownHashSets() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(this.namesOfEnabledKnownHashSets);
    }

    /**
     * Get the names of all explicitly disabled known files hash sets.
     *
     * @return The list of names.
     */
    List<String> getNamesOfDisabledKnownHashSets() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(namesOfDisabledKnownHashSets);
    }

    /**
     * Get the names of all explicitly enabled known bad files hash sets.
     *
     * @return The list of names.
     */
    List<String> getNamesOfEnabledKnownBadHashSets() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(this.namesOfEnabledKnownBadHashSets);
    }

    /**
     * Get the names of all explicitly disabled known bad files hash sets.
     *
     * @return The list of names.
     */
    List<String> getNamesOfDisabledKnownBadHashSets() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(this.namesOfDisabledKnownBadHashSets);
    }

    /**
     * Initialize fields set to null when an instance of a previous, but still
     * compatible, version of this class is de-serialized.
     */
    private void upgradeFromOlderVersions() {
        if (null == this.namesOfDisabledKnownHashSets) {
            this.namesOfDisabledKnownHashSets = new HashSet<>();
        }
        if (null == this.namesOfDisabledKnownBadHashSets) {
            this.namesOfDisabledKnownBadHashSets = new HashSet<>();
        }
    }

}
