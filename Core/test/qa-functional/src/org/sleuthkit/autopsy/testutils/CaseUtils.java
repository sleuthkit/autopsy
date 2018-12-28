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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Class with utility methods for opening and closing cases for functional
 * testing purposes.
 */
public final class CaseUtils {

    /**
     * Creates a case as the current case in the temp directory (system
     * property: java.io.tmpdir). Deletes any previous version of the case in
     * the same location, if it exists. Asserts if there is an error creating
     * the case.
     *
     * @param caseName The case name.
     *
     * @return The new case.
     */
    public static Case createAsCurrentCase(String caseName) {
        /*
         * Try to delete a previous version of the case, if it exists.
         */
        Path caseDirectoryPath = Paths.get(System.getProperty("java.io.tmpdir"), caseName);
        File caseDirectory = caseDirectoryPath.toFile();
        if(caseDirectory.exists() && !FileUtil.deleteDir(caseDirectory)){
            Assert.fail(String.format("Failed to delete existing case %s at %s", caseName, caseDirectoryPath));
        }

        /*
         * Try to create the case.
         */
        Case currentCase = null;
        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails(caseName));
            currentCase = Case.getCurrentCaseThrows();
        } catch (CaseActionException | NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(String.format("Failed to create case %s at %s: %s", caseName, caseDirectoryPath, ex.getMessage()));
        }
        
        // Disable Image Gallery
        Path propPath = Paths.get(currentCase.getModuleDirectory(), "Image Gallery", currentCase.getName() + ".properties");
        Path parent = propPath.getParent();

        Properties props = new Properties();
        try {
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            if (!Files.exists(propPath)) {
                Files.createFile(propPath);
            }
            

            try (OutputStream fos = Files.newOutputStream(propPath)) {
                props.setProperty("enabled", "false");
                props.store(fos, "Disabled Image Gallery (functional test)"); //NON-NLS
            }
        } catch (IOException e) {
            Assert.fail(String.format("Was not able to create a new properties file %s : %s", propPath.toAbsolutePath(), e.getMessage())); //NON-NLS
        }
        
        String autFilePath = Paths.get(caseDirectoryPath.toString(), caseName + ".aut").toString();
        
        // Close and reopen to load the new Image Gallery setting
        try {
            Case.closeCurrentCase();
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(String.format("Failed to close case %s at %s: %s", caseName, caseDirectory, ex.getMessage()));
        }
        
        try {
            Case.openAsCurrentCase(autFilePath);
            currentCase = Case.getCurrentCaseThrows();
        } catch (CaseActionException | NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(String.format("Failed to reopen case %s at %s: %s", caseName, caseDirectory, ex.getMessage()));
        }       
        
        return currentCase;
    }

    /**
     * Closes the current case, and optionally deletes it. Asserts if there is
     * no current case or if there is an error closing or deleting the current
     * case.
     *
     * @param deleteCase True if the case should be deleted after closing it.
     */
    public static void closeCurrentCase(boolean deleteCase) {
        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Failed to get current case");
            return;
        }

        String caseName = currentCase.getName();
        String caseDirectory = currentCase.getCaseDirectory();
        
        try {
            Case.closeCurrentCase();
            if(deleteCase && !FileUtil.deleteDir(new File(caseDirectory))){
                Assert.fail(String.format("Failed to delete case directory for case %s at %s", caseName, caseDirectory));  
            }
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(String.format("Failed to close case %s at %s: %s", caseName, caseDirectory, ex.getMessage()));
        }
    }

    /**
     * Private constructor to prevent utility class object instantiation.
     */
    private CaseUtils() {
    }

}