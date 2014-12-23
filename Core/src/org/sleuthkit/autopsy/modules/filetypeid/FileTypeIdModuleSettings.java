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
package org.sleuthkit.autopsy.modules.filetypeid;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job options for the file type identifier ingest module instances.
 */
// TODO: This class does not need to be public.
public class FileTypeIdModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private static final long MIN_FILE_SIZE_IN_BYTES = 512;
    private boolean skipKnownFiles = true;
    private boolean skipSmallFiles = true;

    FileTypeIdModuleSettings() {
    }

    FileTypeIdModuleSettings(boolean skipKnownFiles, boolean skipSmallFiles) {
        this.skipKnownFiles = skipKnownFiles;
        this.skipSmallFiles = skipSmallFiles;
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    void setSkipKnownFiles(boolean enabled) {
        skipKnownFiles = enabled;
    }

    boolean skipKnownFiles() {
        return skipKnownFiles;
    }

    void setSkipSmallFiles(boolean enabled) {
        this.skipSmallFiles = enabled;
    }

    boolean skipSmallFiles() {
        return skipSmallFiles;
    }
    
    long minFileSizeInBytes() {
        return MIN_FILE_SIZE_IN_BYTES;
    }

}
