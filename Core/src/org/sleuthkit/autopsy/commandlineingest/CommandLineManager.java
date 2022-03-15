/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import static org.sleuthkit.autopsy.casemodule.CaseMetadata.getFileExtension;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * Base class for the command line managers.
 */
class CommandLineManager {

    private static final String LOG_DIR_NAME = "Command Output";
    private static final Logger LOGGER = Logger.getLogger(CommandLineOpenCaseManager.class.getName());

    /**
     * Opens existing case using full path to case directory or full path to
     * .aut file.
     *
     * @param casePath full path to case directory or full path to .aut file
     *
     * @throws CaseActionException
     */
    Case openCase(String casePath) throws CaseActionException {

        String metadataFilePath;
        if (casePath.endsWith(".aut") && (new File(casePath)).isFile()) {
            LOGGER.log(Level.INFO, "Opening case {0}", casePath);
            metadataFilePath = casePath;
        } else {
            LOGGER.log(Level.INFO, "Opening case in directory {0}", casePath);
            metadataFilePath = findAutFile(casePath);
        }
        Case.openAsCurrentCase(metadataFilePath);

        Case newCase = Case.getCurrentCase();
        LOGGER.log(Level.INFO, "Opened case {0}", newCase.getName());

        return newCase;
    }

    /**
     * Finds the path to the .aut file for the specified case directory.
     *
     * @param caseDirectory the directory to check for a .aut file
     *
     * @return the path to the first .aut file found in the directory
     *
     * @throws CaseActionException if there was an issue finding a .aut file
     */
    private String findAutFile(String caseDirectory) throws CaseActionException {
        File caseFolder = Paths.get(caseDirectory).toFile();
        if (caseFolder.exists()) {
            /*
             * Search for '*.aut' files.
             */
            File[] fileArray = caseFolder.listFiles();
            if (fileArray == null) {
                throw new CaseActionException("No files found in case directory");
            }
            String autFilePath = null;
            for (File file : fileArray) {
                String name = file.getName().toLowerCase();
                if (autFilePath == null && name.endsWith(getFileExtension())) {
                    return file.getAbsolutePath();
                }
            }
            throw new CaseActionException("No .aut files found in case directory");
        }
        throw new CaseActionException("Case directory was not found");
    }

    /**
     * Creates a new case using arguments passed in from command line
     * CREATE_CASE command.
     *
     * @param baseCaseName Case name
     * @param rootOutputDirectory Full path to directory in which case output
     * folder will be created
     * @param caseType Type of case being created
     *
     * @throws CaseActionException
     */
    Case createCase(String baseCaseName, String rootOutputDirectory, Case.CaseType caseType) throws CaseActionException {

        LOGGER.log(Level.INFO, "Creating new case {0} in directory {1}", new Object[]{baseCaseName, rootOutputDirectory});
        Path caseDirectoryPath = findCaseDirectory(Paths.get(rootOutputDirectory), baseCaseName);
        if (null != caseDirectoryPath) {
            // found an existing case directory for same case name. the input case name must be unique. Exit.
            LOGGER.log(Level.SEVERE, "Case {0} already exists. Case name must be unique. Exiting", baseCaseName);
            throw new CaseActionException("Case " + baseCaseName + " already exists. Case name must be unique. Exiting");
        } else {
            caseDirectoryPath = createCaseFolderPath(Paths.get(rootOutputDirectory), baseCaseName);

            // Create the case directory
            Case.createCaseDirectory(caseDirectoryPath.toString(), caseType);

            CaseDetails caseDetails = new CaseDetails(baseCaseName);
            Case.createAsCurrentCase(caseType, caseDirectoryPath.toString(), caseDetails);
        }

        Case caseForJob = Case.getCurrentCase();
        LOGGER.log(Level.INFO, "Created case {0}", caseForJob.getName());
        return caseForJob;
    }

    /**
     * Creates a case folder path. Does not create the folder described by the
     * path.
     *
     * @param caseFoldersPath The root case folders path.
     * @param caseName The name of the case.
     *
     * @return A case folder path with a time stamp suffix.
     */
    private Path createCaseFolderPath(Path caseFoldersPath, String caseName) {
        String folderName = caseName + "_" + TimeStampUtils.createTimeStamp();
        return Paths.get(caseFoldersPath.toString(), folderName);
    }

    /**
     * Opens an existing case using arguments passed in from command line.
     *
     * @param caseName Case name
     * @param rootOutputDirectory Full path to top level directory in which case
     * output folder is located
     *
     * @throws CaseActionException
     */
    Case openExistingCase(String caseName, String rootOutputDirectory) throws CaseActionException {
        LOGGER.log(Level.INFO, "Opening case {0} in directory {1}", new Object[]{caseName, rootOutputDirectory});
        Path caseDirectoryPath = findCaseDirectory(Paths.get(rootOutputDirectory), caseName);
        if (null != caseDirectoryPath) {
            // found an existing case directory for same case name.
            Path metadataFilePath = caseDirectoryPath.resolve(caseName + CaseMetadata.getFileExtension());
            Case.openAsCurrentCase(metadataFilePath.toString());
        } else {
            // did not find existing case directory for same case name. Exit.
            LOGGER.log(Level.SEVERE, "Case {0} doesn't exist. Exiting", caseName);
            throw new CaseActionException("Case " + caseName + " doesn't exist. Exiting");
        }

        Case caseForJob = Case.getCurrentCase();
        LOGGER.log(Level.INFO, "Opened case {0}", caseForJob.getName());
        return caseForJob;
    }

    /**
     * Searches a given folder for the most recently modified case folder for a
     * case.
     *
     * @param folderToSearch The folder to be searched.
     * @param caseName The name of the case for which a case folder is to be
     * found.
     *
     * @return The path of the case folder, or null if it is not found.
     */
    private Path findCaseDirectory(Path folderToSearch, String caseName) {
        File searchFolder = new File(folderToSearch.toString());
        if (!searchFolder.isDirectory()) {
            return null;
        }
        Path caseFolderPath = null;
        String[] candidateFolders = searchFolder.list(new CaseFolderFilter(caseName));
        long mostRecentModified = 0;
        for (String candidateFolder : candidateFolders) {
            File file = new File(candidateFolder);
            if (file.lastModified() >= mostRecentModified) {
                mostRecentModified = file.lastModified();
                caseFolderPath = Paths.get(folderToSearch.toString(), file.getPath());
            }
        }
        return caseFolderPath;
    }

    /**
     * Returns full path to directory where command outputs should be saved.
     *
     * @param caseForJob Case object
     *
     * @return Full path to directory where command outputs should be saved
     */
    String getOutputDirPath(Case caseForJob) {
        return caseForJob.getCaseDirectory() + File.separator + LOG_DIR_NAME;
    }

    private static class CaseFolderFilter implements FilenameFilter {

        private final String caseName;
        private final static String CASE_METADATA_EXT = CaseMetadata.getFileExtension();

        CaseFolderFilter(String caseName) {
            this.caseName = caseName;
        }

        @Override
        public boolean accept(File folder, String fileName) {
            File file = new File(folder, fileName);
            if (fileName.length() > TimeStampUtils.getTimeStampLength() && file.isDirectory()) {
                if (TimeStampUtils.endsWithTimeStamp(fileName)) {
                    if (null != caseName) {
                        String fileNamePrefix = fileName.substring(0, fileName.length() - TimeStampUtils.getTimeStampLength());
                        if (fileNamePrefix.equals(caseName)) {
                            return hasCaseMetadataFile(file);
                        }
                    } else {
                        return hasCaseMetadataFile(file);
                    }
                }
            }
            return false;
        }

        /**
         * Determines whether or not there is a case metadata file in a given
         * folder.
         *
         * @param folder The file object representing the folder to search.
         *
         * @return True or false.
         */
        private static boolean hasCaseMetadataFile(File folder) {
            for (File file : folder.listFiles()) {
                if (file.getName().toLowerCase().endsWith(CASE_METADATA_EXT) && file.isFile()) {
                    return true;
                }
            }
            return false;
        }
    }

}
