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
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;

/**
 * Utility enums for FileSearch
 */
class FileSearchData {

    private final static long BYTES_PER_MB = 1000000;

    /**
     * Enum representing how often the file occurs in the Central Repository.
     */
    @NbBundle.Messages({
        "FileSearchData.Frequency.unique.displayName=Unique (1)",
        "FileSearchData.Frequency.rare.displayName=Rare (2-5)",
        "FileSearchData.Frequency.count_10.displayName=6 - 10",
        "FileSearchData.Frequency.count_20.displayName=11 - 20",
        "FileSearchData.Frequency.count_50.displayName=21 - 50",
        "FileSearchData.Frequency.count_100.displayName=51 - 100",
        "FileSearchData.Frequency.common.displayName=Common",
        "FileSearchData.Frequency.unknown.displayName=Unknown",})
    enum Frequency {
        UNIQUE(0, 1, Bundle.FileSearchData_Frequency_unique_displayName()),
        RARE(1, 5, Bundle.FileSearchData_Frequency_rare_displayName()),
        COUNT_10(2, 10, Bundle.FileSearchData_Frequency_count_10_displayName()),
        COUNT_20(3, 20, Bundle.FileSearchData_Frequency_count_20_displayName()),
        COUNT_50(4, 50, Bundle.FileSearchData_Frequency_count_50_displayName()),
        COUNT_100(5, 100, Bundle.FileSearchData_Frequency_count_100_displayName()),
        COMMON(6, 0, Bundle.FileSearchData_Frequency_common_displayName()),
        UNKNOWN(7, 0, Bundle.FileSearchData_Frequency_unknown_displayName());

        private final int ranking;
        private final String displayName;
        private final int maxOccur;

        Frequency(int ranking, int maxOccur, String displayName) {
            this.ranking = ranking;
            this.maxOccur = maxOccur;
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
            if (count <= UNIQUE.maxOccur) {
                return UNIQUE;
            } else if (count <= RARE.maxOccur) {
                return RARE;
            } else if (count <= COUNT_10.maxOccur) {
                return COUNT_10;
            } else if (count <= COUNT_20.maxOccur) {
                return COUNT_20;
            } else if (count <= COUNT_50.maxOccur) {
                return COUNT_50;
            } else if (count <= COUNT_100.maxOccur) {
                return COUNT_100;
            }
            return COMMON;
        }

        /**
         * Get the list of enums that are valid for filtering.
         *
         * @return enums that can be used to filter
         */
        static List<Frequency> getOptionsForFiltering() {
            return Arrays.asList(UNIQUE, RARE, COUNT_10, COUNT_20, COUNT_50, COUNT_100, COMMON);
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
        "FileSearchData.FileSize.OVER_1GB.displayName=1 GB+",
        "FileSearchData.FileSize.OVER_200MB.displayName=200 MB - 1 GB",
        "FileSearchData.FileSize.OVER_50MB.displayName=50 - 200 MB",
        "FileSearchData.FileSize.OVER_1MB.displayName=1 - 50 MB",
        "FileSearchData.FileSize.OVER_100KB.displayName=100 KB - 1 MB",
        "FileSearchData.FileSize.UNDER_100KB.displayName=Under 100 KB",})
    enum FileSize {
        OVER_1GB(0, 1000 * BYTES_PER_MB, -1, Bundle.FileSearchData_FileSize_OVER_1GB_displayName()),
        OVER_200MB(1, 200 * BYTES_PER_MB, 1000 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_OVER_200MB_displayName()),
        OVER_50MB(2, 50 * BYTES_PER_MB, 200 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_OVER_50MB_displayName()),
        OVER_1MB(3, 1 * BYTES_PER_MB, 50 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_OVER_1MB_displayName()),
        OVER_100KB(4, 100000, 1 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_OVER_100KB_displayName()),
        UNDER_100KB(5, 0, 100000, Bundle.FileSearchData_FileSize_UNDER_100KB_displayName());

        private final int ranking;   // Must be unique for each value
        private final long minBytes; // Note that the size must be strictly greater than this to match
        private final long maxBytes;
        private final String displayName;
        final static long NO_MAXIMUM = -1;

        FileSize(int ranking, long minB, long maxB, String displayName) {
            this.ranking = ranking;
            this.minBytes = minB;
            if (maxB >= 0) {
                this.maxBytes = maxB;
            } else {
                this.maxBytes = NO_MAXIMUM;
            }
            this.displayName = displayName;
        }

        /**
         * Get the enum corresponding to the given file size. The file size must
         * be strictly greater than minBytes.
         *
         * @param size the file size
         *
         * @return the enum whose range contains the file size
         */
        static FileSize fromSize(long size) {
            if (size > OVER_1GB.minBytes) {
                return OVER_1GB;
            } else if (size > OVER_200MB.minBytes) {
                return OVER_200MB;
            } else if (size > OVER_50MB.minBytes) {
                return OVER_50MB;
            } else if (size > OVER_1MB.minBytes) {
                return OVER_1MB;
            } else if (size > OVER_100KB.minBytes) {
                return OVER_100KB;
            } else {
                return UNDER_100KB;
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
     * We don't simply use
     * FileTypeUtils.FileTypeCategory because: - Some file types categories
     * overlap - It is convenient to have the "OTHER" option for files that
     * don't match the given types
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

        private FileType(int value, String displayName, Collection<String> mediaTypes) {
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

    /**
     * Enum representing the score of the file.
     */
    @NbBundle.Messages({
        "FileSearchData.Score.notable.displayName=Notable",
        "FileSearchData.Score.interesting.displayName=Interesting",
        "FileSearchData.Score.unknown.displayName=Unknown",})
    enum Score {
        NOTABLE(0, Bundle.FileSearchData_Score_notable_displayName()),
        INTERESTING(1, Bundle.FileSearchData_Score_interesting_displayName()),
        UNKNOWN(2, Bundle.FileSearchData_Score_unknown_displayName());

        private final int ranking;
        private final String displayName;

        Score(int ranking, String displayName) {
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
         * Get the list of enums that are valid for filtering.
         *
         * @return enums that can be used to filter
         */
        static List<Score> getOptionsForFiltering() {
            return Arrays.asList(NOTABLE, INTERESTING);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private FileSearchData() {
        // Class should not be instantiated
    }
}
