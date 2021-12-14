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
import com.google.common.collect.Range;
import java.beans.PropertyChangeEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.accounts.BINRange;
import org.sleuthkit.autopsy.mainui.datamodel.events.CommAccountsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * DAO for fetching credit card information.
 */
public class CreditCardDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(CreditCardDAO.class.getName());
    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    
    private final Cache<SearchParams<Object>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<CommAccountsEvent> accountCounts = new TreeCounts<>();

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
    
    public SearchResultsDTO getCreditCardByFile(CreditCardFileSearchParams searchParams) {

    }
    
    private SearchResultsDTO fetchCreditCardByFile(CreditCardFileSearchParams searchParams) {
//SELECT 
//	f.name, 
//	art.obj_id, 
//	solr_doc.value_text AS solr_document_id,
//	GROUP_CONCAT(art.artifact_id) AS art_ids,
//	GROUP_CONCAT(DISTINCT(art.review_status_id)) AS review_status_ids,
//	COUNT(*) AS accounts
//FROM blackboard_artifacts art
//INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id 
//	AND acct.attribute_type_id = 121 -- TSK_ACCOUNT_TYPE
//	AND acct.value_text = 'CREDIT_CARD' -- Account.Type.CREDIT_CARD.getTypeName()
//LEFT JOIN blackboard_attributes solr_doc ON art.artifact_id = solr_doc.artifact_id 
//	AND solr_doc.attribute_type_id = 114 -- TSK_KEYWORD_SEARCH_DOCUMENT_ID
//LEFT JOIN tsk_files f ON art.obj_id = f.obj_id
//WHERE art.artifact_type_id = 39 -- TSK_ACCOUNT
//-- AND art.data_source_obj_id = ?
//-- include if showRejected status
//--AND art.review_status_id <> 2 -- BlackboardArtifact.ReviewStatus.REJECTED.getID()
//GROUP BY art.obj_id, solr_doc.value_text
//LIMIT 3
//OFFSET 0
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
    
    public SearchResultsDTO getCreditCardByBin(CreditCardBinSearchParams searchParams) {
        
    }
    
    public SearchResultsDTO fetchCreditCardByBin(CreditCardBinSearchParams searchParams) {
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
