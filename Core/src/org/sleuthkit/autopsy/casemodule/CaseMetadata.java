/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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

import java.nio.file.Path;

/**
 * Provides access to case metadata.
 */
public final class CaseMetadata {

    /**
     * Exception thrown by the CaseMetadata class when there is a problem
     * accessing the metadata for a case.
     */
    public final static class CaseMetadataException extends Exception {

        private CaseMetadataException(String message) {
            super(message);
        }

        private CaseMetadataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Case.CaseType caseType;
    private final String caseName;
    private final String caseNumber;
    private final String examiner;
    private final String caseDirectory;
    private final String caseDatabaseName;

    /**
     * Constructs an object that provides access to case metadata.
     *
     * @param metadataFilePath
     */
    public CaseMetadata(Path metadataFilePath) throws CaseMetadataException {
        try {
            // NOTE: This class will eventually replace XMLCaseManagement.
            // This constructor should parse all of the metadata. In the future,
            // case metadata may be moved into the case database.
            XMLCaseManagement metadata = new XMLCaseManagement();
            metadata.open(metadataFilePath.toString());
            caseType = metadata.getCaseType();
            caseName = metadata.getCaseName();
            if (caseName.isEmpty()) {
                throw new CaseMetadataException("Case name missing");
            }
            caseNumber = metadata.getCaseNumber();
            examiner = metadata.getCaseExaminer();
            caseDirectory = metadata.getCaseDirectory();
            if (caseDirectory.isEmpty()) {
                throw new CaseMetadataException("Case directory missing");
            }
            caseDatabaseName = metadata.getDatabaseName();
            if (Case.CaseType.MULTI_USER_CASE == caseType && caseDatabaseName.isEmpty()) {
                throw new CaseMetadataException("Case database name missing");
            }
        } catch (CaseActionException ex) {
            throw new CaseMetadataException(ex.getLocalizedMessage(), ex);
        }
    }

    /**
     * Gets the case type.
     *
     * @return The case type.
     */
    public Case.CaseType getCaseType() {
        return this.caseType;
    }

    /**
     * Gets the case name.
     *
     * @return The case name.
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * Gets the case number.
     *
     * @return The case number, may be empty.
     */
    public String getCaseNumber() {
        return caseNumber;
    }

    /**
     * Gets the examiner.
     *
     * @return The examiner, may be empty.
     */
    public String getExaminer() {
        return examiner;
    }

    /**
     * Gets the case directory.
     *
     * @return The case directory.
     */
    public String getCaseDirectory() {
        return caseDirectory;
    }

    /**
     * Gets the case database name.
     *
     * @return The case database name, will be empty for a single-user case.
     */
    public String getCaseDatabaseName() {
        return caseDatabaseName;
    }

}
