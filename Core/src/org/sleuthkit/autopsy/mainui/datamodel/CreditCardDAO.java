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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.CreditCardEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ReviewStatus;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * DAO for fetching credit card information.
 */
public class CreditCardDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(CreditCardDAO.class.getName());
    private static final String LIKE_ESCAPE_CHAR = "\\";
    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;

    // number of digits to include in bin prefix
    private static final int BIN_PREFIX_NUM = 8;

    private final Cache<SearchParams<CreditCardSearchParams>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<CreditCardEvent> creditCardTreeCounts = new TreeCounts<>();

    private static CreditCardDAO instance = null;

    synchronized static CreditCardDAO getInstance() {
        if (instance == null) {
            instance = new CreditCardDAO();
        }

        return instance;
    }

    SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getCreditCardByFile(CreditCardFileSearchParams searchParams, long startItem, Long maxCount) throws IllegalArgumentException, ExecutionException {
        if (startItem < 0 || (maxCount != null && maxCount < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Start item and max count need to be >= 0 but were [startItem: {0}, maxCount: {1}]",
                    startItem,
                    maxCount == null ? "<null>" : maxCount));
        }

        SearchParams<CreditCardSearchParams> pagedSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return searchParamsCache.get(pagedSearchParams, () -> fetchCreditCardByFile(searchParams, startItem, maxCount));
    }

    private static String getConcatAggregate(DbType dbType, String field) {
        switch (dbType) {
            case POSTGRESQL:
                return MessageFormat.format("STRING_AGG({0}::character varying, ',')", field);
            case SQLITE:
                return MessageFormat.format("GROUP_CONCAT({0})", field);
            default:
                throw new IllegalStateException("Unknown database type: " + dbType);
        }
    }

    @Messages({
        "# {0} - raw file name",
        "# {1} - solr chunk id",
        "CreditCardDAO_fetchCreditCardByFile_file_displayName={0}_chunk_{1}",
        "CreditCardDAO_fetchCreditCardByFile_results_displayName=By File",})
    private SearchResultsDTO fetchCreditCardByFile(CreditCardFileSearchParams searchParams, long startItem, Long maxCount) throws IllegalStateException, TskCoreException, NoCurrentCaseException, SQLException {
        boolean includeRejected = searchParams.isRejectedIncluded();
        Long dataSourceId = searchParams.getDataSourceId();

        String baseFromAndGroupSql = "FROM blackboard_artifacts art\n"
                + "INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id \n"
                + "  AND acct.attribute_type_id = " + BlackboardAttribute.Type.TSK_CARD_NUMBER.getTypeID() + "\n"
                + "LEFT JOIN blackboard_attributes solr_doc ON art.artifact_id = solr_doc.artifact_id \n"
                + "  AND solr_doc.attribute_type_id = " + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() + "\n"
                + "LEFT JOIN tsk_files f ON art.obj_id = f.obj_id\n"
                + "WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() + "\n"
                + (dataSourceId == null ? "" : "AND art.data_source_obj_id = " + dataSourceId + "\n")
                + (includeRejected ? "" : "AND art.review_status_id <> " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + "\n")
                + "GROUP BY art.obj_id, solr_doc.value_text\n";

        String countQuery = "COUNT(*) AS count\n "
                + baseFromAndGroupSql;

        AtomicLong atomicCount = new AtomicLong(0);
        getCase().getCaseDbAccessManager().select(countQuery, (resultSet) -> {
            try {
                if (resultSet.next()) {
                    atomicCount.set(resultSet.getLong("count"));
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("An exception occurred while fetching the count.", ex);
            }
        });

        List<RowDTO> rows = new ArrayList<>();
        long totalResultCount = atomicCount.get();
        if (totalResultCount > 0) {
            String itemQuery = "  art.obj_id AS file_id, \n"
                    + "  solr_doc.value_text AS solr_document_id,\n"
                    + "  " + getConcatAggregate(getCase().getDatabaseType(), "art.artifact_id") + " AS artifact_ids,\n"
                    + "  " + getConcatAggregate(getCase().getDatabaseType(), "DISTINCT(art.review_status_id)") + " AS review_status_ids,\n"
                    + "  COUNT(*) AS count\n"
                    + baseFromAndGroupSql
                    + (maxCount == null ? "" : "LIMIT " + maxCount + "\n")
                    + "OFFSET " + startItem;

            getCase().getCaseDbAccessManager().select(itemQuery, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        Long fileId = resultSet.getLong("file_id");
                        if (resultSet.wasNull()) {
                            continue;
                        }

                        String solrDocId = resultSet.getString("solr_document_id");
                        String artifactIds = resultSet.getString("artifact_ids");
                        String reviewStatusIds = resultSet.getString("review_status_ids");
                        long itemCount = resultSet.getLong("count");

                        Set<BlackboardArtifact> associatedArtifacts = StringUtils.isBlank(artifactIds)
                                ? Collections.emptySet()
                                : getCase().getBlackboard().getDataArtifactsWhere("artifacts.artifact_id IN (" + artifactIds + ")")
                                        .stream()
                                        .collect(Collectors.toSet());

                        AbstractFile file = getCase().getAbstractFileById(fileId);

                        String fileName = StringUtils.isBlank(solrDocId)
                                ? file.getName()
                                : Bundle.CreditCardDAO_fetchCreditCardByFile_file_displayName(file.getName(), StringUtils.substringAfter(solrDocId, "_"));

                        Set<BlackboardArtifact.ReviewStatus> reviewStatuses = StringUtils.isBlank(reviewStatusIds)
                                ? Collections.emptySet()
                                : Stream.of(reviewStatusIds.split(","))
                                        .map(id -> {
                                            try {
                                                String trimmed = id.trim();
                                                int reviewStatusId = Integer.parseInt(trimmed);
                                                return BlackboardArtifact.ReviewStatus.withID(reviewStatusId);
                                            } catch (NumberFormatException ex) {
                                                return null;
                                            }
                                        })
                                        .filter(rs -> rs != null)
                                        .collect(Collectors.toSet());

                        String reviewStatusString = reviewStatuses.stream()
                                .sorted(Comparator.comparing(rs -> rs.getID()))
                                .map(rs -> rs.getDisplayName())
                                .collect(Collectors.joining(", "));

                        rows.add(new CreditCardByFileRow(file, associatedArtifacts, fileName, itemCount, reviewStatuses, reviewStatusString));
                    }
                } catch (SQLException | NoCurrentCaseException | TskCoreException ex) {
                    throw new IllegalStateException("An exception occurred while fetching items.", ex);
                }
            });
        }

        return new BaseSearchResultsDTO(CreditCardByFileRow.getRowType(), Bundle.CreditCardDAO_fetchCreditCardByFile_results_displayName(),
                CreditCardByFileRow.COLUMNS, rows, CreditCardByFileRow.getRowType(), startItem, totalResultCount);
    }

    @Messages({
        "CreditCardDAO_getCreditCardCounts_byFile_displayName=By File",
        "CreditCardDAO_getCreditCardCounts_byBIN_displayName=By BIN",})
    public TreeResultsDTO<CreditCardSearchParams> getCreditCardCounts(Long dataSourceId, boolean includeRejected) throws NoCurrentCaseException, TskCoreException, IllegalStateException {
        String countsQuery = "\n  COUNT(DISTINCT(art.obj_id)) AS file_count,\n"
                + "  COUNT(DISTINCT(art.artifact_id)) AS bin_count\n"
                + " FROM blackboard_artifacts art\n"
                + " INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id\n"
                + "   AND acct.attribute_type_id = " + BlackboardAttribute.Type.TSK_CARD_NUMBER + "\n"
                + " WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT + "\n"
                + (dataSourceId == null ? "" : "AND art.data_source_obj_id = " + dataSourceId + "\n")
                + (includeRejected ? "" : "AND art.review_status_id <> " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + "\n");

        boolean isIndeterminate = !this.creditCardTreeCounts.getEnqueued().isEmpty();

        List<TreeItemDTO<CreditCardSearchParams>> items = new ArrayList<>();
        getCase().getCaseDbAccessManager().select(countsQuery, (resultSet) -> {
            try {
                if (resultSet.next()) {
                    items.add(new TreeItemDTO<>(
                            CreditCardSearchParams.getTypeId(),
                            new CreditCardFileSearchParams(includeRejected, dataSourceId),
                            CreditCardFileSearchParams.getTypeId(),
                            Bundle.CreditCardDAO_getCreditCardCounts_byFile_displayName(),
                            isIndeterminate ? TreeDisplayCount.INDETERMINATE : TreeDisplayCount.getDeterminate(resultSet.getLong("file_count"))));

                    items.add(new TreeItemDTO<>(
                            CreditCardSearchParams.getTypeId(),
                            new CreditCardBinSearchParams(null, includeRejected, dataSourceId),
                            CreditCardBinSearchParams.getTypeId(),
                            Bundle.CreditCardDAO_getCreditCardCounts_byBIN_displayName(),
                            isIndeterminate ? TreeDisplayCount.INDETERMINATE : TreeDisplayCount.getDeterminate(resultSet.getLong("bin_count"))));

                }
            } catch (SQLException ex) {
                throw new IllegalStateException("An Exception occurred while fetching counts.", ex);
            }
        });

        return new TreeResultsDTO<>(items);
    }

    public SearchResultsDTO getCreditCardByBin(CreditCardBinSearchParams searchParams, long startItem, Long maxCount) throws IllegalArgumentException, ExecutionException {
        if (startItem < 0 || (maxCount != null && maxCount < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Start item and max count need to be >= 0 but were [startItem: {0}, maxCount: {1}]",
                    startItem,
                    maxCount == null ? "<null>" : maxCount));
        }

        SearchParams<CreditCardSearchParams> pagedSearchParams = new SearchParams<>(searchParams, startItem, maxCount);

        return searchParamsCache.get(pagedSearchParams, () -> fetchCreditCardByBin(searchParams, startItem, maxCount));
    }

    public SearchResultsDTO fetchCreditCardByBin(CreditCardBinSearchParams searchParams, long startItem, Long maxCount) throws TskCoreException, NoCurrentCaseException, IllegalStateException, SQLException {
        Long dataSourceId = searchParams.getDataSourceId();
        boolean includeRejected = searchParams.isRejectedIncluded();

        String baseQuery = "FROM blackboard_artifacts art\n"
                + "LEFT JOIN blackboard_attributes attr ON art.artifact_id = attr.artifact_id\n"
                + "WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() + "\n"
                + "AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_CARD_NUMBER.getTypeID() + "\n"
                + (dataSourceId == null ? "" : "AND art.data_source_obj_id = ?\n")
                + (includeRejected ? "" : "AND art.review_status_id <> " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + "\n")
                + "AND attr.value_text LIKE ? ESCAPE " + LIKE_ESCAPE_CHAR + "\n"
                + "ORDER BY art.artifact_id\n";

        String countQuery = "COUNT(DISTINCT(art.artifact_id))\n"
                + baseQuery;

        String binLikeStatement = SubDAOUtils.likeEscape(searchParams.getBinPrefix(), LIKE_ESCAPE_CHAR) + "%";

        AtomicLong atomicCount = new AtomicLong(0);
        try (CaseDbPreparedStatement countStatement = getCase().getCaseDbAccessManager().prepareSelect(countQuery)) {
            int parameterIdx = 0;
            if (dataSourceId != null) {
                countStatement.setLong(++parameterIdx, dataSourceId);
            }

            countStatement.setString(++parameterIdx, binLikeStatement);

            getCase().getCaseDbAccessManager().select(countStatement, (resultSet) -> {
                try {
                    if (resultSet.next()) {
                        atomicCount.set(resultSet.getLong("count"));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException("Unable to retrieve count.", ex);
                }
            });
        }

        long totalCount = atomicCount.get();
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        if (totalCount > 0) {
            String pagedIdQuery = "art.artifact_id AS artifact_id\n"
                    + baseQuery
                    + "GROUP BY art.artifact_id\n"
                    + (maxCount == null ? "" : "LIMIT ?\n")
                    + "OFFSET ?\n";

            List<Long> artifactIds = new ArrayList<>();
            try (CaseDbPreparedStatement queryStatement = getCase().getCaseDbAccessManager().prepareSelect(pagedIdQuery)) {
                int parameterIdx = 0;
                if (dataSourceId != null) {
                    queryStatement.setLong(++parameterIdx, dataSourceId);
                }

                queryStatement.setString(++parameterIdx, binLikeStatement);

                if (maxCount != null) {
                    queryStatement.setLong(++parameterIdx, maxCount);
                }

                queryStatement.setLong(++parameterIdx, startItem);

                getCase().getCaseDbAccessManager().select(queryStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            artifactIds.add(resultSet.getLong("artifact_id"));
                        }
                    } catch (SQLException ex) {
                        throw new IllegalStateException("Unable to retrieve artifact ids.", ex);
                    }
                });
            }

            if (!artifactIds.isEmpty()) {
                String artifactIdsStr = artifactIds.stream()
                        .filter(id -> id != null)
                        .map(id -> Long.toString(id))
                        .collect(Collectors.joining(", "));

                artifacts.addAll(getCase().getBlackboard().getDataArtifactsWhere("artifact_id IN (" + artifactIdsStr + ")"));
            }
        }

        getCase().getBlackboard().loadBlackboardAttributes(artifacts);
        BlackboardArtifactDAO.TableData tableData = MainDAO.getInstance().getDataArtifactsDAO().createTableData(BlackboardArtifact.Type.TSK_ACCOUNT, artifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_ACCOUNT, tableData.columnKeys, tableData.rows, startItem, totalCount);
    }

    public TreeResultsDTO<CreditCardBinSearchParams> getCreditCardBinCounts(Long dataSourceId, boolean includeRejected) throws TskCoreException, IllegalStateException, NoCurrentCaseException {

        Set<String> indeterminatePrefixes = this.creditCardTreeCounts.getEnqueued().stream()
                .filter(evts -> evts != null
                && evts.isRejectedStatus() == includeRejected
                && evts.getBinPrefix() != null
                && (dataSourceId == null || Objects.equals(evts.getDataSourceId(), dataSourceId)))
                .map(params -> params.getBinPrefix())
                .collect(Collectors.toSet());

        String countsQuery = "\n  COUNT(*) as count,\n"
                + "  SUBSTR(acct.value_text, 1, " + BIN_PREFIX_NUM + ") AS bin_prefix\n"
                + "  COUNT(DISTINCT(art.artifact_id)) AS bin_count\n"
                + " FROM blackboard_artifacts art\n"
                + " INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id\n"
                + "   AND acct.attribute_type_id = " + BlackboardAttribute.Type.TSK_CARD_NUMBER + "\n"
                + " WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT + "\n"
                + (dataSourceId == null ? "" : "AND art.data_source_obj_id = " + dataSourceId + "\n")
                + (includeRejected ? "" : "AND art.review_status_id <> " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + "\n")
                + "GROUP BY SUBSTR(acct.value_text, 1, " + BIN_PREFIX_NUM + ")\n"
                + "ORDER BY SUBSTR(acct.value_text, 1, " + BIN_PREFIX_NUM + ")\n";

        List<TreeItemDTO<CreditCardBinSearchParams>> items = new ArrayList<>();
        getCase().getCaseDbAccessManager().select(countsQuery, (resultSet) -> {
            try {
                while (resultSet.next()) {
                    String binPrefix = resultSet.getString("bin_prefix");
                    items.add(new TreeItemDTO<>(
                            CreditCardBinSearchParams.getTypeId(),
                            new CreditCardBinSearchParams(binPrefix, includeRejected, dataSourceId),
                            CreditCardFileSearchParams.getTypeId(),
                            binPrefix,
                            indeterminatePrefixes.contains(binPrefix) ? TreeDisplayCount.INDETERMINATE : TreeDisplayCount.getDeterminate(resultSet.getLong("count"))));

                }
            } catch (SQLException ex) {
                throw new IllegalStateException("An Exception occurred while fetching counts.", ex);
            }
        });

        return new TreeResultsDTO<>(items);
    }

    // is account invalidating
    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(
                this.creditCardTreeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count)
        );
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.creditCardTreeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count)
        );
    }

    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt == null) {
            return Collections.emptySet();
        }

        // maps bin prefix => isRejected => Data source ids
        Map<String, Map<Boolean, Set<Long>>> creditCardBinPrefixMap = new HashMap<>();
        Set<Pair<Long, Boolean>> affectedDataSources = new HashSet<>();

        for (BlackboardArtifact art : dataEvt.getArtifacts()) {
            try {
                if (art.getType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
                    BlackboardAttribute attr = art.getAttribute(BlackboardAttribute.Type.TSK_CARD_NUMBER);
                    if (attr != null && attr.getValueString() != null) {

                        String cardNumber = attr.getValueString().trim();
                        String cardPrefix = cardNumber.substring(0, Math.min(cardNumber.length(), BIN_PREFIX_NUM));

                        ReviewStatus reviewStatus = art.getReviewStatus();

                        creditCardBinPrefixMap
                                .computeIfAbsent(cardPrefix, (k) -> new HashMap<>())
                                .computeIfAbsent(ReviewStatus.REJECTED.equals(reviewStatus), (k) -> new HashSet<>())
                                .add(art.getDataSourceObjectID());

                        affectedDataSources.add(Pair.of(art.getDataSourceObjectID(), ReviewStatus.REJECTED.equals(reviewStatus)));
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "There was an error determining events from artifact with id: " + art.getId(), ex);
            }
        }

        if (creditCardBinPrefixMap.isEmpty()) {
            return Collections.emptySet();
        }

        SubDAOUtils.invalidateKeys(this.searchParamsCache, (paramKey) -> {
            if (paramKey instanceof CreditCardBinSearchParams) {
                CreditCardBinSearchParams binKey = ((CreditCardBinSearchParams) paramKey);
                Map<Boolean, Set<Long>> reviewStatuses = creditCardBinPrefixMap.get(binKey.getBinPrefix());
                if (reviewStatuses != null) {
                    return (binKey.isRejectedIncluded()
                            ? Stream.of(reviewStatuses.get(true), reviewStatuses.get(false))
                            : Stream.of(reviewStatuses.get(false)))
                            .filter(dsIds -> dsIds != null)
                            .flatMap(dsIds -> dsIds.stream())
                            .anyMatch(dsId -> binKey.getDataSourceId() == null || Objects.equals(binKey.getDataSourceId(), dsId));
                }
            } else if (paramKey instanceof CreditCardFileSearchParams) {
                CreditCardFileSearchParams fileKey = ((CreditCardFileSearchParams) paramKey);
                return affectedDataSources.stream()
                        .anyMatch(pr -> (fileKey.isRejectedIncluded() || !pr.getRight())
                        && (fileKey.getDataSourceId() == null || Objects.equals(fileKey.getDataSourceId(), pr.getLeft())));

            }
            return false;
        });

        Set<CreditCardEvent> events = new HashSet<>();
        for (Entry<String, Map<Boolean, Set<Long>>> binEntry : creditCardBinPrefixMap.entrySet()) {
            String binPrefix = binEntry.getKey();
            for (Entry<Boolean, Set<Long>> isRejectedEntry : binEntry.getValue().entrySet()) {
                boolean isRejected = isRejectedEntry.getKey();
                for (Long dsId : isRejectedEntry.getValue()) {
                    events.add(new CreditCardEvent(binPrefix, isRejected, dsId));
                }
            }
        }

        Stream<TreeEvent> treeEvents = this.creditCardTreeCounts.enqueueAll(events).stream()
                .map(daoEvt -> new TreeEvent(createTreeItem(daoEvt, TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false));

        return Stream.of(events.stream(), treeEvents)
                .flatMap(s -> s)
                .collect(Collectors.toSet());
    }

    private TreeItemDTO<CreditCardBinSearchParams> createTreeItem(CreditCardEvent daoEvt, TreeDisplayCount count) {
        return new TreeItemDTO<>(CreditCardBinSearchParams.getTypeId(),
                new CreditCardBinSearchParams(daoEvt.getBinPrefix(), daoEvt.isRejectedStatus(), daoEvt.getDataSourceId()),
                daoEvt.getBinPrefix(),
                daoEvt.getBinPrefix(),
                count);
    }

    private boolean isRefreshRequired(CreditCardBinSearchParams parameters, DAOEvent evt) {
        if (!(evt instanceof CreditCardEvent)) {
            return false;
        }

        CreditCardEvent ccEvt = (CreditCardEvent) evt;
        return (parameters.isRejectedIncluded() || !ccEvt.isRejectedStatus())
                && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), ccEvt.getDataSourceId()))
                && Objects.equals(parameters.getBinPrefix(), ccEvt.getBinPrefix());
    }

    private boolean isRefreshRequired(CreditCardFileSearchParams parameters, DAOEvent evt) {
        if (!(evt instanceof CreditCardEvent)) {
            return false;
        }

        CreditCardEvent ccEvt = (CreditCardEvent) evt;
        return (parameters.isRejectedIncluded() || !ccEvt.isRejectedStatus())
                && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), ccEvt.getDataSourceId()));
    }

    /**
     * Handles fetching and paging of credit cards by bin.
     */
    public static class CreditCardByBinFetcher extends DAOFetcher<CreditCardBinSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public CreditCardByBinFetcher(CreditCardBinSearchParams params) {
            super(params);
        }

        protected CreditCardDAO getDAO() {
            return MainDAO.getInstance().getCreditCardDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getCreditCardByBin(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isRefreshRequired(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of credit cards by file.
     */
    public static class CreditCardByFileFetcher extends DAOFetcher<CreditCardFileSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public CreditCardByFileFetcher(CreditCardFileSearchParams params) {
            super(params);
        }

        protected CreditCardDAO getDAO() {
            return MainDAO.getInstance().getCreditCardDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getCreditCardByFile(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isRefreshRequired(this.getParameters(), evt);
        }
    }
}
