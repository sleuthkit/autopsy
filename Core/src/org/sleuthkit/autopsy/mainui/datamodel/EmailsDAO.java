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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 * Provides information to populate the results viewer for data in the Emails
 * section.
 */
public class EmailsDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(EmailsDAO.class.getName());

    private static final String PATH_DELIMITER = "\\";
    private static final String REGEX_PATH_DELIMITER = "\\\\";
    private static final String ESCAPE_CHAR = "$";

    private final Cache<SearchParams<EmailSearchParams>, SearchResultsDTO> searchParamsCache
            = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

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

    private SearchResultsDTO fetchEmailMessageDTOs(SearchParams<EmailSearchParams> searchParams) throws NoCurrentCaseException, TskCoreException, SQLException, IllegalStateException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();

        String pathWhereStatement = StringUtils.isBlank(searchParams.getParamData().getFolder())
                ? "AND attr.value_text IS NULL OR attr.value_text NOT LIKE '/%' ESCAPE '" + ESCAPE_CHAR + " \n"
                : "AND attr.value_text LIKE ? ESCAPE '" + ESCAPE_CHAR + "' \n";

        String baseQuery = "FROM blackboard_artifacts art \n"
                + "LEFT JOIN blackboard_attributes attr ON attr.artifact_id = art.artifact_id \n"
                + "  AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + " \n"
                + "WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + " \n"
                + pathWhereStatement
                + (searchParams.getParamData().getDataSourceId() == null ? "" : "AND art.data_source_obj_id = ? \n")
                + "GROUP BY art.artifact_id \n";

        String itemsQuery = " art.artifact_id AS artifact_id \n"
                + baseQuery
                + "ORDER BY art.artifact_id\n"
                + "OFFSET " + searchParams.getStartItem() + "\n"
                + (searchParams.getMaxResultsCount() == null ? "" : "LIMIT " + searchParams.getMaxResultsCount() + "\n");

        String countsQuery = " COUNT(*) AS count FROM (SELECT art.artifact_id\n" + baseQuery + ") res";

        // this could potentially be done in a query obtaining the artifacts and another retrieving the total count.
        List<Long> pagedIds = new ArrayList<>();
        AtomicReference<Long> totalCount = new AtomicReference<>(0L);

        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(countsQuery)) {

            int paramIdx = 0;
            if (searchParams.getParamData().getFolder() != null) {
                preparedStatement.setString(++paramIdx, MessageFormat.format("%{0}%",
                        SubDAOUtils.likeEscape(searchParams.getParamData().getFolder() + "%", ESCAPE_CHAR)
                ));
            }

            if (searchParams.getParamData().getDataSourceId() != null) {
                preparedStatement.setLong(++paramIdx, searchParams.getParamData().getDataSourceId());
            }

            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    if (resultSet.next()) {
                        totalCount.set(resultSet.getLong("count"));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException("There was an error fetching count with query:\nSELECT" + countsQuery, ex);
                }

            });
        }

        List<BlackboardArtifact> allArtifacts = Collections.emptyList();
        if (totalCount.get() > 0) {
            try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(itemsQuery)) {

                int paramIdx = 0;
                if (searchParams.getParamData().getFolder() != null) {
                    preparedStatement.setString(++paramIdx, MessageFormat.format("%{0}%",
                            SubDAOUtils.likeEscape(searchParams.getParamData().getFolder(), ESCAPE_CHAR)
                    ));
                }

                if (searchParams.getParamData().getDataSourceId() != null) {
                    preparedStatement.setLong(++paramIdx, searchParams.getParamData().getDataSourceId());
                }

                getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            pagedIds.add(resultSet.getLong("artifact_id"));
                        }
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "There was an error fetching emails for ");
                    }

                });
            }

            String whereClause = "artifacts.artifact_id IN (" + pagedIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", ")) + ")";
            allArtifacts = getDataArtifactsAsBBA(blackboard, whereClause);

            // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
            // load many artifacts/attributes at once.
            blackboard.loadBlackboardAttributes(allArtifacts);
        }

        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_EMAIL_MSG, allArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_EMAIL_MSG, tableData.columnKeys,
                tableData.rows, searchParams.getStartItem(), totalCount.get());
    }

    @SuppressWarnings("unchecked")
    private List<BlackboardArtifact> getDataArtifactsAsBBA(Blackboard blackboard, String whereClause) throws TskCoreException {
        return (List<BlackboardArtifact>) (List<? extends BlackboardArtifact>) blackboard.getDataArtifactsWhere(whereClause);
    }

    private static String getLastFolderSegment(String fullPath) {
        if (StringUtils.isNotBlank(fullPath)) {
            String[] folderPieces = fullPath.split(REGEX_PATH_DELIMITER);
            for (int i = folderPieces.length - 1; i >= 0; i--) {
                if (StringUtils.isNotBlank(folderPieces[i])) {
                    return folderPieces[i].trim();
                }
            }
        }

        return null;
    }

    @Messages({"EmailsDAO_getFolderDisplayName_defaultName=[Default]"})
    public static String getFolderDisplayName(String folder) {
        return StringUtils.isBlank(folder)
                ? Bundle.EmailsDAO_getFolderDisplayName_defaultName()
                : folder;
    }

    private static String getNormalizedPath(String origPath) {
        String safePath = StringUtils.defaultString(origPath);
        if (StringUtils.isBlank(safePath)) {
            return "";
        }

        safePath = safePath.trim();
        if (!safePath.endsWith(PATH_DELIMITER)) {
            safePath = safePath + PATH_DELIMITER;
        }

        return safePath;
    }

    public TreeItemDTO<EmailSearchParams> createEmailTreeItem(String fullPath, Long dataSourceId, TreeDisplayCount count) {
        return createEmailTreeItem(fullPath, getLastFolderSegment(fullPath), dataSourceId, count);
    }

    public TreeItemDTO<EmailSearchParams> createEmailTreeItem(String fullPath, String folderName, Long dataSourceId, TreeDisplayCount count) {
        return new TreeItemDTO<>(
                EmailSearchParams.getTypeId(),
                new EmailSearchParams(dataSourceId, fullPath),
                folderName,
                getFolderDisplayName(folderName),
                count
        );
    }

    public Optional<String> getNextSubFolder(String folderParent, String folder) {
        String normalizedParent = folderParent == null ? null : getNormalizedPath(folderParent);
        String normalizedFolder = folder == null ? null : getNormalizedPath(folder);

        if (normalizedParent == null || normalizedFolder.startsWith(normalizedParent)) {
            if (normalizedFolder == null) {
                return Optional.of(null);
            } else {
                int nextDelim = normalizedFolder.indexOf(PATH_DELIMITER, normalizedParent.length());
                if (nextDelim >= 0) {
                    return Optional.of(normalizedFolder.substring(normalizedParent.length(), nextDelim));
                } else {
                    return Optional.of(normalizedFolder.substring(normalizedParent.length()));
                }
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns sql to query for email counts.
     *
     * @param dbType       The db type (postgres/sqlite).
     * @param folder       The parent folder or null for root level.
     * @param dataSourceId The data source id to filter on or null for no
     *                     filter. If non-null, a prepared statement parameter
     *                     will need to be provided at index 2.
     *
     * @return A tuple of the sql and the account like string (or null if no
     *         account filter).
     */
    private static String getFolderChildrenSql(TskData.DbType dbType, String folder, Long dataSourceId) {
        // possible and claused depending on whether or not there is an account to filter on and a data source object id to filter on.

        String folderSplitSql;
        switch (dbType) {
            case POSTGRESQL:
                folderSplitSql = "SPLIT_PART(res.subfolders, '" + PATH_DELIMITER + "', 1)";
                break;
            case SQLITE:
                folderSplitSql = "SUBSTR(res.subfolders, 1, INSTR(res.subfolders, '" + PATH_DELIMITER + "') - 1)";
                break;
            default:
                throw new IllegalArgumentException("Unknown db type: " + dbType);
        }

        String substringFolderSql;
        String folderWhereStatement;
        if (StringUtils.isBlank(folder)) {
            substringFolderSql = "CASE WHEN p.path LIKE '" + PATH_DELIMITER + "%' ESCAPE '" + ESCAPE_CHAR + "' THEN SUBSTR(p.path, 2) ELSE '' END";
            folderWhereStatement = "";
        } else {
            // if exact match, 
            substringFolderSql = "CASE WHEN (p.path = ? OR p.path = ?) THEN NULL ELSE SUBSTR(p.path, LENGTH(?) + 1) END";
            folderWhereStatement = "    WHERE (p.path LIKE ? ESCAPE '" + ESCAPE_CHAR + "' OR p.path = ?)\n";
        }

        String query = "\n  MAX(grouped_res.folder) AS folder\n"
                + "  ,COUNT(*) AS count\n"
                + "FROM (\n"
                + "  SELECT\n"
                + "    (CASE \n"
                + "        WHEN res.subfolders LIKE '%" + PATH_DELIMITER + "%' THEN \n"
                + "          " + folderSplitSql + "\n"
                + "        ELSE\n"
                + "          res.subfolders\n"
                + "    END) AS folder\n"
                + "  FROM (\n"
                + "    SELECT\n"
                + "      " + substringFolderSql + " AS subfolders\n"
                + "    FROM blackboard_artifacts art\n"
                + "    LEFT JOIN (\n"
                + "        SELECT \n"
                + "        MIN(attr.value_text) AS path\n"
                + "        ,attr.artifact_id \n"
                + "        FROM blackboard_attributes attr \n"
                + "        WHERE attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + " \n"
                + "        GROUP BY attr.artifact_id\n"
                + "    ) p ON art.artifact_id = p.artifact_id\n"
                + folderWhereStatement
                + "    AND art.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + " \n"
                + (dataSourceId != null ? "    AND art.data_source_obj_id = ? \n" : "")
                + "  ) res\n"
                + ") grouped_res\n"
                + "GROUP BY LOWER(grouped_res.folder)\n"
                + "ORDER BY LOWER(grouped_res.folder)";

        return query;
    }

    /**
     * Returns the accounts and their counts in the current data source if a
     * data source id is provided or all accounts if data source id is null.
     *
     * @param dataSourceId The data source id or null for no data source filter.
     * @param folder       The email folder parent (using '\' as prefix, suffix,
     *                     and delimiter). If null, root level folders
     *
     * @return The results.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<EmailSearchParams> getEmailCounts(Long dataSourceId, String folder) throws ExecutionException {

        // folder ending with slash if not null
        String endSlashFolder = (folder != null && !folder.endsWith(PATH_DELIMITER))
                ? folder + PATH_DELIMITER
                : folder;

        // a series of full folder paths prefixed and delimiter of '\' (no suffix)
        Set<String> indeterminateTypes = this.emailCounts.getEnqueued().stream()
                .filter(evt -> (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId)))
                .map(evt -> getNextSubFolder(folder, evt.getFolder()))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .collect(Collectors.toSet());

        String query = null;
        try {
            SleuthkitCase skCase = getCase();
            query = getFolderChildrenSql(skCase.getDatabaseType(), endSlashFolder, dataSourceId);

            try (CaseDbPreparedStatement preparedStatement = skCase.getCaseDbAccessManager().prepareSelect(query)) {
                int paramIdx = 0;
                if (folder != null) {
                    preparedStatement.setString(++paramIdx, folder);
                    preparedStatement.setString(++paramIdx, endSlashFolder);
                    preparedStatement.setString(++paramIdx, endSlashFolder);
                    preparedStatement.setString(++paramIdx, SubDAOUtils.likeEscape(endSlashFolder, ESCAPE_CHAR) + "%");
                    preparedStatement.setString(++paramIdx, folder);
                }

                if (dataSourceId != null) {
                    preparedStatement.setLong(++paramIdx, dataSourceId);
                }

                // query for data and create tree data
                List<TreeResultsDTO.TreeItemDTO<EmailSearchParams>> accumulatedData = new ArrayList<>();
                skCase.getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            String rsFolderSegment = resultSet.getString("folder");
                            // if blank returned, assume it belongs to base results; don't provide ending slash
                            String rsPath;
                            if (StringUtils.isNotBlank(folder) && StringUtils.isNotBlank(rsFolderSegment)) {
                                rsPath = endSlashFolder + rsFolderSegment;
                            } else if (StringUtils.isBlank(folder) && StringUtils.isBlank(rsFolderSegment)) {
                                rsPath = "";
                            } else if (StringUtils.isNotBlank(folder)) {
                                rsPath = folder;
                            } else {
                                rsPath = PATH_DELIMITER + rsFolderSegment;
                            }

                            TreeDisplayCount treeDisplayCount = indeterminateTypes.contains(rsPath)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeResultsDTO.TreeDisplayCount.getDeterminate(resultSet.getLong("count"));

                            accumulatedData.add(createEmailTreeItem(rsPath, rsFolderSegment, dataSourceId, treeDisplayCount));
                        }
                    } catch (SQLException ex) {
                        throw new IllegalStateException("A sql exception occurred.", ex);
                    }
                });
                
                // if only one item of this type, don't show children
                if (accumulatedData.size() == 1 && StringUtils.isBlank(accumulatedData.get(0).getId().toString())) {
                    return new TreeResultsDTO<>(Collections.emptyList());
                } else {
                    // return results
                    return new TreeResultsDTO<>(accumulatedData);   
                }
            }

        } catch (SQLException | NoCurrentCaseException | TskCoreException | IllegalStateException ex) {
            throw new ExecutionException(
                    MessageFormat.format("An error occurred while fetching email counts for folder: {0} and sql: \n{1}",
                            folder == null ? "<null>" : folder,
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
                (daoEvt, count) -> createEmailTreeItem(
                        daoEvt.getFolder(),
                        daoEvt.getDataSourceId(),
                        count
                ));
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(
                this.emailCounts,
                (daoEvt, count) -> createEmailTreeItem(
                        daoEvt.getFolder(),
                        daoEvt.getDataSourceId(),
                        count
                ));
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt == null) {
            return Collections.emptySet();
        }

        // maps email folder => data source id
        Map<String, Set<Long>> emailMap = new HashMap<>();

        for (BlackboardArtifact art : dataEvt.getArtifacts()) {
            try {
                if (art.getType().getTypeID() == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
                    BlackboardAttribute attr = art.getAttribute(BlackboardAttribute.Type.TSK_PATH);
                    String folder = attr == null ? null : attr.getValueString();
                    emailMap
                            .computeIfAbsent(folder, (k) -> new HashSet<>())
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

        SubDAOUtils.invalidateKeys(this.searchParamsCache, (searchParams) -> Pair.of(searchParams.getFolder(), searchParams.getDataSourceId()), emailMap);
        List<EmailEvent> emailEvents = new ArrayList<>();
        for (Entry<String, Set<Long>> folderEntry : emailMap.entrySet()) {
            String folder = folderEntry.getKey();
            for (Long dsObjId : folderEntry.getValue()) {
                emailEvents.add(new EmailEvent(dsObjId, folder));
            }
        }

        Stream<TreeEvent> treeEvents = this.emailCounts.enqueueAll(emailEvents).stream()
                .map(daoEvt -> {
                    return new TreeEvent(
                            createEmailTreeItem(
                                    daoEvt.getFolder(),
                                    daoEvt.getDataSourceId(),
                                    TreeResultsDTO.TreeDisplayCount.INDETERMINATE),
                            false);
                });

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
            return Objects.equals(getNormalizedPath(parameters.getFolder()), getNormalizedPath(emailEvt.getFolder()))
                    && (parameters.getDataSourceId() == null || Objects.equals(parameters.getDataSourceId(), emailEvt.getDataSourceId()));
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
