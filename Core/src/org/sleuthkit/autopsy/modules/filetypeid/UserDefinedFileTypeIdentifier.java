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
 * Does file type identification with user-defined file type fileTypeDefs.
 */
class UserDefinedFileTypeIdentifier {

    private final List<FileTypeDefinitionsManager.FileTypeDefinition> fileTypeDefs;

    /**
     * Creates an object that does file type identification with user-defined
 file type fileTypeDefs.
     *
     * @param sigFilePath A path to a fileTypeDef definitions file.
     */
    static UserDefinedFileTypeIdentifier createDetector(String sigFilePath) {
        UserDefinedFileTypeIdentifier detector = new UserDefinedFileTypeIdentifier();
        detector.loadSignatures(sigFilePath);
        return detector;
    }

    /**
     * Create an object that does file type identification with user-defined
 file type fileTypeDefs.
     */
    private UserDefinedFileTypeIdentifier() {
        this.fileTypeDefs = new ArrayList<>();
    }

    /**
     * Loads a set of user-defined file type fileTypeDefs from a file.
     *
     * @param sigFilePath The path to the fileTypeDef definitions file.
     */
    private void loadSignatures(String sigFilePath) {
        // RJCTODO: Load fileTypeDef file, creating 
    }

    /**
     * Attempts to identify a file using the set of user-defined file type
 fileTypeDefs.
     *
     * @param file The file to type.
     * @return A MIME type string or the empty string if identification fails.
     */
    String identify(AbstractFile file) {
        String type = "";
        for (FileTypeDefinitionsManager.FileTypeDefinition fileTypeDef : this.fileTypeDefs) {
            if (fileTypeDef.matches(file)) {
                type = fileTypeDef.getTypeName();
                // RJCTODO: Add attribute to GEN IBNFO artifact?
                // RJCTODO: Handle alert here?
                break;
            }
        }
        return type;
    }

}
