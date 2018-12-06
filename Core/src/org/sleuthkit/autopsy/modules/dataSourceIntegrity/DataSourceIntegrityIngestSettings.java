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
package org.sleuthkit.autopsy.modules.dataSourceIntegrity;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the E01 Verify module.
 */
final class DataSourceIntegrityIngestSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    static final boolean DEFAULT_COMPUTE_HASHES = true;
    static final boolean DEFAULT_VERIFY_HASHES = true;
    
    private boolean computeHashes;
    private boolean verifyHashes;

    /**
     * Instantiate the ingest job settings with default values.
     */
    DataSourceIntegrityIngestSettings() {
        this.computeHashes = DEFAULT_COMPUTE_HASHES;
        this.verifyHashes = DEFAULT_VERIFY_HASHES;
    }

    /**
     * Instantiate the ingest job settings.
     * 
     * @param computeHashes  Compute hashes if none are present
     * @param verifyHashes   Verify hashes if any are present
     */
    DataSourceIntegrityIngestSettings(boolean computeHashes, boolean verifyHashes) {
        this.computeHashes = computeHashes;
        this.verifyHashes = verifyHashes;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }
    
    /**
     * Should hashes be computed if none are present?
     * 
     * @return true if hashes should be computed, false otherwise
     */
    boolean shouldComputeHashes() {
        return computeHashes;
    }
    
    /**
     * Set whether hashes should be computed.
     * 
     * @param computeHashes true if hashes should be computed
     */
    void setComputeHashes(boolean computeHashes) {
        this.computeHashes = computeHashes;
    }
    
    
    /**
     * Should hashes be verified if at least one is present?
     * 
     * @return true if hashes should be verified, false otherwise
     */
    boolean shouldVerifyHashes() {
        return verifyHashes;
    }    
    
    /**
     * Set whether hashes should be verified.
     * 
     * @param verifyHashes true if hashes should be verified
     */
    void setVerifyHashes(boolean verifyHashes) {
        this.verifyHashes = verifyHashes;
    }
}
