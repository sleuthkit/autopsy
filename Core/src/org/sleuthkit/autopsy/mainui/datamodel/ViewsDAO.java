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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
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

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
public class ViewsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(ViewsDAO.class.getName());

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;

    private static final String FILE_VIEW_EXT_TYPE_ID = "FILE_VIEW_BY_EXT";

    private static ViewsDAO instance = null;

    synchronized static ViewsDAO getInstance() {
        if (instance == null) {
            instance = new ViewsDAO();
        }

        return instance;
    }

    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
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

        SearchParams<FileTypeExtensionsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchExtensionSearchResultsDTOs(key.getFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesByMime(FileTypeMimeSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getMimeType() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<FileTypeMimeSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchMimeSearchResultsDTOs(key.getMimeType(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesBySize(FileTypeSizeSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getSizeFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<FileTypeSizeSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchSizeSearchResultsDTOs(key.getSizeFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    private boolean isFilesByExtInvalidating(FileTypeExtensionsSearchParams key, DAOEvent eventData) {
        if (!(eventData instanceof FileTypeExtensionsEvent)) {
            return false;
        }

        FileTypeExtensionsEvent extEvt = (FileTypeExtensionsEvent) eventData;
        String extension = extEvt.getExtension().toLowerCase();
        return key.getFilter().getFilter().contains(extension)
                && (key.getDataSourceId() == null || key.getDataSourceId().equals(extEvt.getDataSourceId()));
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
        return sizeEvt.getSizeFilter().equals(key.getSizeFilter())
                && (key.getDataSourceId() == null || Objects.equals(key.getDataSourceId(), sizeEvt.getDataSourceId()));
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
     * Returns a clause that will filter out files that aren't to be counted in
     * the file extensions view.
     *
     * @return The filter that will need to be proceeded with 'where' or 'and'.
     */
    private String getBaseFileExtensionFilter() {
        return "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known <> " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "");
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
     * Returns a statement to be proceeded with 'where' or 'and' that will
     * filter out results that should not be viewed in mime types view.
     *
     * @return A statement to be proceeded with 'and' or 'where'.
     */
    private String getBaseFileMimeFilter() {
        return "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "")
                + " AND (type IN ("
                + TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()
                + (hideSlackFilesInViewsTree() ? "" : ("," + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.ordinal()))
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
     * The filter for all files to remove those that should never be seen in the
     * file size views.
     *
     * @return The clause to be proceeded with 'where' or 'and'.
     */
    private String getBaseFileSizeFilter() {
        // Ignore unallocated block files.
        return "(type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType() + ")"
                + ((hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "")); //NON-NLS
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
                if (dataSourceId == null || Objects.equals(extEvt.getDataSourceId(), dataSourceId)) {
                    for (FileExtSearchFilter filter : filters) {
                        if (filter.getFilter().contains(evt)) {
                            indeterminateFilters.add(filter);
                        }
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

                    return new TreeItemDTO<>(
                            FileTypeExtensionsSearchParams.getTypeId(),
                            new FileTypeExtensionsSearchParams(entry.getKey(), dataSourceId),
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            displayCount);
                })
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new TreeResultsDTO<>(treeList);
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
                if (dataSourceId == null || Objects.equals(sizeEvt.getDataSourceId(), dataSourceId)) {
                    indeterminateFilters.add(sizeEvt.getSizeFilter());
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

                    return new TreeItemDTO<>(
                            FileTypeSizeSearchParams.getTypeId(),
                            new FileTypeSizeSearchParams(entry.getKey(), dataSourceId),
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            displayCount);
                })
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new TreeResultsDTO<>(treeList);
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

                            return new TreeItemDTO<>(
                                    FileTypeMimeSearchParams.getTypeId(),
                                    new FileTypeMimeSearchParams(entry.getKey(), dataSourceId),
                                    name,
                                    name,
                                    displayCount);
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
                + (baseWhereClauses != null ? ("WHERE " + baseWhereClauses) : "") + ") res \n"
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
                    file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                    file.getType(),
                    cellValues));
        }

        return new BaseSearchResultsDTO(FILE_VIEW_EXT_TYPE_ID, displayName, FileSystemColumnUtils.getColumnKeysForAbstractfile(), fileRows, AbstractFile.class.getName(), startItem, totalResultsCount);
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
    }

    private Pair<String, String> getMimePieces(String mimeType) {
        int idx = mimeType.indexOf("/");
        String mimePrefix = idx > 0 ? mimeType.substring(0, idx) : mimeType;
        String mimeSuffix = idx > 0 ? mimeType.substring(idx + 1) : null;
        return Pair.of(mimePrefix, mimeSuffix);
    }

    @Override
    Set<DAOEvent> handleIngestComplete() {
        // GVDTODO
        return Collections.emptySet();
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        // GVDTODO
        return Collections.emptySet();
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        // GVDTODO maps may not be necessary now that this isn't processing a list of events.
        Map<String, Set<Long>> fileExtensionDsMap = new HashMap<>();
        Map<String, Map<String, Set<Long>>> mimeTypeDsMap = new HashMap<>();
        Map<FileSizeFilter, Set<Long>> fileSizeDsMap = new HashMap<>();

        AbstractFile af = DAOEventUtils.getFileFromFileEvent(evt);
        if (af == null) {
            return Collections.emptySet();
        }

        // create an extension mapping if extension present
        if (!StringUtils.isBlank(af.getNameExtension())) {
            fileExtensionDsMap
                    .computeIfAbsent("." + af.getNameExtension(), (k) -> new HashSet<>())
                    .add(af.getDataSourceObjectId());
        }

        // create a mime type mapping if mime type present
        if (!StringUtils.isBlank(af.getMIMEType())) {
            Pair<String, String> mimePieces = getMimePieces(af.getMIMEType());
            mimeTypeDsMap
                    .computeIfAbsent(mimePieces.getKey(), (k) -> new HashMap<>())
                    .computeIfAbsent(mimePieces.getValue(), (k) -> new HashSet<>())
                    .add(af.getDataSourceObjectId());
        }

        // create a size mapping if size present
        FileSizeFilter sizeFilter = Stream.of(FileSizeFilter.values())
                .filter(filter -> af.getSize() >= filter.getMinBound() && (filter.getMaxBound() == null || af.getSize() < filter.getMaxBound()))
                .findFirst()
                .orElse(null);

        if (sizeFilter != null) {
            fileSizeDsMap
                    .computeIfAbsent(sizeFilter, (k) -> new HashSet<>())
                    .add(af.getDataSourceObjectId());
        }

        if (fileExtensionDsMap.isEmpty() && mimeTypeDsMap.isEmpty() && fileSizeDsMap.isEmpty()) {
            return Collections.emptySet();
        }

        clearRelevantCacheEntries(fileExtensionDsMap, mimeTypeDsMap, fileSizeDsMap);

        return getDAOEvents(fileExtensionDsMap, mimeTypeDsMap, fileSizeDsMap);
    }

    /**
     *
     * Clears relevant cache entries from cache based on digest of autopsy
     * events.
     *
     * @param fileExtensionDsMap Maps the file extension to the data sources
     *                           where files were found with that extension.
     * @param mimeTypeDsMap      Maps the mime type to the data sources where
     *                           files were found with that mime type.
     * @param fileSizeDsMap      Maps the size to the data sources where files
     *
     * @return The list of affected dao events.
     */
    private Set<DAOEvent> getDAOEvents(Map<String, Set<Long>> fileExtensionDsMap,
            Map<String, Map<String, Set<Long>>> mimeTypeDsMap,
            Map<FileSizeFilter, Set<Long>> fileSizeDsMap) {

        Stream<DAOEvent> fileExtStream = fileExtensionDsMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new FileTypeExtensionsEvent(entry.getKey(), dsId)));

        Set<DAOEvent> fileMimeList = new HashSet<>();
        for (Entry<String, Map<String, Set<Long>>> prefixEntry : mimeTypeDsMap.entrySet()) {
            String mimePrefix = prefixEntry.getKey();
            for (Entry<String, Set<Long>> suffixEntry : prefixEntry.getValue().entrySet()) {
                String mimeSuffix = suffixEntry.getKey();
                for (long dsId : suffixEntry.getValue()) {
                    String mimeType = mimePrefix + (mimeSuffix == null ? "" : ("/" + mimeSuffix));
                    fileMimeList.add(new FileTypeMimeEvent(mimeType, dsId));
                }
            }
        }

        Stream<DAOEvent> fileSizeStream = fileSizeDsMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new FileTypeSizeEvent(entry.getKey(), dsId)));

        return Stream.of(fileExtStream, fileMimeList.stream(), fileSizeStream)
                .flatMap(stream -> stream)
                .collect(Collectors.toSet());
    }

    /**
     * Clears relevant cache entries from cache based on digest of autopsy
     * events.
     *
     * @param fileExtensionDsMap Maps the file extension to the data sources
     *                           where files were found with that extension.
     * @param mimeTypeDsMap      Maps the mime type to the data sources where
     *                           files were found with that mime type.
     * @param fileSizeDsMap      Maps the size to the data sources where files
     *                           were found within that size filter.
     */
    private void clearRelevantCacheEntries(Map<String, Set<Long>> fileExtensionDsMap,
            Map<String, Map<String, Set<Long>>> mimeTypeDsMap,
            Map<FileSizeFilter, Set<Long>> fileSizeDsMap) {

        // invalidate cache entries that are affected by events
        ConcurrentMap<SearchParams<?>, SearchResultsDTO> concurrentMap = this.searchParamsCache.asMap();
        concurrentMap.forEach((k, v) -> {
            Object baseParams = k.getParamData();
            if (baseParams instanceof FileTypeExtensionsSearchParams) {
                FileTypeExtensionsSearchParams extParams = (FileTypeExtensionsSearchParams) baseParams;
                // if search params have a filter where extension is present and the data source id is null or ==
                boolean isMatch = extParams.getFilter().getFilter().stream().anyMatch((ext) -> {
                    Set<Long> dsIds = fileExtensionDsMap.get(ext);
                    return (dsIds != null && (extParams.getDataSourceId() == null || dsIds.contains(extParams.getDataSourceId())));
                });

                if (isMatch) {
                    concurrentMap.remove(k);
                }
            } else if (baseParams instanceof FileTypeMimeSearchParams) {
                FileTypeMimeSearchParams mimeParams = (FileTypeMimeSearchParams) baseParams;
                Pair<String, String> mimePieces = getMimePieces(mimeParams.getMimeType());
                Map<String, Set<Long>> suffixes = mimeTypeDsMap.get(mimePieces.getKey());
                if (suffixes == null) {
                    return;
                }

                // if search params is top level mime prefix (without suffix) and data source is null or ==.
                if (mimePieces.getValue() == null
                        && (mimeParams.getDataSourceId() == null
                        || suffixes.values().stream().flatMap(set -> set.stream()).anyMatch(ds -> Objects.equals(mimeParams.getDataSourceId(), ds)))) {

                    concurrentMap.remove(k);
                    // otherwise, see if suffix is present
                } else {
                    Set<Long> dataSources = suffixes.get(mimePieces.getValue());
                    if (dataSources != null && (mimeParams.getDataSourceId() == null || dataSources.contains(mimeParams.getDataSourceId()))) {
                        concurrentMap.remove(k);
                    }
                }

            } else if (baseParams instanceof FileTypeSizeSearchParams) {
                FileTypeSizeSearchParams sizeParams = (FileTypeSizeSearchParams) baseParams;
                Set<Long> dataSources = fileSizeDsMap.get(sizeParams.getSizeFilter());
                if (dataSources != null && (sizeParams.getDataSourceId() == null || dataSources.contains(sizeParams.getDataSourceId()))) {
                    concurrentMap.remove(k);
                }
            }
        });
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
}
