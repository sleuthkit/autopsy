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
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import static org.sleuthkit.autopsy.core.UserPreferences.hideSlackFilesInViewsTree;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
public class ViewsDAO {

    private static final Logger logger = Logger.getLogger(ViewsDAO.class.getName());

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private static final String FILE_VIEW_EXT_TYPE_ID = "FILE_VIEW_BY_EXT";

    private static ViewsDAO instance = null;

    synchronized static ViewsDAO getInstance() {
        if (instance == null) {
            instance = new ViewsDAO();
        }

        return instance;
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

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getFilesByExtension(FileTypeExtensionsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<FileTypeExtensionsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchExtensionSearchResultsDTOs(key.getFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesByMime(FileTypeMimeSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getMimeType() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<FileTypeMimeSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchMimeSearchResultsDTOs(key.getMimeType(), key.getDataSourceId(), startItem, maxCount));
    }

    public SearchResultsDTO getFilesBySize(FileTypeSizeSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getSizeFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<FileTypeSizeSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchSizeSearchResultsDTOs(key.getSizeFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    public boolean isFilesByExtInvalidating(FileTypeExtensionsSearchParams key, Content eventData) {
        if (!(eventData instanceof AbstractFile)) {
            return false;
        }

        AbstractFile file = (AbstractFile) eventData;
        String extension = "." + file.getNameExtension().toLowerCase();
        return key.getFilter().getFilter().contains(extension);
    }

    public boolean isFilesByMimeInvalidating(FileTypeMimeSearchParams key, Content eventData) {
        if (!(eventData instanceof AbstractFile)) {
            return false;
        }

        AbstractFile file = (AbstractFile) eventData;
        String mimeType = file.getMIMEType();
        return key.getMimeType().equalsIgnoreCase(mimeType);
    }

    public boolean isFilesBySizeInvalidating(FileTypeSizeSearchParams key, Content eventData) {
        if (!(eventData instanceof AbstractFile)) {
            return false;
        }

        long size = eventData.getSize();

        return size >= key.getSizeFilter().getMinBound() && (key.getSizeFilter().getMaxBound() == null || size < key.getSizeFilter().getMaxBound());
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
    private static String getFileSizeClause(FileTypeSizeSearchParams.FileSizeFilter filter) {
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
    private String getFileSizesWhereStatement(FileTypeSizeSearchParams.FileSizeFilter filter, Long dataSourceId) {
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
        Map<FileExtSearchFilter, String> whereClauses = filters.stream()
                .collect(Collectors.toMap(
                        filter -> filter,
                        filter -> getFileExtensionClause(filter)));

        Map<FileExtSearchFilter, Long> countsByFilter = getFilesCounts(whereClauses, getBaseFileExtensionFilter(), dataSourceId, true);

        List<TreeItemDTO<FileTypeExtensionsSearchParams>> treeList = countsByFilter.entrySet().stream()
                .map(entry -> {
                    return new TreeItemDTO<>(
                            "FILE_EXT",
                            new FileTypeExtensionsSearchParams(entry.getKey(), dataSourceId),
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            entry.getValue());
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
        Map<FileTypeSizeSearchParams.FileSizeFilter, String> whereClauses = Stream.of(FileTypeSizeSearchParams.FileSizeFilter.values())
                .collect(Collectors.toMap(
                        filter -> filter,
                        filter -> getFileSizeClause(filter)));

        Map<FileTypeSizeSearchParams.FileSizeFilter, Long> countsByFilter = getFilesCounts(whereClauses, getBaseFileSizeFilter(), dataSourceId, true);

        List<TreeItemDTO<FileTypeSizeSearchParams>> treeList = countsByFilter.entrySet().stream()
                .map(entry -> {
                    return new TreeItemDTO<>(
                            "FILE_SIZE",
                            new FileTypeSizeSearchParams(entry.getKey(), dataSourceId),
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            entry.getValue());
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

                            return new TreeItemDTO<>(
                                    "FILE_MIME_TYPE",
                                    new FileTypeMimeSearchParams(entry.getKey(), dataSourceId),
                                    name,
                                    name,
                                    entry.getValue());
                        })
                        .sorted((a, b) -> stringCompare(a.getTypeData().getMimeType(), b.getTypeData().getMimeType()))
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

    private SearchResultsDTO fetchSizeSearchResultsDTOs(FileTypeSizeSearchParams.FileSizeFilter filter, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
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
                    getExtensionMediaType(file.getNameExtension()),
                    file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                    file.getType(),
                    cellValues));
        }

        return new BaseSearchResultsDTO(FILE_VIEW_EXT_TYPE_ID, displayName, FileSystemColumnUtils.getColumnKeysForAbstractfile(), fileRows, startItem, totalResultsCount);
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFilesByExtension(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return true;
            // GVDTODO
//            Content content = DAOEventUtils.getContentFromEvt(evt);
//            if (content == null) {
//                return false;
//            }
//
//            return MainDAO.getInstance().getViewsDAO().isFilesByExtInvalidating(this.getParameters(), content);
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFilesByMime(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return true;
            // GVDTODO
//            Content content = DAOEventUtils.getContentFromEvt(evt);
//            if (content == null) {
//                return false;
//            }
//
//            return MainDAO.getInstance().getViewsDAO().isFilesByMimeInvalidating(this.getParameters(), content);
        }
    }

    /**
     * Handles fetching and paging of data for file types by size.
     */
    public static class FileTypeSizeFetcher extends DAOFetcher<FileTypeSizeSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileTypeSizeFetcher(FileTypeSizeSearchParams params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFilesBySize(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return true;
            
            // GVDTODO
//            Content content = DAOEventUtils.getContentFromEvt(evt);
//            if (content == null) {
//                return false;
//            }
//
//            return MainDAO.getInstance().getViewsDAO().isFilesBySizeInvalidating(this.getParameters(), content);
        }
    }
}
