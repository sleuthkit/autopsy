/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
 *
 * @deprecated This class is obsolete (files are no longer skipped for any
 * reason by file typing) but it is retained for backwards compatibility because
 * it was erroneously made part of the public API and so that old settings files
 * can be deserialized successfully (and ignored).
 */
@Deprecated
public class FileTypeIdModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private boolean skipKnownFiles = true;
    private final boolean skipSmallFiles = false; // No longer used.

    FileTypeIdModuleSettings() {
    }

    FileTypeIdModuleSettings(boolean skipKnownFiles) {
        this.skipKnownFiles = skipKnownFiles;
    }

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

}
