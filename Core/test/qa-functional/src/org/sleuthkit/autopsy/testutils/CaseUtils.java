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

/**
 * Class with common methods for testing related to the creation and elimination
 * of cases.
 */
public final class CaseUtils {

    /**
     * Private constructor for CaseUtils class.
     */
    private CaseUtils() {
    }

    /**
     * Create a case case directory and case for the given case name.
     *
     * @param caseName the name for the case and case directory to have
     */
    public static void createCase(String caseName) {
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
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertTrue(caseDir.exists());
    }

    /**
     * Close the current case, fails test if case was unable to be closed.
     */
    public static void closeCase() {
        try {
            Case.closeCurrentCase();
            //Seems like we need some time to close the case, so file handler later can delete the case directory.
            try {
                Thread.sleep(20000);
            } catch (Exception ex) {

            }
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Delete the case directory if it exists, thows exception if unable to
     * delete case dir to allow the user to determine failure with.
     *
     * @param caseDirectory the case directory to delete
     *
     * @throws IOException thrown if there was an problem deleting the case
     *                     directory
     */
    public static void deleteCaseDir(File caseDirectory) throws IOException {
        if (!caseDirectory.exists()) {
            return;
        }
        //We should determine whether the test fails or passes where this is called
        //It will usually be a test failure when the case can not be deleted
        //but sometimes we might be alright if we are unable to delete it.
        FileUtils.deleteDirectory(caseDirectory);
    }

}
