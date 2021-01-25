/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.modules.photoreccarver;

import java.util.ArrayList;
import java.util.Collections;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import java.util.List;

/**
 * Ingest job settings for the PhotoRec Carver module.
 */
final class PhotoRecCarverIngestJobSettings implements IngestModuleIngestJobSettings {

    /**
     * What kind of filtering should occur for the extension list.
     */
    enum ExtensionFilterOption {
        /**
         * The file extensions should be included (and others should be
         * filtered).
         */
        INCLUDE,
        /**
         * The extensions should be excluded from the results list.
         */
        EXCLUDE,
        /**
         * No extension filtering should take place.
         */
        NO_FILTER
    };

    private static final long serialVersionUID = 1L;

    private boolean keepCorruptedFiles;
    private List<String> extensions;
    private ExtensionFilterOption extensionFilterOption;

    /**
     * Instantiate the ingest job settings with default values.
     */
    PhotoRecCarverIngestJobSettings() {
        this(PhotoRecCarverFileIngestModule.DEFAULT_CONFIG_KEEP_CORRUPTED_FILES,
                PhotoRecCarverFileIngestModule.DEFAULT_CONFIG_EXTENSION_FILTER,
                null);
    }

    /**
     * Sets the photo rec settings.
     *
     * @param keepCorruptedFiles       Whether or not to keep corrupted files.
     * @param fileOptOption            Whether or not the file opt options
     * @param extensionFilterOption    How the includeExcludeExtensions should
     *                                 be filtered.
     * @param includeExcludeExtensions The extensions to include or exclude
     *                                 (i.e. jpg, gif)
     */
    PhotoRecCarverIngestJobSettings(boolean keepCorruptedFiles, ExtensionFilterOption extensionFilterOption, List<String> includeExcludeExtensions) {
        this.keepCorruptedFiles = keepCorruptedFiles;
        setExtensionFilterOption(extensionFilterOption);
        setExtensions(includeExcludeExtensions);
        
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Are corrupted files being kept?
     *
     * @return True if keeping corrupted files; otherwise false.
     */
    boolean isKeepCorruptedFiles() {
        return keepCorruptedFiles;
    }

    /**
     * Keep or disgard corrupted files.
     *
     * @param keepCorruptedFiles Are corrupted files being kept?
     */
    void setKeepCorruptedFiles(boolean keepCorruptedFiles) {
        this.keepCorruptedFiles = keepCorruptedFiles;
    }

    /**
     * Gets extension names (i.e. jpg, exe) to include or exclude from photorec
     * carving.
     *
     * @return The extension names.
     */
    List<String> getExtensions() {
        return extensions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(extensions);
    }

    /**
     * Sets extension names (i.e. jpg, exe) to include or exclude from photorec
     * carving.
     *
     * @param includeExcludeExtensions The extension names.
     */
    void setExtensions(List<String> includeExcludeExtensions) {
        this.extensions = new ArrayList<>();
        if (includeExcludeExtensions != null) {
            this.extensions.addAll(includeExcludeExtensions);
        }
    }

    /**
     * How extension filtering should be handled.
     * @return How extension filtering should be handled.
     */
    ExtensionFilterOption getExtensionFilterOption() {
        return (this.extensionFilterOption == null) ? 
                ExtensionFilterOption.NO_FILTER : 
                extensionFilterOption;
    }

    /**
     * Sets how extension filtering should be handled.
     * @param extensionFilterOption How extension filtering should be handled.
     */
    void setExtensionFilterOption(ExtensionFilterOption extensionFilterOption) {
        this.extensionFilterOption = (extensionFilterOption == null) ? 
                ExtensionFilterOption.NO_FILTER : 
                extensionFilterOption;
    }
}
