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

final class StateFile {

    enum Type {

        READY("state.ready"),
        PROCESSING("state.processing"),
        CANCELLED("state.cancelled"),
        DONE("state.done"),
        ERROR("state.error"),
        COPY_ERROR("state.copyerror"),
        DELETED("state.deleted"),
        INTERRUPTED("state.interrupted"),
        PRIORITIZED("state.prioritized");

        private final String fileName;

        private Type(String fileName) {
            this.fileName = fileName;
        }

        String fileName() {
            return fileName;
        }

    }

    static boolean exists(Path folderPath, Type stateFileType) throws IOException {
        return folderPath.resolve(stateFileType.fileName()).toFile().exists();
    }

    static void create(Path folderPath, Type stateFileType) throws IOException {
        Files.createFile(folderPath.resolve(stateFileType.fileName()));
    }

    static void createIfDoesNotExist(Path folderPath, Type stateFileType) throws IOException {
        try {
            Files.createFile(folderPath.resolve(stateFileType.fileName()));
        } catch (FileAlreadyExistsException ignored) {
            /**
             * The file already exists, the exception is not exceptional.
             */
        }
    }

    static void delete(Path folderPath, Type stateFileType) throws IOException {
        Files.delete(folderPath.resolve(stateFileType.fileName()));
    }

    static void deleteIfExists(Path folderPath, Type stateFileType) throws IOException {
        Files.deleteIfExists(folderPath.resolve(stateFileType.fileName()));
    }

    /**
     * Private, do-nothing constructor to suppress creation of instances of this
     * class.
     */
    private StateFile() {
    }
}
