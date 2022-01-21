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

    /**
     * Sets the values of a results view prepared statement used in
     * fetchEmailMessageDTOs.
     *
     * @param preparedStatement The prepared statement.
     * @param normalizedPath    The query path indicated by TSK_PATH.
     * @param dataSourceId      The data source id.
     *
     * @throws TskCoreException
     */
    private void setResultsViewPreparedStatement(CaseDbPreparedStatement preparedStatement, String normalizedPath, Long dataSourceId) throws TskCoreException {
        int paramIdx = 0;
        if (normalizedPath != null) {
            preparedStatement.setString(++paramIdx, normalizedPath);
            String noEndingSlash = normalizedPath.endsWith(PATH_DELIMITER) ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
            preparedStatement.setString(++paramIdx, noEndingSlash);
        }

        if (dataSourceId != null) {
            preparedStatement.setLong(++paramIdx, dataSourceId);
        }
    }

    private SearchResultsDTO fetchEmailMessageDTOs(SearchParams<EmailSearchParams> searchParams) throws NoCurrentCaseException, TskCoreException, SQLException, IllegalStateException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();

        String normalizedPath = getNormalizedPath(searchParams.getParamData().getFolder());
        String pathWhereStatement = (normalizedPath == null)
                // if searching for result without any folder, find items that are not prefixed with '\' or aren't null
                ? "AND attr.value_text IS NULL OR attr.value_text NOT LIKE '" + PATH_DELIMITER + "%' ESCAPE '" + ESCAPE_CHAR + " \n"
                // the path should start with the prescribed folder
                : "AND (LOWER(attr.value_text) = LOWER(?) OR LOWER(attr.value_text) = LOWER(?))\n";

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
                + (searchParams.getMaxResultsCount() == null ? "" : "LIMIT " + searchParams.getMaxResultsCount() + "\n")
                + "OFFSET " + searchParams.getStartItem() + "\n";

        String countsQuery = " COUNT(*) AS count FROM (SELECT art.artifact_id\n" + baseQuery + ") res";

        // this could potentially be done in a query obtaining the artifacts and another retrieving the total count.
        List<Long> pagedIds = new ArrayList<>();
        AtomicReference<Long> totalCount = new AtomicReference<>(0L);

        // query for counts
        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(countsQuery)) {
            setResultsViewPreparedStatement(preparedStatement, searchParams.getParamData().getFolder(), searchParams.getParamData().getDataSourceId());
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

        // if there is a result count, get paged artifact ids
        List<BlackboardArtifact> allArtifacts = Collections.emptyList();
        if (totalCount.get() > 0) {
            try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(itemsQuery)) {
                setResultsViewPreparedStatement(preparedStatement, searchParams.getParamData().getFolder(), searchParams.getParamData().getDataSourceId());
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

    /**
     * Converts a list of data artifacts gathered using
     * Blackboard.getDataArtifactsWhere into a list of blackboard artifacts.
     *
     * @param blackboard  The TSK blackboard.
     * @param whereClause The where clause to use with
     *                    Blackboard.getDataArtifactsWhere.
     *
     * @return The list of BlackboardArtifacts.
     *
     * @throws TskCoreException
     */
    @SuppressWarnings("unchecked")
    private List<BlackboardArtifact> getDataArtifactsAsBBA(Blackboard blackboard, String whereClause) throws TskCoreException {
        return (List<BlackboardArtifact>) (List<? extends BlackboardArtifact>) blackboard.getDataArtifactsWhere(whereClause);
    }

    /**
     * Determines the last non-blank folder segment.
     *
     * @param fullPath The full path taken from a TSK_PATH.
     *
     * @return The last non-blank folder segment.
     */
    private static String getLastFolderSegment(String fullPath) {
        // getNormalizedPath should remove any trailing whitespace or path delimiters,
        // so take the last index of the path delimiter if it exists, and use everything after that.
        String normalizedPath = getNormalizedPath(fullPath);
        if (normalizedPath == null) {
            return null;
        }

        String pathWithoutEndSlash = normalizedPath.endsWith(PATH_DELIMITER)
                ? normalizedPath.substring(0, normalizedPath.length() - 1)
                : normalizedPath;

        int lastIdx = pathWithoutEndSlash.lastIndexOf(PATH_DELIMITER);
        if (lastIdx >= 0) {
            return pathWithoutEndSlash.substring(lastIdx + 1);
        } else {
            return pathWithoutEndSlash;
        }
    }

    /**
     * Returns the folder display name based on the folder. If blank, returns
     * Default folder string.
     *
     * @param folder The folder.
     *
     * @return The folder display name.
     */
    @Messages({"EmailsDAO_getFolderDisplayName_defaultName=[Default]"})
    public static String getFolderDisplayName(String folder) {
        return folder == null
                ? Bundle.EmailsDAO_getFolderDisplayName_defaultName()
                : folder;
    }

    /**
     * Normalizes the path. If path is blank or does not start with a path
     * delimiter, return an empty string. Otherwise, remove all trailing
     * whitespace and path delimiters.
     *
     * @param origPath The original path.
     *
     * @return The normalized path.
     */
    private static String getNormalizedPath(String origPath) {
        if (origPath == null || !origPath.startsWith(PATH_DELIMITER)) {
            return null;
        }

        if (!origPath.endsWith(PATH_DELIMITER)) {
            origPath = origPath + PATH_DELIMITER;
        }

        return origPath;
    }

    /**
     * Creates a tree item dto with the given parameters.
     *
     * @param fullPath     The full TSK_PATH path.
     * @param dataSourceId The data source object id.
     * @param count        The count to display.
     *
     * @return The tree item dto.
     */
    public TreeItemDTO<EmailSearchParams> createEmailTreeItem(String fullPath, Long dataSourceId, TreeDisplayCount count) {
        String normalizedPath = getNormalizedPath(fullPath);
        String lastSegment = getLastFolderSegment(fullPath);
        String displayName = getFolderDisplayName(lastSegment);
        return new TreeItemDTO<>(
                EmailSearchParams.getTypeId(),
                new EmailSearchParams(dataSourceId, normalizedPath),
                // path for id to lower case so case insensitive
                normalizedPath == null ? 0 : normalizedPath.toLowerCase(),
                displayName,
                count
        );
    }

    /**
     * Returns the next relevant subfolder (the full path) after the parent path
     * or empty if child path is not a sub path of parent path path.
     *
     * @param parentPath The parent path.
     * @param childPath  The child path.
     *
     * @return The next subfolder or empty.
     */
    public Optional<String> getNextSubFolder(String parentPath, String childPath) {
        String normalizedParent = getNormalizedPath(parentPath);
        String normalizedChild = getNormalizedPath(childPath);

        if (normalizedChild == null) {
            return (normalizedParent == null)
                    ? Optional.of(null)
                    : Optional.empty();
        }

        if (normalizedParent == null) {
            normalizedParent = PATH_DELIMITER;
        }

        // ensure that child is a sub path of parent
        if (normalizedChild.toLowerCase().startsWith(normalizedParent.toLowerCase())) {
            int nextDelimiter = normalizedChild.indexOf(PATH_DELIMITER, normalizedParent.length());
            return nextDelimiter >= 0
                    ? Optional.of(getNormalizedPath(normalizedChild.substring(0, nextDelimiter + 1)))
                    : Optional.of(normalizedChild);

        }
        return Optional.empty();
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
        if (folder == null) {
            substringFolderSql = "CASE WHEN p.path LIKE '" + PATH_DELIMITER + "%' ESCAPE '" + ESCAPE_CHAR + "' THEN SUBSTR(p.path, 2) ELSE NULL END";
            folderWhereStatement = "";
        } else {
            // if exact match, 
            substringFolderSql = "      CASE\n"
                    + "        WHEN p.path LIKE ? THEN \n"
                    + "          SUBSTR(p.path, LENGTH(?) + 1)\n"
                    + "        ELSE\n"
                    + "          NULL\n"
                    + "      END";
            folderWhereStatement = "    WHERE (p.path = ? OR p.path LIKE ? ESCAPE '" + ESCAPE_CHAR + "')\n";
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
                + "    FROM (\n"
                + "      SELECT MIN(attr.value_text) AS path\n"
                + "      FROM blackboard_attributes attr \n"
                + "      LEFT JOIN blackboard_artifacts art \n"
                + "        ON attr.artifact_id = art.artifact_id\n"
                + "      WHERE attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_PATH.getTypeID() + " \n"
                + "      AND attr.artifact_type_id = " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + " \n"
                + (dataSourceId != null ? "      AND art.data_source_obj_id = ? \n" : "")
                + "      GROUP BY attr.artifact_id\n"
                + "    ) p\n"
                + folderWhereStatement
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
     * @param dataSourceId   The data source id or null for no data source
     *                       filter.
     * @param normalizedPath The email folder parent (using '\' as prefix,
     *                       suffix, and delimiter). If null, root level folders
     *
     * @return The results.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<EmailSearchParams> getEmailCounts(Long dataSourceId, String folder) throws ExecutionException {

        String normalizedParent = getNormalizedPath(folder);

        // a series of full folder paths prefixed and delimiter of '\' (no suffix)
        Set<String> indeterminateTypes = this.emailCounts.getEnqueued().stream()
                .filter(evt -> (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId)))
                .map(evt -> getNextSubFolder(normalizedParent, evt.getFolder()))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .collect(Collectors.toSet());

        String query = null;
        try {
            SleuthkitCase skCase = getCase();
            query = getFolderChildrenSql(skCase.getDatabaseType(), normalizedParent, dataSourceId);

            try (CaseDbPreparedStatement preparedStatement = skCase.getCaseDbAccessManager().prepareSelect(query)) {
                int paramIdx = 0;
                if (normalizedParent != null) {
                    String likeStatement = SubDAOUtils.likeEscape(normalizedParent, ESCAPE_CHAR) + "%";
                    String normalizedWithoutSlash = normalizedParent.endsWith(PATH_DELIMITER)
                            ? normalizedParent.substring(0, normalizedParent.length() - 1)
                            : normalizedParent;

                    preparedStatement.setString(++paramIdx, likeStatement);
                    preparedStatement.setString(++paramIdx, normalizedParent);
                    preparedStatement.setString(++paramIdx, normalizedWithoutSlash);
                    preparedStatement.setString(++paramIdx, likeStatement);
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
                            String rsPath;
                            if (normalizedParent != null && rsFolderSegment != null) {
                                // both the parent path and next folder segment are present
                                rsPath = getNormalizedPath(normalizedParent + rsFolderSegment + PATH_DELIMITER);
                            } else if (rsFolderSegment == null) {
                                // the folder segment is not present
                                rsPath = getNormalizedPath(normalizedParent);
                            } else {
                                // the normalized parent is not present but the folder segment is
                                rsPath = getNormalizedPath(PATH_DELIMITER + rsFolderSegment + PATH_DELIMITER);
                            }

                            TreeDisplayCount treeDisplayCount = indeterminateTypes.contains(rsPath)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeResultsDTO.TreeDisplayCount.getDeterminate(resultSet.getLong("count"));

                            accumulatedData.add(createEmailTreeItem(rsPath, dataSourceId, treeDisplayCount));
                        }
                    } catch (SQLException ex) {
                        throw new IllegalStateException("A sql exception occurred.", ex);
                    }
                });

                // if only one item of this type, don't show children
                if (accumulatedData.size() == 1 && Objects.equals(accumulatedData.get(0).getSearchParams().getFolder(), folder)) {
                    return new TreeResultsDTO<>(Collections.emptyList());
                } else {
                    // return results
                    return new TreeResultsDTO<>(accumulatedData);
                }
            }

        } catch (SQLException | NoCurrentCaseException | TskCoreException | IllegalStateException ex) {
            throw new ExecutionException(
                    MessageFormat.format("An error occurred while fetching email counts for folder: {0} and sql: \n{1}",
                            normalizedParent == null ? "<null>" : normalizedParent,
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
                    String folder = attr == null ? null : getNormalizedPath(attr.getValueString());
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
            // determines if sub folder or not.  if equivalent, will return present
            return (getNextSubFolder(parameters.getFolder(), emailEvt.getFolder()).isPresent()
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
