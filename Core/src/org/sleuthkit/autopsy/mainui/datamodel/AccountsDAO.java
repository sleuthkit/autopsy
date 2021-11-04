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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the OS
 * Accounts section.
 */
@Messages({
    "AccountsDAO_accountNameProperty_name=Name",
    "AccountsDAO_accountNameProperty_displayName=Name",
    "AccountsDAO_accountNameProperty_desc=Os Account name",
    "AccountsDAO_accountRealmNameProperty_name=RealmName",
    "AccountsDAO_accountRealmNameProperty_displayName=Realm Name",
    "AccountsDAO_accountRealmNameProperty_desc=OS Account Realm Name",
    "AccountsDAO_accountHostNameProperty_name=HostName",
    "AccountsDAO_accountHostNameProperty_displayName=Host",
    "AccountsDAO_accountHostNameProperty_desc=OS Account Host Name",
    "AccountsDAO_accountScopeNameProperty_name=ScopeName",
    "AccountsDAO_accountScopeNameProperty_displayName=Scope",
    "AccountsDAO_accountScopeNameProperty_desc=OS Account Scope Name",
    "AccountsDAO_createdTimeProperty_name=creationTime",
    "AccountsDAO_createdTimeProperty_displayName=Creation Time",
    "AccountsDAO_createdTimeProperty_desc=OS Account Creation Time",
    "AccountsDAO_loginNameProperty_name=loginName",
    "AccountsDAO_loginNameProperty_displayName=Login Name",
    "AccountsDAO_loginNameProperty_desc=OS Account login name",
    "AccountsDAO.createSheet.score.name=S",
    "AccountsDAO.createSheet.score.displayName=S",
    "AccountsDAO.createSheet.count.name=O",
    "AccountsDAO.createSheet.count.displayName=O",
    "AccountsDAO.createSheet.comment.name=C",
    "AccountsDAO.createSheet.comment.displayName=C"
})
public class AccountsDAO {

    private static final int CACHE_SIZE = 5; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final String RESULT_TAG_TYPE_ID = "RESULT_TAG";
    
    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_originalName()), // GVDTODO handle translation
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_filePathColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_commentColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_modifiedTimeColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_changeTimeColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_accessTimeColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_createdTimeColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_sizeColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_md5HashColLbl()),
            getFileColumnKey(Bundle.AccountsDAO_fileColumns_userNameColLbl()));

    private static AccountsDAO instance = null;

    synchronized static AccountsDAO getInstance() {
        if (instance == null) {
            instance = new AccountsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.AccountsDAO_fileColumns_noDescription());
    }
    
    public SearchResultsDTO getAccounts(AccountsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getTagName() == null) {
            throw new IllegalArgumentException("Must have non-null tag name");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        } else if (key.getTagType() == null) {
            throw new IllegalArgumentException("Must have non-null tag type");
        }
        
        SearchParams<AccountsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchTagsDTOs(searchParams));
    }   

    @NbBundle.Messages({"FileTag.name.text=File Tag",
            "ResultTag.name.text=Result Tag"})
    private SearchResultsDTO fetchTagsDTOs(SearchParams<AccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {
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

    private SearchResultsDTO fetchResultTags(SearchParams<AccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        TagName tagName = cacheKey.getParamData().getTagName();
        
        // get all tag results
        List<BlackboardArtifactTag> allTags = new ArrayList<>();
        List<BlackboardArtifactTag> artifactTags = (dataSourceId != null && dataSourceId > 0)
                ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName, dataSourceId)
                : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName);
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
                    null, // GVDTODO translation column
                    contentPath,
                    blackboardTag.getArtifact().getDisplayName(),
                    blackboardTag.getComment(),
                    blackboardTag.getUserName());

            fileRows.add(new BaseRowDTO(
                    cellValues,
                    RESULT_TAG_TYPE_ID,
                    blackboardTag.getId()));
        }

        return new BaseSearchResultsDTO(RESULT_TAG_TYPE_ID, Bundle.ResultTag_name_text(), RESULT_TAG_COLUMNS, fileRows, 0, allTags.size());
    }
    
    /**
     * Handles fetching and paging of data for accounts.
     */
    public static class AccountFetcher extends DAOFetcher<AccountsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AccountFetcher(AccountsSearchParams params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getAccountsDAO().getAccounts(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.OS_ACCOUNTS_ADDED.toString())
                        || eventType.equals(Case.Events.OS_ACCOUNTS_DELETED.toString())) {
                return true;
            }
            return false;
        }
    }
}
