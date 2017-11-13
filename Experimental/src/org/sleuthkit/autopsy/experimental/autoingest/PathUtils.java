/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2017 Basis Technology Corp.
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
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

final class PathUtils {

    private final static String CASE_METADATA_EXT = CaseMetadata.getFileExtension();

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
    static List<Path> findCaseFolders(Path folderToSearch) {
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
    static Path createCaseFolderPath(Path caseFoldersPath, String caseName) {
        String folderName = caseName + "_" + TimeStampUtils.createTimeStamp();
        return Paths.get(caseFoldersPath.toString(), folderName);
    }

    /**
     * Supress creation of instances of this class.
     */
    private PathUtils() {
    }

    private static class CaseFolderFilter implements FilenameFilter {

        private final String caseName;

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
