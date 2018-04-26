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

import java.io.IOException;
import java.nio.file.Path;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;

public final class CaseUtils {
    
    private CaseUtils() {
    }
    
    public static void createCase(Path caseDirectoryPath) {
        //Make sure the test is starting with a clean state. So delete the test directory, if it exists.
        deleteCaseDir(caseDirectoryPath);
        assertFalse("Unable to delete existing test directory", caseDirectoryPath.toFile().exists());
 
        // Create the test directory
        caseDirectoryPath.toFile().mkdirs();
        assertTrue("Unable to create test directory", caseDirectoryPath.toFile().exists());

        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails("IngestFiltersTest"));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }        
        assertTrue(caseDirectoryPath.toFile().exists());
    }
    
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
        
    public static void deleteCaseDir(Path caseDirectoryPath) {
        if (!caseDirectoryPath.toFile().exists()) {
            return;
        }
        try {
            FileUtils.deleteDirectory(caseDirectoryPath.toFile());
        } catch (IOException ex) {
            //We just want to make sure the case directory doesn't exist when the test starts. It shouldn't cause failure if the case directory couldn't be deleted after a test finished.            
            System.out.println("INFO: Unable to delete case directory: " + caseDirectoryPath.toString());
        }
    }

}
