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
import java.util.Comparator;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.python.icu.text.MessageFormat;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.EmailEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
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

    // TODO this should be corrected based on outcome of JIRA-8220 and put in bundle string
    public static final String DEFAULT_STR = "Default";
    private static final String PATH_DELIMITER = "/";
    private static final Pair<String, String> DEFAULT_ACCOUNT_FOLDER = Pair.of(DEFAULT_STR, DEFAULT_STR);

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

    public SearchResultsDTO getEmailMessages(EmailSearchParams searchParams, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (searchParams.getDataSourceId() != null && searchParams.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<EmailSearchParams> emailSearchParams = new SearchParams<>(searchParams, startItem, maxCount);
        return searchParamsCache.get(emailSearchParams, () -> fetchEmailMessageDTOs(emailSearchParams));
    }

    /**
     * Returns a list of paged artifacts.
     *
     * @param arts         The artifacts.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged artifacts.
     */
    List<BlackboardArtifact> getPaged(List<? extends BlackboardArtifact> arts, SearchParams<?> searchParams) {
        Stream<? extends BlackboardArtifact> pagedArtsStream = arts.stream()
                .sorted(Comparator.comparing((art) -> art.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedArtsStream = pagedArtsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedArtsStream.collect(Collectors.toList());
    }

    private static String getPathPiece(String s) {
        return StringUtils.isNotBlank(s) ? s : DEFAULT_STR;
    }

    /**
     * Constructs the value for the TSK_PATH attribute based on email message
     * account and folder.
     *
     * NOTE: Subject to change; see JIRA-8220.
     *
     * @param account The email message account.
     * @param folder  The email message folder.
     *
     * @return The constructed path.
     */
    private static String constructPath(String account, String folder) {
        return Stream.of(account, folder)
                .map(s -> getPathPiece(s))
                .collect(Collectors.joining(PATH_DELIMITER));
    }

    /**
     * Returns a pair of the email account and folder.
     *
     * NOTE: Subject to change; see JIRA-8220.
     *
     * @param art The artifact.
     *
     * @return The pair of the account and folder or default if undetermined.
     */
    private static Pair<String, String> getAccountAndFolder(BlackboardArtifact art) throws TskCoreException {
        BlackboardAttribute pathAttr = art.getAttribute(BlackboardAttribute.Type.TSK_PATH);
        if (pathAttr == null) {
            return DEFAULT_ACCOUNT_FOLDER;
        }

        String pathVal = pathAttr.getValueString();
        if (pathVal == null) {
            return DEFAULT_ACCOUNT_FOLDER;
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
     * @return The pair of the account and folder or default if undetermined.
     */
    private static Pair<String, String> getPathAccountFolder(String pathVal) {
        String[] pieces = pathVal.split(PATH_DELIMITER);

        return Pair.of(getPathPiece(pieces.length > 1 ? pieces[1] : null), getPathPiece(pieces.length > 2 ? pieces[2] : null));
    }

    private SearchResultsDTO fetchEmailMessageDTOs(SearchParams<EmailSearchParams> searchParams) throws NoCurrentCaseException, TskCoreException, SQLException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();

        String constructedPath = constructPath(searchParams.getParamData().getAccount(), searchParams.getParamData().getFolder());
        List<Long> matchingIds = TBD;
        // TODO load paged matching ids; this could be done as one query with new API

        List<BlackboardArtifact> allArtifacts = Collections.emptyList();
        if (!matchingIds.isEmpty()) {
            String whereClause = "artifacts.artifact_id IN (" + matchingIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", ")) + ")";
            allArtifacts = (List<BlackboardArtifact>) (List<? extends BlackboardArtifact>) blackboard.getDataArtifactsWhere(whereClause).stream();

            // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
            // load many artifacts/attributes at once.
            blackboard.loadBlackboardAttributes(allArtifacts);
        }

        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_EMAIL_MSG, allArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_EMAIL_MSG, tableData.columnKeys,
                tableData.rows, searchParams.getStartItem(), allArtifacts.size());
    }

    public TreeItemDTO<EmailSearchParams> createEmailTreeItem(String account, String folder,
            Long dataSourceId, TreeDisplayCount count) {

        return new TreeItemDTO<>(
                EmailSearchParams.getTypeId(),
                new EmailSearchParams(dataSourceId, account, folder),
                folder == null ? getPathPiece(account) : constructPath(account, folder),
                folder == null ? getPathPiece(account) : getPathPiece(folder),
                count
        );
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

        Set<String> indeterminateTypes = this.emailCounts.getEnqueued().stream()
                .filter(evt -> (dataSourceId == null || evt.getDataSourceId() == dataSourceId)
                && (account == null || account.equals(evt.getAccount())))
                .map(evt -> account == null ? getPathPiece(evt.getAccount()) : getPathPiece(evt.getFolder()))
                .collect(Collectors.toSet());

        String query = null;

        try {
            SleuthkitCase skCase = getCase();
            TBD;
            String pathField;
            if (account == null) {
                switch (skCase.getDatabaseType()) {
                    case POSTGRESQL:
                        pathField = "SPLIT_PART(attr.value_text, 2)";
                        break;
                    case SQLITE:
                        pathField = "SUBSTR(attr.value_text, 2, INSTR(SUBSTR(attr.value_text, 2, LENGTH(attr.value_text) - 1), '/') - 1)";
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown database type: " + skCase.getDatabaseType());
                }
            } else {
                pathField = "attr.value_text";
            }

            String escapeChar = "\\";

            String andClauses
                    = (account == null ? "" : "      AND attr.value_text LIKE ? ESCAPE '" + escapeChar + "'\n")
                    + (dataSourceId == null ? "" : "      AND art.data_source_obj_id = ?\n");

            query = " COUNT(*) AS count, p.path "
                    + "  FROM (\n"
                    + "    SELECT\n"
                    + "      MIN(" + pathField + ") AS path, \n"
                    + "      attr.artifact_id\n"
                    + "    FROM blackboard_attributes attr\n"
                    + "    LEFT JOIN blackboard_artifacts art ON attr.artifact_id = art.artifact_id\n"
                    + "    WHERE\n"
                    + "      attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + "\n" // may change due to JIRA-8220
                    + "      AND attr.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + "\n"
                    + andClauses
                    + "    GROUP BY attr.artifact_id\n"
                    + "  ) p\n"
                    + "GROUP BY p.path";

            try (CaseDbPreparedStatement preparedStatement = skCase.getCaseDbAccessManager().prepareSelect(query)) {

                int paramIdx = 0;
                if (account != null) {
                    preparedStatement.setString(++paramIdx,
                            // add initial slash
                            "/"
                            + account
                                    .replaceAll("%", escapeChar + "%")
                                    .replaceAll("_", escapeChar + "_")
                            + "%");
                }

                if (dataSourceId != null) {
                    preparedStatement.setLong(++paramIdx, dataSourceId);
                }

                Map<String, Long> options = new HashMap<>();
                skCase.getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            String path = resultSet.getString("path");
                            long count = resultSet.getLong("count");

                            if (account == null) {
                                options.compute(path, (k, v) -> v == null ? count : v + count);
                            } else {
                                options.compute(getPathAccountFolder(path).getRight(), (k, v) -> v == null ? count : v + count);
                            }
                        }
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
                    }
                });

                List<TreeResultsDTO.TreeItemDTO<EmailSearchParams>> emailParams = options.entrySet().stream()
                        .map(entry -> {
                            String entryAccount = (account == null) ? entry.getKey() : account;
                            String entryFolder = (account == null) ? null : entry.getKey();
                            Long count = entry.getValue();

                            TreeDisplayCount treeDisplayCount = indeterminateTypes.contains((account == null) ? entryAccount : entryFolder)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeResultsDTO.TreeDisplayCount.getDeterminate(count);

                            return createEmailTreeItem(entryAccount, entryFolder, dataSourceId, treeDisplayCount);

                        })
                        .sorted(Comparator.comparing(item -> item.getDisplayName()))
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

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(
                this.emailCounts,
                (daoEvt, count) -> createEmailTreeItem(daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getDataSourceId(), count)
        );
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.emailCounts,
                (daoEvt, count) -> createEmailTreeItem(daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getDataSourceId(), count)
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
                .map(daoEvt -> new TreeEvent(createEmailTreeItem(
                daoEvt.getAccount(), daoEvt.getFolder(), daoEvt.getDataSourceId(), TreeResultsDTO.TreeDisplayCount.INDETERMINATE), false));

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
