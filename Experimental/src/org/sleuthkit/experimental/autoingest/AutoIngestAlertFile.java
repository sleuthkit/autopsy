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
package org.sleuthkit.experimental.autoingest;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Utility for creating and checking for the existence of an automated ingest
 * alert file. The purpose of the file is to put a marker in the case directory
 * when an error or warning occurs in connection with an automated ingest job.
 */
final class AutoIngestAlertFile {

    private static final String ERROR_FILE_NAME = "autoingest.alert";
    private static final Logger logger = Logger.getLogger(AutoIngestAlertFile.class.getName());

    /**
     * Checks whether an automated ingest alert file exists in a case directory.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return True or false.
     */
    static boolean exists(Path caseDirectoryPath) {
        return caseDirectoryPath.resolve(ERROR_FILE_NAME).toFile().exists();
    }

    /**
     * Creates an automated ingest alert file in a case directory if such a file
     * does not already exist.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @return True or false.
     *
     * @throws AutoIngestAlertFileException if there is a problem creating the file.
     */
    static void create(Path caseDirectoryPath) throws AutoIngestAlertFileException {
        try {
            Files.createFile(caseDirectoryPath.resolve(ERROR_FILE_NAME));
        } catch (FileAlreadyExistsException ignored) {
            /*
             * The file already exists, the exception is not exceptional.
             */
        } catch (IOException ex) {
            /*
             * FileAlreadyExistsException implementation is optional, so check
             * for that case.
             */
            if (!exists(caseDirectoryPath)) {
                throw new AutoIngestAlertFileException(String.format("Error creating automated ingest alert file in %s", caseDirectoryPath), ex);
            }
        }
    }

    /**
     * Private, do-nothing constructor to suppress creation of instances of this
     * class.
     */
    private AutoIngestAlertFile() {
    }

    static final class AutoIngestAlertFileException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestAlertFileException(String message) {
            super(message);
        }

        private AutoIngestAlertFileException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
