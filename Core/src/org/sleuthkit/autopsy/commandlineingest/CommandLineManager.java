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
package org.sleuthkit.autopsy.commandlineingest;

import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import static org.sleuthkit.autopsy.casemodule.CaseMetadata.getFileExtension;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Base class for the command line managers.
 */
class CommandLineManager {

    private static final Logger LOGGER = Logger.getLogger(CommandLineOpenCaseManager.class.getName());

    /**
     * Opens existing case.
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

}
