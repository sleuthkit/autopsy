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

            String query
                    = "SELECT blackboard_artifacts.obj_id," //NON-NLS
                    + "      solr_attribute.value_text AS solr_document_id, "; //NON-NLS
            if (skCase.getDatabaseType().equals(TskData.DbType.POSTGRESQL)) {
                query += "      string_agg(blackboard_artifacts.artifact_id::character varying, ',') AS artifact_IDs, " //NON-NLS
                        + "      string_agg(blackboard_artifacts.review_status_id::character varying, ',') AS review_status_ids, ";
            } else {
                query += "      GROUP_CONCAT(blackboard_artifacts.artifact_id) AS artifact_IDs, " //NON-NLS
                        + "      GROUP_CONCAT(blackboard_artifacts.review_status_id) AS review_status_ids, ";
            }
            query += "      COUNT( blackboard_artifacts.artifact_id) AS hits  " //NON-NLS
                    + " FROM blackboard_artifacts " //NON-NLS
                    + " LEFT JOIN blackboard_attributes as solr_attribute ON blackboard_artifacts.artifact_id = solr_attribute.artifact_id " //NON-NLS
                    + "                                AND solr_attribute.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() //NON-NLS
                    + " LEFT JOIN blackboard_attributes as account_type ON blackboard_artifacts.artifact_id = account_type.artifact_id " //NON-NLS
                    + "                                AND account_type.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
                    + "                                AND account_type.value_text = '" + Account.Type.CREDIT_CARD.getTypeName() + "'" //NON-NLS
                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
                    + getFilterByDataSourceClause()
                    + getRejectedArtifactFilterClause()
                    + " GROUP BY blackboard_artifacts.obj_id, solr_document_id " //NON-NLS
                    + " ORDER BY hits DESC ";  //NON-NLS
    }
    
        
    public TreeResultsDTO<CreditCardFileSearchParams> getCreditCardCounts(Long dataSourceId) {
        // file counts
// SELECT art.obj_id
// 	--(SELECT attr.value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = 121 LIMIT 1) AS account, -- TSK_ACCOUNT_TYPE
// FROM blackboard_artifacts art
// INNER JOIN blackboard_attributes acct ON art.artifact_id = acct.artifact_id
// WHERE art.artifact_type_id = 39 -- TSK_ACCOUNT
// AND acct.attribute_type_id = 121 -- TSK_ACCOUNT_TYPE
// AND acct.value_text = 'CREDIT_CARD' -- Account.Type.CREDIT_CARD.getTypeName()
// GROUP BY art.obj_id
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
