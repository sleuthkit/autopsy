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
package org.sleuthkit.autopsy.timeline.utils;

import com.google.common.net.MediaType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypesFilter;

/**
 * Utilities to deal with TimelineFilters
 */
public final class FilterUtils {

    private static final Set<MediaType> MEDIA_MIME_TYPES = Stream.of(
            "image/*",
            "video/*",
            "audio/*",
            "application/vnd.ms-asf", //NON-NLS
            "application/vnd.rn-realmedia", //NON-NLS
            "application/x-shockwave-flash" //NON-NLS 
    ).map(MediaType::parse).collect(Collectors.toSet());

    private static final Set<MediaType> EXECUTABLE_MIME_TYPES = Stream.of(
            "application/x-bat",
            "application/x-dosexec",
            "application/vnd.microsoft.portable-executable",
            "application/x-msdownload",
            "application/exe",
            "application/x-exe",
            "application/dos-exe",
            "vms/exe",
            "application/x-winexe",
            "application/msdos-windows",
            "application/x-msdos-program"
    ).map(MediaType::parse).collect(Collectors.toSet());

    private static final Set<MediaType> DOCUMENT_MIME_TYPES = Stream.of(
            "text/*", //NON-NLS
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
    ).map(MediaType::parse).collect(Collectors.toSet());

    private static final Set<MediaType> NON_OTHER_MIME_TYPES = new HashSet<>();

    static {
        NON_OTHER_MIME_TYPES.addAll(MEDIA_MIME_TYPES);
        NON_OTHER_MIME_TYPES.addAll(DOCUMENT_MIME_TYPES);
        NON_OTHER_MIME_TYPES.addAll(EXECUTABLE_MIME_TYPES);
    }

    private FilterUtils() {
    }

    public static FileTypesFilter createDefaultFileTypesFilter() {
        FileTypesFilter fileTypesFilter = new FileTypesFilter();

        fileTypesFilter.addSubFilter(new FileTypeFilter("Media", MEDIA_MIME_TYPES));
        fileTypesFilter.addSubFilter(new FileTypeFilter("Documents", DOCUMENT_MIME_TYPES));
        fileTypesFilter.addSubFilter(new FileTypeFilter("Executables", EXECUTABLE_MIME_TYPES));
        fileTypesFilter.addSubFilter(new InverseFileTypeFilter("Other", NON_OTHER_MIME_TYPES));

        return fileTypesFilter;
    }

    private static class InverseFileTypeFilter extends FileTypeFilter {

        InverseFileTypeFilter(String displayName, Collection<MediaType> mediaTypes) {
            super(displayName, mediaTypes);
        }

        @Override
        public InverseFileTypeFilter copyOf() {
            return new InverseFileTypeFilter("Other", NON_OTHER_MIME_TYPES);
        }

        @Override
        protected String getSQLWhere(TimelineManager manager) {
            return " NOT " + super.getSQLWhere(manager) ;
        }
    }
}
