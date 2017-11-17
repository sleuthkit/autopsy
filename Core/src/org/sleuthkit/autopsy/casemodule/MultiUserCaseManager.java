/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Handles locating and opening multi-user cases.
 */
final class MultiUserCaseManager {

    private static final Logger LOGGER = Logger.getLogger(MultiUserCaseManager.class.getName());
    private static final String ALERT_FILE_NAME = "autoingest.alert";
    private static MultiUserCaseManager instance;
    private CoordinationService coordinationService;

    /**
     * Gets the multi-user case manager.
     *
     * @return The multi-user case manager singleton.
     *
     * @throws MultiUserCaseManagerException
     */
    synchronized static MultiUserCaseManager getInstance() throws MultiUserCaseManager.MultiUserCaseManagerException {
        if (null == instance) {
            instance = new MultiUserCaseManager();
        }
        return instance;
    }

    /**
     * Constructs an object that handles locating and opening multi-user cases.
     *
     * @throws MultiUserCaseManagerException
     */
    private MultiUserCaseManager() throws MultiUserCaseManagerException {
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new MultiUserCaseManager.MultiUserCaseManagerException("Failed to get the coordination service.", ex);
        }
    }

    /**
     * Gets a list of the cases in the top level case folder
     *
     * @return List of cases.
     *
     * @throws CoordinationServiceException
     */
    List<MultiUserCase> getCases() throws CoordinationServiceException {
        List<MultiUserCase> cases = new ArrayList<>();
        List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
        for (String node : nodeList) {
            Path casePath = Paths.get(node);
            File caseFolder = casePath.toFile();
            if (caseFolder.exists()) {
                /*
                 * Search for '*.aut' and 'autoingest.alert' files.
                 */
                File[] fileArray = caseFolder.listFiles();
                if (fileArray == null) {
                    continue;
                }
                String autFilePath = null;
                boolean alertFileFound = false;
                for (File file : fileArray) {
                    String name = file.getName().toLowerCase();
                    if (autFilePath == null && name.endsWith(".aut")) {
                        autFilePath = file.getAbsolutePath();
                        if (!alertFileFound) {
                            continue;
                        }
                    }
                    if (!alertFileFound && name.endsWith(ALERT_FILE_NAME)) {
                        alertFileFound = true;
                    }
                    if (autFilePath != null && alertFileFound) {
                        break;
                    }
                }

                if (autFilePath != null) {
                    try {
                        CaseStatus caseStatus;
                        if (alertFileFound) {
                            /*
                             * When an alert file exists, ignore the node data
                             * and use the ALERT status.
                             */
                            caseStatus = CaseStatus.ALERT;
                        } else {
                            byte[] rawData = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, node);
                            if (rawData != null && rawData.length > 0) {
                                /*
                                 * When node data exists, use the status stored
                                 * in the node data.
                                 */
                                CaseNodeData caseNodeData = new CaseNodeData(rawData);
                                if (caseNodeData.getErrorsOccurred()) {
                                    caseStatus = CaseStatus.ALERT;
                                } else {
                                    caseStatus = CaseStatus.OK;
                                }
                            } else {
                                /*
                                 * When no node data is available, use the 'OK'
                                 * status to avoid confusing the end-user.
                                 */
                                caseStatus = CaseStatus.OK;
                            }
                        }

                        CaseMetadata caseMetadata = new CaseMetadata(Paths.get(autFilePath));
                        cases.add(new MultiUserCase(casePath, caseMetadata, caseStatus));
                    } catch (CaseMetadata.CaseMetadataException | MultiUserCase.MultiUserCaseException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error reading case metadata file '%s'.", autFilePath), ex);
                    } catch (InterruptedException | CaseNodeData.InvalidDataException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error reading case node data for '%s'.", node), ex);
                    }
                }
            }
        }
        return cases;
    }

    /**
     * Opens a multi-user case.
     *
     * @param caseMetadataFilePath Path to the case metadata file.
     *
     * @throws CaseActionException
     */
    synchronized void openCase(Path caseMetadataFilePath) throws CaseActionException {
        /*
         * Open the case.
         */
        Case.openAsCurrentCase(caseMetadataFilePath.toString());
    }

    /**
     * Exception type thrown when there is an error completing a multi-user case
     * manager operation.
     */
    static final class MultiUserCaseManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing a multi-user case manager operation.
         *
         * @param message The exception message.
         */
        private MultiUserCaseManagerException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing a multi-user case manager operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private MultiUserCaseManagerException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * A representation of a multi-user case.
     */
    static class MultiUserCase implements Comparable<MultiUserCase> {

        private final Path caseDirectoryPath;
        private final String caseDisplayName;
        private final String metadataFileName;
        private final Date createDate;
        private final Date lastAccessedDate;
        private CaseStatus status;

        /**
         * Constructs a representation of a multi-user case
         *
         * @param caseDirectoryPath The case directory path.
         * @param caseMetadata      The case metadata.
         *
         * @throws MultiUserCaseException If no case metadata (.aut) file is
         *                                found in the case directory.
         */
        MultiUserCase(Path caseDirectoryPath, CaseMetadata caseMetadata, CaseStatus status) throws MultiUserCaseException {
            this.caseDirectoryPath = caseDirectoryPath;
            caseDisplayName = caseMetadata.getCaseDisplayName();
            metadataFileName = caseMetadata.getFilePath().getFileName().toString();
            this.status = status;
            BasicFileAttributes fileAttrs = null;
            try {
                fileAttrs = Files.readAttributes(Paths.get(caseDirectoryPath.toString(), metadataFileName), BasicFileAttributes.class);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error reading file attributes of case metadata file in %s, will use current time for case createDate/lastModfiedDate", caseDirectoryPath), ex);
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
         * Gets the case display name. This may differ from the name supplied to
         * the directory or metadata file names if a case has been renamed.
         *
         * @return The case display name.
         */
        String getCaseDisplayName() {
            return this.caseDisplayName;
        }

        /**
         * Gets the creation date for the case, defined as the create time of
         * the case metadata file.
         *
         * @return The case creation date.
         */
        Date getCreationDate() {
            return this.createDate;
        }

        /**
         * Gets the last accessed date for the case, defined as the last
         * accessed time of the case metadata file.
         *
         * @return The last accessed date.
         */
        Date getLastAccessedDate() {
            return this.lastAccessedDate;
        }

        /**
         * Gets metadata (.aut) file name.
         *
         * @return The metadata file name.
         */
        String getMetadataFileName() {
            return this.metadataFileName;
        }

        /**
         * Gets the status of this case.
         *
         * @return See CaseStatus enum definition.
         */
        CaseStatus getStatus() {
            return status;
        }

        /**
         * Gets the case metadata from a case directory path.
         *
         * @param caseDirectoryPath The case directory path.
         *
         * @return Case metadata.
         *
         * @throws CaseMetadata.CaseMetadataException If the CaseMetadata object
         *                                            cannot be constructed.
         * @throws MultiUserCaseException             If no case metadata (.aut)
         *                                            file is found in the case
         *                                            directory.
         */
        private CaseMetadata getCaseMetadataFromCaseDirectoryPath(Path caseDirectoryPath) throws CaseMetadata.CaseMetadataException, MultiUserCaseException {
            CaseMetadata caseMetadata = null;

            File directory = new File(caseDirectoryPath.toString());
            if (directory.isDirectory()) {
                File autFile = null;

                /*
                 * Attempt to find an AUT file via a directory scan.
                 */
                for (File file : directory.listFiles()) {
                    if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                        autFile = file;
                        break;
                    }
                }

                if (autFile == null || !autFile.isFile()) {
                    throw new MultiUserCaseException(String.format("No case metadata (.aut) file found in the case directory '%s'.", caseDirectoryPath.toString()));
                }

                caseMetadata = new CaseMetadata(Paths.get(autFile.getAbsolutePath()));
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
            hash = 71 * hash + Objects.hashCode(this.caseDisplayName);
            return hash;
        }

        /**
         * Compares this MultiUserCase object with another MultiUserCase object
         * for order.
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
             * Compares two MultiUserCase objects for order based on last
             * accessed date (descending).
             *
             * @param object      The first MultiUserCase object
             * @param otherObject The second MultiUserCase object.
             *
             * @return A negative integer, zero, or a positive integer as the
             *         first argument is less than, equal to, or greater than
             *         the second.
             */
            @Override
            public int compare(MultiUserCase object, MultiUserCase otherObject) {
                return -object.getLastAccessedDate().compareTo(otherObject.getLastAccessedDate());
            }
        }

        /**
         * Exception thrown when there is a problem creating a multi-user case.
         */
        final class MultiUserCaseException extends Exception {

            private static final long serialVersionUID = 1L;

            /**
             * Constructs an exception to throw when there is a problem creating
             * a multi-user case.
             *
             * @param message The exception message.
             */
            private MultiUserCaseException(String message) {
                super(message);
            }

            /**
             * Constructs an exception to throw when there is a problem creating
             * a multi-user case.
             *
             * @param message The exception message.
             * @param cause   The cause of the exception, if it was an
             *                exception.
             */
            private MultiUserCaseException(String message, Throwable cause) {
                super(message, cause);
            }
        }

    }

    static enum CaseStatus {
        OK,
        ALERT
    }

}
