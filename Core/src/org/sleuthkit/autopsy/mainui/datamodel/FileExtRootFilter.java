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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.FileTypesByExtension;

/**
 * Root node filters
 */
@NbBundle.Messages(value = {"FileTypeExtensionFilters.tskDatabaseFilter.text=Databases"})
public enum FileExtRootFilter implements FileExtSearchFilter {
    TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskImgFilter.text"), FileTypeExtensions.getImageExtensions()), TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskVideoFilter.text"), FileTypeExtensions.getVideoExtensions()), TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskAudioFilter.text"), FileTypeExtensions.getAudioExtensions()), TSK_ARCHIVE_FILTER(3, "TSK_ARCHIVE_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskArchiveFilter.text"), FileTypeExtensions.getArchiveExtensions()), TSK_DATABASE_FILTER(4, "TSK_DATABASE_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDatabaseFilter.text"), FileTypeExtensions.getDatabaseExtensions()), TSK_DOCUMENT_FILTER(5, "TSK_DOCUMENT_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDocumentFilter.text"), Arrays.asList(".htm", ".html", ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", ".txt", ".rtf")), //NON-NLS
    TSK_EXECUTABLE_FILTER(6, "TSK_EXECUTABLE_FILTER", NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskExecFilter.text"), FileTypeExtensions.getExecutableExtensions());
    //NON-NLS
    final int id;
    final String name;
    final String displayName;
    final List<String> filter;

    private FileExtRootFilter(int id, String name, String displayName, List<String> filter) {
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
    public List<String> getFilter() {
        return Collections.unmodifiableList(this.filter);
    }
    
}
