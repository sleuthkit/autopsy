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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
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

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactSearchParam artifactKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<BlackboardArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        if (hardRefresh) {
            this.dataArtifactCache.invalidate(searchParams);
        }

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
        allAccounts.addAll(blackboard.getAnalysisResultsWhere(originalWhereClause));
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

    public boolean isDataArtifactInvalidating(DataArtifactSearchParam key, ModuleDataEvent eventData) {
        return key.getArtifactType().equals(eventData.getBlackboardArtifactType());
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
            Map<BlackboardArtifact.Type, Long> typeCounts = getCounts(BlackboardArtifact.Category.DATA_ARTIFACT, dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<DataArtifactSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return new TreeResultsDTO.TreeItemDTO<>(
                                BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                                new DataArtifactSearchParam(entry.getKey(), dataSourceId),
                                entry.getKey().getTypeID(),
                                entry.getKey().getDisplayName(),
                                entry.getValue());
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
        String query = "SELECT res.account_type AS account_type, MIN(res.account_display_name) AS account_display_name, COUNT(*) AS count\n"
                + "FROM (\n"
                + "  SELECT MIN(account_types.type_name) AS account_type, MIN(account_types.display_name) AS account_display_name\n"
                + "  FROM blackboard_artifacts\n"
                + "  LEFT JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id\n"
                + "  LEFT JOIN account_types ON blackboard_artifacts.value_text = account_types.type_name\n"
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
                                count));
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getDataArtifactsDAO().getDataArtifactsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            ModuleDataEvent dataEvent = this.getModuleDataFromEvt(evt);
            if (dataEvent == null) {
                return false;
            }

            return MainDAO.getInstance().getDataArtifactsDAO().isDataArtifactInvalidating(this.getParameters(), dataEvent);
        }
    }

    public static class AccountFetcher extends DAOFetcher<AccountSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AccountFetcher(AccountSearchParams params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getDataArtifactsDAO().getAccountsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            // TODO
            return false;
        }
    }
}
