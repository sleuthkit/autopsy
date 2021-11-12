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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the 
 * Communication Accounts section.
 */
@Messages({"CommAccountsDAO.fileColumns.originalName=Source Name",
            "CommAccountsDAO.fileColumns.noDescription=No Description"})
public class CommAccountsDAO {

    private static final int CACHE_SIZE = Account.Type.PREDEFINED_ACCOUNT_TYPES.size(); // number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.CommAccountsDAO_fileColumns_originalName()) // GVDTODO handle translation
            );

    private static CommAccountsDAO instance = null;

    synchronized static CommAccountsDAO getInstance() {
        if (instance == null) {
            instance = new CommAccountsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.CommAccountsDAO_fileColumns_noDescription());
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
     * Returns a list of paged communication accounts results.
     *
     * @param accts         The communication accounts results.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged communication accounts results.
     */
    List<Account> getPaged(List<Account> accts, SearchParams<?> searchParams) {
        Stream<Account> pagedAcctsStream = accts.stream()
                .sorted(Comparator.comparing((acct) -> acct.getAccountID()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedAcctsStream = pagedAcctsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedAcctsStream.collect(Collectors.toList());
    }
    
    /**
     * Get the clause that should be used in order to (not) filter out rejected
     * results from db queries.
     *
     * @return A clause that will or will not filter out rejected artifacts
     *         based on the state of showRejected.
     */
    private String getRejectedArtifactFilterClause(boolean showRejected) {
        return showRejected ? " " : " AND artifacts.review_status_id != " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + " "; // 
    }

    /**
     * Returns the clause to filter artifacts by data source.
     *
     * @return A clause that will or will not filter artifacts by datasource
     *         based on the CasePreferences groupItemsInTreeByDataSource setting
     */
    private String getFilterByDataSourceClause(Long dataSourceId) {
        if (dataSourceId != null && dataSourceId > 0) {
            return "  AND artifacts.data_source_obj_id = " + dataSourceId + " ";
        }
        return " ";
    }

    private final static String COMMUNICATION_ACCOUNTS_QUERY_STRING = "SELECT DISTINCT artifacts.artifact_id AS artifact_id, " //NON-NLS
            + "artifacts.obj_id AS obj_id, artifacts.artifact_obj_id AS artifact_obj_id, artifacts.data_source_obj_id AS data_source_obj_id, artifacts.artifact_type_id AS artifact_type_id, " //NON-NLS
            + " types.type_name AS type_name, types.display_name AS display_name, types.category_type as category_type,"//NON-NLS
            + " artifacts.review_status_id AS review_status_id, " //NON-NLS
            + " data_artifacts.os_account_obj_id as os_account_obj_id, " //NON-NLS
            + " attrs.source AS source "
            + " FROM blackboard_artifacts AS artifacts "
            + " JOIN blackboard_artifact_types AS types " //NON-NLS
            + "		ON artifacts.artifact_type_id = types.artifact_type_id" //NON-NLS
            + " LEFT JOIN tsk_data_artifacts AS data_artifacts "
            + "		ON artifacts.artifact_obj_id = data_artifacts.artifact_obj_id " //NON-NLS
            + " JOIN blackboard_attributes AS attrs "
            + " ON artifacts.artifact_id = attrs.artifact_id " //NON-NLS 
            + " WHERE artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
            + "     AND attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID(); //NON-NLS

    /**
     * Get all artifacts and their attributes for all communication accounts of the type of interest.
     * 
     * @param cacheKey
     * @return 
     */
    String getWhereClause(SearchParams<CommAccountsSearchParams> cacheKey) {
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        Account.Type type = cacheKey.getParamData().getType();
        
        String originalWhereClause
                = COMMUNICATION_ACCOUNTS_QUERY_STRING
                + "     AND attrs.value_text = '" + type.getTypeName() + "'" //NON-NLS
                + getFilterByDataSourceClause(dataSourceId)
                + getRejectedArtifactFilterClause(false); // ELTODO

        String pagedWhereClause = originalWhereClause
                + " ORDER BY artifacts.obj_id ASC"
                + (cacheKey.getMaxResultsCount() != null && cacheKey.getMaxResultsCount() > 0 ? " LIMIT " + cacheKey.getMaxResultsCount() : "")
                + (cacheKey.getStartItem() > 0 ? " OFFSET " + cacheKey.getStartItem() : "");
        return pagedWhereClause;
    }

    long getTotalResultsCount(SearchParams<CommAccountsSearchParams> cacheKey, long currentPageSize) throws TskCoreException, NoCurrentCaseException {
        Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = BlackboardArtifact.Type.TSK_ACCOUNT;
        
        if ( (cacheKey.getStartItem() == 0) // offset is zero AND
            && ( (cacheKey.getMaxResultsCount() != null && currentPageSize < cacheKey.getMaxResultsCount()) // number of results is less than max
                || (cacheKey.getMaxResultsCount() == null)) ) { // OR max number of results was not specified
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
        String pagedWhereClause = getWhereClause(cacheKey);        
        
        SleuthkitCase.CaseDbQuery results = Case.getCurrentCaseThrows().getSleuthkitCase().executeQuery(pagedWhereClause);
        ResultSet rs = results.getResultSet();
        List<RowDTO> fileRows = new ArrayList<>();
        /*
        while (rs.next()) {
            tempList.add(rs.getLong("artifact_obj_id")); // NON-NLS
        }

        long totalResultsCount = getTotalResultsCount(cacheKey, numResults);


        for (DataArtifact account : arts) {
            Account blackboardTag = (Account) account;
            
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

            fileRows.add(new BlackboardArtifactTagsRowDTO(
                    blackboardTag,
                    cellValues,
                    blackboardTag.getId()));
        }

        return new BaseSearchResultsDTO(BlackboardArtifactTagsRowDTO.getTypeIdForClass(), Bundle.ResultTag_name_text(), RESULT_TAG_COLUMNS, fileRows, 0, allAccounts.size());*/
        return null;
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getCommAccountsDAO().getCommAcounts(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            CommAccountsSearchParams params = this.getParameters();
            String eventType = evt.getPropertyName();

            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * Even with the check above, it is still possible that the
                     * case will be closed in a different thread before this
                     * code executes. If that happens, it is possible for the
                     * event to have a null oldValue.
                     */
                    ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                    if (null != eventData
                            && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
                        return true;
                    }
                } catch (NoCurrentCaseException notUsed) {
                    // Case is closed, do nothing.
                }
            }
            return false;
        }
    }
}
