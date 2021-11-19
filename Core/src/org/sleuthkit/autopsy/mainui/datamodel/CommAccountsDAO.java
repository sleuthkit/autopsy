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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.CommAccountsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
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
    private static final int CACHE_SIZE = Account.Type.PREDEFINED_ACCOUNT_TYPES.size(); // number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private static CommAccountsDAO instance = null;

    synchronized static CommAccountsDAO getInstance() {
        if (instance == null) {
            instance = new CommAccountsDAO();
        }

        return instance;
    }

    public SearchResultsDTO getCommAcounts(CommAccountsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getType() == null) {
            throw new IllegalArgumentException("Must have non-null type");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<CommAccountsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

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
                false); // GVDTODO handle approved/rejected account actions

        // get current page of artifacts
        List<BlackboardArtifact> pagedArtifacts = getPaged(allArtifacts, cacheKey);

        // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
        // load many artifacts/attributes at once.
        blackboard.loadBlackboardAttributes(pagedArtifacts);

        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_ACCOUNT, pagedArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_ACCOUNT, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), allArtifacts.size());
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();;
    }

    @Override
    List<DAOEvent> handleAutopsyEvent(Collection<PropertyChangeEvent> evts) {
        // maps account type to the data sources affected
        Map<String, Set<Long>> commAccountsAffected = new HashMap<>();
        try {
            for (PropertyChangeEvent evt : evts) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                    if (null != eventData
                            && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {

                        // check that the update is for the same account type
                        for (BlackboardArtifact artifact : eventData.getArtifacts()) {
                            BlackboardAttribute typeAttr = artifact.getAttribute(BlackboardAttribute.Type.TSK_ACCOUNT_TYPE);
                            commAccountsAffected.computeIfAbsent(typeAttr.getValueString(), (k) -> new HashSet<>())
                                    .add(artifact.getDataSourceObjectID());
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to properly handle module data event.", ex);
        }

        // invalidate cache entries that are affected by events
        ConcurrentMap<SearchParams<?>, SearchResultsDTO> concurrentMap = this.searchParamsCache.asMap();
        concurrentMap.forEach((k, v) -> {
            Object objectKey = k.getParamData();
            if (objectKey instanceof CommAccountsSearchParams) {
                CommAccountsSearchParams commAcctKey = (CommAccountsSearchParams) objectKey;
                Set<Long> dsIdsAffected = commAccountsAffected.get(commAcctKey.getType().getTypeName());
                if (dsIdsAffected != null
                        && (commAcctKey.getDataSourceId() == null
                        || dsIdsAffected.contains(commAcctKey.getDataSourceId()))) {

                    concurrentMap.remove(k);
                }
            }
        });

        return commAccountsAffected.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new CommAccountsEvent(entry.getKey(), dsId)))
                .collect(Collectors.toList());
    }

    private boolean isCommAcctInvalidating(CommAccountsSearchParams parameters, DAOEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

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
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return getDAO().getCommAcounts(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isCommAcctInvalidating(this.getParameters(), evt);
        }
    }
}
