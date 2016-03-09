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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest options for the file extension mismatch detector ingest module.
 */
final class FileExtMismatchDetectorModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private long versionNumber;
    private boolean skipFilesWithNoExtension;
    private boolean skipFilesWithTextPlainMimeType;
    private boolean skipKnownFiles;

    FileExtMismatchDetectorModuleSettings() {
        this.skipFilesWithNoExtension = true;
        this.skipFilesWithTextPlainMimeType = true;
        this.skipKnownFiles = true;
    }

    FileExtMismatchDetectorModuleSettings(boolean skipKnownFiles, boolean skipFilesWithNoExtension, boolean skipFilesWithTextPlainMimeType) {
        this.skipFilesWithNoExtension = skipFilesWithNoExtension;
        this.skipFilesWithTextPlainMimeType = skipFilesWithTextPlainMimeType;
        this.skipKnownFiles = skipKnownFiles;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    void setSkipFilesWithNoExtension(boolean skipFilesWithNoExtension) {
        this.skipFilesWithNoExtension = skipFilesWithNoExtension;
    }

    boolean skipFilesWithNoExtension() {
        return skipFilesWithNoExtension;
    }

    void setSkipFilesWithTextPlainMimeType(boolean skipFilesWithTextPlainMimeType) {
        this.skipFilesWithTextPlainMimeType = skipFilesWithTextPlainMimeType;
    }

    boolean skipFilesWithTextPlainMimeType() {
        return skipFilesWithTextPlainMimeType;
    }

    boolean skipKnownFiles() {
        return skipKnownFiles;
    }

    void setSkipKnownFiles(boolean skipKnownFiles) {
        this.skipKnownFiles = skipKnownFiles;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (0L == versionNumber) {
            /*
             * If the version number is set to the Java field default value of
             * zero, then skipKnownFiles is a new field. Change this to the
             * desired default value of true.
             */
            skipKnownFiles = true;
        }
        versionNumber = 1;
    }

}
