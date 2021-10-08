/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * DTO Representing an abstract file in the results view.
 */
public class FileRowDTO extends BaseRowDTO {
    
    public enum ExtensionMediaType {
        IMAGE, VIDEO, AUDIO, DOC, EXECUTABLE, TEXT, WEB, PDF, ARCHIVE, UNCATEGORIZED
    }
    
    private static String TYPE_ID = "FILE";
    
    public static String getTypeIdForClass() {
        return TYPE_ID;
    }
        
    private final AbstractFile abstractFile;
    private final String fileName;
    private final String extension;
    private final ExtensionMediaType extensionMediaType;
    private final boolean allocated;
    private final TskData.TSK_DB_FILES_TYPE_ENUM fileType;
    private final boolean encryptionDetected;
    private final boolean visibleChildren;

    public FileRowDTO(AbstractFile abstractFile, long id, String fileName, String extension, 
            ExtensionMediaType extensionMediaType, boolean allocated, TskData.TSK_DB_FILES_TYPE_ENUM fileType, 
            boolean encryptionDetected, boolean visibleChildren, List<Object> cellValues) {
        super(cellValues, TYPE_ID, id);
        this.abstractFile = abstractFile;
        this.fileName = fileName;
        this.extension = extension;
        this.extensionMediaType = extensionMediaType;
        this.allocated = allocated;
        this.fileType = fileType;
        this.encryptionDetected = encryptionDetected;
        this.visibleChildren = visibleChildren;
    }

    public ExtensionMediaType getExtensionMediaType() {
        return extensionMediaType;
    }

    public boolean getAllocated() {
        return allocated;
    }

    public TskData.TSK_DB_FILES_TYPE_ENUM getFileType() {
        return fileType;
    }

    public AbstractFile getAbstractFile() {
        return abstractFile;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isEncryptionDetected() {
        return encryptionDetected;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean hasVisibleChildren() {
        return visibleChildren;
    }
}
