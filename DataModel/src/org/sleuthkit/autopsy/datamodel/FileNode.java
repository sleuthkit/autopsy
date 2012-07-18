/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import javax.swing.Action;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.TskData;

/**
 * This class is used to represent the "Node" for the file.
 * It has no children.
 *
 */
public class FileNode extends AbstractFsContentNode<File> {
    
    private final static List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff");
    private final static List<String> VIDEO_EXTENSIONS = Arrays.asList(".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v",
            ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf");
    private final static List<String> AUDIO_EXTENSIONS = Arrays.asList(".aiff", ".aif", ".flac", ".wav", ".m4a", ".ape",
            ".wma", ".mp2", ".mp1", ".mp3", ".aac", ".mp4", ".m4p", ".m1a", ".m2a", ".m4r", ".mpa", ".m3u", ".mid", ".midi", ".ogg");
    private final static List<String> DOCUMENT_EXTENSIONS = Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx");
    private final static List<String> EXECUTABLE_EXTENSIONS = Arrays.asList(".exe", ".msi", ".cmd", ".com", ".bat", ".reg", ".scr", ".dll", ".ini");
    private final static List<String> TEXT_EXTENSIONS = Arrays.asList(".txt", ".rtf", ".log", ".text");
    private final static List<String> WEB_EXTENSIONS = Arrays.asList(".html", ".htm", ".css", ".js", ".php", ".aspx", ".xml");
    private final static List<String> PDF_EXTENSIONS = Arrays.asList(".pdf");
    
    /**
     * 
     * @param file underlying Content
     */
    public FileNode(File file) {
        this(file, true);
    }
    
    public FileNode(File file, boolean directoryBrowseMode) {
        super(file, directoryBrowseMode);
        
        // set name, display name, and icon
        if (File.dirFlagToValue(file.getDir_flags()).equals(TskData.TSK_FS_NAME_FLAG_ENUM.TSK_FS_NAME_FLAG_UNALLOC.toString())) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
        } else {
            this.setIconBaseWithExtension(getIconForFileType(file));
        }
    }

    /**
     * Right click action for this node
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        return new Action[]{};
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }
    
    // Given a file, returns the correct icon for said
    // file based off it's extension
    static String getIconForFileType(File file) {
        // Get the name, extension
        String name = file.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1) {
            return "org/sleuthkit/autopsy/images/file-icon.png";
        }
        String ext = name.substring(dotIndex).toLowerCase();
        
        // Images
        for(String s:IMAGE_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/image-file.png"; }
        }
        // Videos
        for(String s:VIDEO_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/video-file.png"; }
        }
        // Audio Files
        for(String s:AUDIO_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/audio-file.png"; }
        }
        // Documents
        for(String s:DOCUMENT_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/doc-file.png"; }
        }
        // Executables / System Files
        for(String s:EXECUTABLE_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/exe-file.png"; }
        }
        // Text Files
        for(String s:TEXT_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/text-file.png"; }
        }
        // Web Files
        for(String s:WEB_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/web-file.png"; }
        }
        // PDFs
        for(String s:PDF_EXTENSIONS) {
            if(ext.equals(s)) { return "org/sleuthkit/autopsy/images/pdf-file.png"; }
        }
        // Else return the default
        return "org/sleuthkit/autopsy/images/file-icon.png";
        
    }
}
