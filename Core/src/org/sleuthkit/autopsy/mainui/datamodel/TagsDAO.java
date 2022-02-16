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
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.Tags;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION_UNITS;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_SIZE;
import org.sleuthkit.autopsy.mainui.datamodel.TagsSearchParams.TagType;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TagsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.autopsy.tags.TagUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
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

    private static final Logger logger = Logger.getLogger(TagsDAO.class.getName());

    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS

    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_originalName()),
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

    private final Cache<SearchParams<TagsSearchParams>, SearchResultsDTO> searchParamsCache
            = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<TagsEvent> treeCounts = new TreeCounts<>();

    public SearchResultsDTO getTags(TagsSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        } else if (key.getTagType() == null) {
            throw new IllegalArgumentException("Must have non-null tag type");
        }

        SearchParams<TagsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
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
        TagName tagNameId = cacheKey.getParamData().getTagName();

        TagsManager tm = Case.getCurrentCase().getServices().getTagsManager();
        // get all tag results
        List<BlackboardArtifactTag> allTags = new ArrayList<>();
        List<BlackboardArtifactTag> artifactTags = (dataSourceId != null && dataSourceId > 0)
                ? tm.getBlackboardArtifactTagsByTagName(tagNameId, dataSourceId)
                : tm.getBlackboardArtifactTagsByTagName(tagNameId);
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
                    null,
                    contentPath,
                    blackboardTag.getArtifact().getDisplayName(),
                    blackboardTag.getComment(),
                    blackboardTag.getUserName());

            fileRows.add(new BlackboardArtifactTagsRowDTO(
                    blackboardTag,
                    cellValues,
                    blackboardTag.getId()));
        }

        return new BaseSearchResultsDTO(BlackboardArtifactTagsRowDTO.getTypeIdForClass(), Bundle.ResultTag_name_text(), RESULT_TAG_COLUMNS, fileRows, BlackboardArtifactTag.class.getName(), 0, allTags.size());
    }

    private SearchResultsDTO fetchFileTags(SearchParams<TagsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        TagName tagNameId = cacheKey.getParamData().getTagName();

        TagsManager tm = Case.getCurrentCase().getServices().getTagsManager();

        // get all tag results
        List<ContentTag> allTags = new ArrayList<>();
        List<ContentTag> contentTags = (dataSourceId != null && dataSourceId > 0)
                ? tm.getContentTagsByTagName(tagNameId, dataSourceId)
                : tm.getContentTagsByTagName(tagNameId);
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
                    null,
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

        return new BaseSearchResultsDTO(ContentTagsRowDTO.getTypeIdForClass(), Bundle.FileTag_name_text(), FILE_TAG_COLUMNS, fileRows, ContentTag.class.getName(), 0, allTags.size());
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
    private boolean isTagsInvalidatingEvent(TagsSearchParams tagParams, DAOEvent daoEvt) {
        if (!(daoEvt instanceof TagsEvent)) {
            return false;
        }

        TagsEvent tagEvt = (TagsEvent) daoEvt;
        return (Objects.equals(tagParams.getTagName(), tagEvt.getTagName())
                && tagParams.getTagType().equals(tagEvt.getTagType())
                && (tagParams.getDataSourceId() == null
                || tagEvt.getDataSourceId() == null
                || tagParams.getDataSourceId() == tagEvt.getDataSourceId()));
    }

    private TreeItemDTO<TagsSearchParams> getTreeItem(TagsEvent evt, TreeResultsDTO.TreeDisplayCount count) {
        return new TreeItemDTO<>(
                TagsSearchParams.getTypeId(),
                new TagsSearchParams(evt.getTagName(), evt.getTagType(), evt.getDataSourceId()),
                evt.getTagName().getId(),
                evt.getTagName().getDisplayName(),
                count);
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(this.treeCounts, (evt, count) -> getTreeItem(evt, count));
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(this.treeCounts, (evt, count) -> getTreeItem(evt, count));
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        TagsEvent data = getTagData(evt);
        if (data == null) {
            return Collections.emptySet();
        }

        SubDAOUtils.invalidateKeys(this.searchParamsCache, (searchParams) -> {
            return (Objects.equals(searchParams.getTagType(), data.getTagType())
                    && Objects.equals(searchParams.getTagName(), data.getTagName())
                    && (searchParams.getDataSourceId() == null || Objects.equals(searchParams.getDataSourceId(), data.getDataSourceId())));
        });

        Collection<TagsEvent> daoEvents = Collections.singletonList(data);

        Collection<TreeEvent> treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                .map(arEvt -> new TreeEvent(getTreeItem(arEvt, TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false))
                .collect(Collectors.toList());

        return Stream.of(daoEvents, treeEvents)
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toSet());
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
    private TagsEvent getTagData(PropertyChangeEvent evt) {
        if (evt instanceof BlackBoardArtifactTagAddedEvent) {
            BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
            // ensure tag added event has a valid content id
            if (event.getAddedTag() != null
                    && event.getAddedTag().getContent() != null
                    && event.getAddedTag().getArtifact() != null) {
                return new TagsEvent(TagType.RESULT, event.getAddedTag().getName(), event.getAddedTag().getArtifact().getDataSourceObjectID());
            }

        } else if (evt instanceof BlackBoardArtifactTagDeletedEvent) {
            BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
            BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo deletedTagInfo = event.getDeletedTagInfo();
            if (deletedTagInfo != null) {
                return new TagsEvent(TagType.RESULT, deletedTagInfo.getName(), null);
            }
        } else if (evt instanceof ContentTagAddedEvent) {
            ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
            // ensure tag added event has a valid content id
            if (event.getAddedTag() != null && event.getAddedTag().getContent() != null) {
                Content content = event.getAddedTag().getContent();
                Long dsId = content instanceof AbstractFile ? ((AbstractFile) content).getDataSourceObjectId() : null;
                return new TagsEvent(TagType.FILE, event.getAddedTag().getName(), dsId);
            }
        } else if (evt instanceof ContentTagDeletedEvent) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            // ensure tag deleted event has a valid content id
            ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
            if (deletedTagInfo != null) {
                return new TagsEvent(TagType.FILE, deletedTagInfo.getName(), null);
            }
        }
        return null;
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException, TskCoreException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public TreeResultsDTO<? extends TagNameSearchParams> getNameCounts(Long dataSourceId) throws ExecutionException {
        Set<TagName> indeterminateTagNameIds = this.treeCounts.getEnqueued().stream()
                .filter(evt -> dataSourceId == null || evt.getDataSourceId() == dataSourceId)
                .map(evt -> evt.getTagName())
                .collect(Collectors.toSet());

        Map<TagName, TreeDisplayCount> tagNameCount = new HashMap<>();
        try {
            TagsManager tm = Case.getCurrentCaseThrows().getServices().getTagsManager();

            List<TagName> tagNamesInUse;
            if (UserPreferences.showOnlyCurrentUserTags()) {
                String userName = System.getProperty(USER_NAME_PROPERTY);
                tagNamesInUse = (dataSourceId != null)
                        ? tm.getTagNamesInUseForUser(dataSourceId, userName)
                        : tm.getTagNamesInUseForUser(userName);
            } else {
                tagNamesInUse = (dataSourceId != null)
                        ? Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse(dataSourceId)
                        : Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
            }

            for (TagName tagName : tagNamesInUse) {
                if (indeterminateTagNameIds.contains(tagName)) {
                    tagNameCount.put(tagName, TreeDisplayCount.INDETERMINATE);
                    continue;
                }

                long tagsCount;
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    if (dataSourceId != null) {
                        tagsCount = tm.getContentTagsCountByTagNameForUser(tagName, dataSourceId, userName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagNameForUser(tagName, dataSourceId, userName);
                    } else {
                        tagsCount = tm.getContentTagsCountByTagNameForUser(tagName, userName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagNameForUser(tagName, userName);
                    }
                } else {
                    if (dataSourceId != null) {
                        tagsCount = tm.getContentTagsCountByTagName(tagName, dataSourceId);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagName(tagName, dataSourceId);
                    } else {
                        tagsCount = tm.getContentTagsCountByTagName(tagName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagName(tagName);
                    }
                }

                tagNameCount.put(tagName, TreeDisplayCount.getDeterminate(tagsCount));
            }

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }

        List<TreeResultsDTO.TreeItemDTO<TagNameSearchParams>> tagNameParams = tagNameCount.entrySet().stream()
                .map(e -> createTagNameTreeItem(e.getKey(), dataSourceId, e.getValue()))
                .collect(Collectors.toList());

        // return results
        return new TreeResultsDTO<>(tagNameParams);
    }

    public TreeItemDTO<TagNameSearchParams> createTagNameTreeItem(TagName tagName, Long dataSourceId, TreeResultsDTO.TreeDisplayCount treeDisplayCount) {
        return new TreeItemDTO<>(
                TagNameSearchParams.getTypeId(),
                new TagNameSearchParams(tagName, dataSourceId),
                tagName.getId(),
                tagName.getDisplayName(),
                treeDisplayCount
        );
    }

    private long getContentTagCount(Long dataSourceId, TagName tagName) throws NoCurrentCaseException, TskCoreException {

        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            return (dataSourceId != null)
                    ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagNameForUser(tagName, dataSourceId, userName)
                    : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagNameForUser(tagName, userName);
        } else {
            return (dataSourceId != null)
                    ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagName(tagName, dataSourceId)
                    : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagName(tagName);
        }
    }

    private long getArtifactTagCount(Long dataSourceId, TagName tagName) throws NoCurrentCaseException, TskCoreException {
        if (UserPreferences.showOnlyCurrentUserTags()) {
            String userName = System.getProperty(USER_NAME_PROPERTY);
            return (dataSourceId != null)
                    ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagNameForUser(tagName, dataSourceId, userName)
                    : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagNameForUser(tagName, userName);
        } else {
            return (dataSourceId != null)
                    ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName, dataSourceId)
                    : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName);
        }
    }

    public TreeResultsDTO<? extends TagsSearchParams> getTypeCounts(TagNameSearchParams searchParams) throws ExecutionException {
        Long dataSourceId = searchParams.getDataSourceId();
        TagName tagName = searchParams.getTagName();

        Set<TagType> indeterminateTagTypes = this.treeCounts.getEnqueued().stream()
                .filter(evt -> (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId)) && Objects.equals(tagName, evt.getTagName()))
                .map(evt -> evt.getTagType())
                .collect(Collectors.toSet());

        try {
            return new TreeResultsDTO<>(Arrays.asList(
                    createTagTypeTreeItem(
                            tagName,
                            TagType.FILE,
                            dataSourceId,
                            indeterminateTagTypes.contains(TagType.FILE)
                            ? TreeDisplayCount.INDETERMINATE
                            : TreeDisplayCount.getDeterminate(getContentTagCount(dataSourceId, tagName))),
                    createTagTypeTreeItem(
                            tagName,
                            TagType.RESULT,
                            dataSourceId,
                            indeterminateTagTypes.contains(TagType.RESULT)
                            ? TreeDisplayCount.INDETERMINATE
                            : TreeDisplayCount.getDeterminate(getArtifactTagCount(dataSourceId, tagName)))
            ));
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching tag type counts.", ex);
        }
    }

    public TreeItemDTO<TagsSearchParams> createTagTypeTreeItem(TagName tagName, TagType tagType, Long dataSourceId, TreeResultsDTO.TreeDisplayCount treeDisplayCount) {
        return new TreeItemDTO<>(
                TagsSearchParams.getTypeId(),
                new TagsSearchParams(tagName, tagType, dataSourceId),
                tagName.getId(),
                tagType.getDisplayName(),
                treeDisplayCount
        );
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
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getTags(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isTagsInvalidatingEvent(this.getParameters(), evt);
        }
    }
}
