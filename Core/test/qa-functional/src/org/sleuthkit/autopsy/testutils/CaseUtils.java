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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 * Class with common methods for testing related to the creation and elimination
 * of cases.
 */
public final class CaseUtils {

    /**
     * Create a case case directory and case for the given case name.
     *
     * @param caseName The name for the case and case directory to have
     *
     * @return The new case
     */
    public static Case createAsCurrentCase(String caseName) {
        Case currentCase = null;
        //Make sure the case is starting with a clean state. So delete the case directory, if it exists.
        Path caseDirectoryPath = Paths.get(System.getProperty("java.io.tmpdir"), caseName);
        File caseDir = new File(caseDirectoryPath.toString());
        try {
            deleteCaseDir(caseDir);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertFalse("Unable to delete existing test directory", caseDir.exists());
        // Create the test directory
        caseDir.mkdirs();
        assertTrue("Unable to create test directory", caseDir.exists());

        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails(caseName));
            currentCase = Case.getCurrentCaseThrows();
        } catch (CaseActionException | NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        assertTrue(caseDir.exists());

        return currentCase;
    }

    /**
     * Close the current case. This will fail the test if the case
     * was unable to be closed.
     * 
     * @param deleteCase Delete the case after closure?
     */
    public static void closeCurrentCase(boolean deleteCase) {
        try {
            if (Case.isCaseOpen()) {
                String currentCaseDirectory = Case.getCurrentCase().getCaseDirectory();
                Case.closeCurrentCase();
                System.gc();
                
                // Seems like we need some time to close the case, so file handler later can delete the case directory.
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException ex) {
                    
                }
                
                if (deleteCase) {
                    deleteCaseDir(new File(currentCaseDirectory));
                }
            }
        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Delete the case directory if it exists, thows exception if unable to
     * delete case dir to allow the user to determine failure with.
     *
     * @param caseDirectory The case directory to delete
     *
     * @throws IOException Thrown if there was an problem deleting the case
     *                     directory
     */
    public static void deleteCaseDir(File caseDirectory) throws IOException {
        if (!caseDirectory.exists()) {
            return;
        }
        FileUtils.deleteDirectory(caseDirectory);
    }

    /**
     * Private constructor to prevent utility class instantiation.
     */
    private CaseUtils() {
    }

}
