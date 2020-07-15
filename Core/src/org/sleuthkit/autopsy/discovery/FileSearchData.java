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
package org.sleuthkit.autopsy.discovery;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;

/**
 * Utility enums for FileSearch.
 */
final class FileSearchData {

    private final static long BYTES_PER_MB = 1000000;

    /**
     * Enum representing how often the file occurs in the Central Repository.
     */
    @NbBundle.Messages({
        "FileSearchData.Frequency.unique.displayName=Unique (1)",
        "FileSearchData.Frequency.rare.displayName=Rare (2-10)",
        "FileSearchData.Frequency.common.displayName=Common (11 - 100)",
        "FileSearchData.Frequency.verycommon.displayName=Very Common (100+)",
        "FileSearchData.Frequency.known.displayName=Known (NSRL)",
        "FileSearchData.Frequency.unknown.displayName=Unknown",})
    enum Frequency {
        UNIQUE(0, 1, Bundle.FileSearchData_Frequency_unique_displayName()),
        RARE(1, 10, Bundle.FileSearchData_Frequency_rare_displayName()),
        COMMON(2, 100, Bundle.FileSearchData_Frequency_common_displayName()),
        VERY_COMMON(3, 0, Bundle.FileSearchData_Frequency_verycommon_displayName()),
        KNOWN(4, 0, Bundle.FileSearchData_Frequency_known_displayName()),
        UNKNOWN(5, 0, Bundle.FileSearchData_Frequency_unknown_displayName());

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
            if (count <= UNIQUE.getMaxOccur()) {
                return UNIQUE;
            } else if (count <= RARE.getMaxOccur()) {
                return RARE;
            } else if (count <= COMMON.getMaxOccur()) {
                return COMMON;
            }
            return VERY_COMMON;
        }

        /**
         * Get the list of enums that are valid for filtering when a CR is
         * enabled.
         *
         * @return enums that can be used to filter with a CR.
         */
        static List<Frequency> getOptionsForFilteringWithCr() {
            return Arrays.asList(UNIQUE, RARE, COMMON, VERY_COMMON, KNOWN);
        }

        /**
         * Get the list of enums that are valid for filtering when no CR is
         * enabled.
         *
         * @return enums that can be used to filter without a CR.
         */
        static List<Frequency> getOptionsForFilteringWithoutCr() {
            return Arrays.asList(KNOWN, UNKNOWN);
        }

        @Override
        public String toString() {
            return displayName;
        }

        /**
         * @return the maxOccur
         */
        int getMaxOccur() {
            return maxOccur;
        }
    }

    /**
     * Enum representing the file size
     */
    @NbBundle.Messages({
        "FileSearchData.FileSize.XXLARGE.displayName=XXLarge",
        "FileSearchData.FileSize.XLARGE.displayName=XLarge",
        "FileSearchData.FileSize.LARGE.displayName=Large",
        "FileSearchData.FileSize.MEDIUM.displayName=Medium",
        "FileSearchData.FileSize.SMALL.displayName=Small",
        "FileSearchData.FileSize.XSMALL.displayName=XSmall",
        "FileSearchData.FileSize.10PlusGb=: 10GB+",
        "FileSearchData.FileSize.5gbto10gb=: 5-10GB",
        "FileSearchData.FileSize.1gbto5gb=: 1-5GB",
        "FileSearchData.FileSize.100mbto1gb=: 100MB-1GB",
        "FileSearchData.FileSize.200PlusMb=: 200MB+",
        "FileSearchData.FileSize.50mbto200mb=: 50-200MB",
        "FileSearchData.FileSize.500kbto100mb=: 500KB-100MB",
        "FileSearchData.FileSize.1mbto50mb=: 1-50MB",
        "FileSearchData.FileSize.100kbto1mb=: 100KB-1MB",
        "FileSearchData.FileSize.16kbto100kb=: 16-100KB",
        "FileSearchData.FileSize.upTo500kb=: 0-500KB",
        "FileSearchData.FileSize.upTo16kb=: 0-16KB",})
    enum FileSize {
        XXLARGE_VIDEO(0, 10000 * BYTES_PER_MB, -1, Bundle.FileSearchData_FileSize_XXLARGE_displayName(), Bundle.FileSearchData_FileSize_10PlusGb()),
        XLARGE_VIDEO(1, 5000 * BYTES_PER_MB, 10000 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_XLARGE_displayName(), Bundle.FileSearchData_FileSize_5gbto10gb()),
        LARGE_VIDEO(2, 1000 * BYTES_PER_MB, 5000 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_LARGE_displayName(), Bundle.FileSearchData_FileSize_1gbto5gb()),
        MEDIUM_VIDEO(3, 100 * BYTES_PER_MB, 1000 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_MEDIUM_displayName(), Bundle.FileSearchData_FileSize_100mbto1gb()),
        SMALL_VIDEO(4, 500000, 100 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_SMALL_displayName(), Bundle.FileSearchData_FileSize_500kbto100mb()),
        XSMALL_VIDEO(5, 0, 500000, Bundle.FileSearchData_FileSize_XSMALL_displayName(), Bundle.FileSearchData_FileSize_upTo500kb()),
        XXLARGE_IMAGE(6, 200 * BYTES_PER_MB, -1, Bundle.FileSearchData_FileSize_XXLARGE_displayName(), Bundle.FileSearchData_FileSize_200PlusMb()),
        XLARGE_IMAGE(7, 50 * BYTES_PER_MB, 200 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_XLARGE_displayName(), Bundle.FileSearchData_FileSize_50mbto200mb()),
        LARGE_IMAGE(8, 1 * BYTES_PER_MB, 50 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_LARGE_displayName(), Bundle.FileSearchData_FileSize_1mbto50mb()),
        MEDIUM_IMAGE(9, 100000, 1 * BYTES_PER_MB, Bundle.FileSearchData_FileSize_MEDIUM_displayName(), Bundle.FileSearchData_FileSize_100kbto1mb()),
        SMALL_IMAGE(10, 16000, 100000, Bundle.FileSearchData_FileSize_SMALL_displayName(), Bundle.FileSearchData_FileSize_16kbto100kb()),
        XSMALL_IMAGE(11, 0, 16000, Bundle.FileSearchData_FileSize_XSMALL_displayName(), Bundle.FileSearchData_FileSize_upTo16kb());

        private final int ranking;   // Must be unique for each value
        private final long minBytes; // Note that the size must be strictly greater than this to match
        private final long maxBytes;
        private final String sizeGroup;
        private final String displaySize;
        final static long NO_MAXIMUM = -1;

        FileSize(int ranking, long minB, long maxB, String displayName, String displaySize) {
            this.ranking = ranking;
            this.minBytes = minB;
            if (maxB >= 0) {
                this.maxBytes = maxB;
            } else {
                this.maxBytes = NO_MAXIMUM;
            }
            this.sizeGroup = displayName;
            this.displaySize = displaySize;
        }

        /**
         * Get the enum corresponding to the given file size for image files.
         * The file size must be strictly greater than minBytes.
         *
         * @param size the file size
         *
         * @return the enum whose range contains the file size
         */
        static FileSize fromImageSize(long size) {
            if (size > XXLARGE_IMAGE.getMinBytes()) {
                return XXLARGE_IMAGE;
            } else if (size > XLARGE_IMAGE.getMinBytes()) {
                return XLARGE_IMAGE;
            } else if (size > LARGE_IMAGE.getMinBytes()) {
                return LARGE_IMAGE;
            } else if (size > MEDIUM_IMAGE.getMinBytes()) {
                return MEDIUM_IMAGE;
            } else if (size > SMALL_IMAGE.getMinBytes()) {
                return SMALL_IMAGE;
            } else {
                return XSMALL_IMAGE;
            }
        }

        /**
         * Get the enum corresponding to the given file size for video files.
         * The file size must be strictly greater than minBytes.
         *
         * @param size the file size
         *
         * @return the enum whose range contains the file size
         */
        static FileSize fromVideoSize(long size) {
            if (size > XXLARGE_VIDEO.getMinBytes()) {
                return XXLARGE_VIDEO;
            } else if (size > XLARGE_VIDEO.getMinBytes()) {
                return XLARGE_VIDEO;
            } else if (size > LARGE_VIDEO.getMinBytes()) {
                return LARGE_VIDEO;
            } else if (size > MEDIUM_VIDEO.getMinBytes()) {
                return MEDIUM_VIDEO;
            } else if (size > SMALL_VIDEO.getMinBytes()) {
                return SMALL_VIDEO;
            } else {
                return XSMALL_VIDEO;
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
            return sizeGroup + displaySize;
        }
        
        String getSizeGroup(){
            return sizeGroup;
        }

        /**
         * Get the list of enums that are valid for most file sizes.
         *
         * @return Enums that can be used to filter most file including images
         *         by size.
         */
        static List<FileSize> getDefaultSizeOptions() {
            return Arrays.asList(XXLARGE_IMAGE, XLARGE_IMAGE, LARGE_IMAGE, MEDIUM_IMAGE, SMALL_IMAGE, XSMALL_IMAGE);
        }

        /**
         * Get the list of enums that are valid for video sizes.
         *
         * @return enums that can be used to filter videos by size.
         */
        static List<FileSize> getOptionsForVideos() {
            return Arrays.asList(XXLARGE_VIDEO, XLARGE_VIDEO, LARGE_VIDEO, MEDIUM_VIDEO, SMALL_VIDEO, XSMALL_VIDEO);
        }
    }

    //Discovery uses a different list of document mime types than FileTypeUtils.FileTypeCategory.DOCUMENTS
    private static final ImmutableSet<String> DOCUMENT_MIME_TYPES
            = new ImmutableSet.Builder<String>()
                    .add("text/html", //NON-NLS
                            "text/csv", //NON-NLS
                            "application/rtf", //NON-NLS
                            "application/pdf", //NON-NLS
                            "application/xhtml+xml", //NON-NLS
                            "application/x-msoffice", //NON-NLS
                            "application/msword", //NON-NLS
                            "application/msword2", //NON-NLS
                            "application/vnd.wordperfect", //NON-NLS
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
                            "application/vnd.ms-powerpoint", //NON-NLS
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
                            "application/vnd.ms-excel", //NON-NLS
                            "application/vnd.ms-excel.sheet.4", //NON-NLS
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", //NON-NLS
                            "application/vnd.oasis.opendocument.presentation", //NON-NLS
                            "application/vnd.oasis.opendocument.spreadsheet", //NON-NLS
                            "application/vnd.oasis.opendocument.text" //NON-NLS
                    ).build();

    private static final ImmutableSet<String> IMAGE_UNSUPPORTED_DOC_TYPES
            = new ImmutableSet.Builder<String>()
                    .add("application/pdf", //NON-NLS
                            "application/xhtml+xml").build(); //NON-NLS

    static Collection<String> getDocTypesWithoutImageExtraction() {
        return Collections.unmodifiableCollection(IMAGE_UNSUPPORTED_DOC_TYPES);
    }

    /**
     * Enum representing the file type. We don't simply use
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
        DOCUMENTS(4, Bundle.FileSearchData_FileType_Documents_displayName(), DOCUMENT_MIME_TYPES),
        OTHER(5, Bundle.FileSearchData_FileType_Other_displayName(), new ArrayList<>());

        private final int ranking;  // For ordering in the UI
        private final String displayName;
        private final Collection<String> mediaTypes;

        FileType(int value, String displayName, Collection<String> mediaTypes) {
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
            return Collections.unmodifiableCollection(mediaTypes);
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
                if (type.getMediaTypes().contains(mimeType)) {
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
            return Arrays.asList(IMAGE, VIDEO);
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
