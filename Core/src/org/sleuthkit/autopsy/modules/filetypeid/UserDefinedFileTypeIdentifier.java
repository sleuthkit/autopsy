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

import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Does file type identification for user-defined file types.
 */
final class UserDefinedFileTypeIdentifier {

    private Map<String, FileType> fileTypes;

    /**
     * Creates an object that can do file type identification for user-defined
     * file types.
     */
    UserDefinedFileTypeIdentifier() {
        fileTypes = new HashMap<>();
    }

    /**
     * Gets the user-defined file types from the user-defined file types
     * manager.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    void loadFileTypes() throws UserDefinedFileTypesManager.UserDefinedFileTypesException {
        fileTypes = UserDefinedFileTypesManager.getInstance().getFileTypes();
    }

    /**
     * Attempts to identify a file using the set of user-defined file type file
     * types.
     *
     * @param file The file to type.
     * @return A FileType object or null if identification fails.
     */
    FileType identify(final AbstractFile file) {
        FileType type = null;
        for (FileType fileType : this.fileTypes.values()) {
            if (fileType.matches(file)) {
                type = fileType;
                break;
            }
        }
        return type;
    }

}
