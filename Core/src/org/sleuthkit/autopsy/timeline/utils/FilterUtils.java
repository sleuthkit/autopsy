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

import java.util.HashSet;
import java.util.Set;
import org.openide.util.NbBundle;
import static org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory.DOCUMENTS;
import static org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory.EXECUTABLE;
import static org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory.MEDIA;
import org.sleuthkit.datamodel.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.InverseFileTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.FileTypesFilter;

/**
 * Utilities to deal with TimelineFilters
 */
public final class FilterUtils {

    private static final Set<String> NON_OTHER_MIME_TYPES = new HashSet<>();

    static {
        NON_OTHER_MIME_TYPES.addAll(DOCUMENTS.getMediaTypes());
        NON_OTHER_MIME_TYPES.addAll(EXECUTABLE.getMediaTypes());
        NON_OTHER_MIME_TYPES.addAll(MEDIA.getMediaTypes());
    }

    private FilterUtils() {
    }

    /**
     * Create a new FileTypesFilter with the default FileTypeFilters for Media,
     * Documents, Executables, and Other.
     *
     * @return The new FileTypesFilter.
     */
    @NbBundle.Messages({
        "FilterUtils.otherFilter.displayName=Other"})
    public static FileTypesFilter createDefaultFileTypesFilter() {
        FileTypesFilter fileTypesFilter = new FileTypesFilter();
        fileTypesFilter.addSubFilter(new FileTypeFilter(MEDIA.getDisplayName(), MEDIA.getMediaTypes()));
        fileTypesFilter.addSubFilter(new FileTypeFilter(DOCUMENTS.getDisplayName(), DOCUMENTS.getMediaTypes()));
        fileTypesFilter.addSubFilter(new FileTypeFilter(EXECUTABLE.getDisplayName(), EXECUTABLE.getMediaTypes()));
        fileTypesFilter.addSubFilter(new InverseFileTypeFilter(Bundle.FilterUtils_otherFilter_displayName(), NON_OTHER_MIME_TYPES));
        return fileTypesFilter;
    }
}
