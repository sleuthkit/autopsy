/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils;

/**
 * Utility enums for FileSearch
 */
class FileSearchData {
    
    /**
     * Enum representing how often the file occurs in the Central Repository.
     */
    @NbBundle.Messages({
        "FileSearchData.Frequency.unique.displayName=Unique",
        "FileSearchData.Frequency.rare.displayName=Rare",
        "FileSearchData.Frequency.common.displayName=Common",
        "FileSearchData.Frequency.unknown.displayName=Unknown",
    })
    enum Frequency {
	UNIQUE(0, Bundle.FileSearchData_Frequency_unique_displayName()),
	RARE(1, Bundle.FileSearchData_Frequency_rare_displayName()),
	COMMON(2, Bundle.FileSearchData_Frequency_common_displayName()),
	UNKNOWN(3, Bundle.FileSearchData_Frequency_unknown_displayName());
        
        private final int ranking;
        private final String displayName;
        static private final long uniqueMax = 1;
        static private final long rareMax = 5;
        
        Frequency(int ranking, String displayName) {
            this.ranking = ranking;
            this.displayName = displayName;
        }
        
        /**
         * Get the rank for sorting.
         * 
         * @return the rank (lower should be displayed first)
         */
        int getRanking() {
            return ranking;
        }
        
        /**
         * Get the enum matching the given occurrence count.
         * 
         * @param count Number of times a file is in the Central Repository.
         * 
         * @return the corresponding enum 
         */
        static Frequency fromCount(long count) {
            if (count <= uniqueMax) {
                return UNIQUE;
            } else if (count < rareMax) {
                return RARE;
            }
            return COMMON;
        }
        
        /**
         * Get the list of enums that are valid for filtering.
         * 
         * @return enums that can be used to filter
         */
        static List<Frequency> getOptionsForFiltering() {
            return Arrays.asList(UNIQUE, RARE, COMMON);
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Enum representing the file size
     */
    @NbBundle.Messages({
        "FileSearchData.FileSize.XL.displayName=1GB+",
        "FileSearchData.FileSize.large.displayName=200MB-1GB",
        "FileSearchData.FileSize.medium.displayName=50-200MB",
        "FileSearchData.FileSize.small.displayName=1-50MB",
        "FileSearchData.FileSize.XS.displayName=Under 1MB",
    })
    enum FileSize {
        XL(0, 1000, -1, Bundle.FileSearchData_FileSize_XL_displayName()),
        LARGE(1, 200, 1000, Bundle.FileSearchData_FileSize_large_displayName()),
        MEDIUM(2, 50, 200, Bundle.FileSearchData_FileSize_medium_displayName()),
        SMALL(3, 1, 50, Bundle.FileSearchData_FileSize_small_displayName()),
        XS(4, 0, 1, Bundle.FileSearchData_FileSize_XS_displayName());

        private final int ranking;   // Must be unique for each value
        private final long minBytes; // Note that the size must be strictly greater than this to match
        private final long maxBytes;
        private final String displayName;  
        private final static long BYTES_PER_MB = 1048576;
        final static long NO_MAXIMUM = -1;
        
        FileSize(int ranking, long minMB, long maxMB, String displayName) {
            this.ranking = ranking;
            this.minBytes = minMB * BYTES_PER_MB ;
            if (maxMB >= 0) {
                this.maxBytes = maxMB * BYTES_PER_MB;
            } else {
                this.maxBytes = NO_MAXIMUM;
            }
            this.displayName = displayName;
        }
                   
        /**
         * Get the enum corresponding to the given file size.
         * The file size must be strictly greater than minBytes.
         * 
         * @param size the file size
         * 
         * @return the enum whose range contains the file size
         */
        static FileSize fromSize(long size) {
            if (size > XL.minBytes) {
                return XL;
            } else if (size > LARGE.minBytes) {
                return LARGE;
            } else if (size > MEDIUM.minBytes) {
                return MEDIUM;
            } else if (size > SMALL.minBytes) {
                return SMALL;
            } else {
                return XS;
            }
        }
        
        /**
         * Get the upper limit of the range.
         * 
         * @return the maximum file size that will fit in this range.
         */
        long getMaxBytes() {
            return maxBytes;
        }
        
        /**
         * Get the lower limit of the range.
         * 
         * @return the maximum file size that is not part of this range
         */
        long getMinBytes() {
            return minBytes;
        }
        
        /**
         * Get the rank for sorting.
         * 
         * @return the rank (lower should be displayed first)
         */
        int getRanking() {
            return ranking;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
   
    /**
     * Enum representing the file type.
     * We don't simply use FileTypeUtils.FileTypeCategory because: 
     *   - Some file types categories overlap
     *   - It is convenient to have the "OTHER" option for files that don't match the given types
     */
    @NbBundle.Messages({
        "FileSearchData.FileType.Audio.displayName=Audio",
        "FileSearchData.FileType.Video.displayName=Video",
        "FileSearchData.FileType.Image.displayName=Image",
        "FileSearchData.FileType.Documents.displayName=Documents",
        "FileSearchData.FileType.Executables.displayName=Executables",
        "FileSearchData.FileType.Other.displayName=Other/Unknown"})
    enum FileType {
        
        IMAGE(0, Bundle.FileSearchData_FileType_Image_displayName(), FileTypeUtils.FileTypeCategory.IMAGE.getMediaTypes()),
        AUDIO(1, Bundle.FileSearchData_FileType_Audio_displayName(), FileTypeUtils.FileTypeCategory.AUDIO.getMediaTypes()),
        VIDEO(2, Bundle.FileSearchData_FileType_Video_displayName(), FileTypeUtils.FileTypeCategory.VIDEO.getMediaTypes()),
        EXECUTABLE(3, Bundle.FileSearchData_FileType_Executables_displayName(), FileTypeUtils.FileTypeCategory.EXECUTABLE.getMediaTypes()),
        DOCUMENTS(4, Bundle.FileSearchData_FileType_Documents_displayName(), FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes()),
        OTHER(5, Bundle.FileSearchData_FileType_Other_displayName(), new ArrayList<>());
        
        private final int ranking;  // For ordering in the UI
        private final String displayName;
        private final Collection<String> mediaTypes;
        
        private FileType (int value, String displayName, Collection<String> mediaTypes) {
            this.ranking = value;
            this.displayName = displayName;
            this.mediaTypes = mediaTypes;
        }
        
        /**
         * Get the MIME types matching this category.
         * 
         * @return Collection of MIME type strings
         */
        Collection<String> getMediaTypes() {
            return mediaTypes;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
        
        /**
         * Get the rank for sorting.
         * 
         * @return the rank (lower should be displayed first)
         */
        int getRanking() {
            return ranking;
        }
        
        /**
         * Get the enum matching the given MIME type.
         * 
         * @param mimeType The MIME type for the file
         * 
         * @return the corresponding enum (will be OTHER if no types matched)
         */
        static FileType fromMIMEtype(String mimeType) {
            for (FileType type : FileType.values()) {
                if (type.mediaTypes.contains(mimeType)) {
                    return type;
                }
            }
            return OTHER;
        }
        
        /**
         * Get the list of enums that are valid for filtering.
         * 
         * @return enums that can be used to filter
         */
        static List<FileType> getOptionsForFiltering() {
            return Arrays.asList(IMAGE, AUDIO, VIDEO, EXECUTABLE, DOCUMENTS);
        }
    }
    
    private FileSearchData() {
        // Class should not be instantiated
    }
}
