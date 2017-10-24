/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * A representation of a case created by automated ingest.
 */
class MultiUserCase implements Comparable<MultiUserCase> {

    private static final Logger logger = Logger.getLogger(MultiUserCase.class.getName());
    private final Path caseDirectoryPath;
    private final String caseName;
    private final Path metadataFilePath;
    private final Date createDate;
    private final Date lastAccessedDate;

    /**
     * Constructs a representation of case created by automated ingest.
     *
     * @param caseDirectoryPath The case directory path.
     */
    MultiUserCase(Path caseDirectoryPath) throws CaseMetadata.CaseMetadataException {
        CaseMetadata caseMetadata = null;
        
        try {
            caseMetadata = getCaseMetadataFromCaseDirectoryPath(caseDirectoryPath);
        } catch (CaseMetadata.CaseMetadataException ex) {
            logger.log(Level.SEVERE, String.format("Error reading the case metadata for %s.", caseDirectoryPath), ex);
            throw ex;
        }
        
        this.caseDirectoryPath = caseDirectoryPath;
        caseName = caseMetadata.getCaseDisplayName();
        metadataFilePath = caseDirectoryPath.resolve(caseMetadata.getCaseName() + CaseMetadata.getFileExtension());
        BasicFileAttributes fileAttrs = null;
        try {
            fileAttrs = Files.readAttributes(metadataFilePath, BasicFileAttributes.class);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, will use current time for case createDate/lastModfiedDate", caseDirectoryPath), ex);
        }
        if (null != fileAttrs) {
            createDate = new Date(fileAttrs.creationTime().toMillis());
            lastAccessedDate = new Date(fileAttrs.lastAccessTime().toMillis());
        } else {
            createDate = new Date();
            lastAccessedDate = new Date();
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
     * Gets the last accessed date for the case, defined as the last accessed
     * time of the case metadata file.
     *
     * @return The last accessed date.
     */
    Date getLastAccessedDate() {
        return this.lastAccessedDate;
    }

    /**
     * Gets the status of this case based on the auto ingest result file in the
     * case directory.
     *
     * @return See CaseStatus enum definition.
     */
    CaseStatus getStatus() {
        if(caseDirectoryPath.resolve("autoingest.alert").toFile().exists()) {
            return CaseStatus.ALERT;
        } else {
            return CaseStatus.OK;
        }
    }

    /**
     * Gets the case metadata from a case directory path.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return Case metadata.
     */
    static CaseMetadata getCaseMetadataFromCaseDirectoryPath(Path caseDirectoryPath) throws CaseMetadata.CaseMetadataException {
        CaseMetadata caseMetadata = null;
        
        File directory = new File(caseDirectoryPath.toString());
        if (directory.isDirectory()) {
            String fileNamePrefix = directory.getName();
            if (TimeStampUtils.endsWithTimeStamp(fileNamePrefix)) {
                fileNamePrefix = fileNamePrefix.substring(0, fileNamePrefix.length() - TimeStampUtils.getTimeStampLength());
            }
            
            File file = new File(directory + "/" + fileNamePrefix + CaseMetadata.getFileExtension());
            if(file.isFile()) {
                caseMetadata = new CaseMetadata(Paths.get(file.getAbsolutePath()));
            }
        }
        
        return caseMetadata;
    }

    /**
     * Indicates whether or not some other object is "equal to" this
     * MultiUserCase object.
     *
     * @param other The other object.
     *
     * @return True or false.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MultiUserCase)) {
            return false;
        }
        if (other == this) {
            return true;
        }
        return this.caseDirectoryPath.toString().equals(((MultiUserCase) other).caseDirectoryPath.toString());
    }

    /**
     * Returns a hash code value for this MultiUserCase object.
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
     * Compares this AutopIngestCase object with abnother MultiUserCase object
 for order.
     */
    @Override
    public int compareTo(MultiUserCase other) {
        return -this.lastAccessedDate.compareTo(other.getLastAccessedDate());
    }

    /**
     * Comparator for a descending order sort on date created.
     */
    static class LastAccessedDateDescendingComparator implements Comparator<MultiUserCase> {

        /**
         * Compares two MultiUserCase objects for order based on last accessed
 date (descending).
         *
         * @param object      The first MultiUserCase object
         * @param otherObject The second AuotIngestCase object.
         *
         * @return A negative integer, zero, or a positive integer as the first
         *         argument is less than, equal to, or greater than the second.
         */
        @Override
        public int compare(MultiUserCase object, MultiUserCase otherObject) {
            return -object.getLastAccessedDate().compareTo(otherObject.getLastAccessedDate());
        }
    }

    enum CaseStatus {

        OK,
        ALERT
    }

}
