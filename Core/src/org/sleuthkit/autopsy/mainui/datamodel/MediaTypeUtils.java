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

import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;

/**
 *
 */
public class MediaTypeUtils {
    
    public enum ExtensionMediaType {
        IMAGE, VIDEO, AUDIO, DOC, EXECUTABLE, TEXT, WEB, PDF, ARCHIVE, UNCATEGORIZED
    }
    
    public static ExtensionMediaType getExtensionMediaType(String ext) {
        if (StringUtils.isBlank(ext)) {
            return ExtensionMediaType.UNCATEGORIZED;
        } else {
            ext = "." + ext;
        }
        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
            return ExtensionMediaType.IMAGE;
        } else if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
            return ExtensionMediaType.VIDEO;
        } else if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
            return ExtensionMediaType.AUDIO;
        } else if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
            return ExtensionMediaType.DOC;
        } else if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
            return ExtensionMediaType.EXECUTABLE;
        } else if (FileTypeExtensions.getTextExtensions().contains(ext)) {
            return ExtensionMediaType.TEXT;
        } else if (FileTypeExtensions.getWebExtensions().contains(ext)) {
            return ExtensionMediaType.WEB;
        } else if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
            return ExtensionMediaType.PDF;
        } else if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
            return ExtensionMediaType.ARCHIVE;
        } else {
            return ExtensionMediaType.UNCATEGORIZED;
        }
    }
    
    /**
     * Gets the path to the icon file that should be used to visually represent
     * an AbstractFile, using the file name extension to select the icon.
     *
     * @param file An AbstractFile.
     *
     * @return An icon file path.
     */
    public static String getIconForFileType(ExtensionMediaType fileType) {
        if (fileType == null) {
            return "org/sleuthkit/autopsy/images/file-icon.png";
        }

        switch (fileType) {
            case IMAGE:
                return "org/sleuthkit/autopsy/images/image-file.png";
            case VIDEO:
                return "org/sleuthkit/autopsy/images/video-file.png";
            case AUDIO:
                return "org/sleuthkit/autopsy/images/audio-file.png";
            case DOC:
                return "org/sleuthkit/autopsy/images/doc-file.png";
            case EXECUTABLE:
                return "org/sleuthkit/autopsy/images/exe-file.png";
            case TEXT:
                return "org/sleuthkit/autopsy/images/text-file.png";
            case WEB:
                return "org/sleuthkit/autopsy/images/web-file.png";
            case PDF:
                return "org/sleuthkit/autopsy/images/pdf-file.png";
            case ARCHIVE:
                return "org/sleuthkit/autopsy/images/archive-file.png";
            default:
            case UNCATEGORIZED:
                return "org/sleuthkit/autopsy/images/file-icon.png";
        }
    }
    
    private MediaTypeUtils() {
    }
}
