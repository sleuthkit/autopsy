/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

    private String minimumEntropy = null;
    private String minimumFileSize = null;
    private boolean fileSizeMultipleEnforced;
    private boolean slackFilesAllowed;

    /**
     * Instantiate the ingest job settings with default values.
     */
    EncryptionDetectionIngestJobSettings() {
        this.minimumEntropy = EncryptionDetectionFileIngestModule.DEFAULT_CONFIG_MINIMUM_ENTROPY;
        this.minimumFileSize = EncryptionDetectionFileIngestModule.DEFAULT_CONFIG_MINIMUM_FILE_SIZE;
        this.fileSizeMultipleEnforced = EncryptionDetectionFileIngestModule.DEFAULT_CONFIG_FILE_SIZE_MULTIPLE_ENFORCED;
        this.slackFilesAllowed = EncryptionDetectionFileIngestModule.DEFAULT_CONFIG_SLACK_FILES_ALLOWED;
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
    EncryptionDetectionIngestJobSettings(String minimumEntropy, String minimumFileSize, boolean fileSizeMultipleEnforced, boolean slackFilesAllowed) {
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
    String getMinimumEntropy() {
        return minimumEntropy;
    }

    /**
     * Set the minimum entropy necessary for the creation of blackboard
     * artifacts.
     */
    void setMinimumEntropy(String minimumEntropy) {
        this.minimumEntropy = minimumEntropy;
    }

    /**
     * Get the minimum file size (in megabytes) necessary for the creation of
     * blackboard artifacts.
     *
     * @return The minimum file size.
     */
    String getMinimumFileSize() {
        return minimumFileSize;
    }

    /**
     * Set the minimum file size (in megabytes) necessary for the creation of
     * blackboard artifacts.
     */
    void setMinimumFileSize(String minimumFileSize) {
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
