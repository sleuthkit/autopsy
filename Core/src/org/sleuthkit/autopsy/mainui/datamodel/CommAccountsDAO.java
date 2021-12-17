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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION_UNITS;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_SIZE;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.events.CommAccountsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the
 * Communication Accounts section.
 */
@Messages({"CommAccountsDAO.fileColumns.noDescription=No Description"})
public class CommAccountsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(CommAccountsDAO.class.getName());
    private final Cache<SearchParams<CommAccountsSearchParams>, SearchResultsDTO> searchParamsCache = 
            CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<CommAccountsEvent> accountCounts = new TreeCounts<>();

    private static CommAccountsDAO instance = null;

    synchronized static CommAccountsDAO getInstance() {
        if (instance == null) {
            instance = new CommAccountsDAO();
        }

        return instance;
    }

    SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getCommAcounts(CommAccountsSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getType() == null) {
            throw new IllegalArgumentException("Must have non-null type");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<CommAccountsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchCommAccountsDTOs(searchParams));
    }

    /**
     * Returns a list of paged artifacts.
     *
     * @param arts         The artifacts.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged artifacts.
     */
    List<BlackboardArtifact> getPaged(List<? extends BlackboardArtifact> arts, SearchParams<?> searchParams) {
        Stream<? extends BlackboardArtifact> pagedArtsStream = arts.stream()
                .sorted(Comparator.comparing((art) -> art.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedArtsStream = pagedArtsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedArtsStream.collect(Collectors.toList());
    }

    long getTotalResultsCount(SearchParams<CommAccountsSearchParams> cacheKey, long currentPageSize) throws TskCoreException, NoCurrentCaseException {
        Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = BlackboardArtifact.Type.TSK_ACCOUNT;

        if ((cacheKey.getStartItem() == 0) // offset is zero AND
                && ((cacheKey.getMaxResultsCount() != null && currentPageSize < cacheKey.getMaxResultsCount()) // number of results is less than max
                || (cacheKey.getMaxResultsCount() == null))) { // OR max number of results was not specified
            return currentPageSize;
        } else {
            if (dataSourceId != null) {
                return blackboard.getArtifactsCount(artType.getTypeID(), dataSourceId);
            } else {
                return blackboard.getArtifactsCount(artType.getTypeID());
            }
        }
    }

    @NbBundle.Messages({"CommAccounts.name.text=Communication Accounts"})
    private SearchResultsDTO fetchCommAccountsDTOs(SearchParams<CommAccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException, SQLException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();
        Account.Type type = cacheKey.getParamData().getType();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        List<BlackboardArtifact> allArtifacts = blackboard.getArtifacts(BlackboardArtifact.Type.TSK_ACCOUNT,
                BlackboardAttribute.Type.TSK_ACCOUNT_TYPE, type.getTypeName(), dataSourceId,
                false);

        // get current page of artifacts
        List<BlackboardArtifact> pagedArtifacts = getPaged(allArtifacts, cacheKey);

        // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
        // load many artifacts/attributes at once.
        blackboard.loadBlackboardAttributes(pagedArtifacts);

        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_ACCOUNT, pagedArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_ACCOUNT, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), allArtifacts.size());
    }

    private static TreeResultsDTO.TreeItemDTO<CommAccountsSearchParams> createAccountTreeItem(Account.Type accountType, Long dataSourceId, TreeResultsDTO.TreeDisplayCount count) {
        return new TreeResultsDTO.TreeItemDTO<>(
                CommAccountsSearchParams.getTypeId(),
                new CommAccountsSearchParams(accountType, dataSourceId),
                accountType.getTypeName(),
                accountType.getDisplayName(),
                count);
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
    public TreeResultsDTO<CommAccountsSearchParams> getAccountsCounts(Long dataSourceId) throws ExecutionException {
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

        List<TreeResultsDTO.TreeItemDTO<CommAccountsSearchParams>> accountParams = new ArrayList<>();
        try {
            Set<Account.Type> indeterminateTypes = this.accountCounts.getEnqueued().stream()
                    .filter(evt -> dataSourceId == null || evt.getDataSourceId() == dataSourceId)
                    .map(evt -> evt.getAccountType())
                    .collect(Collectors.toSet());

            getCase().getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String accountTypeName = resultSet.getString("account_type");
                        String accountDisplayName = resultSet.getString("account_display_name");
                        Account.Type accountType = new Account.Type(accountTypeName, accountDisplayName);
                        long count = resultSet.getLong("count");
                        TreeDisplayCount treeDisplayCount = indeterminateTypes.contains(accountType)
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeResultsDTO.TreeDisplayCount.getDeterminate(count);
                        
                        accountParams.add(createAccountTreeItem(accountType, dataSourceId, treeDisplayCount));
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
        this.searchParamsCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(
                this.accountCounts,
                (daoEvt, count) -> createAccountTreeItem(daoEvt.getAccountType(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.accountCounts,
                (daoEvt, count) -> createAccountTreeItem(daoEvt.getAccountType(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt == null) {
            return Collections.emptySet();
        }

        Map<Account.Type, Set<Long>> accountTypeMap = new HashMap<>();

        for (BlackboardArtifact art : dataEvt.getArtifacts()) {
            try {
                if (art.getType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
                    BlackboardAttribute accountTypeAttribute = art.getAttribute(BlackboardAttribute.Type.TSK_ACCOUNT_TYPE);
                    if (accountTypeAttribute == null) {
                        continue;
                    }

                    String accountTypeName = accountTypeAttribute.getValueString();
                    if (accountTypeName == null) {
                        continue;
                    }

                    accountTypeMap.computeIfAbsent(getCase().getCommunicationsManager().getAccountType(accountTypeName), (k) -> new HashSet<>())
                            .add(art.getDataSourceObjectID());
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to fetch artifact category for artifact with id: " + art.getId(), ex);
            }
        }

        // don't do anything else if no relevant events
        if (accountTypeMap.isEmpty()) {
            return Collections.emptySet();
        }

        SubDAOUtils.invalidateKeys(this.searchParamsCache,
                (sp) -> Pair.of(sp.getType(), sp.getDataSourceId()), accountTypeMap);

        List<CommAccountsEvent> accountEvents = new ArrayList<>();
        for (Map.Entry<Account.Type, Set<Long>> entry : accountTypeMap.entrySet()) {
            Account.Type accountType = entry.getKey();
            for (Long dsObjId : entry.getValue()) {
                CommAccountsEvent newEvt = new CommAccountsEvent(accountType, dsObjId);
                accountEvents.add(newEvt);
            }
        }

        Stream<TreeEvent> treeEvents = this.accountCounts.enqueueAll(accountEvents).stream()
                .map(daoEvt -> new TreeEvent(createAccountTreeItem(daoEvt.getAccountType(), daoEvt.getDataSourceId(), TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false));

        return Stream.of(accountEvents.stream(), treeEvents)
                .flatMap(s -> s)
                .collect(Collectors.toSet());
    }

    /**
     * Returns true if the dao event could update the data stored in the
     * parameters.
     *
     * @param parameters The parameters.
     * @param evt        The event.
     *
     * @return True if event invalidates parameters.
     */
    private boolean isCommAcctInvalidating(CommAccountsSearchParams parameters, DAOEvent evt) {
        if (evt instanceof CommAccountsEvent) {
            CommAccountsEvent commEvt = (CommAccountsEvent) evt;
            return (parameters.getType().getTypeName().equals(commEvt.getType()))
                    && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), commEvt.getDataSourceId()));
        } else {
            return false;

        }
    }

    /**
     * Handles fetching and paging of data for communication accounts.
     */
    public static class CommAccountFetcher extends DAOFetcher<CommAccountsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public CommAccountFetcher(CommAccountsSearchParams params) {
            super(params);
        }

        protected CommAccountsDAO getDAO() {
            return MainDAO.getInstance().getCommAccountsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getCommAcounts(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isCommAcctInvalidating(this.getParameters(), evt);
        }
    }
}
