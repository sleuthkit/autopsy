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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
@Messages({"TagsDAO.fileColumns.nameColLbl=Name",
    "TagsDAO.fileColumns.originalName=Original Name",
    "TagsDAO.fileColumns.filePathColLbl=File Path",
    "TagsDAO.fileColumns.commentColLbl=Comment",
    "TagsDAO.fileColumns.modifiedTimeColLbl=Modified Time",
    "TagsDAO.fileColumns.changeTimeColLbl=Changed Time",
    "TagsDAO.fileColumns.accessTimeColLbl=Accessed Time",
    "TagsDAO.fileColumns.createdTimeColLbl=Created Time",
    "TagsDAO.fileColumns.sizeColLbl=Size",
    "TagsDAO.fileColumns.md5HashColLbl=MD5 Hash",
    "TagsDAO.fileColumns.userNameColLbl=User Name",
    "TagsDAO.fileColumns.noDescription=No Description",
    "TagsDAO.tagColumns.sourceNameColLbl=Source Name",
    "TagsDAO.tagColumns.origNameColLbl=Original Name",
    "TagsDAO.tagColumns.sourcePathColLbl=Source File Path",
    "TagsDAO.tagColumns.typeColLbl=Result Type",
    "TagsDAO.tagColumns.commentColLbl=Comment",
    "TagsDAO.tagColumns.userNameColLbl=User Name"})
public class TagsDAO {

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    
    private static final String FILE_TAG_TYPE_ID = "FILE_TAG";
    private static final String RESULT_TAG_TYPE_ID = "RESULT_TAG";
    private static final String FILE_TAG_DISPLAY_NAME = "File Tag";
    private static final String RESULT_TAG_DISPLAY_NAME = "Result Tag";
    
    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_originalName()), // ELODO handle translation
            getFileColumnKey(Bundle.TagsDAO_fileColumns_filePathColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_commentColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_modifiedTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_changeTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_accessTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_createdTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_sizeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_md5HashColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_userNameColLbl()));

    private static final List<ColumnKey> RESULT_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_tagColumns_sourceNameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_origNameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_sourcePathColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_typeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_commentColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_userNameColLbl()));

    private static TagsDAO instance = null;

    synchronized static TagsDAO getInstance() {
        if (instance == null) {
            instance = new TagsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.TagsDAO_fileColumns_noDescription());
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }
    
    static ExtensionMediaType getExtensionMediaType(String ext) {
        if (StringUtils.isBlank(ext)) {
            return ExtensionMediaType.UNCATEGORIZED;
        } else {
            ext = "." + ext;
        }
        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
            return ExtensionMediaType.IMAGE;
        } else if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
            return ExtensionMediaType.VIDEO;
        } else if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
            return ExtensionMediaType.AUDIO;
        } else if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
            return ExtensionMediaType.DOC;
        } else if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
            return ExtensionMediaType.EXECUTABLE;
        } else if (FileTypeExtensions.getTextExtensions().contains(ext)) {
            return ExtensionMediaType.TEXT;
        } else if (FileTypeExtensions.getWebExtensions().contains(ext)) {
            return ExtensionMediaType.WEB;
        } else if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
            return ExtensionMediaType.PDF;
        } else if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
            return ExtensionMediaType.ARCHIVE;
        } else {
            return ExtensionMediaType.UNCATEGORIZED;
        }
    }
    
    public SearchResultsDTO getTags(TagsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getTagName() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }
        
        SearchParams<TagsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchTagsDTOs(key.getTagName(), key.getTagType(), key.getDataSourceId(), startItem, maxCount));
    }

    @NbBundle.Messages({"FileTag.name.text=File Tag"})
    private SearchResultsDTO fetchTagsDTOs(TagName tagName, TagsSearchParams.TagType type, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        if (null == type) {
            // ELTODO throw?
            return null;
        }

        switch (type) {
            case FILE:
                return fetchFileTags(tagName, dataSourceId, startItem, maxResultCount);
            case RESULT:
                return fetchResultTags(tagName, dataSourceId, startItem, maxResultCount);
            default:
                // ELTODO throw?
            return null;
        }
    }
    
    /* GET RESULT TAGS
     * BlackboardArtifactTagNodeFactory.createKeys(List<BlackboardArtifactTag> tags)
     * BlackboardArtifactTagNode.createSheet()
     */
    
    /* GET FILE TAGS
     * ContentTagNodeFactory.createKeys(List<ContentTag> tags)
     * ContentTagNode.createSheet()
     */
    
    private SearchResultsDTO fetchResultTags(TagName tagName, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {

        // ELTODO startItem, maxResultCount
        
        List<BlackboardArtifactTag> tags = new ArrayList<>();
        // Use the blackboard artifact tags bearing the specified tag name as the tags.
        List<BlackboardArtifactTag> artifactTags = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName, dataSourceId)
                : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName);
        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            for (BlackboardArtifactTag tag : artifactTags) {
                if (userName.equals(tag.getUserName())) {
                    tags.add(tag);
                }
            }
        } else {
            tags.addAll(artifactTags);
        }

        List<RowDTO> fileRows = new ArrayList<>();
        for (BlackboardArtifactTag tag : tags) {
            String name = tag.getContent().getName();  // As a backup.
            try {
                name = tag.getArtifact().getShortDescription();
            } catch (TskCoreException ignore) {
                // it's a WARNING, skip
            }
            
            String contentPath;
            try {
                contentPath = tag.getContent().getUniquePath();
            } catch (TskCoreException ex) {
                contentPath = NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.unavail.text");
            }

            List<Object> cellValues = Arrays.asList(
                    name,
                    null, // ELTODO translation column
                    contentPath,
                    tag.getArtifact().getDisplayName(),
                    tag.getComment(),
                    tag.getUserName());

            fileRows.add(new BaseRowDTO(
                    cellValues,
                    RESULT_TAG_TYPE_ID,
                    tag.getId()));
        }

        return new BaseSearchResultsDTO(RESULT_TAG_TYPE_ID, RESULT_TAG_DISPLAY_NAME, RESULT_TAG_COLUMNS, fileRows, startItem, fileRows.size());
    }
    
    private SearchResultsDTO fetchFileTags(TagName tagName, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {

        // ELTODO startItem, maxResultCount
        
        List<ContentTag> tags = new ArrayList<>();
        List<ContentTag> contentTags = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName, dataSourceId)
                : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName);
        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            for (ContentTag tag : contentTags) {
                if (userName.equals(tag.getUserName())) {
                    tags.add(tag);
                }
            }
        } else {
            tags.addAll(contentTags);
        }

        List<RowDTO> fileRows = new ArrayList<>();
        for (ContentTag tag : tags) {
            Content content = tag.getContent();
            String contentPath = content.getUniquePath();
            AbstractFile file = content instanceof AbstractFile ? (AbstractFile) content : null;

            List<Object> cellValues = Arrays.asList(
                    content.getName(),
                    null, // ELTODO translation column
                    contentPath,
                    tag.getComment(),
                    file != null ? TimeZoneUtils.getFormattedTime(file.getMtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getCtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getAtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getCrtime()) : "",
                    content.getSize(),
                    file != null ? StringUtils.defaultString(file.getMd5Hash()) : "",
                    tag.getUserName());

            fileRows.add(new BaseRowDTO(
                    cellValues,
                    FILE_TAG_TYPE_ID,
                    file.getId()));
        }

        return new BaseSearchResultsDTO(FILE_TAG_TYPE_ID, FILE_TAG_DISPLAY_NAME, FILE_TAG_COLUMNS, fileRows, startItem, fileRows.size());
    }
}
