/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testutils;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * Class with utility methods for opening and closing cases for functional
 * testing purposes.
 */
public final class CaseUtils {

    /**
     * Appends a time stamp to the given case name for uniqueness and creates a
     * case as the current case in the temp directory. Asserts if there is an
     * error creating the case.
     *
     * @param caseName The case name.
     *
     * @return The new case.
     */
    public static Case createAsCurrentCase(String caseName) throws TestUtilsException {
        String uniqueCaseName = caseName + "_" + TimeStampUtils.createTimeStamp();
        Path caseDirectoryPath = Paths.get(System.getProperty("java.io.tmpdir"), uniqueCaseName);
        Case currentCase = null;
        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails(uniqueCaseName));
            currentCase = Case.getCurrentCaseThrows();
        } catch (CaseActionException | NoCurrentCaseException ex) {
            throw new TestUtilsException(String.format("Failed to create case %s at %s", uniqueCaseName, caseDirectoryPath), ex);
        }
        return currentCase;
    }

    /**
     * Closes the current case, and optionally deletes it. Asserts if there is
     * no current case or if there is an error closing the current case.
     */
    public static void closeCurrentCase() throws TestUtilsException {
        Case currentCase;

        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new TestUtilsException("Failed to get current case.", ex);
        }

        String caseName = currentCase.getName();
        String caseDirectory = currentCase.getCaseDirectory();
        try {
            Case.closeCurrentCase();
        } catch (CaseActionException ex) {
            throw new TestUtilsException(String.format("Failed to close case %s at %s", caseName, caseDirectory), ex);
        }
    }

    /**
     * Private constructor to prevent utility class object instantiation.
     */
    private CaseUtils() {
    }

}
