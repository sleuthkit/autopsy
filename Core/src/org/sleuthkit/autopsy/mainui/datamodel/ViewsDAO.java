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
import com.google.common.collect.ImmutableSet;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import static org.sleuthkit.autopsy.core.UserPreferences.hideSlackFilesInViewsTree;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.DeletedContentEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileTypeExtensionsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileTypeMimeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileTypeSizeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_TYPE_ENUM;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
public class ViewsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(ViewsDAO.class.getName());

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private static final Map<String, Set<FileExtSearchFilter>> EXTENSION_FILTER_MAP
            = Stream.of((FileExtSearchFilter[]) FileExtRootFilter.values(), FileExtDocumentFilter.values(), FileExtExecutableFilter.values())
                    .flatMap(arr -> Stream.of(arr))
                    .flatMap(filter -> filter.getFilter().stream().map(ext -> Pair.of(ext, filter)))
                    .collect(Collectors.groupingBy(
                            pair -> pair.getKey(),
                            Collectors.mapping(pair -> pair.getValue(),
                                    Collectors.toSet())));

    private static final String FILE_VIEW_EXT_TYPE_ID = "FILE_VIEW_BY_EXT";

    private static ViewsDAO instance = null;

    synchronized static ViewsDAO getInstance() {
        if (instance == null) {
            instance = new ViewsDAO();
        }

        return instance;
    }

    private final Cache<SearchParams<Object>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<DAOEvent> treeCounts = new TreeCounts<>();

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getFilesByExtension(FileTypeExtensionsSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<Object> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchExtensionSearchResultsDTOs(key.getFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesByMime(FileTypeMimeSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getMimeType() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<Object> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchMimeSearchResultsDTOs(key.getMimeType(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesBySize(FileTypeSizeSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getSizeFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<Object> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchSizeSearchResultsDTOs(key.getSizeFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    /**
     * Returns search results for the given deleted content search params.
     *
     * @param params    The deleted content search params.
     * @param startItem The starting item to start returning at.
     * @param maxCount  The maximum number of items to return.
     *
     * @return The search results.
     *
     * @throws ExecutionException
     * @throws IllegalArgumentException
     */
    public SearchResultsDTO getDeletedContent(DeletedContentSearchParams params, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (params.getFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (params.getDataSourceId() != null && params.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<Object> searchParams = new SearchParams<>(params, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchDeletedSearchResultsDTOs(params.getFilter(), params.getDataSourceId(), startItem, maxCount));
    }

    private boolean isFilesByExtInvalidating(FileTypeExtensionsSearchParams key, DAOEvent eventData) {
        if (!(eventData instanceof FileTypeExtensionsEvent)) {
            return false;
        }

        FileTypeExtensionsEvent extEvt = (FileTypeExtensionsEvent) eventData;
        return (extEvt.getExtensionFilter() == null || key.getFilter().equals(extEvt.getExtensionFilter()))
                && (key.getDataSourceId() == null || extEvt.getDataSourceId() == null || key.getDataSourceId().equals(extEvt.getDataSourceId()));
    }

    private boolean isFilesByMimeInvalidating(FileTypeMimeSearchParams key, DAOEvent eventData) {
        if (!(eventData instanceof FileTypeMimeEvent)) {
            return false;
        }

        FileTypeMimeEvent mimeEvt = (FileTypeMimeEvent) eventData;
        return mimeEvt.getMimeType().startsWith(key.getMimeType())
                && (key.getDataSourceId() == null || Objects.equals(key.getDataSourceId(), mimeEvt.getDataSourceId()));
    }

    private boolean isFilesBySizeInvalidating(FileTypeSizeSearchParams key, DAOEvent eventData) {
        if (!(eventData instanceof FileTypeSizeEvent)) {
            return false;
        }

        FileTypeSizeEvent sizeEvt = (FileTypeSizeEvent) eventData;
        return (sizeEvt.getSizeFilter() == null || sizeEvt.getSizeFilter().equals(key.getSizeFilter()))
                && (key.getDataSourceId() == null || sizeEvt.getDataSourceId() == null || Objects.equals(key.getDataSourceId(), sizeEvt.getDataSourceId()));
    }

    private boolean isDeletedContentInvalidating(DeletedContentSearchParams params, DAOEvent eventData) {
        if (!(eventData instanceof DeletedContentEvent)) {
            return false;
        }

        DeletedContentEvent deletedContentEvt = (DeletedContentEvent) eventData;
        return (deletedContentEvt.getFilter() == null || deletedContentEvt.getFilter().equals(params.getFilter()))
                && (params.getDataSourceId() == null || deletedContentEvt.getDataSourceId() == null
                || Objects.equals(params.getDataSourceId(), deletedContentEvt.getDataSourceId()));
    }

    /**
     * Returns a sql 'and' clause to filter by data source id if one is present.
     *
     * @param dataSourceId The data source id or null.
     *
     * @return Returns clause if data source id is present or blank string if
     *         not.
     */
    private static String getDataSourceAndClause(Long dataSourceId) {
        return (dataSourceId != null && dataSourceId > 0
                ? " AND data_source_obj_id = " + dataSourceId
                : " ");
    }

    /**
     * Returns clause that will determine if file extension is within the
     * filter's set of extensions.
     *
     * @param filter The filter.
     *
     * @return The sql clause that will need to be proceeded with 'where' or
     *         'and'.
     */
    private static String getFileExtensionClause(FileExtSearchFilter filter) {
        return "extension IN (" + filter.getFilter().stream()
                .map(String::toLowerCase)
                .map(s -> "'" + StringUtils.substringAfter(s, ".") + "'")
                .collect(Collectors.joining(", ")) + ")";
    }

    /**
     * @return If user preference of hide known files, returns sql and clause to
     *         hide known files or returns empty string otherwise.
     */
    private String getHideKnownAndClause() {
        return (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known <> " + FileKnown.KNOWN.getFileKnownValue() + ") ") : "");
    }

    /**
     * @return A clause (no 'and' or 'where' prefixed) indicating the dir_type
     *         is regular.
     */
    private String getRegDirTypeClause() {
        return "(dir_type = " + TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")";
    }

    /**
     * Returns a clause that will filter out files that aren't to be counted in
     * the file extensions view.
     *
     * @return The filter that will need to be proceeded with 'where' or 'and'.
     */
    private String getBaseFileExtensionFilter() {
        return getRegDirTypeClause() + getHideKnownAndClause();
    }

    /**
     * Returns a statement to be proceeded with 'where' or 'and' that will
     * filter results to the provided filter and data source id (if non null).
     *
     * @param filter       The file extension filter.
     * @param dataSourceId The data source id or null if no data source
     *                     filtering is to occur.
     *
     * @return The sql statement to be proceeded with 'and' or 'where'.
     */
    private String getFileExtensionWhereStatement(FileExtSearchFilter filter, Long dataSourceId) {
        String whereClause = getBaseFileExtensionFilter()
                + getDataSourceAndClause(dataSourceId)
                + " AND (" + getFileExtensionClause(filter) + ")";
        return whereClause;
    }

    /**
     * @return The TSK_DB_FILES_TYPE_ENUm values allowed for mime type view
     *         items.
     */
    private Set<TSK_DB_FILES_TYPE_ENUM> getMimeDbFilesTypes() {
        return Stream.of(
                TSK_DB_FILES_TYPE_ENUM.FS,
                TSK_DB_FILES_TYPE_ENUM.CARVED,
                TSK_DB_FILES_TYPE_ENUM.DERIVED,
                TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE,
                TSK_DB_FILES_TYPE_ENUM.LOCAL,
                (hideSlackFilesInViewsTree() ? null : (TSK_DB_FILES_TYPE_ENUM.SLACK)))
                .filter(ordinal -> ordinal != null)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a statement to be proceeded with 'where' or 'and' that will
     * filter results to the provided filter and data source id (if non null).
     *
     * @param filter       The deleted content filter.
     * @param dataSourceId The data source id or null if no data source
     *                     filtering is to occur.
     *
     * @return The sql statement to be proceeded with 'and' or 'where'.
     */
    private String getDeletedContentWhereStatement(DeletedContentFilter filter, Long dataSourceId) {
        String whereClause = getDeletedContentClause(filter) + getDataSourceAndClause(dataSourceId);
        return whereClause;
    }

    /**
     * Returns a statement to be proceeded with 'where' or 'and' that will
     * filter out results that should not be viewed in mime types view.
     *
     * @return A statement to be proceeded with 'and' or 'where'.
     */
    private String getBaseFileMimeFilter() {
        return getRegDirTypeClause()
                + getHideKnownAndClause()
                + " AND (type IN ("
                + getMimeDbFilesTypes().stream().map(v -> Integer.toString(v.ordinal())).collect(Collectors.joining(", "))
                + "))";
    }

    /**
     * Returns a sql statement to be proceeded with 'where' or 'and' that will
     * filter to the specified mime type.
     *
     * @param mimeType     The mime type.
     * @param dataSourceId The data source object id or null if no data source
     *                     filtering is to occur.
     *
     * @return A statement to be proceeded with 'and' or 'where'.
     */
    private String getFileMimeWhereStatement(String mimeType, Long dataSourceId) {
        String whereClause = getBaseFileMimeFilter()
                + getDataSourceAndClause(dataSourceId)
                + " AND mime_type = '" + mimeType + "'";
        return whereClause;
    }

    /**
     * Returns clause to be proceeded with 'where' or 'and' to filter files to
     * those within the bounds of the filter.
     *
     * @param filter The size filter.
     *
     * @return The clause to be proceeded with 'where' or 'and'.
     */
    private static String getFileSizeClause(FileSizeFilter filter) {
        return filter.getMaxBound() == null
                ? "(size >= " + filter.getMinBound() + ")"
                : "(size >= " + filter.getMinBound() + " AND size < " + filter.getMaxBound() + ")";
    }

    /**
     * Returns clause to be proceeded with 'where' or 'and' to filter deleted
     * content.
     *
     * @param filter The deleted content filter.
     *
     * @return The clause to be proceeded with 'where' or 'and'.
     */
    private static String getDeletedContentClause(DeletedContentFilter filter) throws IllegalArgumentException {
        switch (filter) {
            case FS_DELETED_FILTER:
                return "dir_flags = " + TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue() //NON-NLS
                        + " AND meta_flags != " + TSK_FS_META_FLAG_ENUM.ORPHAN.getValue() //NON-NLS
                        + " AND type = " + TSK_DB_FILES_TYPE_ENUM.FS.getFileType(); //NON-NLS

            case ALL_DELETED_FILTER:
                return " ( "
                        + "( "
                        + "(dir_flags = " + TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue() //NON-NLS
                        + " OR " //NON-NLS
                        + "meta_flags = " + TSK_FS_META_FLAG_ENUM.ORPHAN.getValue() //NON-NLS
                        + ")"
                        + " AND type = " + TSK_DB_FILES_TYPE_ENUM.FS.getFileType() //NON-NLS
                        + " )"
                        + " OR type = " + TSK_DB_FILES_TYPE_ENUM.CARVED.getFileType() //NON-NLS
                        + " OR (dir_flags = " + TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                        + " AND type = " + TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.getFileType() + " )"
                        + " )";

            default:
                throw new IllegalArgumentException(MessageFormat.format("Unsupported filter type to get deleted content: {0}", filter)); //NON-NLS
        }
    }

    /**
     * The filter for all files to remove those that should never be seen in the
     * file size views.
     *
     * @return The clause to be proceeded with 'where' or 'and'.
     */
    private String getBaseFileSizeFilter() {
        // Ignore unallocated block files.
        return "(type != " + TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType() + ")" + getHideKnownAndClause();
    }

    /**
     * Creates a clause to be proceeded with 'where' or 'and' that will show
     * files specified by the filter and the specified data source.
     *
     * @param filter       The file size filter.
     * @param dataSourceId The id of the data source or null if no data source
     *                     filtering.
     *
     * @return The clause to be proceeded with 'where' or 'and'.
     */
    private String getFileSizesWhereStatement(FileSizeFilter filter, Long dataSourceId) {
        String query = getBaseFileSizeFilter()
                + " AND " + getFileSizeClause(filter)
                + getDataSourceAndClause(dataSourceId);

        return query;
    }

    /**
     * Returns counts for a collection of file extension search filters.
     *
     * @param filters      The filters. Each one will have an entry in the
     *                     returned results.
     * @param dataSourceId The data source object id or null if no data source
     *                     filtering should occur.
     *
     * @return The results.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileTypeExtensionsSearchParams> getFileExtCounts(Collection<FileExtSearchFilter> filters, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        Set<FileExtSearchFilter> indeterminateFilters = new HashSet<>();
        for (DAOEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof FileTypeExtensionsEvent) {
                FileTypeExtensionsEvent extEvt = (FileTypeExtensionsEvent) evt;
                if (dataSourceId == null || extEvt.getDataSourceId() == null || Objects.equals(extEvt.getDataSourceId(), dataSourceId)) {
                    if (extEvt.getExtensionFilter() == null) {
                        // add all filters if extension filter is null and keep going
                        indeterminateFilters.addAll(filters);
                        break;
                    } else if (filters.contains(extEvt.getExtensionFilter())) {
                        indeterminateFilters.add(extEvt.getExtensionFilter());
                    }
                }
            }
        }

        Map<FileExtSearchFilter, String> whereClauses = filters.stream()
                .collect(Collectors.toMap(
                        filter -> filter,
                        filter -> getFileExtensionClause(filter)));

        Map<FileExtSearchFilter, Long> countsByFilter = getFilesCounts(whereClauses, getBaseFileExtensionFilter(), dataSourceId, true);

        List<TreeItemDTO<FileTypeExtensionsSearchParams>> treeList = countsByFilter.entrySet().stream()
                .map(entry -> {
                    TreeDisplayCount displayCount = indeterminateFilters.contains(entry.getKey())
                            ? TreeDisplayCount.INDETERMINATE
                            : TreeDisplayCount.getDeterminate(entry.getValue());

                    return createExtensionTreeItem(entry.getKey(), dataSourceId, displayCount);
                })
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new TreeResultsDTO<>(treeList);
    }

    /**
     * Creates an extension tree item.
     *
     * @param filter       The extension filter.
     * @param dataSourceId The data source id or null.
     * @param displayCount The count to display.
     *
     * @return The extension tree item.
     */
    private TreeItemDTO<FileTypeExtensionsSearchParams> createExtensionTreeItem(FileExtSearchFilter filter, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                FileTypeExtensionsSearchParams.getTypeId(),
                new FileTypeExtensionsSearchParams(filter, dataSourceId),
                filter,
                filter == null ? "" : filter.getDisplayName(),
                displayCount);
    }

    /**
     * Returns counts for file size categories.
     *
     * @param dataSourceId The data source object id or null if no data source
     *                     filtering should occur.
     *
     * @return The results.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileTypeSizeSearchParams> getFileSizeCounts(Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        Set<FileSizeFilter> indeterminateFilters = new HashSet<>();
        for (DAOEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof FileTypeSizeEvent) {
                FileTypeSizeEvent sizeEvt = (FileTypeSizeEvent) evt;
                if (dataSourceId == null || sizeEvt.getDataSourceId() == null || Objects.equals(sizeEvt.getDataSourceId(), dataSourceId)) {
                    if (sizeEvt.getSizeFilter() == null) {
                        // if null size filter, indicates full refresh and all file sizes need refresh.
                        indeterminateFilters.addAll(Arrays.asList(FileSizeFilter.values()));
                        break;
                    } else {
                        indeterminateFilters.add(sizeEvt.getSizeFilter());
                    }
                }
            }
        }

        Map<FileSizeFilter, String> whereClauses = Stream.of(FileSizeFilter.values())
                .collect(Collectors.toMap(
                        filter -> filter,
                        filter -> getFileSizeClause(filter)));

        Map<FileSizeFilter, Long> countsByFilter = getFilesCounts(whereClauses, getBaseFileSizeFilter(), dataSourceId, true);

        List<TreeItemDTO<FileTypeSizeSearchParams>> treeList = countsByFilter.entrySet().stream()
                .map(entry -> {
                    TreeDisplayCount displayCount = indeterminateFilters.contains(entry.getKey())
                            ? TreeDisplayCount.INDETERMINATE
                            : TreeDisplayCount.getDeterminate(entry.getValue());

                    return createSizeTreeItem(entry.getKey(), dataSourceId, displayCount);
                })
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new TreeResultsDTO<>(treeList);
    }

    /**
     * Returns counts for deleted content categories.
     *
     * @param dataSourceId The data source object id or null if no data source
     *                     filtering should occur.
     *
     * @return The results.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<DeletedContentSearchParams> getDeletedContentCounts(Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        Set<DeletedContentFilter> indeterminateFilters = new HashSet<>();
        for (DAOEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof DeletedContentEvent) {
                DeletedContentEvent deletedEvt = (DeletedContentEvent) evt;
                if (dataSourceId == null || deletedEvt.getDataSourceId() == null || Objects.equals(deletedEvt.getDataSourceId(), dataSourceId)) {
                    if (deletedEvt.getFilter() == null) {
                        // if null filter, indicates full refresh and all file sizes need refresh.
                        indeterminateFilters.addAll(Arrays.asList(DeletedContentFilter.values()));
                        break;
                    } else {
                        indeterminateFilters.add(deletedEvt.getFilter());
                    }
                }
            }
        }

        String queryStr = Stream.of(DeletedContentFilter.values())
                .map((filter) -> {
                    String clause = getDeletedContentClause(filter);
                    return MessageFormat.format("    (SELECT COUNT(*) FROM tsk_files WHERE {0}) AS {1}", clause, filter.name());
                })
                .collect(Collectors.joining(", \n"));

        try {
            SleuthkitCase skCase = getCase();

            List<TreeItemDTO<DeletedContentSearchParams>> treeList = new ArrayList<>();
            skCase.getCaseDbAccessManager().select(queryStr, (resultSet) -> {
                try {
                    if (resultSet.next()) {
                        for (DeletedContentFilter filter : DeletedContentFilter.values()) {
                            long count = resultSet.getLong(filter.name());
                            TreeDisplayCount displayCount = indeterminateFilters.contains(filter)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeDisplayCount.getDeterminate(count);

                            treeList.add(createDeletedContentTreeItem(filter, dataSourceId, displayCount));
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching file type counts.", ex);
                }
            });

            return new TreeResultsDTO<>(treeList);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching file counts with query:\n" + queryStr, ex);
        }
    }

    private static TreeItemDTO<DeletedContentSearchParams> createDeletedContentTreeItem(DeletedContentFilter filter, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                "DELETED_CONTENT",
                new DeletedContentSearchParams(filter, dataSourceId),
                filter,
                filter == null ? "" : filter.getDisplayName(),
                displayCount);
    }

    /**
     * Creates a size tree item.
     *
     * @param filter       The file size filter.
     * @param dataSourceId The data source id.
     * @param displayCount The display count.
     *
     * @return The tree item.
     */
    private TreeItemDTO<FileTypeSizeSearchParams> createSizeTreeItem(FileSizeFilter filter, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                FileTypeSizeSearchParams.getTypeId(),
                new FileTypeSizeSearchParams(filter, dataSourceId),
                filter,
                filter == null ? "" : filter.getDisplayName(),
                displayCount);
    }

    /**
     * Returns counts for file mime type categories.
     *
     * @param prefix       The prefix mime type (i.e. 'application', 'audio').
     *                     If null, prefix counts are gathered.
     * @param dataSourceId The data source object id or null if no data source
     *                     filtering should occur.
     *
     * @return The results.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileTypeMimeSearchParams> getFileMimeCounts(String prefix, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        String prefixWithSlash = StringUtils.isNotBlank(prefix) ? prefix.replaceAll("/", "") + "/" : null;
        String likeItem = StringUtils.isNotBlank(prefixWithSlash) ? prefixWithSlash.replaceAll("%", "") + "%" : null;

        Set<String> indeterminateMimeTypes = new HashSet<>();
        for (DAOEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof FileTypeMimeEvent) {
                FileTypeMimeEvent mimeEvt = (FileTypeMimeEvent) evt;
                if ((dataSourceId == null || Objects.equals(mimeEvt.getDataSourceId(), dataSourceId))
                        && (prefixWithSlash == null || mimeEvt.getMimeType().startsWith(prefixWithSlash))) {

                    String mimePortion = prefixWithSlash != null
                            ? mimeEvt.getMimeType().substring(prefixWithSlash.length())
                            : mimeEvt.getMimeType().substring(0, mimeEvt.getMimeType().indexOf("/"));

                    indeterminateMimeTypes.add(mimePortion);
                }
            }
        }

        String baseFilter = "WHERE " + getBaseFileMimeFilter()
                + getDataSourceAndClause(dataSourceId)
                + (StringUtils.isNotBlank(prefix) ? " AND mime_type LIKE ? " : " AND mime_type IS NOT NULL ");

        try {
            SleuthkitCase skCase = getCase();
            String mimeType;
            if (StringUtils.isNotBlank(prefix)) {
                mimeType = "mime_type";
            } else {
                switch (skCase.getDatabaseType()) {
                    case POSTGRESQL:
                        mimeType = "SPLIT_PART(mime_type, '/', 1)";
                        break;
                    case SQLITE:
                        mimeType = "SUBSTR(mime_type, 0, instr(mime_type, '/'))";
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown database type: " + skCase.getDatabaseType());
                }
            }

            String query = mimeType + " AS mime_type, COUNT(*) AS count\n"
                    + "FROM tsk_files\n"
                    + baseFilter + "\n"
                    + "GROUP BY " + mimeType;

            Map<String, Long> typeCounts = new HashMap<>();

            try (CaseDbPreparedStatement casePreparedStatement = skCase.getCaseDbAccessManager().prepareSelect(query)) {

                if (likeItem != null) {
                    casePreparedStatement.setString(1, likeItem);
                }

                skCase.getCaseDbAccessManager().select(casePreparedStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            String mimeTypeId = resultSet.getString("mime_type");
                            if (mimeTypeId != null) {
                                long count = resultSet.getLong("count");
                                typeCounts.put(mimeTypeId, count);
                            }
                        }
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "An error occurred while fetching file mime type counts.", ex);
                    }
                });

                List<TreeItemDTO<FileTypeMimeSearchParams>> treeList = typeCounts.entrySet().stream()
                        .map(entry -> {
                            String name = prefixWithSlash != null && entry.getKey().startsWith(prefixWithSlash)
                                    ? entry.getKey().substring(prefixWithSlash.length())
                                    : entry.getKey();

                            TreeDisplayCount displayCount = indeterminateMimeTypes.contains(name)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeDisplayCount.getDeterminate(entry.getValue());

                            return createMimeTreeItem(entry.getKey(), name, dataSourceId, displayCount);
                        })
                        .sorted((a, b) -> stringCompare(a.getSearchParams().getMimeType(), b.getSearchParams().getMimeType()))
                        .collect(Collectors.toList());

                return new TreeResultsDTO<>(treeList);
            } catch (TskCoreException | SQLException ex) {
                throw new ExecutionException("An error occurred while fetching file counts with query:\n" + query, ex);
            }
        } catch (NoCurrentCaseException ex) {
            throw new ExecutionException("An error occurred while fetching file counts.", ex);
        }
    }

    /**
     * Creates a mime type tree item.
     *
     * @param fullMime     The full mime type.
     * @param mimeName     The mime type segment that will be displayed (suffix
     *                     or prefix).
     * @param dataSourceId The data source id.
     * @param displayCount The count to display.
     *
     * @return The created tree item.
     */
    private TreeItemDTO<FileTypeMimeSearchParams> createMimeTreeItem(String fullMime, String mimeName, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                FileTypeMimeSearchParams.getTypeId(),
                new FileTypeMimeSearchParams(fullMime, dataSourceId),
                mimeName,
                mimeName,
                displayCount);
    }

    /**
     * Provides case insensitive comparator integer for strings that may be
     * null.
     *
     * @param a String that may be null.
     * @param b String that may be null.
     *
     * @return The comparator value placing null first.
     */
    private int stringCompare(String a, String b) {
        if (a == null && b == null) {
            return 0;
        } else if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        } else {
            return a.compareToIgnoreCase(b);
        }
    }

    /**
     * Determines counts for files in multiple categories.
     *
     * @param whereClauses     A mapping of objects to their respective where
     *                         clauses.
     * @param baseFilter       A filter for files applied before performing
     *                         groupings and counts. It shouldn't have a leading
     *                         'AND' or 'WHERE'.
     * @param dataSourceId     The data source object id or null if no data
     *                         source filtering.
     * @param includeZeroCount Whether or not to return an item if there are 0
     *                         matches.
     *
     * @return A mapping of the keys in the 'whereClauses' mapping to their
     *         respective counts.
     *
     * @throws ExecutionException
     */
    private <T> Map<T, Long> getFilesCounts(Map<T, String> whereClauses, String baseFilter, Long dataSourceId, boolean includeZeroCount) throws ExecutionException {
        // get artifact types and counts

        Map<Integer, T> types = new HashMap<>();
        String whenClauses = "";

        int idx = 0;
        for (Entry<T, String> e : whereClauses.entrySet()) {
            types.put(idx, e.getKey());
            whenClauses += "    WHEN " + e.getValue() + " THEN " + idx + " \n";
            idx++;
        }

        String switchStatement = "  CASE \n"
                + whenClauses
                + "    ELSE -1 \n"
                + "  END AS type_id \n";

        String dataSourceClause = dataSourceId != null && dataSourceId > 0 ? "data_source_obj_id = " + dataSourceId : null;

        String baseWhereClauses = Stream.of(dataSourceClause, baseFilter)
                .filter(s -> StringUtils.isNotBlank(s))
                .collect(Collectors.joining(" AND "));

        String query = "res.type_id, COUNT(*) AS count FROM \n"
                + "(SELECT \n"
                + switchStatement
                + "FROM tsk_files \n"
                + (StringUtils.isNotBlank(baseWhereClauses) ? ("WHERE " + baseWhereClauses) : "") + ") res \n"
                + "WHERE res.type_id >= 0 \n"
                + "GROUP BY res.type_id";

        Map<T, Long> typeCounts = new HashMap<>();
        try {
            SleuthkitCase skCase = getCase();

            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        int typeIdx = resultSet.getInt("type_id");
                        T type = types.remove(typeIdx);
                        if (type != null) {
                            long count = resultSet.getLong("count");
                            typeCounts.put(type, count);
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching file type counts.", ex);
                }
            });
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching file counts with query:\n" + query, ex);
        }

        if (includeZeroCount) {
            for (T remaining : types.values()) {
                typeCounts.put(remaining, 0L);
            }
        }

        return typeCounts;
    }

    private SearchResultsDTO fetchDeletedSearchResultsDTOs(DeletedContentFilter filter, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getDeletedContentWhereStatement(filter, dataSourceId);
        return fetchFileViewFiles(whereStatement, filter.getDisplayName(), startItem, maxResultCount);
    }

    private SearchResultsDTO fetchExtensionSearchResultsDTOs(FileExtSearchFilter filter, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileExtensionWhereStatement(filter, dataSourceId);
        return fetchFileViewFiles(whereStatement, filter.getDisplayName(), startItem, maxResultCount);
    }

    @NbBundle.Messages({"FileTypesByMimeType.name.text=By MIME Type"})
    private SearchResultsDTO fetchMimeSearchResultsDTOs(String mimeType, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileMimeWhereStatement(mimeType, dataSourceId);
        final String MIME_TYPE_DISPLAY_NAME = Bundle.FileTypesByMimeType_name_text();
        return fetchFileViewFiles(whereStatement, MIME_TYPE_DISPLAY_NAME, startItem, maxResultCount);
    }

    private SearchResultsDTO fetchSizeSearchResultsDTOs(FileSizeFilter filter, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileSizesWhereStatement(filter, dataSourceId);
        return fetchFileViewFiles(whereStatement, filter.getDisplayName(), startItem, maxResultCount);
    }

    private SearchResultsDTO fetchFileViewFiles(String originalWhereStatement, String displayName, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {

        // Add offset and/or paging, if specified
        String modifiedWhereStatement = originalWhereStatement
                + " ORDER BY obj_id ASC"
                + (maxResultCount != null && maxResultCount > 0 ? " LIMIT " + maxResultCount : "")
                + (startItem > 0 ? " OFFSET " + startItem : "");

        List<AbstractFile> files = getCase().findAllFilesWhere(modifiedWhereStatement);

        long totalResultsCount;
        // get total number of results
        if ((startItem == 0) // offset is zero AND
                && ((maxResultCount != null && files.size() < maxResultCount) // number of results is less than max
                || (maxResultCount == null))) { // OR max number of results was not specified
            totalResultsCount = files.size();
        } else {
            // do a query to get total number of results
            totalResultsCount = getCase().countFilesWhere(originalWhereStatement);
        }

        List<RowDTO> fileRows = new ArrayList<>();
        for (AbstractFile file : files) {

            List<Object> cellValues = FileSystemColumnUtils.getCellValuesForAbstractFile(file);

            fileRows.add(new FileRowDTO(
                    file,
                    file.getId(),
                    file.getName(),
                    file.getNameExtension(),
                    MediaTypeUtils.getExtensionMediaType(file.getNameExtension()),
                    file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.ALLOC),
                    file.getType(),
                    cellValues));
        }

        return new BaseSearchResultsDTO(FILE_VIEW_EXT_TYPE_ID, displayName, FileSystemColumnUtils.getColumnKeysForAbstractfile(), fileRows, AbstractFile.class.getName(), startItem, totalResultsCount);
    }

    private Pair<String, String> getMimePieces(String mimeType) {
        int idx = mimeType.indexOf("/");
        String mimePrefix = idx > 0 ? mimeType.substring(0, idx) : mimeType;
        String mimeSuffix = idx > 0 ? mimeType.substring(idx + 1) : null;
        return Pair.of(mimePrefix, mimeSuffix);
    }

    private TreeItemDTO<?> createTreeItem(DAOEvent daoEvent, TreeDisplayCount count) {
        if (daoEvent instanceof FileTypeExtensionsEvent) {
            FileTypeExtensionsEvent extEvt = (FileTypeExtensionsEvent) daoEvent;
            return createExtensionTreeItem(extEvt.getExtensionFilter(), extEvt.getDataSourceId(), count);
        } else if (daoEvent instanceof FileTypeMimeEvent) {
            FileTypeMimeEvent mimeEvt = (FileTypeMimeEvent) daoEvent;
            Pair<String, String> mimePieces = getMimePieces(mimeEvt.getMimeType());
            String mimeName = mimePieces.getRight() == null ? mimePieces.getLeft() : mimePieces.getRight();
            return createMimeTreeItem(mimeEvt.getMimeType(), mimeName, mimeEvt.getDataSourceId(), count);
        } else if (daoEvent instanceof FileTypeSizeEvent) {
            FileTypeSizeEvent sizeEvt = (FileTypeSizeEvent) daoEvent;
            return createSizeTreeItem(sizeEvt.getSizeFilter(), sizeEvt.getDataSourceId(), count);
        } else if (daoEvent instanceof DeletedContentEvent) {
            DeletedContentEvent sizeEvt = (DeletedContentEvent) daoEvent;
            return createDeletedContentTreeItem(sizeEvt.getFilter(), sizeEvt.getDataSourceId(), count);
        } else {
            return null;
        }
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        SubDAOUtils.invalidateKeys(this.searchParamsCache,
                (searchParams) -> searchParamsMatchEvent(null, null, null, null, null, true, searchParams));

        Set<? extends DAOEvent> treeEvts = SubDAOUtils.getIngestCompleteEvents(this.treeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count));

        Set<? extends DAOEvent> fileViewRefreshEvents = getFileViewRefreshEvents(null);

        List<? extends DAOEvent> fileViewRefreshTreeEvents = fileViewRefreshEvents.stream()
                .map(evt -> new TreeEvent(createTreeItem(evt, TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toList());

        return Stream.of(treeEvts, fileViewRefreshEvents, fileViewRefreshTreeEvents)
                .flatMap(c -> c.stream())
                .collect(Collectors.toSet());
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(this.treeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count));
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        Long dsId = null;
        boolean dataSourceAdded = false;
        Set<FileExtSearchFilter> evtExtFilters = null;
        Set<DeletedContentFilter> deletedContentFilters = null;
        String evtMimeType = null;
        FileSizeFilter evtFileSize = null;

        if (Case.Events.DATA_SOURCE_ADDED.toString().equals(evt.getPropertyName())) {
            dsId = evt.getNewValue() instanceof Long ? (Long) evt.getNewValue() : null;
            dataSourceAdded = true;
        } else {
            AbstractFile af = DAOEventUtils.getFileFromFileEvent(evt);
            if (af == null) {
                return Collections.emptySet();
            } else if (hideKnownFilesInViewsTree() && TskData.FileKnown.KNOWN.equals(af.getKnown())) {
                return Collections.emptySet();
            }

            dsId = af.getDataSourceObjectId();

            // create an extension mapping if extension present
            if (!StringUtils.isBlank(af.getNameExtension()) && TSK_FS_NAME_TYPE_ENUM.REG.equals(af.getDirType())) {
                evtExtFilters = EXTENSION_FILTER_MAP.getOrDefault("." + af.getNameExtension(), Collections.emptySet());
            }

            deletedContentFilters = getMatchingDeletedContentFilters(af);

            // create a mime type mapping if mime type present
            if (!StringUtils.isBlank(af.getMIMEType()) && TSK_FS_NAME_TYPE_ENUM.REG.equals(af.getDirType()) && getMimeDbFilesTypes().contains(af.getType())) {
                evtMimeType = af.getMIMEType();
            }

            // create a size mapping if size present in filters
            if (!TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.equals(af.getType())) {
                evtFileSize = Stream.of(FileSizeFilter.values())
                        .filter(filter -> af.getSize() >= filter.getMinBound() && (filter.getMaxBound() == null || af.getSize() < filter.getMaxBound()))
                        .findFirst()
                        .orElse(null);
            }

            if (evtExtFilters == null || evtExtFilters.isEmpty() && deletedContentFilters.isEmpty() && evtMimeType == null && evtFileSize == null) {
                return Collections.emptySet();
            }
        }

        return invalidateAndReturnEvents(evtExtFilters, evtMimeType, evtFileSize, deletedContentFilters, dsId, dataSourceAdded);
    }

    /**
     * Handles invalidating caches and returning events based on digest.
     *
     * @param evtExtFilters         The file extension filters or empty set.
     * @param evtMimeType           The mime type or null.
     * @param evtFileSize           The file size filter or null.
     * @param deletedContentFilters The set of affected deleted content filters.
     * @param dsId                  The data source id or null.
     * @param dataSourceAdded       Whether or not this is a data source added
     *                              event.
     *
     * @return The set of dao events to be fired.
     */
    private Set<DAOEvent> invalidateAndReturnEvents(Set<FileExtSearchFilter> evtExtFilters, String evtMimeType,
            FileSizeFilter evtFileSize, Set<DeletedContentFilter> deletedContentFilters, Long dsId, boolean dataSourceAdded) {

        SubDAOUtils.invalidateKeys(this.searchParamsCache,
                (searchParams) -> searchParamsMatchEvent(evtExtFilters, deletedContentFilters,
                        evtMimeType, evtFileSize, dsId, dataSourceAdded, searchParams));

        return getDAOEvents(evtExtFilters, deletedContentFilters, evtMimeType, evtFileSize, dsId, dataSourceAdded);
    }

    private boolean searchParamsMatchEvent(Set<FileExtSearchFilter> evtExtFilters,
            Set<DeletedContentFilter> deletedContentFilters,
            String evtMimeType,
            FileSizeFilter evtFileSize,
            Long dsId,
            boolean dataSourceAdded,
            Object searchParams) {

        if (searchParams instanceof FileTypeExtensionsSearchParams) {
            FileTypeExtensionsSearchParams extParams = (FileTypeExtensionsSearchParams) searchParams;
            // if data source added or evtExtFilters contain param filter
            return (dataSourceAdded || (evtExtFilters != null && evtExtFilters.contains(extParams.getFilter())))
                    // and data source is either null or they are equal data source ids
                    && (extParams.getDataSourceId() == null || dsId == null || Objects.equals(extParams.getDataSourceId(), dsId));

        } else if (searchParams instanceof FileTypeMimeSearchParams) {
            FileTypeMimeSearchParams mimeParams = (FileTypeMimeSearchParams) searchParams;
            return evtMimeType != null && evtMimeType.startsWith(mimeParams.getMimeType())
                    && (mimeParams.getDataSourceId() == null || Objects.equals(mimeParams.getDataSourceId(), dsId));

        } else if (searchParams instanceof FileTypeSizeSearchParams) {
            FileTypeSizeSearchParams sizeParams = (FileTypeSizeSearchParams) searchParams;
            // if data source added or size filter is equal to param filter
            return (dataSourceAdded || Objects.equals(sizeParams.getSizeFilter(), evtFileSize))
                    // and data source is either null or they are equal data source ids
                    && (sizeParams.getDataSourceId() == null || dsId == null || Objects.equals(sizeParams.getDataSourceId(), dsId));
        } else if (searchParams instanceof DeletedContentSearchParams) {
            DeletedContentSearchParams deletedParams = (DeletedContentSearchParams) searchParams;
            return (dataSourceAdded || (deletedContentFilters != null && deletedContentFilters.contains(deletedParams.getFilter())))
                    && (deletedParams.getDataSourceId() == null || dsId == null || Objects.equals(deletedParams.getDataSourceId(), dsId));
        } else {
            return false;
        }
    }

    /**
     * Clears relevant cache entries from cache based on digest of autopsy
     * events.
     *
     * @param extFilters            The set of affected extension filters.
     * @param deletedContentFilters The set of affected deleted content filters.
     * @param mimeType              The affected mime type or null.
     * @param sizeFilter            The affected size filter or null.
     * @param dsId                  The file object id.
     * @param dataSourceAdded       A data source was added.
     *
     * @return The list of affected dao events.
     */
    private Set<DAOEvent> getDAOEvents(Set<FileExtSearchFilter> extFilters,
            Set<DeletedContentFilter> deletedContentFilters,
            String mimeType,
            FileSizeFilter sizeFilter,
            Long dsId,
            boolean dataSourceAdded) {

        Stream<DAOEvent> extEvents = extFilters == null
                ? Stream.empty()
                : extFilters.stream()
                        .map(extFilter -> new FileTypeExtensionsEvent(extFilter, dsId));

        Stream<DAOEvent> deletedEvents = deletedContentFilters == null
                ? Stream.empty()
                : deletedContentFilters.stream()
                        .map(deletedFilter -> new DeletedContentEvent(deletedFilter, dsId));

        List<DAOEvent> daoEvents = Stream.concat(extEvents, deletedEvents)
                .collect(Collectors.toList());

        if (mimeType != null) {
            daoEvents.add(new FileTypeMimeEvent(mimeType, dsId));
        }

        if (sizeFilter != null) {
            daoEvents.add(new FileTypeSizeEvent(sizeFilter, dsId));
        }

        List<TreeEvent> treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                .map(daoEvt -> new TreeEvent(createTreeItem(daoEvt, TreeDisplayCount.INDETERMINATE), false))
                .collect(Collectors.toList());

        // data source added events are not necessarily fired before ingest completed/cancelled, so don't handle dataSourceAdded events with delay.
        Set<DAOEvent> forceRefreshEvents = (dataSourceAdded)
                ? getFileViewRefreshEvents(dsId)
                : Collections.emptySet();

        List<TreeEvent> forceRefreshTreeEvents = forceRefreshEvents.stream()
                .map(evt -> new TreeEvent(createTreeItem(evt, TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toList());

        return Stream.of(daoEvents, treeEvents, forceRefreshEvents, forceRefreshTreeEvents)
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toSet());
    }

    private Set<DeletedContentFilter> getMatchingDeletedContentFilters(AbstractFile af) {
        Set<DeletedContentFilter> toRet = new HashSet<>();

        TSK_DB_FILES_TYPE_ENUM type = af.getType();

        if (af.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)
                && !af.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.ORPHAN)
                && TSK_DB_FILES_TYPE_ENUM.FS.equals(type)) {

            toRet.add(DeletedContentFilter.FS_DELETED_FILTER);
        }

        if ((((af.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC) || af.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.ORPHAN)) && TSK_DB_FILES_TYPE_ENUM.FS.equals(type))
                || TSK_DB_FILES_TYPE_ENUM.CARVED.equals(type)
                || (af.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC) && TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.equals(type)))) {

            toRet.add(DeletedContentFilter.ALL_DELETED_FILTER);
        }

        return toRet;
    }

    /**
     * Returns events for when a full refresh is required because module content
     * events will not necessarily provide events for files (i.e. data source
     * added, ingest cancelled/completed).
     *
     * @param dataSourceId The data source id or null if not applicable.
     *
     * @return The set of events that apply in this situation.
     */
    private Set<DAOEvent> getFileViewRefreshEvents(Long dataSourceId) {
        return ImmutableSet.of(
                new DeletedContentEvent(null, dataSourceId),
                new FileTypeSizeEvent(null, dataSourceId),
                new FileTypeExtensionsEvent(null, dataSourceId)
        );
    }

    /**
     * Handles fetching and paging of data for file types by extension.
     */
    public static class FileTypeExtFetcher extends DAOFetcher<FileTypeExtensionsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileTypeExtFetcher(FileTypeExtensionsSearchParams params) {
            super(params);
        }

        protected ViewsDAO getDAO() {
            return MainDAO.getInstance().getViewsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getFilesByExtension(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isFilesByExtInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of data for file types by mime type.
     */
    public static class FileTypeMimeFetcher extends DAOFetcher<FileTypeMimeSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileTypeMimeFetcher(FileTypeMimeSearchParams params) {
            super(params);
        }

        protected ViewsDAO getDAO() {
            return MainDAO.getInstance().getViewsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getFilesByMime(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isFilesByMimeInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of data for file types by size.
     */
    public class FileTypeSizeFetcher extends DAOFetcher<FileTypeSizeSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileTypeSizeFetcher(FileTypeSizeSearchParams params) {
            super(params);
        }

        protected ViewsDAO getDAO() {
            return MainDAO.getInstance().getViewsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getFilesBySize(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isFilesBySizeInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of data for deleted content.
     */
    public static class DeletedFileFetcher extends DAOFetcher<DeletedContentSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public DeletedFileFetcher(DeletedContentSearchParams params) {
            super(params);
        }

        protected ViewsDAO getDAO() {
            return MainDAO.getInstance().getViewsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getDeletedContent(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isDeletedContentInvalidating(this.getParameters(), evt);
        }

    }
}
