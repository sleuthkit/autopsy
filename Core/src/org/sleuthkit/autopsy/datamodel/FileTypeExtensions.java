package org.sleuthkit.autopsy.datamodel;

import java.util.Arrays;
import java.util.List;

/**
 * Contains Lists of commonly known and used file type extensions
 * and 'getters' to obtain them.
 */
public class FileTypeExtensions {
    private final static List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp");
    private final static List<String> VIDEO_EXTENSIONS = Arrays.asList(".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v",
            ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf");
    private final static List<String> AUDIO_EXTENSIONS = Arrays.asList(".aiff", ".aif", ".flac", ".wav", ".m4a", ".ape",
            ".wma", ".mp2", ".mp1", ".mp3", ".aac", ".mp4", ".m4p", ".m1a", ".m2a", ".m4r", ".mpa", ".m3u", ".mid", ".midi", ".ogg");
    private final static List<String> DOCUMENT_EXTENSIONS = Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx");
    private final static List<String> EXECUTABLE_EXTENSIONS = Arrays.asList(".exe", ".msi", ".cmd", ".com", ".bat", ".reg", ".scr", ".dll", ".ini");
    private final static List<String> TEXT_EXTENSIONS = Arrays.asList(".txt", ".rtf", ".log", ".text", ".xml");
    private final static List<String> WEB_EXTENSIONS = Arrays.asList(".html", ".htm", ".css", ".js", ".php", ".aspx");
    private final static List<String> PDF_EXTENSIONS = Arrays.asList(".pdf");
    private final static List<String> ARCHIVE_EXTENSIONS = Arrays.asList(".zip", ".rar", ".7zip", ".arj", ".gzip", ".bzip", ".bzip2", ".cab", ".jar", ".cpio", ".ar");
    
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
}
