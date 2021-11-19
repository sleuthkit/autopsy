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
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.mainui.datamodel.TagsSearchParams.TagType;
import org.sleuthkit.autopsy.mainui.datamodel.events.TagsEvent;
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
public class TagsDAO extends AbstractDAO {

    private static final int CACHE_SIZE = 5; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<TagsSearchParams>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

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
     * Returns true if the DAO event could have an impact on the given search
     * params.
     *
     * @param tagParams The tag params.
     * @param daoEvt    The DAO event.
     *
     * @return True if the event could affect the results of the search params.
     */
    public boolean isTagsInvalidatingEvent(TagsSearchParams tagParams, DAOEvent daoEvt) {
        if (!(daoEvt instanceof TagsEvent)) {
            return false;
        }

        TagsEvent tagEvt = (TagsEvent) daoEvt;
        return (tagParams.getTagName().getId() == tagEvt.getTagNameId()
                && tagParams.getTagType().equals(tagEvt.getTagType())
                && (tagParams.getDataSourceId() == null
                || tagEvt.getDataSourceId() == null
                || tagParams.getDataSourceId() == tagEvt.getDataSourceId()));
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
    }

    @Override
    List<DAOEvent> handleAutopsyEvent(Collection<PropertyChangeEvent> evts) {
        Map<Pair<TagType, Long>, Set<Optional<Long>>> mapping = new HashMap<>();
        for (PropertyChangeEvent evt : evts) {
            // tag type, tag name id, data source id (or null if unknown)
            Triple<TagType, Long, Long> data = getTagData(evt);
            if (data != null) {
                mapping.computeIfAbsent(Pair.of(data.getLeft(), data.getMiddle()), k -> new HashSet<>())
                        .add(Optional.ofNullable(data.getRight()));
            }
        }

        // don't continue if no mapping entries
        if (mapping.isEmpty()) {
            return Collections.emptyList();
        }

        ConcurrentMap<SearchParams<TagsSearchParams>, SearchResultsDTO> concurrentMap = this.searchParamsCache.asMap();
        concurrentMap.forEach((k, v) -> {
            TagsSearchParams paramData = k.getParamData();
            Set<Optional<Long>> affectedDataSources = mapping.get(Pair.of(paramData.getTagType(), paramData.getTagName().getId()));
            // we only clear key if the tag name / type line up and either the parameters data source wasn't specified,
            // there is a wild card data source for the event, or the data source is contained in the list of data sources
            // affected by the event
            if (affectedDataSources != null
                    && (paramData.getDataSourceId() == null
                    || affectedDataSources.contains(Optional.empty())
                    || affectedDataSources.contains(Optional.of(paramData.getDataSourceId())))) {
                concurrentMap.remove(k);
            }
        });

        return mapping.entrySet().stream()
                .flatMap(entry -> {
                    TagType tagType = entry.getKey().getLeft();
                    Long tagNameId = entry.getKey().getRight();

                    return entry.getValue().stream()
                            .map((dsIdOpt) -> new TagsEvent(tagType, tagNameId, dsIdOpt.orElse(null)));
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns tag information from an event or null if no tag information
     * found.
     *
     * @param evt The autopsy event.
     *
     * @return tag type, tag name id, data source id (or null if none determined
     *         from event).
     */
    private Triple<TagType, Long, Long> getTagData(PropertyChangeEvent evt) {
        if (evt instanceof BlackBoardArtifactTagAddedEvent) {
            BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
            // ensure tag added event has a valid content id
            if (event.getAddedTag() != null
                    && event.getAddedTag().getContent() != null
                    && event.getAddedTag().getArtifact() != null) {
                return Triple.of(TagType.RESULT, event.getAddedTag().getName().getId(), event.getAddedTag().getArtifact().getDataSourceObjectID());
            }

        } else if (evt instanceof BlackBoardArtifactTagDeletedEvent) {
            BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
            BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo deletedTagInfo = event.getDeletedTagInfo();
            if (deletedTagInfo != null) {
                return Triple.of(TagType.RESULT, deletedTagInfo.getName().getId(), null);
            }
        } else if (evt instanceof ContentTagAddedEvent) {
            ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
            // ensure tag added event has a valid content id
            if (event.getAddedTag() != null && event.getAddedTag().getContent() != null) {
                Content content = event.getAddedTag().getContent();
                Long dsId = content instanceof AbstractFile ? ((AbstractFile) content).getDataSourceObjectId() : null;
                return Triple.of(TagType.FILE, event.getAddedTag().getName().getId(), dsId);
            }
        } else if (evt instanceof ContentTagDeletedEvent) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            // ensure tag deleted event has a valid content id
            ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
            if (deletedTagInfo != null) {
                return Triple.of(TagType.FILE, deletedTagInfo.getName().getId(), null);
            }
        }
        return null;
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

        protected TagsDAO getDAO() {
            return MainDAO.getInstance().getTagsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return getDAO().getTags(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isTagsInvalidatingEvent(this.getParameters(), evt);
        }
    }
}
