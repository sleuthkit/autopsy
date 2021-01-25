/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.multiusercases;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * Utility methods for using the coordination service for multi-user cases.
 */
public final class CoordinationServiceUtils {

    private static final String CASE_AUTO_INGEST_LOG_NAME = "AUTO_INGEST_LOG.TXT"; //NON-NLS
    private static final String RESOURCES_LOCK_SUFFIX = "_RESOURCES"; //NON-NLS

    /**
     * Gets the path of the case resources coordination service node for a case.
     * This coordiantion service node is used for case resource locking.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return The case resources coordination service node path.
     */
    public static String getCaseResourcesNodePath(Path caseDirectoryPath) {
        return caseDirectoryPath + RESOURCES_LOCK_SUFFIX;
    }

    /**
     * Gets the path of the case auto ingest log coordination service node for a
     * case. This coordination service node is used for serializing case auto
     * ingest log writes.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return The case auto ingest log coordination service node path.
     */
    public static String getCaseAutoIngestLogNodePath(Path caseDirectoryPath) {
        return Paths.get(caseDirectoryPath.toString(), CASE_AUTO_INGEST_LOG_NAME).toString();
    }

    /**
     * Gets the path of the case directory coordination service node for a case.
     * This coordination service node is used for locking the case directory and
     * for storing data about the case.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return The case directory coordination service node path.
     */
    public static String getCaseDirectoryNodePath(Path caseDirectoryPath) {
        return caseDirectoryPath.toString();
    }

    /**
     * Gets the path of the case name coordination service node for a case. This
     * coordination service node is used to lock the case name so that only one
     * node at a time can create a case with a particular name.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return The case name coordination service node path.
     */
    public static String getCaseNameNodePath(Path caseDirectoryPath) {
        String caseName = caseDirectoryPath.getFileName().toString();
        if (TimeStampUtils.endsWithTimeStamp(caseName)) {
            caseName = TimeStampUtils.removeTimeStamp(caseName);
            if (caseName.endsWith("_")) {
                caseName = caseName.substring(0, caseName.length() - 1);
            }
        }
        return caseName;
    }

    /**
     * Determines whether or not a coordination service node path is a case auto
     * ingest node path.
     *
     * @param nodePath The node path.
     *
     * @return True or false.
     */
    public static boolean isCaseAutoIngestLogNodePath(String nodePath) {
        return Paths.get(nodePath).getFileName().toString().equals(CASE_AUTO_INGEST_LOG_NAME);
    }

    /**
     * Determines whether or not a coordination service node path is a case
     * resources node path.
     *
     * @param nodePath The node path.
     *
     * @return True or false.
     */
    public static boolean isCaseResourcesNodePath(String nodePath) {
        return Paths.get(nodePath).getFileName().toString().endsWith(RESOURCES_LOCK_SUFFIX);
    }

    /**
     * Determines whether or not a coordination service node path is a case name
     * node path.
     *
     * @param nodePath The node path.
     *
     * @return True or false.
     */
    public static boolean isCaseNameNodePath(String nodePath) {
        return !(nodePath.contains("\\") || nodePath.contains("//"));
    }

    /**
     * Prevents instantiation of this uitlity class.
     */
    private CoordinationServiceUtils() {
    }

}
