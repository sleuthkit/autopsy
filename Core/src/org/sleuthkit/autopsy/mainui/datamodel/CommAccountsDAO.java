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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
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
        return showRejected ? " " : " AND blackboard_artifacts.review_status_id != " + BlackboardArtifact.ReviewStatus.REJECTED.getID() + " "; //NON-NLS
    }

    /**
     * Returns the clause to filter artifacts by data source.
     *
     * @return A clause that will or will not filter artifacts by datasource
     *         based on the CasePreferences groupItemsInTreeByDataSource setting
     */
    private String getFilterByDataSourceClause(Long dataSourceId) {
        if (dataSourceId != null && dataSourceId > 0) {
            return "  AND blackboard_artifacts.data_source_obj_id = " + dataSourceId + " ";
        }
        return " ";
    }    
    
    String getWhereClause(SearchParams<CommAccountsSearchParams> cacheKey) {
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        Account.Type type = cacheKey.getParamData().getType();
        
        String originalWhereClause
                = " blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
                + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                + "     AND blackboard_attributes.value_text = '" + type + "'" //NON-NLS
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
    private SearchResultsDTO fetchCommAccountsDTOs(SearchParams<CommAccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();

        String pagedWhereClause = getWhereClause(cacheKey);
        
        List<BlackboardArtifact> arts = new ArrayList<>();
        arts.addAll(blackboard.getDataArtifactsWhere(pagedWhereClause));
        blackboard.loadBlackboardAttributes(arts);
        
        long totalResultsCount = getTotalResultsCount(cacheKey, arts.size());
        BlackboardArtifactDAO.TableData tableData = DataArtifactDAO.getInstance().createTableData(BlackboardArtifact.Type.TSK_ACCOUNT, arts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_ACCOUNT, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), totalResultsCount);

        /*List<RowDTO> fileRows = new ArrayList<>();
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
            /*
            // handle artifact/result account changes
            if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())
                        || eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
                
                // ignore non-artifact/result account changes
                if (params.getTagType() != TagsSearchParams.TagType.RESULT) {
                    return false;
                }
                
                if (evt instanceof AutopsyEvent) {
                    if (evt instanceof BlackBoardArtifactTagAddedEvent) {
                        // An artifact associated with the current case has been tagged.
                        BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
                        // ensure account added event has a valid content id
                        if (event.getAddedTag() == null || event.getAddedTag().getContent() == null || event.getAddedTag().getArtifact() == null) {
                            return false;
                        }
                        return params.getTagName().getId() == event.getAddedTag().getId();
                    } else if (evt instanceof BlackBoardArtifactTagDeletedEvent) {
                        // A account has been removed from an artifact associated with the current case.
                        BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
                        // ensure account deleted event has a valid content id
                        BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo deletedTagInfo = event.getDeletedTagInfo();
                        if (deletedTagInfo == null) {
                            return false;
                        }
                        return params.getTagName().getId() == deletedTagInfo.getTagID();
                    }
                }
            }
            
            // handle file/content account changes
            if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())
                    || eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                
                // ignore non-file/content account changes
                if (params.getTagType() != TagsSearchParams.TagType.FILE) {
                    return false;
                }

                if (evt instanceof AutopsyEvent) {
                    if (evt instanceof ContentTagAddedEvent) {
                        // Content associated with the current case has been tagged.
                        ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
                        // ensure account added event has a valid content id
                        if (event.getAddedTag() == null || event.getAddedTag().getContent() == null) {
                            return false;
                        }
                        return params.getTagName().getId() == event.getAddedTag().getId();
                    } else if (evt instanceof ContentTagDeletedEvent) {
                        // A account has been removed from content associated with the current case.
                        ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                        // ensure account deleted event has a valid content id
                        ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
                        if (deletedTagInfo == null) {
                            return false;
                        }                        
                        return params.getTagName().getId() == deletedTagInfo.getTagID();
                    }
                }
            }*/
            return false;
        }
    }
}
