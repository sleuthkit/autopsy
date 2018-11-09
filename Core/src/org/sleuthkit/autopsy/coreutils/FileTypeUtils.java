/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.openide.util.NbBundle;

/**
 * Utilities for dealing with file/mime-types
 */
public final class FileTypeUtils {

    private static final ImmutableSet<String> IMAGE_MIME_TYPES;
    private static final ImmutableSet<String> AUDIO_MIME_TYPES;
    private static final ImmutableSet<String> VIDEO_MIME_TYPES;
    private static final ImmutableSet<String> MULTI_MEDIA_MIME_TYPES;
    private static final ImmutableSet<String> DOCUMENT_MIME_TYPES;
    private static final ImmutableSet<String> EXECUTABLE_MIME_TYPES;
    private static final ImmutableSet<String> VISUAL_MEDIA_MIME_TYPES;

    static {
        IMAGE_MIME_TYPES = new ImmutableSet.Builder<String>()
                .addAll(asList(ImageIO.getReaderMIMETypes()))
                .add("image/bmp", //NON-NLS
                        "image/gif", //NON-NLS
                        "image/jpeg", //NON-NLS
                        "image/png", //NON-NLS
                        "image/tiff", //NON-NLS
                        "image/vnd.adobe.photoshop", //NON-NLS
                        "image/x-raw-nikon", //NON-NLS
                        "image/x-ms-bmp", //NON-NLS
                        "image/x-icon", //NON-NLS
                        "image/webp", //NON-NLS
                        "image/vnd.microsoft.icon" //NON-NLS
                ).build();
        AUDIO_MIME_TYPES = new ImmutableSet.Builder<String>()
                .add("audio/midi", //NON-NLS
                        "audio/mpeg", //NON-NLS
                        "audio/webm", //NON-NLS
                        "audio/ogg", //NON-NLS
                        "audio/wav" //NON-NLS
                ).build();
        VIDEO_MIME_TYPES = new ImmutableSet.Builder<String>()
                .add("video/webm", //NON-NLS
                        "video/3gpp", //NON-NLS
                        "video/3gpp2", //NON-NLS
                        "video/ogg", //NON-NLS
                        "video/mpeg", //NON-NLS
                        "video/mp4", //NON-NLS
                        "video/quicktime", //NON-NLS
                        "video/x-msvideo", //NON-NLS
                        "video/x-flv", //NON-NLS
                        "video/x-m4v", //NON-NLS
                        "video/x-ms-wmv"//NON-NLS
                ).build();
        VISUAL_MEDIA_MIME_TYPES = new ImmutableSet.Builder<String>()
                .addAll(IMAGE_MIME_TYPES)
                .addAll(VIDEO_MIME_TYPES)
                .add("application/vnd.ms-asf", //NON-NLS
                        "application/vnd.rn-realmedia", //NON-NLS
                        "application/x-shockwave-flash" //NON-NLS 
                ).build();
        MULTI_MEDIA_MIME_TYPES = new ImmutableSet.Builder<String>()
                .addAll(IMAGE_MIME_TYPES)
                .addAll(AUDIO_MIME_TYPES)
                .addAll(VIDEO_MIME_TYPES)
                .add("application/vnd.ms-asf", //NON-NLS
                        "application/vnd.rn-realmedia", //NON-NLS
                        "application/x-shockwave-flash" //NON-NLS 
                )
                .build();
        DOCUMENT_MIME_TYPES = new ImmutableSet.Builder<String>()
                .add("text/plain", //NON-NLS
                        "text/css", //NON-NLS
                        "text/html", //NON-NLS
                        "text/csv", //NON-NLS
                        "application/rtf", //NON-NLS
                        "application/pdf", //NON-NLS
                        "application/json", //NON-NLS
                        "application/javascript", //NON-NLS
                        "application/xml", //NON-NLS
                        "application/x-msoffice", //NON-NLS
                        "application/x-ooxml", //NON-NLS
                        "application/msword", //NON-NLS
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
                        "application/vnd.ms-powerpoint", //NON-NLS
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
                        "application/vnd.ms-excel", //NON-NLS
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", //NON-NLS
                        "application/vnd.oasis.opendocument.presentation", //NON-NLS
                        "application/vnd.oasis.opendocument.spreadsheet", //NON-NLS
                        "application/vnd.oasis.opendocument.text" //NON-NLS
                ).build();
        EXECUTABLE_MIME_TYPES = new ImmutableSet.Builder<String>()
                .add("application/x-bat",//NON-NLS
                        "application/x-dosexec",//NON-NLS
                        "application/vnd.microsoft.portable-executable",//NON-NLS
                        "application/x-msdownload",//NON-NLS
                        "application/exe",//NON-NLS
                        "application/x-exe",//NON-NLS
                        "application/dos-exe",//NON-NLS
                        "vms/exe",//NON-NLS
                        "application/x-winexe",//NON-NLS
                        "application/msdos-windows",//NON-NLS
                        "application/x-msdos-program"//NON-NLS
                ).build();
    }

    private FileTypeUtils() {

    }

    /**
     * Enum of categories/groups of file types.
     */
    @NbBundle.Messages({
        "FileTypeUtils.FileTypeCategory.Audio.displayName=Audio",
        "FileTypeUtils.FileTypeCategory.Video.displayName=Video",
        "FileTypeUtils.FileTypeCategory.Image.displayName=Image",
        "FileTypeUtils.FileTypeCategory.Media.displayName=Media",
        "FileTypeUtils.FileTypeCategory.Visual.displayName=Visual",
        "FileTypeUtils.FileTypeCategory.Documents.displayName=Documents",
        "FileTypeUtils.FileTypeCategory.Executables.displayName=Executables"})
    static public enum FileTypeCategory {

        IMAGE(Bundle.FileTypeUtils_FileTypeCategory_Image_displayName(),
                IMAGE_MIME_TYPES,
                Collections.emptyList()),
        VIDEO(Bundle.FileTypeUtils_FileTypeCategory_Video_displayName(),
                VIDEO_MIME_TYPES,
                Collections.emptyList()),
        AUDIO(Bundle.FileTypeUtils_FileTypeCategory_Audio_displayName(),
                AUDIO_MIME_TYPES,
                Collections.emptyList()),
        VISUAL(Bundle.FileTypeUtils_FileTypeCategory_Media_displayName(),
                VISUAL_MEDIA_MIME_TYPES,
                Collections.emptyList()),
        MEDIA(Bundle.FileTypeUtils_FileTypeCategory_Media_displayName(),
                MULTI_MEDIA_MIME_TYPES,
                Collections.emptyList()),
        EXECUTABLE(Bundle.FileTypeUtils_FileTypeCategory_Executables_displayName(),
                EXECUTABLE_MIME_TYPES,
                Collections.emptyList()),
        DOCUMENTS(Bundle.FileTypeUtils_FileTypeCategory_Documents_displayName(),
                DOCUMENT_MIME_TYPES,
                Collections.emptyList());

        private final String displayName;
        private final ImmutableSet<String> mediaTypes;
        private final ImmutableSet<String> extensions;

        private FileTypeCategory(String displayName, Collection<String> mediaTypes, Collection<String> extensions) {
            this.displayName = displayName;
            this.mediaTypes = ImmutableSet.copyOf(mediaTypes);
            this.extensions = ImmutableSet.copyOf(extensions);
        }

        public String getDisplayName() {
            return displayName;
        }

        public ImmutableSet<String> getMediaTypes() {
            return mediaTypes;

        }

        public ImmutableSet<String> getExtension() {
            return extensions;
        }
    }
}
