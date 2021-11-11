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

import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the allTags
 section.
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
public class TagsDAO extends AbstractDAO {

    private static final int CACHE_SIZE = 5; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    
    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_originalName()), // GVDTODO handle translation
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
    
    public SearchResultsDTO getTags(TagsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getTagName() == null) {
            throw new IllegalArgumentException("Must have non-null tag name");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        } else if (key.getTagType() == null) {
            throw new IllegalArgumentException("Must have non-null tag type");
        }
        
        SearchParams<TagsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchTagsDTOs(searchParams));
    }   

    @NbBundle.Messages({"FileTag.name.text=File Tag",
            "ResultTag.name.text=Result Tag"})
    private SearchResultsDTO fetchTagsDTOs(SearchParams<TagsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {
        switch (cacheKey.getParamData().getTagType()) {
            case FILE:
                return fetchFileTags(cacheKey);
            case RESULT:
                return fetchResultTags(cacheKey);
            default:
                throw new IllegalArgumentException("Unsupported tag type");
        }
    }
    
    /**
     * Returns a list of paged tag results.
     *
     * @param tags         The tag results.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged tag results.
     */
    List<? extends Tag> getPaged(List<? extends Tag> tags, SearchParams<?> searchParams) {
        Stream<? extends Tag> pagedTagsStream = tags.stream()
                .sorted(Comparator.comparing((tag) -> tag.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedTagsStream = pagedTagsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedTagsStream.collect(Collectors.toList());
    }

    private SearchResultsDTO fetchResultTags(SearchParams<TagsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        TagName tagName = cacheKey.getParamData().getTagName();
        
        // get all tag results
        List<BlackboardArtifactTag> allTags = new ArrayList<>();
        List<BlackboardArtifactTag> artifactTags = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName, dataSourceId)
                : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName);
        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            for (BlackboardArtifactTag tag : artifactTags) {
                if (userName.equals(tag.getUserName())) {
                    allTags.add(tag);
                }
            }
        } else {
            allTags.addAll(artifactTags);
        }
        
        // get current page of tag results
        List<? extends Tag> pagedTags = getPaged(allTags, cacheKey);

        List<RowDTO> fileRows = new ArrayList<>();
        for (Tag tag : pagedTags) {
            BlackboardArtifactTag blackboardTag = (BlackboardArtifactTag) tag;
            
            String name = blackboardTag.getContent().getName();  // As a backup.
            try {
                name = blackboardTag.getArtifact().getShortDescription();
            } catch (TskCoreException ignore) {
                // it's a WARNING, skip
            }
            
            String contentPath;
            try {
                contentPath = blackboardTag.getContent().getUniquePath();
            } catch (TskCoreException ex) {
                contentPath = NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.unavail.text");
            }

            List<Object> cellValues = Arrays.asList(name,
                    null, // GVDTODO translation column
                    contentPath,
                    blackboardTag.getArtifact().getDisplayName(),
                    blackboardTag.getComment(),
                    blackboardTag.getUserName());

            fileRows.add(new BlackboardArtifactTagsRowDTO(
                    blackboardTag,
                    cellValues,
                    blackboardTag.getId()));
        }

        return new BaseSearchResultsDTO(BlackboardArtifactTagsRowDTO.getTypeIdForClass(), Bundle.ResultTag_name_text(), RESULT_TAG_COLUMNS, fileRows, 0, allTags.size());
    }
    
    private SearchResultsDTO fetchFileTags(SearchParams<TagsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        TagName tagName = cacheKey.getParamData().getTagName();
        
        // get all tag results
        List<ContentTag> allTags = new ArrayList<>();
        List<ContentTag> contentTags = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName, dataSourceId)
                : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName);
        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            for (ContentTag tag : contentTags) {
                if (userName.equals(tag.getUserName())) {
                    allTags.add(tag);
                }
            }
        } else {
            allTags.addAll(contentTags);
        }
        
        // get current page of tag results
        List<? extends Tag> pagedTags = getPaged(allTags, cacheKey);
        
        List<RowDTO> fileRows = new ArrayList<>();
        for (Tag tag : pagedTags) {
            ContentTag contentTag = (ContentTag) tag;
            Content content = contentTag.getContent();
            String contentPath = content.getUniquePath();
            AbstractFile file = content instanceof AbstractFile ? (AbstractFile) content : null;

            List<Object> cellValues = Arrays.asList(
                    content.getName(),
                    null, // GVDTODO translation column
                    contentPath,
                    contentTag.getComment(),
                    file != null ? TimeZoneUtils.getFormattedTime(file.getMtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getCtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getAtime()) : "",
                    file != null ? TimeZoneUtils.getFormattedTime(file.getCrtime()) : "",
                    content.getSize(),
                    file != null ? StringUtils.defaultString(file.getMd5Hash()) : "",
                    contentTag.getUserName());

            fileRows.add(new ContentTagsRowDTO(
                    contentTag,
                    cellValues,
                    file.getId()));
        }

        return new BaseSearchResultsDTO(ContentTagsRowDTO.getTypeIdForClass(), Bundle.FileTag_name_text(), FILE_TAG_COLUMNS, fileRows, 0, allTags.size());
    }
    
    /**
     * Handles fetching and paging of data for allTags.
     */
    public static class TagFetcher extends DAOFetcher<TagsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public TagFetcher(TagsSearchParams params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getTagsDAO().getTags(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return true;
            
            // GVDTODO

//            TagsSearchParams params = this.getParameters();
//            String eventType = evt.getPropertyName();
//            
//            // handle artifact/result tag changes
//            if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())
//                        || eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
//                
//                // ignore non-artifact/result tag changes
//                if (params.getTagType() != TagsSearchParams.TagType.RESULT) {
//                    return false;
//                }
//                
//                if (evt instanceof AutopsyEvent) {
//                    if (evt instanceof BlackBoardArtifactTagAddedEvent) {
//                        // An artifact associated with the current case has been tagged.
//                        BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
//                        // ensure tag added event has a valid content id
//                        if (event.getAddedTag() == null || event.getAddedTag().getContent() == null || event.getAddedTag().getArtifact() == null) {
//                            return false;
//                        }
//                        return params.getTagName().getId() == event.getAddedTag().getId();
//                    } else if (evt instanceof BlackBoardArtifactTagDeletedEvent) {
//                        // A tag has been removed from an artifact associated with the current case.
//                        BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
//                        // ensure tag deleted event has a valid content id
//                        BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo deletedTagInfo = event.getDeletedTagInfo();
//                        if (deletedTagInfo == null) {
//                            return false;
//                        }
//                        return params.getTagName().getId() == deletedTagInfo.getTagID();
//                    }
//                }
//            }
//            
//            // handle file/content tag changes
//            if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())
//                    || eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
//                
//                // ignore non-file/content tag changes
//                if (params.getTagType() != TagsSearchParams.TagType.FILE) {
//                    return false;
//                }
//
//                if (evt instanceof AutopsyEvent) {
//                    if (evt instanceof ContentTagAddedEvent) {
//                        // Content associated with the current case has been tagged.
//                        ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
//                        // ensure tag added event has a valid content id
//                        if (event.getAddedTag() == null || event.getAddedTag().getContent() == null) {
//                            return false;
//                        }
//                        return params.getTagName().getId() == event.getAddedTag().getId();
//                    } else if (evt instanceof ContentTagDeletedEvent) {
//                        // A tag has been removed from content associated with the current case.
//                        ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
//                        // ensure tag deleted event has a valid content id
//                        ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
//                        if (deletedTagInfo == null) {
//                            return false;
//                        }                        
//                        return params.getTagName().getId() == deletedTagInfo.getTagID();
//                    }
//                }
//            }
//            return false;
        }
    }
}
