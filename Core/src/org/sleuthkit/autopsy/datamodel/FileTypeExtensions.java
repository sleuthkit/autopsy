/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.Arrays;
import java.util.List;

/**
 * Contains Lists of commonly known and used file type extensions and 'getters'
 * to obtain them.
 */
public class FileTypeExtensions {

    private final static List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp", ".tec", ".tif", ".webp"); //NON-NLS
    private final static List<String> VIDEO_EXTENSIONS = Arrays.asList(".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v", //NON-NLS
            ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf"); //NON-NLS
    private final static List<String> AUDIO_EXTENSIONS = Arrays.asList(".aiff", ".aif", ".flac", ".wav", ".m4a", ".ape", //NON-NLS
            ".wma", ".mp2", ".mp1", ".mp3", ".aac", ".mp4", ".m4p", ".m1a", ".m2a", ".m4r", ".mpa", ".m3u", ".mid", ".midi", ".ogg"); //NON-NLS
    private final static List<String> DOCUMENT_EXTENSIONS = Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx"); //NON-NLS
    private final static List<String> EXECUTABLE_EXTENSIONS = Arrays.asList(".exe", ".msi", ".cmd", ".com", ".bat", ".reg", ".scr", ".dll", ".ini"); //NON-NLS
    private final static List<String> TEXT_EXTENSIONS = Arrays.asList(".txt", ".rtf", ".log", ".text", ".xml"); //NON-NLS
    private final static List<String> WEB_EXTENSIONS = Arrays.asList(".html", ".htm", ".css", ".js", ".php", ".aspx"); //NON-NLS
    private final static List<String> PDF_EXTENSIONS = Arrays.asList(".pdf"); //NON-NLS
    private final static List<String> ARCHIVE_EXTENSIONS = Arrays.asList(".zip", ".rar", ".7zip", ".7z", ".arj", ".tar", ".gzip", ".bzip", ".bzip2", ".cab", ".jar", ".cpio", ".ar", ".gz", ".tgz", ".bz2"); //NON-NLS
    private final static List<String> DATABASE_EXTENSIONS = Arrays.asList(".db", ".db3", ".sqlite", ".sqlite3"); //NON-NLS

    public static List<String> getImageExtensions() {
        return IMAGE_EXTENSIONS;
    }

    public static List<String> getVideoExtensions() {
        return VIDEO_EXTENSIONS;
    }

    public static List<String> getAudioExtensions() {
        return AUDIO_EXTENSIONS;
    }

    public static List<String> getDocumentExtensions() {
        return DOCUMENT_EXTENSIONS;
    }

    public static List<String> getExecutableExtensions() {
        return EXECUTABLE_EXTENSIONS;
    }

    public static List<String> getTextExtensions() {
        return TEXT_EXTENSIONS;
    }

    public static List<String> getWebExtensions() {
        return WEB_EXTENSIONS;
    }

    public static List<String> getPDFExtensions() {
        return PDF_EXTENSIONS;
    }

    public static List<String> getArchiveExtensions() {
        return ARCHIVE_EXTENSIONS;
    }

    public static List<String> getDatabaseExtensions() {
        return DATABASE_EXTENSIONS;
    }
    
    private FileTypeExtensions() {
    }

}
