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
 * Ingest options for the file extension mismatch detection ingest module.
 */
final class FileExtMismatchDetectorModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private long versionNumber;
    private boolean skipFilesWithNoExtension;
    @Deprecated
    private boolean skipFilesWithTextPlainMimeType; // No longer used, retained to maintain serialization compatibility.
    private boolean skipKnownFiles;
    private CHECK_TYPE checkType;

    /*
     * Extension mismatches can be checked for all files, for all files except
     * text files, or for media and executable files only.
     */
    enum CHECK_TYPE {
        ALL, NO_TEXT_FILES, ONLY_MEDIA_AND_EXE
    }

    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_MEDIA_AND_EXE.
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
            "application/x-msdownload",
            "application/msword",
            "application/pdf",
            "application/rtf",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.text",
            "application/x-msoffice",
            "application/x-ooxml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
    ).collect(Collectors.toSet());

    /**
     * Constructs an object with the ingest options for the file extension
     * mismatch detection ingest module.
     */
    FileExtMismatchDetectorModuleSettings() {
        this.versionNumber = 2;
        this.skipFilesWithNoExtension = true;
        this.skipKnownFiles = true;
        this.checkType = CHECK_TYPE.ONLY_MEDIA_AND_EXE;
    }

    /**
     * Gets the serialization version number.
     *
     * @return A serialization version number.
     */
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Sets the flag indicating whether or not files without extensions should
     * be skipped during file extension mismatch checking.
     *
     * @param skipFilesWithNoExtension The desired value of the flag.
     */
    void setSkipFilesWithNoExtension(boolean skipFilesWithNoExtension) {
        this.skipFilesWithNoExtension = skipFilesWithNoExtension;
    }

    /**
     * Gets the flag indicating whether or not files without extensions should
     * be skipped during file extension mismatch checking.
     *
     * @return The flag value.
     */
    boolean skipFilesWithNoExtension() {
        return skipFilesWithNoExtension;
    }

    /**
     * Sets the flag indicating whether or not known files should be skipped
     * during file extension mismatch checking.
     *
     * @param skipKnownFiles The desired value of the flag.
     */
    void setSkipKnownFiles(boolean skipKnownFiles) {
        this.skipKnownFiles = skipKnownFiles;
    }

    /**
     * Gets the flag indicating whether or not known files should be skipped
     * during file extension mismatch checking.
     *
     * @return The flag value.
     */
    boolean skipKnownFiles() {
        return skipKnownFiles;
    }

    /**
     * Sets whether extension mismatches should be checked for all files, for
     * all files except text files, or for media and executable files only.
     *
     * @param checkType The check type.
     */
    void setCheckType(CHECK_TYPE checkType) {
        this.checkType = checkType;
    }

    /**
     * Gets whether extension mismatches should be checked for all files, for
     * all files except text files, or for media and executable files only.
     *
     * @return checkType The check type.
     */
    CHECK_TYPE getCheckType() {
        return checkType;
    }

    /**
     * Called by convention by the serialization infrastructure when
     * deserializing a FileExtMismatchDetectorModuleSettings object.
     *
     * @param in The object input stream provided by the serialization
     *           infrastructure.
     *
     * @throws IOException            If there is a problem reading the
     *                                serialized data.
     * @throws ClassNotFoundException If the class definition for the serialized
     *                                data cannot be found.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (0L == versionNumber) {
            /*
             * If the version number is set to the Java field default value of
             * zero, then versionNumber and skipKnownFiles are new fields.
             * Change this to the desired default value of true.
             */
            skipKnownFiles = true;
            versionNumber = 1;
        }
        if (1 == versionNumber) {
            /*
             * Set the default value of the new checkType field, it is currently
             * null.
             */
            checkType = CHECK_TYPE.ONLY_MEDIA_AND_EXE;
            versionNumber = 2;
        }
    }

}
