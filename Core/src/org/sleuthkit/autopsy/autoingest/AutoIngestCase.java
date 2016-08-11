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
package org.sleuthkit.autopsy.autoingest;

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
 * A representation of case created by automated ingest.
 */
class AutoIngestCase implements Comparable<AutoIngestCase> {

    private static final Logger logger = Logger.getLogger(AutoIngestCase.class.getName());
    private final Path caseFolderPath;
    private final String caseName;
    private final Path metadataFilePath;
    private Date createDate;
    private Date lastModfiedDate;

    /**
     * Constructs s representation of case created by automated ingest.
     *
     * @param caseFolderPath The case folder path.
     */
    AutoIngestCase(Path caseFolderPath) {
        this.caseFolderPath = caseFolderPath;
        caseName = PathUtils.caseNameFromCaseFolderPath(caseFolderPath);
        metadataFilePath = caseFolderPath.resolve(caseName + CaseMetadata.getFileExtension());
        try {
            BasicFileAttributes fileAttrs = Files.readAttributes(metadataFilePath, BasicFileAttributes.class);
            createDate = new Date(fileAttrs.creationTime().toMillis());
            lastModfiedDate = new Date(fileAttrs.lastModifiedTime().toMillis());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, will use current time for case createDate/lastModfiedDate", caseFolderPath), ex);
            createDate = new Date();
            lastModfiedDate = new Date();
        }
    }

    /**
     * Gets the case folder path.
     *
     * @return The case folder path.
     */
    Path getCaseFolderPath() {
        return this.caseFolderPath;
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
     * @return The creation date.
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
    Date getLastAccessedDate() {
        try {
            BasicFileAttributes fileAttrs = Files.readAttributes(metadataFilePath, BasicFileAttributes.class);
            createDate = new Date(fileAttrs.creationTime().toMillis());
            lastModfiedDate = new Date(fileAttrs.lastModifiedTime().toMillis());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, lastModfiedDate time not updated", caseFolderPath), ex);
        }
        return lastModfiedDate;
    }

    /**
     * Gets the status of this case based on state files in the case folder.
     *
     * @return See CaseStatus enum definition.
     */
    CaseStatus getStatus() {
        try {
            if (StateFile.exists(caseFolderPath, StateFile.Type.CANCELLED)) {
                return CaseStatus.CANCELLATIONS;
            } else if (StateFile.exists(caseFolderPath, StateFile.Type.ERROR)) {
                return CaseStatus.ERRORS;
            } else if (StateFile.exists(caseFolderPath, StateFile.Type.INTERRUPTED)) {
                return CaseStatus.INTERRUPTS;
            } else {
                return CaseStatus.OK;
            }
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, String.format("Failed to determine status of case at %s", caseFolderPath), ex);
            return CaseStatus.ERRORS;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AutoIngestCase)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        AutoIngestCase rhs = (AutoIngestCase) obj;

        return this.caseFolderPath.toString().equals(rhs.caseFolderPath.toString());
    }

    /**
     * @inheritDoc
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.caseFolderPath);
        hash = 71 * hash + Objects.hashCode(this.createDate);
        hash = 71 * hash + Objects.hashCode(this.caseName);
        return hash;
    }

    /**
     * Default sorting is by last accessed date, descending.
     */
    @Override
    public int compareTo(AutoIngestCase o) {
        return -this.lastModfiedDate.compareTo(o.getLastAccessedDate());
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestCase> on reverse
     * chronological date created (descending)
     *
     */
    static class ReverseDateLastAccessedComparator implements Comparator<AutoIngestCase> {

        @Override
        public int compare(AutoIngestCase o1, AutoIngestCase o2) {
            return -o1.getLastAccessedDate().compareTo(o2.getLastAccessedDate());
        }
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestCase> on reverse
     * chronological date created (descending)
     *
     */
    static class ReverseDateCreatedComparator implements Comparator<AutoIngestCase> {

        @Override
        public int compare(AutoIngestCase o1, AutoIngestCase o2) {
            return -o1.getCreationDate().compareTo(o2.getCreationDate());
        }
    }
}
