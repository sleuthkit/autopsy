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

import org.sleuthkit.autopsy.mainui.datamodel.events.DataArtifactEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * DAO for providing data about data artifacts to populate the results viewer.
 */
public class DataArtifactDAO extends BlackboardArtifactDAO {

    private static Logger logger = Logger.getLogger(DataArtifactDAO.class.getName());

    private static DataArtifactDAO instance = null;

    synchronized static DataArtifactDAO getInstance() {
        if (instance == null) {
            instance = new DataArtifactDAO();
        }

        return instance;
    }

    /**
     * @return The set of types that are not shown in the tree.
     */
    public static Set<BlackboardArtifact.Type> getIgnoredTreeTypes() {
        return BlackboardArtifactDAO.getIgnoredTreeTypes();
    }

    private final Cache<SearchParams<? extends BlackboardArtifactSearchParam>, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final TreeCounts<DataArtifactEvent> treeCounts = new TreeCounts<>();

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(SearchParams<BlackboardArtifactSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        String pagedWhereClause = getWhereClause(cacheKey);

        List<BlackboardArtifact> arts = new ArrayList<>();
        arts.addAll(blackboard.getDataArtifactsWhere(pagedWhereClause));
        blackboard.loadBlackboardAttributes(arts);

        long totalResultsCount = getTotalResultsCount(cacheKey, arts.size());

        TableData tableData = createTableData(artType, arts);
        return new DataArtifactTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), totalResultsCount);
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof DataArtifact)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be a data artifact");
        }
        return new DataArtifactRowDTO((DataArtifact) artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id);
    }

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<BlackboardArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return dataArtifactCache.get(searchParams, () -> fetchDataArtifactsForTable(searchParams));
    }

    /**
     * Fetch data artifacts with the given account type from the database.
     *
     * @param searchParams The search params for the account type to fetch.
     *
     * @return The results.
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    private DataArtifactTableSearchResultsDTO fetchAccounts(SearchParams<? extends AccountSearchParams> searchParams) throws NoCurrentCaseException, TskCoreException {

        // TODO improve performance
        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = searchParams.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = searchParams.getParamData().getArtifactType();

        // We currently can't make a query on the set name field because need to use a prepared statement
        String originalWhereClause = " artifacts.artifact_type_id = " + artType.getTypeID() + " ";
        if (dataSourceId != null) {
            originalWhereClause += " AND artifacts.data_source_obj_id = " + dataSourceId + " ";
        }

        String expectedAccountType = searchParams.getParamData().getAccountType();

        List<BlackboardArtifact> allAccounts = new ArrayList<>();
        allAccounts.addAll(blackboard.getDataArtifactsWhere(originalWhereClause));
        blackboard.loadBlackboardAttributes(allAccounts);

        // Filter for the selected set
        List<BlackboardArtifact> arts = new ArrayList<>();
        for (BlackboardArtifact art : allAccounts) {
            BlackboardAttribute accountTypeAttr = art.getAttribute(BlackboardAttribute.Type.TSK_ACCOUNT_TYPE);
            if ((expectedAccountType == null && accountTypeAttr == null)
                    || (expectedAccountType != null && accountTypeAttr != null && expectedAccountType.equals(accountTypeAttr.getValueString()))) {
                arts.add(art);
            }
        }

        List<BlackboardArtifact> pagedArtifacts = getPaged(arts, searchParams);
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new DataArtifactTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, searchParams.getStartItem(), arts.size());
    }

    /**
     * Gets the cached data or fetched data for the given account search params.
     *
     * @param searchParams The search params.
     * @param startItem    The starting item.
     * @param maxCount     The maximum count of items to return.
     *
     * @return The resulting data.
     *
     * @throws ExecutionException
     * @throws IllegalArgumentException
     */
    public DataArtifactTableSearchResultsDTO getAccountsForTable(AccountSearchParams searchParams, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (searchParams.getDataSourceId() != null && searchParams.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Data source id must be null or > 0.",
                    searchParams.getDataSourceId() == null ? "<null>" : searchParams.getDataSourceId()));
        }

        SearchParams<AccountSearchParams> pagedSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return dataArtifactCache.get(pagedSearchParams, () -> fetchAccounts(pagedSearchParams));
    }

    private boolean isDataArtifactInvalidating(DataArtifactSearchParam key, DAOEvent eventData) {
        if (!(eventData instanceof DataArtifactEvent)) {
            return false;
        } else {
            DataArtifactEvent dataArtEvt = (DataArtifactEvent) eventData;
            return key.getArtifactType().getTypeID() == dataArtEvt.getArtifactType().getTypeID()
                    && (key.getDataSourceId() == null || (key.getDataSourceId() == dataArtEvt.getDataSourceId()));
        }
    }

    public void dropDataArtifactCache() {
        dataArtifactCache.invalidateAll();
    }

    /**
     * Returns a search results dto containing rows of counts data.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The results where rows are CountsRowDTO of
     *         DataArtifactSearchParam.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<DataArtifactSearchParam> getDataArtifactCounts(Long dataSourceId) throws ExecutionException {
        try {
            // get row dto's sorted by display name
            Set<BlackboardArtifact.Type> indeterminateTypes = this.treeCounts.getEnqueued().stream()
                    .filter(evt -> dataSourceId == null || evt.getDataSourceId() == dataSourceId)
                    .map(evt -> evt.getArtifactType())
                    .collect(Collectors.toSet());

            Map<BlackboardArtifact.Type, Long> typeCounts = getCounts(BlackboardArtifact.Category.DATA_ARTIFACT, dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<DataArtifactSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return createTreeItem(entry.getKey(), dataSourceId,
                                indeterminateTypes.contains(entry.getKey())
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(entry.getValue()));
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    /**
     * Returns the accounts and their counts in the current data source if a
     * data source id is provided or all accounts if data source id is null.
     *
     * @param dataSourceId The data source id or null for no data source filter.
     *
     * @return The results.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<AccountSearchParams> getAccountsCounts(Long dataSourceId) throws ExecutionException {
        String query = "res.account_type AS account_type, MIN(res.account_display_name) AS account_display_name, COUNT(*) AS count\n"
                + "FROM (\n"
                + "  SELECT MIN(account_types.type_name) AS account_type, MIN(account_types.display_name) AS account_display_name\n"
                + "  FROM blackboard_artifacts\n"
                + "  LEFT JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id\n"
                + "  LEFT JOIN account_types ON blackboard_attributes.value_text = account_types.type_name\n"
                + "  WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() + "\n"
                + "  AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.Type.TSK_ACCOUNT_TYPE.getTypeID() + "\n"
                + (dataSourceId != null && dataSourceId > 0 ? "  AND blackboard_artifacts.data_source_obj_id = " + dataSourceId + " " : " ") + "\n"
                + "  -- group by artifact_id to ensure only one account type per artifact\n"
                + "  GROUP BY blackboard_artifacts.artifact_id\n"
                + ") res\n"
                + "GROUP BY res.account_type\n"
                + "ORDER BY MIN(res.account_display_name)";

        List<TreeItemDTO<AccountSearchParams>> accountParams = new ArrayList<>();
        try {
            getCase().getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String accountType = resultSet.getString("account_type");
                        String accountDisplayName = resultSet.getString("account_display_name");
                        long count = resultSet.getLong("count");
                        accountParams.add(new TreeItemDTO<>(
                                accountType,
                                new AccountSearchParams(accountType, dataSourceId),
                                accountType,
                                accountDisplayName,
                                TreeDisplayCount.getDeterminate(count)));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
                }
            });

            // return results
            return new TreeResultsDTO<>(accountParams);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    @Override
    void clearCaches() {
        this.dataArtifactCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt == null) {
            return Collections.emptySet();
        }

        Map<BlackboardArtifact.Type, Set<Long>> artifactTypeDataSourceMap = dataEvt.getArtifacts().stream()
                .map((art) -> {
                    try {
                        if (BlackboardArtifact.Category.DATA_ARTIFACT.equals(art.getType().getCategory())) {
                            return Pair.of(art.getType(), art.getDataSourceObjectID());
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Unable to fetch artifact category for artifact with id: " + art.getId(), ex);
                    }
                    return null;
                })
                .filter(pr -> pr != null)
                .collect(Collectors.groupingBy(pr -> pr.getKey(), Collectors.mapping(pr -> pr.getValue(), Collectors.toSet())));

        // don't do anything else if no relevant events
        if (artifactTypeDataSourceMap.isEmpty()) {
            return Collections.emptySet();
        }

        // invalidate cache entries that are affected by events
        ConcurrentMap<SearchParams<? extends BlackboardArtifactSearchParam>, DataArtifactTableSearchResultsDTO> concurrentMap = this.dataArtifactCache.asMap();
        concurrentMap.forEach((k, v) -> {
            Set<Long> dsIds = artifactTypeDataSourceMap.get(k.getParamData().getArtifactType());
            if (dsIds != null) {
                Long searchDsId = k.getParamData().getDataSourceId();
                if (searchDsId == null || dsIds.contains(searchDsId)) {
                    concurrentMap.remove(k);
                }
            }
        });

        // gather dao events based on artifacts
        List<DataArtifactEvent> dataArtifactEvents = new ArrayList<>();
        for (Entry<BlackboardArtifact.Type, Set<Long>> entry : artifactTypeDataSourceMap.entrySet()) {
            BlackboardArtifact.Type artType = entry.getKey();
            for (Long dsObjId : entry.getValue()) {
                DataArtifactEvent newEvt = new DataArtifactEvent(artType, dsObjId);
                dataArtifactEvents.add(newEvt);
            }
        }

        List<TreeEvent> newTreeEvents = this.treeCounts.enqueueAll(dataArtifactEvents).stream()
                .map(daoEvt -> new TreeEvent(createTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.INDETERMINATE), false))
                .collect(Collectors.toList());

        return Stream.of(dataArtifactEvents, newTreeEvents)
                .flatMap((lst) -> lst.stream())
                .collect(Collectors.toSet());
    }

    private TreeItemDTO<DataArtifactSearchParam> createTreeItem(BlackboardArtifact.Type artifactType, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeResultsDTO.TreeItemDTO<>(
                BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                new DataArtifactSearchParam(artifactType, dataSourceId),
                artifactType.getTypeID(),
                artifactType.getDisplayName(),
                displayCount);
    }

    @Override
    Set<DAOEvent> handleIngestComplete() {
        return this.treeCounts.flushEvents().stream()
                .map(daoEvt -> new TreeEvent(createTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toSet());
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return this.treeCounts.getEventTimeouts().stream()
                .map(daoEvt -> new TreeEvent(createTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toSet());
    }


    /*
     * Handles fetching and paging of data artifacts.
     */
    public static class DataArtifactFetcher extends DAOFetcher<DataArtifactSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public DataArtifactFetcher(DataArtifactSearchParam params) {
            super(params);
        }

        protected DataArtifactDAO getDAO() {
            return MainDAO.getInstance().getDataArtifactsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getDataArtifactsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isDataArtifactInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of account data artifacts.
     */
    public static class DataArtifactAccountFetcher extends DAOFetcher<AccountSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public DataArtifactAccountFetcher(AccountSearchParams params) {
            super(params);
        }

        protected DataArtifactDAO getDAO() {
            return MainDAO.getInstance().getDataArtifactsDAO();
        }


        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getAccountsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            // TODO
            return false;
        }
    }
}
