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
    
    public SearchResultsDTO getCreditCardByFile(CreditCardFileSearchParams searchParams) {
//                    String query
//                    = "SELECT blackboard_artifacts.artifact_obj_id " //NON-NLS
//                    + " FROM blackboard_artifacts " //NON-NLS
//                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
//                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
//                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
//                    + "     AND blackboard_attributes.value_text >= '" + bin.getBINStart() + "' AND  blackboard_attributes.value_text < '" + (bin.getBINEnd() + 1) + "'" //NON-NLS
//                    + getFilterByDataSourceClause()
//                    + getRejectedArtifactFilterClause()
//                    + " ORDER BY blackboard_attributes.value_text"; //NON-NLS
//            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
//                    ResultSet rs = results.getResultSet();) {
//                while (rs.next()) {
//                    list.add(skCase.getBlackboard().getDataArtifactById(rs.getLong("artifact_obj_id"))); //NON-NLS
//                }
//            } catch (TskCoreException | SQLException ex) {
//                LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS
//
//            }
    }
    
    private SearchResultsDTO fetchCreditCardByFile(CreditCardFileSearchParams searchParams) {
//            String query
//                    = "SELECT count(blackboard_artifacts.artifact_id ) AS count" //NON-NLS
//                    + " FROM blackboard_artifacts " //NON-NLS
//                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id " //NON-NLS
//                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
//                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
//                    + "     AND blackboard_attributes.value_text >= '" + bin.getBINStart() + "' AND  blackboard_attributes.value_text < '" + (bin.getBINEnd() + 1) + "'" //NON-NLS
//                    + getFilterByDataSourceClause()
//                    + getRejectedArtifactFilterClause();
//            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
//                    ResultSet resultSet = results.getResultSet();) {
//                while (resultSet.next()) {
//                    setDisplayName(getBinRangeString(bin) + " (" + resultSet.getLong("count") + ")"); //NON-NLS
//                }
//            } catch (TskCoreException | SQLException ex) {
//                LOGGER.log(Level.SEVERE, "Error querying for account artifacts.", ex); //NON-NLS
//
//            }
    }
    
    public SearchResultsDTO getCreditCardByBin(CreditCardBinSearchParams searchParams) {
        
    }
    
    public SearchResultsDTO fetchCreditCardByBin(CreditCardBinSearchParams searchParams) {

    }
    
    public TreeResultsDTO<CreditCardBinSearchParams> getCreditCardBinCounts(Long dataSourceId) {
//            String query
//                    = "SELECT blackboard_artifacts.obj_id," //NON-NLS
//                    + "      solr_attribute.value_text AS solr_document_id, "; //NON-NLS
//            if (skCase.getDatabaseType().equals(TskData.DbType.POSTGRESQL)) {
//                query += "      string_agg(blackboard_artifacts.artifact_id::character varying, ',') AS artifact_IDs, " //NON-NLS
//                        + "      string_agg(blackboard_artifacts.review_status_id::character varying, ',') AS review_status_ids, ";
//            } else {
//                query += "      GROUP_CONCAT(blackboard_artifacts.artifact_id) AS artifact_IDs, " //NON-NLS
//                        + "      GROUP_CONCAT(blackboard_artifacts.review_status_id) AS review_status_ids, ";
//            }
//            query += "      COUNT( blackboard_artifacts.artifact_id) AS hits  " //NON-NLS
//                    + " FROM blackboard_artifacts " //NON-NLS
//                    + " LEFT JOIN blackboard_attributes as solr_attribute ON blackboard_artifacts.artifact_id = solr_attribute.artifact_id " //NON-NLS
//                    + "                                AND solr_attribute.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID.getTypeID() //NON-NLS
//                    + " LEFT JOIN blackboard_attributes as account_type ON blackboard_artifacts.artifact_id = account_type.artifact_id " //NON-NLS
//                    + "                                AND account_type.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID() //NON-NLS
//                    + "                                AND account_type.value_text = '" + Account.Type.CREDIT_CARD.getTypeName() + "'" //NON-NLS
//                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
//                    + getFilterByDataSourceClause()
//                    + getRejectedArtifactFilterClause()
//                    + " GROUP BY blackboard_artifacts.obj_id, solr_document_id " //NON-NLS
//                    + " ORDER BY hits DESC ";  //NON-NLS
//            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
//                    ResultSet resultSet = results.getResultSet();) {
//                while (resultSet.next()) {
//                    list.add(new Accounts.FileWithCCN(
//                            resultSet.getLong("obj_id"), //NON-NLS
//                            resultSet.getString("solr_document_id"), //NON-NLS
//                            unGroupConcat(resultSet.getString("artifact_IDs"), Long::valueOf), //NON-NLS
//                            resultSet.getLong("hits"), //NON-NLS
//                            new HashSet<>(unGroupConcat(resultSet.getString("review_status_ids"), reviewStatusID -> BlackboardArtifact.ReviewStatus.withID(Integer.valueOf(reviewStatusID))))));  //NON-NLS
//                }
//            } catch (TskCoreException | SQLException ex) {
//                LOGGER.log(Level.SEVERE, "Error querying for files with ccn hits.", ex); //NON-NLS
//
//            }
//            return true;
    }
    
    public TreeResultsDTO<CreditCardFileSearchParams> getCreditCardFileCounts(Long dataSourceId) {
//        
//            String query
//                    = "SELECT SUBSTR(blackboard_attributes.value_text,1,8) AS BIN, " //NON-NLS
//                    + "     COUNT(blackboard_artifacts.artifact_id) AS count " //NON-NLS
//                    + " FROM blackboard_artifacts " //NON-NLS
//                    + "      JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id" //NON-NLS
//                    + " WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() //NON-NLS
//                    + "     AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID() //NON-NLS
//                    + getFilterByDataSourceClause()
//                    + getRejectedArtifactFilterClause()
//                    + " GROUP BY BIN " //NON-NLS
//                    + " ORDER BY BIN "; //NON-NLS
//            try (SleuthkitCase.CaseDbQuery results = skCase.executeQuery(query);
//                    ResultSet resultSet = results.getResultSet();) {
//                //sort all te individual bins in to the ranges
//                while (resultSet.next()) {
//                    final Integer bin = Integer.valueOf(resultSet.getString("BIN"));
//                    long count = resultSet.getLong("count");
//
//                    BINRange binRange = (BINRange) CreditCards.getBINInfo(bin);
//                    Accounts.BinResult previousResult = binRanges.get(bin);
//
//                    if (previousResult != null) {
//                        binRanges.remove(Range.closed(previousResult.getBINStart(), previousResult.getBINEnd()));
//                        count += previousResult.getCount();
//                    }
//
//                    if (binRange == null) {
//                        binRanges.put(Range.closed(bin, bin), new Accounts.BinResult(count, bin, bin));
//                    } else {
//                        binRanges.put(Range.closed(binRange.getBINstart(), binRange.getBINend()), new Accounts.BinResult(count, binRange));
//                    }
//                }
//                binRanges.asMapOfRanges().values().forEach(list::add);
//            } catch (TskCoreException | SQLException ex) {
//                LOGGER.log(Level.SEVERE, "Error querying for BINs.", ex); //NON-NLS
//            }

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
