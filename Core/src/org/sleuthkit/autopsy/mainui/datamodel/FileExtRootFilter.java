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

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.collections.set.UnmodifiableSet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;

/**
 * Root node filters
 */
@Messages({
    "FileExtRootFilter_image_displayName=Images",
    "FileExtRootFilter_executable_displayName=Executable",
    "FileExtRootFilter_video_displayName=Video",
    "FileExtRootFilter_audio_displayName=Audio",
    "FileExtRootFilter_archives_displayName=Archives",
    "FileExtRootFilter_documents_displayName=Documents",
    "FileExtRootFilter_databases_displayName=Databases",})
public enum FileExtRootFilter implements FileExtSearchFilter {
    TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", Bundle.FileExtRootFilter_image_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getImageExtensions()))),
    TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", Bundle.FileExtRootFilter_video_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getVideoExtensions()))),
    TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", Bundle.FileExtRootFilter_audio_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getAudioExtensions()))),
    TSK_ARCHIVE_FILTER(3, "TSK_ARCHIVE_FILTER", Bundle.FileExtRootFilter_archives_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getArchiveExtensions()))),
    TSK_DATABASE_FILTER(4, "TSK_DATABASE_FILTER", Bundle.FileExtRootFilter_databases_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getDatabaseExtensions()))),
    TSK_DOCUMENT_FILTER(5, "TSK_DOCUMENT_FILTER", Bundle.FileExtRootFilter_documents_displayName(),
            ImmutableSet.of(".htm", ".html", ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", ".txt", ".rtf")), //NON-NLS
    TSK_EXECUTABLE_FILTER(6, "TSK_EXECUTABLE_FILTER", Bundle.FileExtRootFilter_executable_displayName(),
            Collections.unmodifiableSet(new HashSet<>(FileTypeExtensions.getExecutableExtensions())));
    //NON-NLS
    final int id;
    final String name;
    final String displayName;
    final Set<String> filter;

    private FileExtRootFilter(int id, String name, String displayName, Set<String> filter) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.filter = filter;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public Set<String> getFilter() {
        return this.filter;
    }

}
