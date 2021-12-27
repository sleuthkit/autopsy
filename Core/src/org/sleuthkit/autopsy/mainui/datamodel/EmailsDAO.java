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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.python.icu.text.MessageFormat;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION_UNITS;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_SIZE;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.EmailEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides information to populate the results viewer for data in the
 * Emails section.
 */
public class EmailsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(EmailsDAO.class.getName());

    private static final String PATH_DELIMITER = "/";
    private static final String ESCAPE_CHAR = "\\";

    private final Cache<SearchParams<EmailSearchParams>, SearchResultsDTO> searchParamsCache = 
            CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
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

    public SearchResultsDTO getEmailMessages(EmailSearchParams searchParams, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (searchParams.getDataSourceId() != null && searchParams.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        } else if ((searchParams.getAccount() == null) != (searchParams.getFolder() == null)) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Either folder and account are null or they are both non-null.  Received [account: {0}, folder: {1}]",
                            StringUtils.defaultIfBlank(searchParams.getAccount(), "<null>"),
                            StringUtils.defaultIfBlank(searchParams.getFolder(), "<null>")));
        }

        SearchParams<EmailSearchParams> emailSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return searchParamsCache.get(emailSearchParams, () -> fetchEmailMessageDTOs(emailSearchParams));
    }

    /**
     * Returns a pair of the email account and folder.
     *
     * NOTE: Subject to change; see JIRA-8220.
     *
     * @param art The artifact.
     *
     * @return The pair of the account and folder or null if undetermined.
     */
    private static Pair<String, String> getAccountAndFolder(BlackboardArtifact art) throws TskCoreException {
        BlackboardAttribute pathAttr = art.getAttribute(BlackboardAttribute.Type.TSK_PATH);
        if (pathAttr == null) {
            return Pair.of("", "");
        }

        String pathVal = pathAttr.getValueString();
        if (pathVal == null) {
            return Pair.of("", "");
        }

        return getPathAccountFolder(pathVal);
    }

    /**
     * Returns a pair of the email account and folder.
     *
     * NOTE: Subject to change; see JIRA-8220.
     *
     * @param art The path value.
     *
     * @return The pair of the account and folder or null if undetermined.
     */
    private static Pair<String, String> getPathAccountFolder(String pathVal) {
        String[] pieces = pathVal.split(PATH_DELIMITER);
        return pieces.length < 4
                ? Pair.of("", "")
                : Pair.of(pieces[2], pieces[3]);
    }

    private SearchResultsDTO fetchEmailMessageDTOs(SearchParams<EmailSearchParams> searchParams) throws NoCurrentCaseException, TskCoreException, SQLException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();

        boolean unknownPath = StringUtils.isBlank(searchParams.getParamData().getAccount());

        String query = " art.artifact_id AS artifact_id \n"
                + "FROM blackboard_artifacts art \n"
                + "LEFT JOIN blackboard_attributes attr ON attr.artifact_id = art.artifact_id \n"
                + "  AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + " \n"
                + "WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + " \n"
                + (unknownPath
                        ? "AND (attr.value_text IS NULL OR attr.value_text NOT LIKE '%/%/%/%' ESCAPE '" + ESCAPE_CHAR + "' OR attr.value_text LIKE ?) \n"
                        : "AND attr.value_text LIKE ? ESCAPE '" + ESCAPE_CHAR + "' \n")
                + (searchParams.getParamData().getDataSourceId() == null ? "" : "AND art.data_source_obj_id = ? \n")
                + "GROUP BY art.artifact_id \n"
                + "ORDER BY art.artifact_id";

        List<Long> allMatchingIds = new ArrayList<>();

        // TODO load paged artifacts; 
        // this could potentially be done in a query obtaining the artifacts and another retrieving the total count.
        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(query)) {

            int paramIdx = 0;
            preparedStatement.setString(++paramIdx, MessageFormat.format("%/%/{0}/{1}%",
                    SubDAOUtils.likeEscape(searchParams.getParamData().getAccount(), ESCAPE_CHAR),
                    SubDAOUtils.likeEscape(searchParams.getParamData().getFolder(), ESCAPE_CHAR)
            ));

            if (searchParams.getParamData().getDataSourceId() != null) {
                preparedStatement.setLong(++paramIdx, searchParams.getParamData().getDataSourceId());
            }

            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        allMatchingIds.add(resultSet.getLong("artifact_id"));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "There was an error fetching emails for ");
                }

            });
        }
        
        Stream<Long> pagedIdStream = allMatchingIds.stream()
                .skip(searchParams.getStartItem());
        
        if (searchParams.getMaxResultsCount() != null && searchParams.getMaxResultsCount() > 0) {
            pagedIdStream = pagedIdStream.limit(searchParams.getMaxResultsCount());
        }
        
        List<Long> pagedIds = pagedIdStream.collect(Collectors.toList());

        List<BlackboardArtifact> allArtifacts = Collections.emptyList();
        if (!allMatchingIds.isEmpty()) {
            String whereClause = "artifacts.artifact_id IN (" + pagedIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", ")) + ")";
            allArtifacts = getDataArtifactsAsBBA(blackboard, whereClause);

            // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
            // load many artifacts/attributes at once.
            blackboard.loadBlackboardAttributes(allArtifacts);
        }

        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_EMAIL_MSG, allArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_EMAIL_MSG, tableData.columnKeys,
                tableData.rows, searchParams.getStartItem(), allMatchingIds.size());
    }

    @SuppressWarnings("unchecked")
    private List<BlackboardArtifact> getDataArtifactsAsBBA(Blackboard blackboard, String whereClause) throws TskCoreException {
        return (List<BlackboardArtifact>) (List<? extends BlackboardArtifact>) blackboard.getDataArtifactsWhere(whereClause);
    }

    @Messages("EmailsDAO_getAccountDisplayName_defaultName=Default")
    public String getAccountDisplayName(String account, Set<String> folders) {
        String accountName = StringUtils.isBlank(account) ? Bundle.EmailsDAO_getAccountDisplayName_defaultName() : account;
        if (CollectionUtils.isEmpty(folders)) {
            return accountName;
        } else {
            String folderDisplay = folders.stream()
                    .map(f -> StringUtils.isBlank(f) ? Bundle.EmailsDAO_getFolderDisplayName_defaultName() : f)
                    .sorted((a, b) -> a.compareToIgnoreCase(b))
                    .collect(Collectors.joining(", "));

            return MessageFormat.format("{0} ([{1}])", accountName, folderDisplay);
        }

    }

    @Messages({"EmailsDAO_getFolderDisplayName_defaultName=Default"})
    public String getFolderDisplayName(String folder) {
        return StringUtils.isBlank(folder) ? Bundle.EmailsDAO_getFolderDisplayName_defaultName() : folder;
    }

    public TreeItemDTO<EmailSearchParams> createEmailTreeItem(String account, String folder, String displayName,
            Long dataSourceId, TreeDisplayCount count) {

        return new TreeItemDTO<>(
                EmailSearchParams.getTypeId(),
                new EmailSearchParams(dataSourceId, account, folder),
                Stream.of(account, folder)
                        .map(s -> StringUtils.isBlank(s) ? "" : s)
                        .collect(Collectors.joining(PATH_DELIMITER)),
                displayName,
                count
        );
    }

    /**
     * Returns sql to query for email counts.
     *
     * @param dbType       The db type (postgres/sqlite).
     * @param account      The account string to filter on. Null if no filter.
     *                     Empty if account is default.
     * @param dataSourceId The data source id to filter on or null for no
     *                     filter. If non-null, a prepared statement parameter
     *                     will need to be provided at index 2.
     *
     * @return A tuple of the sql and the account like string (or null if no
     *         account filter).
     */
    private static Pair<String, String> getAccountFolderSql(TskData.DbType dbType, String account, Long dataSourceId) {
        // possible and claused depending on whether or not there is an account to filter on and a data source object id to filter on.

        String accountClause = "";
        if (account != null) {
            if (StringUtils.isBlank(account)) {
                accountClause = "      AND (attr.value_text IS NULL OR attr.value_text NOT LIKE '%/%/%/%' OR attr.value_text LIKE ?)\n";
            } else {
                accountClause = "      AND attr.value_text LIKE ? ESCAPE '" + ESCAPE_CHAR + "'\n";
            }
        }

        String dataSourceClause = (dataSourceId == null ? "" : "      AND art.data_source_obj_id = ?\n");

        // get path attribute value for emails 
        String innerQuery = "SELECT\n"
                + "      MIN(attr.value_text) AS path, \n"
                + "      art.artifact_id\n"
                + "    FROM blackboard_artifacts art\n"
                + "    LEFT JOIN blackboard_attributes attr ON attr.artifact_id = art.artifact_id\n"
                + "      AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + "\n" // may change due to JIRA-8220
                + "    WHERE\n"
                + " art.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + "\n"
                + accountClause
                + dataSourceClause
                + "    GROUP BY art.artifact_id\n";

        // get index 2 (account) and index 3 (folder) after splitting on delimiter
        String accountFolderQuery;
        switch (dbType) {
            case POSTGRESQL:
                accountFolderQuery = "SELECT\n"
                        + (account != null ? "" : "  SPLIT_PART(a.path, '" + PATH_DELIMITER + "', 3) AS account,\n")
                        + "  SPLIT_PART(a.path, '" + PATH_DELIMITER + "', 4) AS folder\n"
                        + "FROM (\n"
                        + innerQuery
                        + "\n) a";
                break;
            case SQLITE:
                accountFolderQuery = "SELECT\n"
                        + (account != null ? "" : "  a.account AS account,\n")
                        + "  (CASE \n"
                        + "    WHEN INSTR(a.remaining, '" + PATH_DELIMITER + "') > 0 THEN SUBSTR(a.remaining, 1, INSTR(a.remaining, '" + PATH_DELIMITER + "') - 1) \n"
                        + "    ELSE a.remaining\n"
                        + "  END) AS folder\n"
                        + "FROM (\n"
                        + "  SELECT \n"
                        + "    SUBSTR(l.ltrimmed, 1, INSTR(l.ltrimmed, '" + PATH_DELIMITER + "') - 1) AS account,\n"
                        + "    SUBSTR(l.ltrimmed, INSTR(l.ltrimmed, '" + PATH_DELIMITER + "') + 1) AS remaining\n"
                        + "  FROM (\n"
                        + "      SELECT SUBSTR(email_paths.path, INSTR(SUBSTR(email_paths.path, 2), '" + PATH_DELIMITER + "') + 2) AS ltrimmed\n"
                        + "      FROM (\n"
                        + innerQuery
                        + "      ) email_paths\n"
                        + "  ) l\n"
                        + ") a\n";
                break;
            default:
                throw new IllegalArgumentException("Unknown db type: " + dbType);
        }

        // group and get counts
        String sql = "  COUNT(*) AS count,\n "
                + (account != null ? "" : "  account_folder.account,\n")
                + "  account_folder.folder\n"
                + "FROM (\n"
                + accountFolderQuery
                + "\n) AS account_folder\n"
                + "GROUP BY \n"
                + (account != null ? "" : "  account_folder.account,\n")
                + "  account_folder.folder";

        String accountLikeStr = (account == null)
                ? null
                : "%/%/" + SubDAOUtils.likeEscape(account, ESCAPE_CHAR) + "/%";

        return Pair.of(sql, accountLikeStr);
    }

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
    public TreeResultsDTO<EmailSearchParams> getEmailCounts(Long dataSourceId, String account) throws ExecutionException {

        // track indeterminate types by key (account if account is null, account folders if account parameter is non-null)
        Set<String> indeterminateTypes = this.emailCounts.getEnqueued().stream()
                .filter(evt -> (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                && (account == null || account.equals(evt.getAccount())))
                .map(evt -> account == null ? evt.getAccount() : evt.getFolder())
                .collect(Collectors.toSet());

        String query = null;
        try {
            SleuthkitCase skCase = getCase();
            Pair<String, String> sqlAndLike = getAccountFolderSql(skCase.getDatabaseType(), account, dataSourceId);
            query = sqlAndLike.getLeft();
            String accountLikeStr = sqlAndLike.getRight();
            
            try (CaseDbPreparedStatement preparedStatement = skCase.getCaseDbAccessManager().prepareSelect(query)) {

                int paramIdx = 0;
                if (account != null) {
                    preparedStatement.setString(++paramIdx, accountLikeStr);
                }

                if (dataSourceId != null) {
                    preparedStatement.setLong(++paramIdx, dataSourceId);
                }

                // query for data
                List<EmailCountsData> accumulatedData = new ArrayList<>();
                skCase.getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                    accumulatedData.addAll(processCountsResultSet(resultSet, account));
                });

                // create tree data from that
                List<TreeResultsDTO.TreeItemDTO<EmailSearchParams>> emailParams = accumulatedData.stream()
                        .map(entry -> {
                            TreeDisplayCount treeDisplayCount = indeterminateTypes.contains(entry.getKey())
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeResultsDTO.TreeDisplayCount.getDeterminate(entry.getCount());

                            return createEmailTreeItem(entry.getAccount(), entry.getFolder(), entry.getDisplayName(), dataSourceId, treeDisplayCount);

                        })
                        .sorted((a,b) -> {
                            boolean keyADown = StringUtils.isBlank((account == null ? a.getSearchParams().getAccount() : a.getSearchParams().getFolder()));
                            boolean keyBDown = StringUtils.isBlank((account == null ? b.getSearchParams().getAccount() : b.getSearchParams().getFolder()));
                                   
                            if (keyADown != keyBDown) {
                                return Boolean.compare(keyADown, keyBDown);
                            } else {
                                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                            }
                        })
                        .collect(Collectors.toList());

                // return results
                return new TreeResultsDTO<>(emailParams);
            }

        } catch (SQLException | NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException(
                    MessageFormat.format("An error occurred while fetching email counts for account: {0} and sql: \n{1}",
                            account == null ? "<null>" : account,
                            query == null ? "<null>" : query),
                    ex);
        }
    }

    /**
     * Processes a result querying for email counts.
     *
     * @param resultSet The result set.
     * @param account   The account for which results apply. If null, email
     *                  counts data is returned for an account level.
     *
     * @return The email counts data.
     */
    private List<EmailCountsData> processCountsResultSet(ResultSet resultSet, String account) {
        try {
            if (account == null) {
                Map<String, Set<String>> accountFolders = new HashMap<>();
                Map<String, Long> counts = new HashMap<>();
                while (resultSet.next()) {
                    long count = resultSet.getLong("count");
                    String resultFolder = resultSet.getString("folder");
                    String resultAccount = resultSet.getString("account");
                    if (StringUtils.isBlank(resultFolder) || StringUtils.isBlank(resultAccount)) {
                        resultFolder = "";
                        resultAccount = "";
                    }

                    counts.compute(resultAccount, (k, v) -> v == null ? count : v + count);
                    accountFolders
                            .computeIfAbsent(resultAccount, (k) -> new HashSet<>())
                            .add(resultFolder);
                }

                return counts.entrySet().stream()
                        .map(e -> {
                            String thisAccount = e.getKey();
                            String displayName = getAccountDisplayName(e.getKey(), accountFolders.get(e.getKey()));
                            Long count = e.getValue();

                            return new EmailCountsData(thisAccount, null, thisAccount, displayName, count);
                        })
                        .collect(Collectors.toList());
            } else {

                Map<String, Long> counts = new HashMap<>();
                while (resultSet.next()) {
                    long count = resultSet.getLong("count");
                    String resultFolder = resultSet.getString("folder");
                    if (StringUtils.isBlank(resultFolder) || StringUtils.isBlank(account)) {
                        resultFolder = "";
                    }
                    
                    counts.compute(resultFolder, (k, v) -> v == null ? count : v + count);
                }
                
                return counts.entrySet().stream()
                        .map(e -> new EmailCountsData(account, e.getKey(), e.getKey(), getFolderDisplayName(e.getKey()), e.getValue()))
                        .collect(Collectors.toList());
            }

        } catch (SQLException ex) {
            logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
            return Collections.emptyList();
        }
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
                (daoEvt, count) -> createEmailTreeItem(daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getFolder(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.emailCounts,
                (daoEvt, count) -> createEmailTreeItem(daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getFolder(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt == null) {
            return Collections.emptySet();
        }

        // maps email account => folder => data source id
        Map<String, Map<String, Set<Long>>> emailMap = new HashMap<>();

        for (BlackboardArtifact art : dataEvt.getArtifacts()) {
            try {
                if (art.getType().getTypeID() == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
                    Pair<String, String> accountFolder = getAccountAndFolder(art);
                    emailMap
                            .computeIfAbsent(accountFolder.getLeft(), (k) -> new HashMap<>())
                            .computeIfAbsent(accountFolder.getRight(), (k) -> new HashSet<>())
                            .add(art.getDataSourceObjectID());
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to fetch email message info for: " + art.getId(), ex);
            }
        }

        // don't do anything else if no relevant events
        if (emailMap.isEmpty()) {
            return Collections.emptySet();
        }

        SubDAOUtils.invalidateKeys(this.searchParamsCache, (searchParams) -> {
            Map<String, Set<Long>> folders = emailMap.get(searchParams.getAccount());
            if (folders == null) {
                return false;
            }

            Set<Long> dsIds = folders.get(searchParams.getFolder());
            if (dsIds == null) {
                return false;
            }
            return searchParams.getDataSourceId() == null || dsIds.contains(searchParams.getDataSourceId());
        });

        List<EmailEvent> emailEvents = new ArrayList<>();
        for (Entry<String, Map<String, Set<Long>>> accountEntry : emailMap.entrySet()) {
            String acct = accountEntry.getKey();
            for (Entry<String, Set<Long>> folderEntry : accountEntry.getValue().entrySet()) {
                String folder = folderEntry.getKey();
                for (Long dsObjId : folderEntry.getValue()) {
                    emailEvents.add(new EmailEvent(dsObjId, acct, folder));
                }
            }
        }

        Stream<TreeEvent> treeEvents = this.emailCounts.enqueueAll(emailEvents).stream()
                .map(daoEvt -> new TreeEvent(createEmailTreeItem(daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getFolder(),
                daoEvt.getDataSourceId(), TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false));

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
            EmailEvent emailEvt = (EmailEvent) evt;
            return (Objects.equals(parameters.getAccount(), emailEvt.getAccount())
                    && Objects.equals(parameters.getFolder(), emailEvt.getFolder())
                    && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), emailEvt.getDataSourceId())));
        } else {
            return false;

        }
    }

    /**
     * Holds data for email counts.
     */
    private static final class EmailCountsData {

        private final String displayName;
        private final String account;
        private final String folder;
        private final String key;
        private final Long count;

        /**
         * Main constructor.
         *
         * @param account     The relevant email account.
         * @param folder      The relevant email folder.
         * @param key         The key when querying for what should be
         *                    indeterminate folders (account if no account
         *                    parameter; otherwise, folder).
         * @param displayName The display name.
         * @param count
         */
        public EmailCountsData(String account, String folder, String key, String displayName, Long count) {
            this.displayName = displayName;
            this.account = account;
            this.folder = folder;
            this.key = key;
            this.count = count;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAccount() {
            return account;
        }

        public String getFolder() {
            return folder;
        }

        public String getKey() {
            return key;
        }

        public Long getCount() {
            return count;
        }

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
            return getDAO().getEmailMessages(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isEmailInvalidating(this.getParameters(), evt);
        }
    }
}
