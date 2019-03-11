/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
public class CaseCoordinationServiceUtils {

    private static final String CASE_AUTO_INGEST_LOG_NAME = "AUTO_INGEST_LOG.TXT"; //NON-NLS
    private static final String RESOURCES_LOCK_SUFFIX = "_RESOURCES"; //NON-NLS

    public static String getCaseResourcesLockName(Path caseDirectoryPath) {
        return caseDirectoryPath + RESOURCES_LOCK_SUFFIX;
    }

    public static String getCaseAutoIngestLogLockName(Path caseDirectoryPath) {
        return Paths.get(caseDirectoryPath.toString(), CASE_AUTO_INGEST_LOG_NAME).toString();
    }

    public static String getCaseDirectoryLockName(Path caseDirectoryPath) {
        return caseDirectoryPath.toString();
    }

    public static String getCaseLockName(Path caseDirectoryPath) {
        String caseName = caseDirectoryPath.getFileName().toString();
        if (TimeStampUtils.endsWithTimeStamp(caseName)) {
            caseName = TimeStampUtils.removeTimeStamp(caseName);
            if (caseName.endsWith("_")) {
                caseName = caseName.substring(0, caseName.length() - 1);
            }
        }
        return caseName;
    }

    public static boolean isCaseAutoIngestLogLockName(String lockName) {
        return Paths.get(lockName).getFileName().toString().equals(CASE_AUTO_INGEST_LOG_NAME);
    }

    public static boolean isCaseResourcesLockName(String lockName) {
        return Paths.get(lockName).getFileName().toString().endsWith(RESOURCES_LOCK_SUFFIX);
    }

    public static boolean isCaseLockName(String lockName) {
        return !(lockName.contains("\\") || lockName.contains("//"));      
    }
    
    /**
     * Prevents instantiation of this uitlity class.
     */
    private CaseCoordinationServiceUtils() {
    }

}
