/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.encryptiondetection;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the Encryption Detection module.
 */
final class EncryptionDetectionIngestJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private static final double DEFAULT_CONFIG_MINIMUM_ENTROPY = 7.5;
    private static final int DEFAULT_CONFIG_MINIMUM_FILE_SIZE = 5242880; // 5MB;
    private static final boolean DEFAULT_CONFIG_FILE_SIZE_MULTIPLE_ENFORCED = true;
    private static final boolean DEFAULT_CONFIG_SLACK_FILES_ALLOWED = true;
    private double minimumEntropy;
    private int minimumFileSize;
    private boolean fileSizeMultipleEnforced;
    private boolean slackFilesAllowed;

    /**
     * Instantiate the ingest job settings with default values.
     */
    EncryptionDetectionIngestJobSettings() {
        this.minimumEntropy = DEFAULT_CONFIG_MINIMUM_ENTROPY;
        this.minimumFileSize = DEFAULT_CONFIG_MINIMUM_FILE_SIZE;
        this.fileSizeMultipleEnforced = DEFAULT_CONFIG_FILE_SIZE_MULTIPLE_ENFORCED;
        this.slackFilesAllowed = DEFAULT_CONFIG_SLACK_FILES_ALLOWED;
    }

    /**
     * Instantiate the ingest job settings.
     *
     * @param minimumEntropy           The minimum entropy.
     * @param minimumFileSize          The minimum file size.
     * @param fileSizeMultipleEnforced Files must be a multiple of 512 to be
     *                                 processed.
     * @param slackFilesAllowed        Slack files can be processed.
     */
    EncryptionDetectionIngestJobSettings(double minimumEntropy, int minimumFileSize, boolean fileSizeMultipleEnforced, boolean slackFilesAllowed) {
        this.minimumEntropy = minimumEntropy;
        this.minimumFileSize = minimumFileSize;
        this.fileSizeMultipleEnforced = fileSizeMultipleEnforced;
        this.slackFilesAllowed = slackFilesAllowed;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Get the minimum entropy necessary for the creation of blackboard
     * artifacts.
     *
     * @return The minimum entropy.
     */
    double getMinimumEntropy() {
        return minimumEntropy;
    }

    /**
     * Set the minimum entropy necessary for the creation of blackboard
     * artifacts.
     */
    void setMinimumEntropy(double minimumEntropy) {
        this.minimumEntropy = minimumEntropy;
    }

    /**
     * Get the minimum file size necessary for the creation of blackboard
     * artifacts.
     *
     * @return The minimum file size.
     */
    int getMinimumFileSize() {
        return minimumFileSize;
    }

    /**
     * Set the minimum file size necessary for the creation of blackboard
     * artifacts.
     */
    void setMinimumFileSize(int minimumFileSize) {
        this.minimumFileSize = minimumFileSize;
    }

    /**
     * Is the file size multiple enforced?
     *
     * @return True if enforcement is enabled; otherwise false.
     */
    boolean isFileSizeMultipleEnforced() {
        return fileSizeMultipleEnforced;
    }

    /**
     * Enable or disable file size multiple enforcement.
     */
    void setFileSizeMultipleEnforced(boolean fileSizeMultipleEnforced) {
        this.fileSizeMultipleEnforced = fileSizeMultipleEnforced;
    }

    /**
     * Are slack files allowed for processing?
     *
     * @return True if slack files are allowed; otherwise false.
     */
    boolean isSlackFilesAllowed() {
        return slackFilesAllowed;
    }

    /**
     * Allow or disallow slack files for processing.
     */
    void setSlackFilesAllowed(boolean slackFilesAllowed) {
        this.slackFilesAllowed = slackFilesAllowed;
    }
}
