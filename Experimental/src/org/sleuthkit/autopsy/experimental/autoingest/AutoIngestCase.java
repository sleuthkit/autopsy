/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A representation of a case created by automated ingest.
 */
class AutoIngestCase implements Comparable<AutoIngestCase> {

    private static final Logger logger = Logger.getLogger(AutoIngestCase.class.getName());
    private final Path caseDirectoryPath;
    private final String caseName;
    private final Path metadataFilePath;
    private final Date createDate;
    private Date lastModfiedDate;

    /**
     * Constructs a representation of case created by automated ingest.
     *
     * @param caseDirectoryPath The case directory path.
     */
    // RJCTODO: Throw instead of reporting error, let client decide what to do.
    AutoIngestCase(Path caseDirectoryPath) {
        this.caseDirectoryPath = caseDirectoryPath;
        caseName = PathUtils.caseNameFromCaseDirectoryPath(caseDirectoryPath);
        metadataFilePath = caseDirectoryPath.resolve(caseName + CaseMetadata.getFileExtension());
        BasicFileAttributes fileAttrs = null;
        try {
            fileAttrs = Files.readAttributes(metadataFilePath, BasicFileAttributes.class);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, will use current time for case createDate/lastModfiedDate", caseDirectoryPath), ex);
        }
        if (null != fileAttrs) {
            createDate = new Date(fileAttrs.creationTime().toMillis());
            lastModfiedDate = new Date(fileAttrs.lastModifiedTime().toMillis());
        } else {
            createDate = new Date();
            lastModfiedDate = new Date();
        }
    }

    /**
     * Gets the case directory path.
     *
     * @return The case directory path.
     */
    Path getCaseDirectoryPath() {
        return this.caseDirectoryPath;
    }

    /**
     * Gets the case name.
     *
     * @return The case name.
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Gets the creation date for the case, defined as the create time of the
     * case metadata file.
     *
     * @return The case creation date.
     */
    Date getCreationDate() {
        return this.createDate;
    }

    /**
     * Gets the last accessed date for the case, defined as the last modified
     * time of the case metadata file.
     *
     * @return The last accessed date.
     */
    // RJCTODO: Throw instead of reporting error, let client decide what to do.
    Date getLastAccessedDate() {
        try {
            BasicFileAttributes fileAttrs = Files.readAttributes(metadataFilePath, BasicFileAttributes.class);
            lastModfiedDate = new Date(fileAttrs.lastModifiedTime().toMillis());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, lastModfiedDate time not updated", caseDirectoryPath), ex);
        }
        return lastModfiedDate;
    }

    /**
     * Gets the status of this case based on the auto ingest result file in the
     * case directory.
     *
     * @return See CaseStatus enum definition.
     */
    CaseStatus getStatus() {
        if (AutoIngestAlertFile.exists(caseDirectoryPath)) {
            return CaseStatus.ALERT;
        } else {
            return CaseStatus.OK;
        }
    }

    /**
     * Indicates whether or not some other object is "equal to" this
     * AutoIngestCase object.
     *
     * @param other The other object.
     *
     * @return True or false.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AutoIngestCase)) {
            return false;
        }
        if (other == this) {
            return true;
        }
        return this.caseDirectoryPath.toString().equals(((AutoIngestCase) other).caseDirectoryPath.toString());
    }

    /**
     * Returns a hash code value for this AutoIngestCase object.
     *
     * @return The has code.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.caseDirectoryPath);
        hash = 71 * hash + Objects.hashCode(this.createDate);
        hash = 71 * hash + Objects.hashCode(this.caseName);
        return hash;
    }

    /**
     * Compares this AutopIngestCase object with abnother AutoIngestCase object
     * for order.
     */
    @Override
    public int compareTo(AutoIngestCase other) {
        return -this.lastModfiedDate.compareTo(other.getLastAccessedDate());
    }

    /**
     * Comparator for a descending order sort on date created.
     */
    static class LastAccessedDateDescendingComparator implements Comparator<AutoIngestCase> {

        /**
         * Compares two AutoIngestCase objects for order based on last accessed
         * date (descending).
         *
         * @param object      The first AutoIngestCase object
         * @param otherObject The second AuotIngestCase object.
         *
         * @return A negative integer, zero, or a positive integer as the first
         *         argument is less than, equal to, or greater than the second.
         */
        @Override
        public int compare(AutoIngestCase object, AutoIngestCase otherObject) {
            return -object.getLastAccessedDate().compareTo(otherObject.getLastAccessedDate());
        }
    }

    enum CaseStatus {

        OK,
        ALERT
    }

}
