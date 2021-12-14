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
import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * DAO for fetching credit card information.
 */
public class CreditCardDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(CreditCardDAO.class.getName());
    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;

    private final Cache<SearchParams<? extends CreditCardSearchParams>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<CreditCardSearchParams> creditCardTree = new TreeCounts<>();

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

        SearchParams<CreditCardFileSearchParams> pagedSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return searchParamsCache.get(pagedSearchParams, () -> fetchCreditCardByFile(pagedSearchParams));
    }

    private SearchResultsDTO fetchCreditCardByFile(SearchParams<CreditCardFileSearchParams> searchParams) {
        boolean includeRejected = searchParams.getParamData().isIncludeRejected();
        
        String countQuery = "";
        
        String query = "SELECT \n"
                + "  art.obj_id file_id, \n"
                + "  solr_doc.value_text AS solr_document_id,\n"
                + "  GROUP_CONCAT(art.artifact_id) AS art_ids,\n"
                + "  GROUP_CONCAT(DISTINCT(art.review_status_id)) AS review_status_ids,\n"
                + "  COUNT(*) AS accounts\n"
                + "FROM blackboard_artifacts art\n"
                + "INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id \n"
                + "  AND acct.attribute_type_id = " + BlackboardAttribute.Type.TSK_ACCOUNT_TYPE.getTypeID() + "\n"
                + "  AND acct.value_text = " +  Account.Type.CREDIT_CARD.getTypeName()+ "\n"
                + "LEFT JOIN blackboard_attributes solr_doc ON art.artifact_id = solr_doc.artifact_id \n"
                + "  AND solr_doc.attribute_type_id = " + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() + "\n"
                + "LEFT JOIN tsk_files f ON art.obj_id = f.obj_id\n"
                + "WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() + "\n"
                + "-- AND art.data_source_obj_id = ?\n"

                + "--AND art.review_status_id <> 2 -- BlackboardArtifact.ReviewStatus.REJECTED.getID()\n"
                + "GROUP BY art.obj_id, solr_doc.value_text\n"
                + "LIMIT ?\n"
                + "OFFSET ?";
    }

    public TreeResultsDTO<CreditCardFileSearchParams> getCreditCardCounts(Long dataSourceId) {
        // file counts
// SELECT art.obj_id
// FROM blackboard_artifacts art
// INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id
// WHERE art.artifact_type_id = 39 -- TSK_ACCOUNT
// AND acct.attribute_type_id = 121 -- TSK_ACCOUNT_TYPE
// AND acct.value_text = 'CREDIT_CARD' -- Account.Type.CREDIT_CARD.getTypeName()
// GROUP BY art.obj_id

// bin counts
//SELECT COUNT(DISTINCT(art.artifact_id)) AS count
//FROM blackboard_artifacts art
//LEFT JOIN blackboard_attributes attr ON art.artifact_id = attr.artifact_id
//WHERE art.artifact_type_id = 39 -- TSK_ACCOUNT
//AND attr.attribute_type_id = 109 -- TSK_CARD_NUMBER
//-- AND art.data_source_obj_id = ?
//-- include if showRejected status
//AND art.review_status_id <> 2 -- BlackboardArtifact.ReviewStatus.REJECTED.getID()
    }

    public SearchResultsDTO getCreditCardByBin(CreditCardBinSearchParams searchParams, long startItem, Long maxCount) throws IllegalArgumentException, ExecutionException {
        if (startItem < 0 || (maxCount != null && maxCount < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Start item and max count need to be >= 0 but were [startItem: {0}, maxCount: {1}]",
                    startItem,
                    maxCount == null ? "<null>" : maxCount));
        }

        SearchParams<CreditCardBinSearchParams> pagedSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return searchParamsCache.get(pagedSearchParams, () -> fetchCreditCardByBin(pagedSearchParams));
    }

    public SearchResultsDTO fetchCreditCardByBin(SearchParams<CreditCardBinSearchParams> searchParams) {
//SELECT art.artifact_id
//FROM blackboard_artifacts art
//LEFT JOIN blackboard_attributes attr ON art.artifact_id = attr.artifact_id
//WHERE art.artifact_type_id = 39 -- TSK_ACCOUNT
//AND attr.attribute_type_id = 109 -- TSK_CARD_NUMBER
//-- AND art.data_source_obj_id = ?
//-- include if showRejected status
//AND art.review_status_id <> 2 -- BlackboardArtifact.ReviewStatus.REJECTED.getID()
//AND attr.value_text LIKE '20140106%'
//GROUP BY art.artifact_id
//ORDER BY art.artifact_id
//LIMIT 1
//OFFSET 0
    }

    public TreeResultsDTO<CreditCardBinSearchParams> getCreditCardBinCounts(Long dataSourceId) {

    }

    // is account invalidating
    @Override
    void clearCaches() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Set<? extends TreeEvent> shouldRefreshTree() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
