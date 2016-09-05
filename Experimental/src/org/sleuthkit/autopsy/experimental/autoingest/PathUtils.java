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

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;

final class PathUtils {

    private static final List<String> CASE_METADATA_FILE_EXTS = Arrays.asList(new String[]{CaseMetadata.getFileExtension()});
    private static final GeneralFilter caseMetadataFileFilter = new GeneralFilter(CASE_METADATA_FILE_EXTS, "Autopsy Case File");

    /**
     * Searches a given folder for the most recently modified case folder for a
     * case.
     *
     * @param folderToSearch The folder to be searched.
     * @param caseName       The name of the case for which a case folder is to
     *                       be found.
     *
     * @return The path of the case folder, or null if it is not found.
     */
    static Path findCaseDirectory(Path folderToSearch, String caseName) {
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
     * Gets a listing of case folders in a given folder.
     *
     * @param folderToSearch The path of the folder to search.
     *
     * @return A list of the output case folder paths.
     */
    static List<Path> findCaseFolders(Path folderToSearch) { // RJCTODO: Rename
        File searchFolder = new File(folderToSearch.toString());
        if (!searchFolder.isDirectory()) {
            return Collections.emptyList();
        }
        String[] caseFolders = searchFolder.list(new CaseFolderFilter(null));
        List<Path> caseFolderPaths = new ArrayList<>();
        for (String path : caseFolders) {
            caseFolderPaths.add(Paths.get(folderToSearch.toString(), path));
        }
        return caseFolderPaths;
    }

    /**
     * Determines whether or not there is a case metadata file in a given
     * folder.
     *
     * @param folderPath Path to the folder to search.
     *
     * @return True or false.
     */
    static boolean hasCaseMetadataFile(Path folderPath) {
        /**
         * TODO: If need be, this can be rewritten without the FilenameFilter so
         * that it does not necessarily visit every file in the folder.
         */
        File folder = folderPath.toFile();
        if (!folder.isDirectory()) {
            return false;
        }

        String[] caseDataFiles = folder.list((File folder1, String fileName) -> {
            File file = new File(folder1, fileName);
            if (file.isFile()) {
                return caseMetadataFileFilter.accept(file);
            }
            return false;
        });
        return caseDataFiles.length != 0;
    }

    /**
     * Extracts the case name from a case folder path.
     *
     * @param caseFolderPath A case folder path.
     *
     * @return A case name, with the time stamp suffix removed.
     */
    static String caseNameFromCaseDirectoryPath(Path caseFolderPath) {
        String caseName = caseFolderPath.getFileName().toString();
        if (caseName.length() > TimeStampUtils.getTimeStampLength()) {
            return caseName.substring(0, caseName.length() - TimeStampUtils.getTimeStampLength());
        } else {
            return caseName;
        }
    }

    /**
     * Creates a case folder path. Does not create the folder described by the
     * path.
     *
     * @param caseFoldersPath The root case folders path.
     * @param caseName        The name of the case.
     *
     * @return A case folder path with a time stamp suffix.
     */
    static Path createCaseFolderPath(Path caseFoldersPath, String caseName) { // RJCTODO: Rename
        String folderName = caseName + "_" + TimeStampUtils.createTimeStamp();
        return Paths.get(caseFoldersPath.toString(), folderName);
    }

    private static class CaseFolderFilter implements FilenameFilter {

        private final String caseName;

        CaseFolderFilter(String caseName) {
            this.caseName = caseName;
        }

        @Override
        public boolean accept(File folder, String fileName) {
            File file = new File(folder, fileName);
            if (file.isDirectory() && fileName.length() > TimeStampUtils.getTimeStampLength()) {
                Path filePath = Paths.get(file.getPath());
                if (TimeStampUtils.endsWithTimeStamp(fileName)) {
                    if (null != caseName) {
                        String fileNamePrefix = fileName.substring(0, fileName.length() - TimeStampUtils.getTimeStampLength());
                        if (fileNamePrefix.equals(caseName)) {
                            return hasCaseMetadataFile(filePath);
                        }
                    } else {
                        return hasCaseMetadataFile(filePath);
                    }
                }
            }
            return false;
        }

    }

    /**
     * Supress creation of instances of this class.
     */
    private PathUtils() {
    }

}
