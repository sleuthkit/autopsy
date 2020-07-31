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

    private static final long serialVersionUID = 1L;

    private boolean keepCorruptedFiles;
    private List<String> includeExcludeExtensions;
    private boolean fileOptOption;
    private boolean includeElseExclude;

    /**
     * Instantiate the ingest job settings with default values.
     */
    PhotoRecCarverIngestJobSettings() {
        this(PhotoRecCarverFileIngestModule.DEFAULT_CONFIG_KEEP_CORRUPTED_FILES,
                PhotoRecCarverFileIngestModule.DEFAULT_CONFIG_FILE_OPT_OPTIONS,
                PhotoRecCarverFileIngestModule.DEFAULT_CONFIG_INCLUDE_ELSE_EXCLUDE,
                null);
    }

    /**
     * Sets the photo rec settings.
     *
     * @param keepCorruptedFiles       Whether or not to keep corrupted files.
     * @param fileOptOption            Whether or not the file opt options
     *                                 should be enabled (whether or not to
     *                                 include/exclude file extensions).
     * @param includeElseExclude       If file opt options is enabled, whether
     *                                 to include only the extensions listed or
     *                                 exclude extensions from output.
     * @param includeExcludeExtensions The extensions to include or exclude
     *                                 (i.e. jpg, gif)
     */
    public PhotoRecCarverIngestJobSettings(boolean keepCorruptedFiles, boolean fileOptOption, boolean includeElseExclude, List<String> includeExcludeExtensions) {
        this.keepCorruptedFiles = keepCorruptedFiles;
        this.fileOptOption = fileOptOption;
        this.includeElseExclude = includeElseExclude;
        setIncludeExcludeExtensions(includeExcludeExtensions);
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
    public List<String> getIncludeExcludeExtensions() {
        return includeExcludeExtensions == null ? 
                Collections.emptyList() : 
                Collections.unmodifiableList(includeExcludeExtensions);
    }

    /**
     * Sets extension names (i.e. jpg, exe) to include or exclude from photorec
     * carving.
     *
     * @param includeExcludeExtensions The extension names.
     */
    public void setIncludeExcludeExtensions(List<String> includeExcludeExtensions) {
        this.includeExcludeExtensions = new ArrayList<>();
        if (includeExcludeExtensions != null) {
            this.includeExcludeExtensions.addAll(includeExcludeExtensions);
        }
    }

    /**
     * Returns whether or not the fileopt option (and subsequent file extension
     * filtering) should be enabled.
     *
     * @return Whether or not the fileopt option (and subsequent file extension
     *         filtering) should be enabled.
     */
    public boolean hasFileOptOption() {
        return fileOptOption;
    }

    /**
     * Returns whether or not the fileopt option (and subsequent file extension
     * filtering) should be enabled.
     *
     * @param fileOptOption Whether or not the fileopt option (and subsequent
     *                      file extension filtering) should be enabled.
     */
    public void setFileOptOption(boolean fileOptOption) {
        this.fileOptOption = fileOptOption;
    }

    /**
     * If the hasFileOptOption is true, this determines whether
     * includeExcludeExtensions will be included in the results (excluding all
     * others) or includeExcludeExtensions will be excluded from results
     * (including all others).
     *
     * @return Whether to include or exclude includeExcludeExtensions.
     */
    public boolean isIncludeElseExclude() {
        return includeElseExclude;
    }

    /**
     * Sets whether or not to include or exclude files. If the hasFileOptOption
     * is true, this determines whether includeExcludeExtensions will be
     * included in the results (excluding all others) or
     * includeExcludeExtensions will be excluded from results (including all
     * others).
     *
     * @param includeElseExclude Whether to include or exclude
     *                           includeExcludeExtensions.
     */
    public void setIncludeElseExclude(boolean includeElseExclude) {
        this.includeElseExclude = includeElseExclude;
    }
}
