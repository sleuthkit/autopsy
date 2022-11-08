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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Utility enums for searches made for files with Discovery.
 */
public final class SearchData {

    private final static long BYTES_PER_MB = 1000000;
    private static final Set<BlackboardArtifact.ARTIFACT_TYPE> DOMAIN_ARTIFACT_TYPES = 
            EnumSet.of(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY);

    
    /**
     * Enum representing the notability of the result in the Central Repository.
     */
    @NbBundle.Messages({
        "SearchData.prevNotable.displayName=Previously Notable",
        "SearchData.notPrevNotable.displayName=Previously Not Notable"
    })
    public enum PreviouslyNotable {
        PREVIOUSLY_NOTABLE(0, Bundle.SearchData_prevNotable_displayName()),
        NOT_PREVIOUSLY_NOTABLE(1, Bundle.SearchData_notPrevNotable_displayName());
        
        private final int ranking;
        private final String displayName;
        
        PreviouslyNotable(int ranking, String displayName) {
            this.ranking = ranking;
            this.displayName = displayName;
        }
        
        public int getRanking() {
            return ranking;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Enum representing the number of page views a domain has received. Page 
     * views a grouped into ranges for cleaner display and management.
     */
    @NbBundle.Messages({
        "# {0} - minValue",
        "# {1} - maxValue",
        "SearchData.PageViews.rangeTemplate={0}-{1} page views",
        "SearchData.PageViews.over1000=1000+ page views"
    })
    public enum PageViews {
        OVER_1000(1001, Long.MAX_VALUE), // ranking, minValue, maxValue
        UP_TO_1000(501, 1000),
        UP_TO_500(101, 500),
        UP_TO_100(51, 100),
        UP_TO_50(11, 50),
        UP_TO_10(0, 10);
        
        private final long minValue;
        private final long maxValue;
        
        PageViews(long minValue, long maxValue) {
            this.maxValue = maxValue;
            this.minValue = minValue;
        }
        
        @Override
        public String toString() {
            if (this == PageViews.OVER_1000) {
                return Bundle.SearchData_PageViews_over1000();
            } else {
                return Bundle.SearchData_PageViews_rangeTemplate(Long.toString(minValue), Long.toString(maxValue));
            }
        }
        
        /**
         * Determines if the given count is covered by this PageView interval.
         */
        boolean covers(long count) {
            return count >= minValue && count <= maxValue;
        }
        
        /**
         * Utility to fetch the appropriate PageView interval for the given count.
         */
        public static PageViews fromPageViewCount(long count) {
            for (PageViews view : PageViews.values()) {
                if (view.covers(count)) {
                    return view;
                }
            }
            return null;
        }
    }
    
    /**
     * Enum representing how often the result occurs in the Central Repository.
     */
    @NbBundle.Messages({
        "SearchData.Frequency.unique.displayName=Unique (1)",
        "SearchData.Frequency.rare.displayName=Rare (2-10)",
        "SearchData.Frequency.common.displayName=Common (11 - 100)",
        "SearchData.Frequency.verycommon.displayName=Very Common (100+)",
        "SearchData.Frequency.known.displayName=Known (NSRL)",
        "SearchData.Frequency.unknown.displayName=Unknown",})
    public enum Frequency {
        UNIQUE(0, 1, Bundle.SearchData_Frequency_unique_displayName()),
        RARE(1, 10, Bundle.SearchData_Frequency_rare_displayName()),
        COMMON(2, 100, Bundle.SearchData_Frequency_common_displayName()),
        VERY_COMMON(3, 0, Bundle.SearchData_Frequency_verycommon_displayName()),
        KNOWN(4, 0, Bundle.SearchData_Frequency_known_displayName()),
        UNKNOWN(5, 0, Bundle.SearchData_Frequency_unknown_displayName());

        private final int ranking;
        private final String displayName;
        private final int maxOccur;

        /**
         * Construct a new frequency enum value.
         *
         * @param ranking     The rank for sorting.
         * @param maxOccur    The max occurrences this enum value is for.
         * @param displayName The display name for this enum value.
         */
        Frequency(int ranking, int maxOccur, String displayName) {
            this.ranking = ranking;
            this.maxOccur = maxOccur;
            this.displayName = displayName;
        }

        /**
         * Get the rank for sorting.
         *
         * @return The rank (lower should be displayed first).
         */
        public int getRanking() {
            return ranking;
        }

        /**
         * Get the enum matching the given occurrence count.
         *
         * @param count Number of times a result is in the Central Repository.
         *
         * @return The corresponding enum.
         */
        public static Frequency fromCount(long count) {
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
        public static List<Frequency> getOptionsForFilteringWithCr() {
            return Arrays.asList(UNIQUE, RARE, COMMON, VERY_COMMON, KNOWN);
        }

        /**
         * Get the list of enums that are valid for filtering when no CR is
         * enabled.
         *
         * @return enums that can be used to filter without a CR.
         */
        public static List<Frequency> getOptionsForFilteringWithoutCr() {
            return Arrays.asList(KNOWN, UNKNOWN);
        }

        @Override
        public String toString() {
            return displayName;
        }

        /**
         * Get the maximum number of occurrences this enum value is for.
         *
         * @return The maximum number of occurrences this enum value is for.
         */
        public int getMaxOccur() {
            return maxOccur;
        }
    }

    /**
     * Enum representing the file size.
     */
    @NbBundle.Messages({
        "SearchData.FileSize.XXLARGE.displayName=XXLarge",
        "SearchData.FileSize.XLARGE.displayName=XLarge",
        "SearchData.FileSize.LARGE.displayName=Large",
        "SearchData.FileSize.MEDIUM.displayName=Medium",
        "SearchData.FileSize.SMALL.displayName=Small",
        "SearchData.FileSize.XSMALL.displayName=XSmall",
        "SearchData.FileSize.10PlusGb=: 10GB+",
        "SearchData.FileSize.5gbto10gb=: 5-10GB",
        "SearchData.FileSize.1gbto5gb=: 1-5GB",
        "SearchData.FileSize.100mbto1gb=: 100MB-1GB",
        "SearchData.FileSize.200PlusMb=: 200MB+",
        "SearchData.FileSize.50mbto200mb=: 50-200MB",
        "SearchData.FileSize.500kbto100mb=: 500KB-100MB",
        "SearchData.FileSize.1mbto50mb=: 1-50MB",
        "SearchData.FileSize.100kbto1mb=: 100KB-1MB",
        "SearchData.FileSize.16kbto100kb=: 16-100KB",
        "SearchData.FileSize.upTo500kb=: 0-500KB",
        "SearchData.FileSize.upTo16kb=: 0-16KB",})
    public enum FileSize {
        XXLARGE_VIDEO(0, 10000 * BYTES_PER_MB, -1, Bundle.SearchData_FileSize_XXLARGE_displayName(), Bundle.SearchData_FileSize_10PlusGb()),
        XLARGE_VIDEO(1, 5000 * BYTES_PER_MB, 10000 * BYTES_PER_MB, Bundle.SearchData_FileSize_XLARGE_displayName(), Bundle.SearchData_FileSize_5gbto10gb()),
        LARGE_VIDEO(2, 1000 * BYTES_PER_MB, 5000 * BYTES_PER_MB, Bundle.SearchData_FileSize_LARGE_displayName(), Bundle.SearchData_FileSize_1gbto5gb()),
        MEDIUM_VIDEO(3, 100 * BYTES_PER_MB, 1000 * BYTES_PER_MB, Bundle.SearchData_FileSize_MEDIUM_displayName(), Bundle.SearchData_FileSize_100mbto1gb()),
        SMALL_VIDEO(4, 500000, 100 * BYTES_PER_MB, Bundle.SearchData_FileSize_SMALL_displayName(), Bundle.SearchData_FileSize_500kbto100mb()),
        XSMALL_VIDEO(5, 0, 500000, Bundle.SearchData_FileSize_XSMALL_displayName(), Bundle.SearchData_FileSize_upTo500kb()),
        XXLARGE_IMAGE(6, 200 * BYTES_PER_MB, -1, Bundle.SearchData_FileSize_XXLARGE_displayName(), Bundle.SearchData_FileSize_200PlusMb()),
        XLARGE_IMAGE(7, 50 * BYTES_PER_MB, 200 * BYTES_PER_MB, Bundle.SearchData_FileSize_XLARGE_displayName(), Bundle.SearchData_FileSize_50mbto200mb()),
        LARGE_IMAGE(8, 1 * BYTES_PER_MB, 50 * BYTES_PER_MB, Bundle.SearchData_FileSize_LARGE_displayName(), Bundle.SearchData_FileSize_1mbto50mb()),
        MEDIUM_IMAGE(9, 100000, 1 * BYTES_PER_MB, Bundle.SearchData_FileSize_MEDIUM_displayName(), Bundle.SearchData_FileSize_100kbto1mb()),
        SMALL_IMAGE(10, 16000, 100000, Bundle.SearchData_FileSize_SMALL_displayName(), Bundle.SearchData_FileSize_16kbto100kb()),
        XSMALL_IMAGE(11, 0, 16000, Bundle.SearchData_FileSize_XSMALL_displayName(), Bundle.SearchData_FileSize_upTo16kb());

        private final int ranking;   // Must be unique for each value
        private final long minBytes; // Note that the size must be strictly greater than this to match
        private final long maxBytes;
        private final String sizeGroup;
        private final String displaySize;
        final static long NO_MAXIMUM = -1;

        /**
         * Construct a new FileSize enum value.
         *
         * @param ranking     The rank for sorting.
         * @param minB        The minimum size included in this enum value.
         * @param maxB        The maximum size included in this enum value.
         * @param displayName The display name for this enum value.
         * @param displaySize The size to display in association with this enum
         *                    value.
         */
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
         * @param size The file size.
         *
         * @return The enum whose range contains the file size.
         */
        public static FileSize fromImageSize(long size) {
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
         * @param size The file size.
         *
         * @return The enum whose range contains the file size.
         */
        public static FileSize fromVideoSize(long size) {
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
         * @return The maximum file size that will fit in this range.
         */
        public long getMaxBytes() {
            return maxBytes;
        }

        /**
         * Get the lower limit of the range.
         *
         * @return The maximum file size that is not part of this range.
         */
        public long getMinBytes() {
            return minBytes;
        }

        /**
         * Get the rank for sorting.
         *
         * @return The rank (lower should be displayed first).
         */
        public int getRanking() {
            return ranking;
        }

        @Override
        public String toString() {
            return sizeGroup + displaySize;
        }

        /**
         * Get the name of the size group. For example Small.
         *
         * @return The name of the size group. For example Small.
         */
        public String getSizeGroup() {
            return sizeGroup;
        }

        /**
         * Get the list of enums that are valid for most file sizes.
         *
         * @return Enums that can be used to filter most file including images
         *         by size.
         */
        public static List<FileSize> getDefaultSizeOptions() {
            return Arrays.asList(XXLARGE_IMAGE, XLARGE_IMAGE, LARGE_IMAGE, MEDIUM_IMAGE, SMALL_IMAGE, XSMALL_IMAGE);
        }

        /**
         * Get the list of enums that are valid for video sizes.
         *
         * @return enums that can be used to filter videos by size.
         */
        public static List<FileSize> getOptionsForVideos() {
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

    /**
     * Get the list of document types for which image extraction is not
     * supported.
     *
     * @return The list of document types for which image extraction is not
     *         supported.
     */
    public static Collection<String> getDocTypesWithoutImageExtraction() {
        return Collections.unmodifiableCollection(IMAGE_UNSUPPORTED_DOC_TYPES);
    }

    /**
     * Enum representing the type.
     */
    @NbBundle.Messages({
        "SearchData.FileType.Audio.displayName=Audio",
        "SearchData.FileType.Video.displayName=Video",
        "SearchData.FileType.Image.displayName=Image",
        "SearchData.FileType.Documents.displayName=Document",
        "SearchData.FileType.Executables.displayName=Executable",
        "SearchData.AttributeType.Domain.displayName=Domain",
        "SearchData.FileType.Other.displayName=Other/Unknown"})
    public enum Type {

        IMAGE(0, Bundle.SearchData_FileType_Image_displayName(), FileTypeUtils.FileTypeCategory.IMAGE.getMediaTypes(), new ArrayList<>()),
        AUDIO(1, Bundle.SearchData_FileType_Audio_displayName(), FileTypeUtils.FileTypeCategory.AUDIO.getMediaTypes(), new ArrayList<>()),
        VIDEO(2, Bundle.SearchData_FileType_Video_displayName(), FileTypeUtils.FileTypeCategory.VIDEO.getMediaTypes(), new ArrayList<>()),
        EXECUTABLE(3, Bundle.SearchData_FileType_Executables_displayName(), FileTypeUtils.FileTypeCategory.EXECUTABLE.getMediaTypes(), new ArrayList<>()),
        DOCUMENT(4, Bundle.SearchData_FileType_Documents_displayName(), DOCUMENT_MIME_TYPES, new ArrayList<>()),
        DOMAIN(6, Bundle.SearchData_AttributeType_Domain_displayName(), new ArrayList<>(), DOMAIN_ARTIFACT_TYPES),
        OTHER(5, Bundle.SearchData_FileType_Other_displayName(), new ArrayList<>(), new ArrayList<>());

        private final int ranking;  // For ordering in the UI
        private final String displayName;
        private final Collection<String> mediaTypes;
        private final Collection<BlackboardArtifact.ARTIFACT_TYPE> artifactTypes;

        /**
         * Construct a new Type enum value.
         *
         * @param value         Integer value for comparison.
         * @param displayName   The display name for this type.
         * @param mediaTypes    The list of mime types this type is defined by
         *                      if it is file type.
         * @param artifactTypes The list of artifact types this type is defined
         *                      by if it is an attribute type.
         */
        Type(int value, String displayName, Collection<String> mediaTypes, Collection<BlackboardArtifact.ARTIFACT_TYPE> artifactTypes) {
            this.ranking = value;
            this.displayName = displayName;
            this.mediaTypes = mediaTypes;
            this.artifactTypes = artifactTypes;
        }

        /**
         * Get the MIME types matching this category.
         *
         * @return Collection of MIME type strings
         */
        public Collection<String> getMediaTypes() {
            return Collections.unmodifiableCollection(mediaTypes);
        }

        /**
         * Get the BlackboardArtifact types matching this category.
         *
         * @return Collection of BlackboardArtifact.ARTIFACT_TYPE objects.
         */
        public Collection<BlackboardArtifact.ARTIFACT_TYPE> getArtifactTypes() {
            return Collections.unmodifiableCollection(artifactTypes);
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
        public int getRanking() {
            return ranking;
        }

    }

    /**
     * Enum representing the score of the item.
     */
    @NbBundle.Messages({
        "SearchData.Score.notable.displayName=Notable",
        "SearchData.Score.interesting.displayName=Interesting",
        "SearchData.Score.unknown.displayName=Unknown",})
    public enum Score {
        NOTABLE(0, Bundle.SearchData_Score_notable_displayName()),
        INTERESTING(1, Bundle.SearchData_Score_interesting_displayName()),
        UNKNOWN(2, Bundle.SearchData_Score_unknown_displayName());

        private final int ranking;
        private final String displayName;

        /**
         * Construct a new Score enum value.
         *
         * @param ranking     The rank for sorting.
         * @param displayName The display name for this enum value.
         */
        Score(int ranking, String displayName) {
            this.ranking = ranking;
            this.displayName = displayName;
        }

        /**
         * Get the rank for sorting.
         *
         * @return The rank (lower should be displayed first).
         */
        public int getRanking() {
            return ranking;
        }

        /**
         * Get the list of enums that are valid for filtering.
         *
         * @return Enums that can be used to filter.
         */
        public static List<Score> getOptionsForFiltering() {
            return Arrays.asList(NOTABLE, INTERESTING);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Private constructor for SearchData class.
     */
    private SearchData() {
        // Class should not be instantiated
    }
}
