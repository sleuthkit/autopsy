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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest options for the file extension mismatch detector ingest module.
 */
final class FileExtMismatchDetectorModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private long versionNumber;
    private boolean skipFilesWithNoExtension;
    private boolean skipKnownFiles;
    private CHECK_TYPE checkType;

    // no longer used, but kept in to maintain compatibility with serialization
    private boolean skipFilesWithTextPlainMimeType;

    enum CHECK_TYPE {
        ALL, NO_TEXT_FILES, ONLY_MEDIA_AND_EXE
    }
    /*
     * For "basic mode" only image and executable files should be checked for
     * mismatches. This is a set of the MIME types that would be checked when
     * checkType is ONLY_MEDIA_AND_EXE.
     */
    static final Set<String> MEDIA_AND_EXE_MIME_TYPES = Stream.of(
            "image/bmp",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/x-ms-bmp",
            "application/dos-exe",
            "application/exe",
            "application/x-dosexec",
            "application/x-exe",
            "application/x-msdownload").collect(Collectors.toSet());

    FileExtMismatchDetectorModuleSettings() {
        this.skipFilesWithNoExtension = true;
        this.skipKnownFiles = true;
        this.checkType = CHECK_TYPE.NO_TEXT_FILES;
    }

    FileExtMismatchDetectorModuleSettings(boolean skipKnownFiles, boolean skipFilesWithNoExtension, CHECK_TYPE checkType) {
        this.skipFilesWithNoExtension = skipFilesWithNoExtension;
        this.skipKnownFiles = skipKnownFiles;
        this.checkType = checkType;
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

    void setSkipKnownFiles(boolean skipKnownFiles) {
        this.skipKnownFiles = skipKnownFiles;
    }

    boolean skipKnownFiles() {
        return skipKnownFiles;
    }

    void setCheckType(CHECK_TYPE checkType) {
        this.checkType = checkType;
    }

    CHECK_TYPE getCheckType() {
        return checkType;
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
