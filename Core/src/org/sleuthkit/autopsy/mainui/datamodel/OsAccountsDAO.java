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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.python.google.common.collect.ImmutableSet;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.OsAccountEvent;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.OsAccountRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the OS
 * Accounts section.
 */
@Messages({
    "OsAccountsDAO_accountNameProperty_displayName=Name",
    "OsAccountsDAO_accountRealmNameProperty_displayName=Realm Name",
    "OsAccountsDAO_accountHostNameProperty_displayName=Host",
    "OsAccountsDAO_accountScopeNameProperty_displayName=Scope",
    "OsAccountsDAO_createdTimeProperty_displayName=Creation Time",
    "OsAccountsDAO_loginNameProperty_displayName=Login Name",
    "OsAccountsDAO.createSheet.score.displayName=S",
    "OsAccountsDAO.createSheet.comment.displayName=C",
    "OsAccountsDAO.createSheet.count.displayName=O",
    "OsAccountsDAO.fileColumns.noDescription=No Description",})
public class OsAccountsDAO extends AbstractDAO {

    private static final int CACHE_SIZE = 5; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private static final String OS_ACCOUNTS_TYPE_ID = "OS_ACCOUNTS";

    private static final List<ColumnKey> OS_ACCOUNTS_WITH_SCO_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.OsAccountsDAO_accountNameProperty_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_createSheet_score_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_createSheet_comment_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_createSheet_count_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_loginNameProperty_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_accountHostNameProperty_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_accountScopeNameProperty_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_accountRealmNameProperty_displayName()),
            getFileColumnKey(Bundle.OsAccountsDAO_createdTimeProperty_displayName()));

    private static final Set<String> OS_EVENTS = ImmutableSet.of(
            Case.Events.OS_ACCOUNTS_ADDED.toString(),
            Case.Events.OS_ACCOUNTS_DELETED.toString(),
            Case.Events.OS_ACCOUNTS_UPDATED.toString(),
            Case.Events.OS_ACCT_INSTANCES_ADDED.toString()
    );

    private static OsAccountsDAO instance = null;

    synchronized static OsAccountsDAO getInstance() {
        if (instance == null) {
            instance = new OsAccountsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.OsAccountsDAO_fileColumns_noDescription());
    }

    public SearchResultsDTO getAccounts(OsAccountsSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("Search parameters are null");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<OsAccountsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchAccountsDTOs(searchParams));
    }

    private boolean isOSAccountInvalidatingEvt(OsAccountsSearchParams searchParams, DAOEvent evt) {
        return evt instanceof OsAccountEvent;
    }

    /**
     * Returns a list of paged OS Accounts results.
     *
     * @param accounts     The OS Accounts results.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged OS Accounts results.
     */
    List<OsAccount> getPaged(List<OsAccount> accounts, SearchParams<?> searchParams) {
        Stream<OsAccount> pagedAccountsStream = accounts.stream()
                .sorted(Comparator.comparing((acct) -> acct.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedAccountsStream = pagedAccountsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedAccountsStream.collect(Collectors.toList());
    }

    @NbBundle.Messages({"OsAccounts.name.text=OS Accounts"})
    private SearchResultsDTO fetchAccountsDTOs(SearchParams<OsAccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();

        // get all accounts
        List<OsAccount> allAccounts = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getSleuthkitCase().getOsAccountManager().getOsAccountsByDataSourceObjId(dataSourceId)
                : Case.getCurrentCaseThrows().getSleuthkitCase().getOsAccountManager().getOsAccounts();

        // get current page of accounts results
        List<OsAccount> pagedAccounts = getPaged(allAccounts, cacheKey);

        List<RowDTO> fileRows = new ArrayList<>();
        for (OsAccount account : pagedAccounts) {

            Optional<String> optional = account.getLoginName();
            Optional<Long> creationTimeValue = account.getCreationTime();
            String timeDisplayStr
                    = creationTimeValue.isPresent() ? TimeZoneUtils.getFormattedTime(creationTimeValue.get()) : "";
            List<Object> cellValues = Arrays.asList(
                    account.getName() != null ? account.getName() : "",
                    null,
                    null,
                    null,
                    optional.isPresent() ? optional.get() : "",
                    "",
                    "",
                    "",
                    timeDisplayStr);

            fileRows.add(new OsAccountRowDTO(
                    account,
                    cellValues));
        };

        return new BaseSearchResultsDTO(OS_ACCOUNTS_TYPE_ID, Bundle.OsAccounts_name_text(), OS_ACCOUNTS_WITH_SCO_COLUMNS, fileRows, OS_ACCOUNTS_TYPE_ID, 0, allAccounts.size());
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
    }

    @Override
    Set<DAOEvent> handleIngestComplete() {
        return Collections.emptySet();
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return Collections.emptySet();
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        if (!OS_EVENTS.contains(evt.getPropertyName())) {
            return Collections.emptySet();
        }

        this.searchParamsCache.invalidateAll();

        return Collections.singleton(new OsAccountEvent());
    }

    /**
     * Handles fetching and paging of data for accounts.
     */
    public static class AccountFetcher extends DAOFetcher<OsAccountsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AccountFetcher(OsAccountsSearchParams params) {
            super(params);
        }

        protected OsAccountsDAO getDAO() {
            return MainDAO.getInstance().getOsAccountsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getAccounts(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isOSAccountInvalidatingEvt(this.getParameters(), evt);
        }
    }
}
