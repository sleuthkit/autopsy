/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Does file type identification with user-defined file types.
 */
final class UserDefinedFileTypeIdentifier {

    private final List<FileType> fileTypes;

    /**
     * Creates an object that does file type identification with user-defined
     * file types. Does not load the file type definitions.
     */
    UserDefinedFileTypeIdentifier() {
        this.fileTypes = new ArrayList<>();
    }

    /**
     * Loads the set of user-defined file types.
     */
    void loadFileTypes() throws UserDefinedFileTypes.UserDefinedFileTypesException {
        this.fileTypes.clear();
        this.fileTypes.addAll(UserDefinedFileTypes.getFileTypes());
    }

    /**
     * Attempts to identify a file using the set of user-defined file type
     * file types.
     *
     * @param file The file to type.
     * @return A FileType object or null if identification fails.
     */
    FileType identify(AbstractFile file) {
        FileType type = null;
        for (FileType fileType : this.fileTypes) {
            if (fileType.matches(file)) {
                type = fileType;
                break;
            }
        }
        return type;
    }

}
