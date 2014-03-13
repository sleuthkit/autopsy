/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest options for the hash lookup file ingest module.
 */
// Note that this class is not yet used as intended.
public class HashLookupOptions implements IngestModuleIngestJobSettings {

    private boolean shouldCalculateHashes = true;
    private ArrayList<HashDbManager.HashDb> knownFileHashSets;
    private ArrayList<HashDbManager.HashDb> knownBadFileHashSets;

    HashLookupOptions() {
        shouldCalculateHashes = true;
        knownFileHashSets = new ArrayList<>();
        knownBadFileHashSets = new ArrayList<>();
    }

    HashLookupOptions(boolean shouldCalculateHashes, List<HashDbManager.HashDb> knownFileHashSets, List<HashDbManager.HashDb> knownBadFileHashSets) {
        this.shouldCalculateHashes = shouldCalculateHashes;
        this.knownFileHashSets = new ArrayList<>(knownFileHashSets);
        this.knownBadFileHashSets = new ArrayList<>(knownBadFileHashSets);
    }

    @Override
    public boolean areValid() {
        // RJCTODO: Verify that hash sets are present in hash db manager
        return true;
    }

    boolean shouldCalculateHashes() {
        return shouldCalculateHashes;
    }

    void setShouldCalculateHashes(boolean shouldCalculateHashes) {
        this.shouldCalculateHashes = shouldCalculateHashes;
    }

    List<HashDbManager.HashDb> getKnownFileHashSets() {
        return new ArrayList<>(knownFileHashSets);
    }

    void setKnownFileHashSets(List<HashDbManager.HashDb> hashSets) {
        knownFileHashSets = new ArrayList<>(hashSets);
    }

    List<HashDbManager.HashDb> getKnownBadFileHashSets() {
        return new ArrayList<>(knownBadFileHashSets);
    }

    void setKnownBadFileHashSets(List<HashDbManager.HashDb> hashSets) {
        knownBadFileHashSets = new ArrayList<>(hashSets);
    }
}
