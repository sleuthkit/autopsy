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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.EmailEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the
 * Communication Accounts section.
 */
public class EmailsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(EmailsDAO.class.getName());
    private static final int CACHE_SIZE = Account.Type.PREDEFINED_ACCOUNT_TYPES.size(); // number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<EmailSearchParams>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<EmailEvent> emailCounts = new TreeCounts<>();

    private static EmailsDAO instance = null;

    synchronized static EmailsDAO getInstance() {
        if (instance == null) {
            instance = new EmailsDAO();
        }

        return instance;
    }

    SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getCommAcounts(EmailSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {

    }

    @NbBundle.Messages({"CommAccounts.name.text=Communication Accounts"})
    private SearchResultsDTO fetchCommAccountsDTOs(SearchParams<EmailSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException, SQLException {

    }

    private static TreeResultsDTO.TreeItemDTO<EmailSearchParams> createEmailTreeItem(String account, String folder, Long dataSourceId, TreeResultsDTO.TreeDisplayCount count) {

    }
    
    
    
    
//        switch (skCase.getDatabaseType()) {
//        case POSTGRESQL:
//            mimeType = "SPLIT_PART(mime_type, '/', 1)";
//            break;
//        case SQLITE:
//            mimeType = "SUBSTR(mime_type, 0, instr(mime_type, '/'))";
//            break;
//        default:
//            throw new IllegalArgumentException("Unknown database type: " + skCase.getDatabaseType());
//    }
                            
    
        /**
     * Parse the path of the email msg to get the account name and folder in
     * which the email is contained.
     *
     * @param path - the TSK_PATH to the email msg
     *
     * @return a map containg the account and folder which the email is stored
     *         in
     */
//    public static final Map<String, String> parsePath(String path) {
//        Map<String, String> parsed = new HashMap<>();
//        String[] split = path == null ? new String[0] : path.split(MAIL_PATH_SEPARATOR);
//        if (split.length < 4) {
//            parsed.put(MAIL_ACCOUNT, NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultAcct.text"));
//            parsed.put(MAIL_FOLDER, NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultFolder.text"));
//            return parsed;
//        }
//        parsed.put(MAIL_ACCOUNT, split[2]);
//        parsed.put(MAIL_FOLDER, split[3]);
//        return parsed;
//    }
//    private static final String MAIL_PATH_SEPARATOR = "/";
    
//    public TreeResultsDTO<EmailsSearchParams> getEmailCounts(EmailsSearchParams searchParams) throws ExecutionException {
//        private final Map<String, Map<String, List<Long>>> accounts = new LinkedHashMap<>();
//
//        EmailResults() {
//            update();
//        }
//
//        public Set<String> getAccounts() {
//            synchronized (accounts) {
//                return accounts.keySet();
//            }
//        }
//
//        public Set<String> getFolders(String account) {
//            synchronized (accounts) {
//                return accounts.get(account).keySet();
//            }
//        }
//
//        public List<Long> getArtifactIds(String account, String folder) {
//            synchronized (accounts) {
//                return accounts.get(account).get(folder);
//            }
//        }
        
        
//        String query = "SELECT \n"
//                + "	art.artifact_obj_id AS artifact_obj_id,\n"
//                + "	(SELECT value_text FROM blackboard_attributes attr\n"
//                + "	WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = " + pathAttrId + "\n"
//                + "	LIMIT 1) AS value_text\n"
//                + "FROM \n"
//                + "	blackboard_artifacts art\n"
//                + "	WHERE art.artifact_type_id = " + emailArtifactId + "\n"
//                + ((filteringDSObjId > 0) ? "	AND art.data_source_obj_id = " + filteringDSObjId : "");
//    }
    
    

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
    public TreeResultsDTO<CommAccountsSearchParams> getEmailCounts(Long dataSourceId, String account) throws ExecutionException {
//        String query = "res.account_type AS account_type, MIN(res.account_display_name) AS account_display_name, COUNT(*) AS count\n"
//                + "FROM (\n"
//                + "  SELECT MIN(account_types.type_name) AS account_type, MIN(account_types.display_name) AS account_display_name\n"
//                + "  FROM blackboard_artifacts\n"
//                + "  LEFT JOIN blackboard_attributes ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_id\n"
//                + "  LEFT JOIN account_types ON blackboard_attributes.value_text = account_types.type_name\n"
//                + "  WHERE blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID() + "\n"
//                + "  AND blackboard_attributes.attribute_type_id = " + BlackboardAttribute.Type.TSK_ACCOUNT_TYPE.getTypeID() + "\n"
//                + (dataSourceId != null && dataSourceId > 0 ? "  AND blackboard_artifacts.data_source_obj_id = " + dataSourceId + " " : " ") + "\n"
//                + "  -- group by artifact_id to ensure only one account type per artifact\n"
//                + "  GROUP BY blackboard_artifacts.artifact_id\n"
//                + ") res\n"
//                + "GROUP BY res.account_type\n"
//                + "ORDER BY MIN(res.account_display_name)";
//
//        List<TreeResultsDTO.TreeItemDTO<CommAccountsSearchParams>> accountParams = new ArrayList<>();
//        try {
//            Set<Account.Type> indeterminateTypes = this.accountCounts.getEnqueued().stream()
//                    .filter(evt -> dataSourceId == null || evt.getDataSourceId() == dataSourceId)
//                    .map(evt -> evt.getAccountType())
//                    .collect(Collectors.toSet());
//
//            getCase().getCaseDbAccessManager().select(query, (resultSet) -> {
//                try {
//                    while (resultSet.next()) {
//                        String accountTypeName = resultSet.getString("account_type");
//                        String accountDisplayName = resultSet.getString("account_display_name");
//                        Account.Type accountType = new Account.Type(accountTypeName, accountDisplayName);
//                        long count = resultSet.getLong("count");
//                        TreeDisplayCount treeDisplayCount = indeterminateTypes.contains(accountType)
//                                ? TreeDisplayCount.INDETERMINATE
//                                : TreeResultsDTO.TreeDisplayCount.getDeterminate(count);
//                        
//                        accountParams.add(createAccountTreeItem(accountType, dataSourceId, treeDisplayCount));
//                    }
//                } catch (SQLException ex) {
//                    logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
//                }
//            });
//
//            // return results
//            return new TreeResultsDTO<>(accountParams);
//
//        } catch (NoCurrentCaseException | TskCoreException ex) {
//            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
//        }
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(
                this.emailCounts,
                (daoEvt, count) -> createAccountTreeItem(daoEvt.getAccountType(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.emailCounts,
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

        Map<String, Map<String, Set<Long>>> emailMap = new HashMap<>();

        for (BlackboardArtifact art : dataEvt.getArtifacts()) {
            try {
                if (art.getType().getTypeID() == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
                    BlackboardAttribute accountTypeAttribute = art.getAttribute(BlackboardAttribute.Type.TSK_ACCOUNT_TYPE);
                    if (accountTypeAttribute == null) {
                        continue;
                    }

                    String accountTypeName = accountTypeAttribute.getValueString();
                    if (accountTypeName == null) {
                        continue;
                    }

                    Pair<String, String> accountAndFolder = getAccountAndFolder(art);

                    emailMap
                            .computeIfAbsent(accountAndFolder.getLeft(), (k) -> new HashMap<>())
                            .computeIfAbsent(accountAndFolder.getRight(), (k) -> new HashSet<>())
                            .add(art.getDataSourceObjectID());
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to fetch artifact category for artifact with id: " + art.getId(), ex);
            }
        }

        // don't do anything else if no relevant events
        if (emailMap.isEmpty()) {
            return Collections.emptySet();
        }

        // GVDTODO invalidate keys
        List<EmailEvent> emailEvents = new ArrayList<>();
        for (Entry<String, Map<String, Set<Long>>> accountEntry : emailMap.entrySet()) {
            String acct = accountEntry.getKey();
            for (Entry<String, Set<Long>> folderEntry : accountEntry.getValue().entrySet()) {
                String folder = folderEntry.getKey();
                for (Long dsObjId : entry.getValue()) {
                    EmailEvent newEmailsEvent = ...;
                    String.add(newEvt);
                }
            }

        }

        Stream<TreeEvent> treeEvents = this.emailCounts.enqueueAll(emailEvents).stream()
                .map(daoEvt -> new TreeEvent(createAccountTreeItem(TBD, daoEvt.getDataSourceId(), TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false));

        return Stream.of(emailEvents.stream(), treeEvents)
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
    private boolean isEmailInvalidating(EmailSearchParams parameters, DAOEvent evt) {
        if (evt instanceof EmailEvent) {
            EmailEvent commEvt = (EmailEvent) evt;
            return (parameters.getType().getTypeName().equals(commEvt.getType()))
                    && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), commEvt.getDataSourceId()));
        } else {
            return false;

        }
    }

    private Pair<String, String> getAccountAndFolder(BlackboardArtifact art) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Handles fetching and paging of data for communication accounts.
     */
    public static class EmailFetcher extends DAOFetcher<EmailSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public EmailFetcher(EmailSearchParams params) {
            super(params);
        }

        protected EmailsDAO getDAO() {
            return MainDAO.getInstance().getEmailsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getEmails(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isEmailInvalidating(this.getParameters(), evt);
        }
    }
}
