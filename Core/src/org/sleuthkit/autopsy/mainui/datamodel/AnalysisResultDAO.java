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

import org.sleuthkit.autopsy.mainui.datamodel.events.AnalysisResultEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.TskDataModelObjectsDeletedEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DeleteAnalysisResultEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.KeywordHitEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import static org.sleuthkit.datamodel.TskData.KeywordSearchQueryType.REGEX;
import static org.sleuthkit.datamodel.TskData.KeywordSearchQueryType.SUBSTRING;

/**
 * DAO for providing data about analysis results to populate the results viewer.
 */
public class AnalysisResultDAO extends BlackboardArtifactDAO {

    private static Logger logger = Logger.getLogger(AnalysisResultDAO.class.getName());

    private static AnalysisResultDAO instance = null;

    @NbBundle.Messages({
        "AnalysisResultDAO.columnKeys.score.name=Score",
        "AnalysisResultDAO.columnKeys.score.displayName=Score",
        "AnalysisResultDAO.columnKeys.score.description=Score",
        "AnalysisResultDAO.columnKeys.conclusion.name=Conclusion",
        "AnalysisResultDAO.columnKeys.conclusion.displayName=Conclusion",
        "AnalysisResultDAO.columnKeys.conclusion.description=Conclusion",
        "AnalysisResultDAO.columnKeys.justification.name=Justification",
        "AnalysisResultDAO.columnKeys.justification.displayName=Justification",
        "AnalysisResultDAO.columnKeys.justification.description=Justification",
        "AnalysisResultDAO.columnKeys.configuration.name=Configuration",
        "AnalysisResultDAO.columnKeys.configuration.displayName=Configuration",
        "AnalysisResultDAO.columnKeys.configuration.description=Configuration",
        "AnalysisResultDAO.columnKeys.sourceType.name=SourceType",
        "AnalysisResultDAO.columnKeys.sourceType.displayName=Source Type",
        "AnalysisResultDAO.columnKeys.sourceType.description=Source Type"
    })
    static final ColumnKey SCORE_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_score_name(),
            Bundle.AnalysisResultDAO_columnKeys_score_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_score_description()
    );

    static final ColumnKey CONCLUSION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_conclusion_name(),
            Bundle.AnalysisResultDAO_columnKeys_conclusion_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_conclusion_description()
    );

    static final ColumnKey CONFIGURATION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_configuration_name(),
            Bundle.AnalysisResultDAO_columnKeys_configuration_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_configuration_description()
    );

    static final ColumnKey JUSTIFICATION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_justification_name(),
            Bundle.AnalysisResultDAO_columnKeys_justification_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_justification_description()
    );

    static final ColumnKey SOURCE_TYPE_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_sourceType_name(),
            Bundle.AnalysisResultDAO_columnKeys_sourceType_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_sourceType_description()
    );

    synchronized static AnalysisResultDAO getInstance() {
        if (instance == null) {
            instance = new AnalysisResultDAO();
        }
        return instance;
    }

    /**
     * @return The set of types that are not shown in the tree.
     */
    public static Set<BlackboardArtifact.Type> getIgnoredTreeTypes() {
        return BlackboardArtifactDAO.getIgnoredTreeTypes();
    }

    private final Cache<SearchParams<BlackboardArtifactSearchParam>, AnalysisResultTableSearchResultsDTO> analysisResultCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final Cache<SearchParams<AnalysisResultSearchParam>, AnalysisResultTableSearchResultsDTO> configHitCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final Cache<SearchParams<KeywordHitSearchParam>, AnalysisResultTableSearchResultsDTO> keywordHitCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private final TreeCounts<AnalysisResultEvent> treeCounts = new TreeCounts<>();

    private AnalysisResultTableSearchResultsDTO fetchAnalysisResultsForTable(SearchParams<BlackboardArtifactSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        List<BlackboardArtifact> arts = new ArrayList<>();
        String pagedWhereClause = getWhereClause(cacheKey);
        arts.addAll(blackboard.getAnalysisResultsWhere(pagedWhereClause));
        blackboard.loadBlackboardAttributes(arts);

        // Get total number of results
        long totalResultsCount = getTotalResultsCount(cacheKey, arts.size());

        TableData tableData = createTableData(artType, arts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), totalResultsCount);
    }

    private AnalysisResultTableSearchResultsDTO fetchKeywordHitsForTable(SearchParams<? extends AnalysisResultSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();
        KeywordHitSearchParam searchParams = (KeywordHitSearchParam) cacheKey.getParamData();
        Long dataSourceId = searchParams.getDataSourceId();
        BlackboardArtifact.Type artType = searchParams.getArtifactType();

        // get all keyword hits for the search params
        List<BlackboardArtifact> allHits = blackboard.getKeywordSearchResults(searchParams.getKeyword(), searchParams.getRegex(), searchParams.getSearchType(), searchParams.getConfiguration(), dataSourceId);

        // populate all attributes in one optimized database call
        blackboard.loadBlackboardAttributes(allHits);

        // do paging, if necessary
        List<BlackboardArtifact> pagedArtifacts = getPaged(allHits, cacheKey);
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), allHits.size());
    }

    // filters results by configuration attr and needs a search param with the configuration
    private AnalysisResultTableSearchResultsDTO fetchConfigResultsForTable(SearchParams<? extends AnalysisResultSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        String expectedConfiguration = cacheKey.getParamData().getConfiguration();

        // where clause without paging
        String originalWhereClause = " artifacts.artifact_type_id = ? AND analysis_results.configuration = ? ";
        if (dataSourceId != null) {
            originalWhereClause += " AND artifacts.data_source_obj_id = ? ";
        }

        // where clause with paging
        String pagedWhereClause = originalWhereClause
                + " ORDER BY artifacts.obj_id ASC"
                + (cacheKey.getMaxResultsCount() != null && cacheKey.getMaxResultsCount() > 0 ? " LIMIT ? " : "")
                + (cacheKey.getStartItem() > 0 ? " OFFSET ? " : "");

        // base from query without where clause
        String baseQuery = " FROM blackboard_artifacts artifacts "
                + "INNER JOIN tsk_analysis_results analysis_results "
                + "ON artifacts.artifact_obj_id = analysis_results.artifact_obj_id WHERE ";

        // query for total count of matching items
        int paramIdx = 0;
        AtomicLong analysisResultCount = new AtomicLong(0);
        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(
                " COUNT(DISTINCT artifacts.artifact_id) AS count " + baseQuery + originalWhereClause)) {

            preparedStatement.setInt(++paramIdx, artType.getTypeID());
            preparedStatement.setString(++paramIdx, expectedConfiguration);

            if (dataSourceId != null) {
                preparedStatement.setLong(++paramIdx, dataSourceId);
            }

            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    if (resultSet.next()) {
                        analysisResultCount.set(resultSet.getLong("count"));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching results from result set.", ex);
                }

            });

        } catch (SQLException ex) {
            throw new TskCoreException(MessageFormat.format(
                    "An error occurred while fetching analysis result type: {0} with configuration: {1}.",
                    artType.getTypeName(),
                    expectedConfiguration),
                    ex);
        }

        List<Long> artifactIds = new ArrayList<>();
        // query to get artifact id's to be displayed if total count exceeds the start item position
        if (analysisResultCount.get() > cacheKey.getStartItem()) {
            paramIdx = 0;
            try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(
                    " artifacts.artifact_id AS artifact_id " + baseQuery + pagedWhereClause)) {

                preparedStatement.setInt(++paramIdx, artType.getTypeID());
                preparedStatement.setString(++paramIdx, expectedConfiguration);

                if (dataSourceId != null) {
                    preparedStatement.setLong(++paramIdx, dataSourceId);
                }

                if (cacheKey.getMaxResultsCount() != null && cacheKey.getMaxResultsCount() > 0) {
                    preparedStatement.setLong(++paramIdx, cacheKey.getMaxResultsCount());
                }

                if (cacheKey.getStartItem() > 0) {
                    preparedStatement.setLong(++paramIdx, cacheKey.getStartItem());
                }

                getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                    try {
                        while (resultSet.next()) {
                            artifactIds.add(resultSet.getLong("artifact_id"));
                        }
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "An error occurred while fetching results from result set.", ex);
                    }

                });

            } catch (SQLException ex) {
                throw new TskCoreException(MessageFormat.format(
                        "An error occurred while fetching analysis result type: {0} with configuration: {1}.",
                        artType.getTypeName(),
                        expectedConfiguration),
                        ex);
            }
        }

        // if there are artifact ids, get the artifacts with attributes
        List<BlackboardArtifact> pagedArtifacts = new ArrayList<>();
        if (artifactIds.size() > 0) {
            String artifactQueryWhere = " artifacts.artifact_id IN (" + artifactIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(",")) + ") ";
            pagedArtifacts.addAll(blackboard.getAnalysisResultsWhere(artifactQueryWhere));
            blackboard.loadBlackboardAttributes(pagedArtifacts);
        }
        
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), analysisResultCount.get());
    }

    @Override
    void addAnalysisResultColumnKeys(List<ColumnKey> columnKeys) {
        // Make sure these are in the same order as in addAnalysisResultFields()
        columnKeys.add(SOURCE_TYPE_COL);
        columnKeys.add(SCORE_COL);
        columnKeys.add(CONCLUSION_COL);
        columnKeys.add(CONFIGURATION_COL);
        columnKeys.add(JUSTIFICATION_COL);
    }

    @Override
    void addAnalysisResultFields(BlackboardArtifact artifact, List<Object> cells) throws TskCoreException {
        if (!(artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not add fields for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }

        // Make sure these are in the same order as in addAnalysisResultColumnKeys()
        AnalysisResult analysisResult = (AnalysisResult) artifact;
        cells.add(getSourceObjType(analysisResult.getParent()));
        cells.add(analysisResult.getScore().getSignificance().getDisplayName());
        cells.add(analysisResult.getConclusion());
        cells.add(analysisResult.getConfiguration());
        cells.add(analysisResult.getJustification());
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }
        return new AnalysisResultRowDTO((AnalysisResult) artifact, srcContent, isTimelineSupported, cellValues, id);
    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultsForTable(AnalysisResultSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.ANALYSIS_RESULT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and analysis result.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<BlackboardArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return analysisResultCache.get(searchParams, () -> fetchAnalysisResultsForTable(searchParams));
    }

    private boolean isAnalysisResultsInvalidating(AnalysisResultSearchParam key, DAOEvent eventData) {

        if (eventData instanceof DeleteAnalysisResultEvent) {
            return true;
        }

        if (!(eventData instanceof AnalysisResultEvent)) {
            return false;
        }

        AnalysisResultEvent analysisResultEvt = (AnalysisResultEvent) eventData;
        return key.getArtifactType().getTypeID() == analysisResultEvt.getArtifactType().getTypeID()
                && (key.getDataSourceId() == null || key.getDataSourceId() == analysisResultEvt.getDataSourceId());
    }

    private boolean isAnalysisResultsConfigInvalidating(AnalysisResultSearchParam key, DAOEvent event) {
        if (event instanceof DeleteAnalysisResultEvent) {
            return true;
        }

        if (!(event instanceof AnalysisResultEvent)) {
            return false;
        }

        AnalysisResultEvent setEvent = (AnalysisResultEvent) event;
        return isAnalysisResultsInvalidating(key, setEvent)
                && Objects.equals(key.getConfiguration(), setEvent.getConfiguration());
    }

    private boolean isKeywordHitInvalidating(KeywordHitSearchParam parameters, DAOEvent event) {
        if (event instanceof DeleteAnalysisResultEvent) {
            return true;
        }

        if (!(event instanceof KeywordHitEvent)) {
            return false;
        }

        KeywordHitEvent khEvt = (KeywordHitEvent) event;
        return isAnalysisResultsInvalidating(parameters, khEvt)
                && (parameters.getKeyword() == null || Objects.equals(parameters.getKeyword(), khEvt.getMatch()))
                && (parameters.getRegex() == null || Objects.equals(parameters.getRegex(), khEvt.getSearchString()))
                && (parameters.getSearchType() == null || Objects.equals(parameters.getSearchType(), khEvt.getSearchType()));

    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultConfigResults(AnalysisResultSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<AnalysisResultSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return configHitCache.get(searchParams, () -> fetchConfigResultsForTable(searchParams));
    }

    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<KeywordHitSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return keywordHitCache.get(searchParams, () -> fetchKeywordHitsForTable(searchParams));
    }

    /**
     * Returns a search results dto containing rows of counts data.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The results where rows are row of AnalysisResultSearchParam.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<AnalysisResultSearchParam> getAnalysisResultCounts(Long dataSourceId) throws ExecutionException {
        try {

            Set<BlackboardArtifact.Type> indeterminateTypes = this.treeCounts.getEnqueued().stream()
                    .filter(evt -> dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                    .map(evt -> evt.getArtifactType())
                    .collect(Collectors.toSet());

            // get row dto's sorted by display name
            Map<BlackboardArtifact.Type, Pair<Long, Boolean>> typeCounts = getCounts(dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<AnalysisResultSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        TreeDisplayCount displayCount = indeterminateTypes.contains(entry.getKey())
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(entry.getValue().getLeft());

                        return new AnalysisResultTreeItem(entry.getKey(), null, dataSourceId, displayCount, entry.getValue().getRight());
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching analysis result counts.", ex);
        }
    }

    /**
     * Returns the count of each artifact type.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The mapping of type to count and whether or not an item has a
     *         configuration.
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    Map<BlackboardArtifact.Type, Pair<Long, Boolean>> getCounts(Long dataSourceId) throws NoCurrentCaseException, TskCoreException {
        SleuthkitCase skCase = getCase();
        String query
                = "\n  r.artifact_type_id\n"
                + "  ,COUNT(*) AS count\n"
                + "  ,MAX(r.has_configuration) AS has_configuration\n"
                + "FROM\n"
                + "(SELECT \n"
                + "  art.artifact_type_id\n"
                + "  ,CASE WHEN ar.configuration IS NOT NULL AND ar.configuration <> '' THEN 1 ELSE 0 END AS has_configuration\n"
                + "FROM blackboard_artifacts art\n"
                + "INNER JOIN blackboard_artifact_types types ON types.category_type = 1 AND art.artifact_type_id = types.artifact_type_id\n"
                + "LEFT JOIN tsk_analysis_results ar ON art.artifact_obj_id = ar.artifact_obj_id\n"
                + " WHERE art.artifact_type_id NOT IN (" + BlackboardArtifactDAO.IGNORED_TYPES_SQL_SET + ") "
                + (dataSourceId == null ? "" : (" AND art.data_source_obj_id = " + dataSourceId + " ")) + "\n"
                + ") r GROUP BY r.artifact_type_id";

        Map<BlackboardArtifact.Type, Pair<Long, Boolean>> typeCounts = new HashMap<>();

        skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
            try {
                while (resultSet.next()) {
                    int artifactTypeId = resultSet.getInt("artifact_type_id");
                    BlackboardArtifact.Type type = skCase.getBlackboard().getArtifactType(artifactTypeId);
                    long count = resultSet.getLong("count");
                    boolean hasConfiguration = resultSet.getByte("has_configuration") > 0;
                    typeCounts.put(type, Pair.of(count, hasConfiguration));
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching artifact type counts with query:\nSELECT" + query, ex);
            }
        });

        return typeCounts;
    }

    /**
     *
     * @param type         The artifact type to filter on.
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return A mapping of configurations to their counts.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    Map<String, Long> getConfigurationCountsMap(BlackboardArtifact.Type type, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        if (dataSourceId != null && dataSourceId <= 0) {
            throw new IllegalArgumentException("Expected data source id to be > 0");
        }

        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = "\n  ar.configuration AS configuration\n"
                    + "  ,COUNT(*) AS count\n"
                    + "FROM blackboard_artifacts art\n"
                    + "LEFT JOIN tsk_analysis_results ar ON art.artifact_obj_id = ar.artifact_obj_id\n"
                    + "	 WHERE  art.artifact_type_id = " + type.getTypeID() + " \n"
                    + ((dataSourceId == null) ? "" : "  AND art.data_source_obj_id = " + dataSourceId + " \n")
                    + "GROUP BY ar.configuration";

            Map<String, Long> configurationCounts = new HashMap<>();
            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String configuration = resultSet.getString("configuration");
                        long count = resultSet.getLong("count");
                        configurationCounts.put(configuration, count);
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching configuration counts with query:\nSELECT" + query, ex);
                }
            });

            return configurationCounts;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching configuration counts", ex);
        }
    }

    /**
     * Get counts for individual configurations of the provided type to be used
     * in the tree view.
     *
     * @param type            The blackboard artifact type.
     * @param dataSourceId    The data source object id for which the results
     *                        should be filtered or null if no data source
     *                        filtering.
     * @param blankConfigName For artifacts with no configuration, this is the
     *                        name to provide. If null or empty, artifacts
     *                        without a configuration will be ignored.
     *
     * @return The configurations along with counts to display.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<AnalysisResultSearchParam> getConfigurationCounts(
            BlackboardArtifact.Type type,
            Long dataSourceId,
            String blankConfigName) throws IllegalArgumentException, ExecutionException {

        Set<String> indeterminateConfigCounts = new HashSet<>();
        for (AnalysisResultEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof AnalysisResultEvent
                    && (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                    && evt.getArtifactType().equals(type)) {
                indeterminateConfigCounts.add(evt.getConfiguration());
            }
        }

        List<TreeItemDTO<AnalysisResultSearchParam>> allConfigurations
                = getConfigurationCountsMap(type, dataSourceId).entrySet().stream()
                        .sorted((a, b) -> compareStrings(a.getKey(), b.getKey()))
                        .map(entry -> {
                            TreeDisplayCount displayCount = indeterminateConfigCounts.contains(entry.getKey())
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeDisplayCount.getDeterminate(entry.getValue());

                            return getConfigTreeItem(type,
                                    dataSourceId,
                                    entry.getKey(),
                                    StringUtils.isBlank(entry.getKey()) ? blankConfigName : entry.getKey(),
                                    displayCount);
                        })
                        .collect(Collectors.toList());

        return new TreeResultsDTO<>(allConfigurations);
    }
    
    /**
     * Get count of Malware nodes to be used in the tree view.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     * @param converter    Means of converting from data source id and set name
     *                     to an AnalysisResultSetSearchParam
     *
     * @return The sets along with counts to display.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<AnalysisResultSearchParam> getMalwareCounts(
            Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        
        // ELTODO handle indeterminate counts?

        List<TreeItemDTO<AnalysisResultSearchParam>> allSets = new ArrayList<>(); 
        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = "COUNT(*) AS count " //NON-NLS
                    + "FROM blackboard_artifacts,tsk_analysis_results WHERE " //NON-NLS
                    + "blackboard_artifacts.artifact_type_id=" + MALWARE_ARTIFACT_TYPE.getTypeID() //NON-NLS
                    + " AND tsk_analysis_results.artifact_obj_id=blackboard_artifacts.artifact_obj_id" //NON-NLS
                    + " AND (tsk_analysis_results.significance=" + Score.Significance.NOTABLE.getId() //NON-NLS
                    + " OR tsk_analysis_results.significance=" + Score.Significance.LIKELY_NOTABLE.getId() + " )"; //NON-NLS
            if (dataSourceId != null && dataSourceId > 0) {
                query += "  AND blackboard_artifacts.data_source_obj_id = " + dataSourceId; //NON-NLS
            }

            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        TreeDisplayCount displayCount = TreeDisplayCount.getDeterminate(resultSet.getLong("count"));

                        allSets.add(getConfigTreeItem(
                                MALWARE_ARTIFACT_TYPE.getTypeID(),
                                dataSourceId,
                                "" /*arEvt.getConfiguration()*/, // ELTODO get configuration as well
                                "Malware" /*StringUtils.isBlank(arEvt.getConfiguration()) ? arEvt.getArtifactType().getDisplayName() : arEvt.getConfiguration()*/,
                                displayCount));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching set name counts.", ex);
                }
            });
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching keyword set hits.", ex);
        }

        return new TreeResultsDTO<>(allSets);
    }    

    /**
     * Get counts for individual sets of the provided type to be used in the
     * tree view.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     * @param nullSetName  For artifacts with no set, this is the name to
     *                     provide. If null, artifacts without a set name will
     *                     be ignored.
     * @param converter    Means of converting from data source id and set name
     *                     to an AnalysisResultSetSearchParam
     *
     * @return The sets along with counts to display.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<KeywordListSearchParam> getKwSetCounts(
            Long dataSourceId,
            String nullSetName) throws IllegalArgumentException, ExecutionException {

        Set<String> indeterminateSetNames = new HashSet<>();
        for (AnalysisResultEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof KeywordHitEvent
                    && (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))) {
                indeterminateSetNames.add(((KeywordHitEvent) evt).getSetName());
            }
        }

        List<TreeItemDTO<KeywordListSearchParam>> allSets = new ArrayList<>();
        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = " res.set_name, COUNT(*) AS count \n"
                    + "FROM ( \n"
                    + "  SELECT art.artifact_id, \n"
                    + "  (SELECT value_text \n"
                    + "    FROM blackboard_attributes attr \n"
                    + "    WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID() + " LIMIT 1) AS set_name \n"
                    + "	 FROM blackboard_artifacts art \n"
                    + "	 WHERE  art.artifact_type_id = " + BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID() + " \n"
                    + ((dataSourceId == null) ? "" : "  AND art.data_source_obj_id = " + dataSourceId + " \n")
                    + ") res \n"
                    + "GROUP BY res.set_name\n"
                    + "ORDER BY res.set_name";

            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String setName = resultSet.getString("set_name");

                        TreeDisplayCount displayCount = indeterminateSetNames.contains(setName)
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(resultSet.getLong("count"));

                        allSets.add(getKeywordListTreeItem(
                                dataSourceId,
                                setName,
                                StringUtils.isBlank(setName) ? nullSetName : setName,
                                displayCount));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching set name counts.", ex);
                }
            });
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching keyword set hits.", ex);
        }

        Collections.sort(allSets, (a, b) -> compareStrings(a.getSearchParams().getSetName(), b.getSearchParams().getSetName()));

        return new TreeResultsDTO<>(allSets);
    }

    /**
     * Compares strings to properly order for the tree.
     *
     * @param a The first string.
     * @param b The second string.
     *
     * @return The comparator result.
     */
    private int compareStrings(String a, String b) {
        if (a == null && b == null) {
            return 0;
        } else if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        } else {
            return a.compareToIgnoreCase(b);
        }
    }

    /**
     * Returns the search term counts for a set name of keyword search results.
     *
     * @param setName      The set name.
     * @param dataSourceId The data source id or null.
     *
     * @return The search terms and counts.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    @Messages({
        "# {0} - searchTerm",
        "AnalysisResultDAO_getKeywordSearchTermCounts_exactMatch={0} (Exact)",
        "# {0} - searchTerm",
        "AnalysisResultDAO_getKeywordSearchTermCounts_substringMatch={0} (Substring)",
        "# {0} - searchTerm",
        "AnalysisResultDAO_getKeywordSearchTermCounts_regexMatch={0} (Regex)",})
    public TreeResultsDTO<? extends KeywordSearchTermParams> getKeywordSearchTermCounts(String setName, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        if (dataSourceId != null && dataSourceId <= 0) {
            throw new IllegalArgumentException("Expected data source id to be > 0");
        }

        Set<Pair<String, TskData.KeywordSearchQueryType>> indeterminateSearchTerms = new HashSet<>();
        for (AnalysisResultEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof KeywordHitEvent
                    && (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                    && evt.getArtifactType().equals(BlackboardArtifact.Type.TSK_KEYWORD_HIT)
                    && Objects.equals(((KeywordHitEvent) evt).getSetName(), setName)) {

                KeywordHitEvent keywordEvt = (KeywordHitEvent) evt;
                indeterminateSearchTerms.add(Pair.of(keywordEvt.getSearchString(), keywordEvt.getSearchType()));
            }
        }

        String dataSourceClause = dataSourceId == null
                ? ""
                : "AND art.data_source_obj_id = ?\n";

        String setNameClause = setName == null
                ? "attr_res.set_name IS NULL"
                : "attr_res.set_name = ?";

        String query = "res.search_term,\n"
                + "  res.search_type,\n"
                // this should be unique for each one
                + "  MIN(res.configuration) AS configuration,\n"
                + "  SUM(res.count) AS count,\n"
                + "  -- when there are multiple keyword groupings, return true for has children\n"
                + "  CASE\n"
                + "    WHEN COUNT(*) > 1 THEN 1\n"
                + "	ELSE 0\n"
                + "  END AS has_children\n"
                + "FROM (\n"
                + "  -- get keyword value, search type, search term, and count grouped by (keyword, regex, search_type) "
                + "  -- in order to determine if groupings have children\n"
                + "  SELECT \n"
                + "    attr_res.keyword, \n"
                + "    attr_res.search_type,\n"
                + "    MIN(attr_res.configuration) AS configuration,\n"
                + "    COUNT(*) AS count,\n"
                + "    CASE \n"
                + "      WHEN attr_res.search_type = 0 OR attr_res.regexp_str IS NULL THEN \n"
                + "        attr_res.keyword\n"
                + "      ELSE \n"
                + "        attr_res.regexp_str\n"
                + "    END AS search_term\n"
                + "  FROM (\n"
                + "	-- get pertinent attribute values for artifacts\n"
                + "    SELECT art.artifact_id, \n"
                + "    ar.configuration,\n"
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID() + " LIMIT 1) AS set_name,\n"
                + "    (SELECT value_int32 FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID() + " LIMIT 1) AS search_type,\n"
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_REGEXP.getTypeID() + " LIMIT 1) AS regexp_str,\n"
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD.getTypeID() + " LIMIT 1) AS keyword\n"
                + "    FROM blackboard_artifacts art\n"
                + "    LEFT JOIN tsk_analysis_results ar ON ar.artifact_obj_id = art.artifact_obj_id\n"
                + "    WHERE  art.artifact_type_id = " + BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID() + "\n"
                + dataSourceClause
                + "  ) attr_res\n"
                + "  WHERE " + setNameClause + "\n"
                + "  GROUP BY attr_res.regexp_str, attr_res.keyword, attr_res.search_type\n"
                + ") res\n"
                + "GROUP BY res.search_term, res.search_type\n"
                + "ORDER BY res.search_term, res.search_type";

        // get artifact types and counts
        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(query)) {

            int paramIdx = 0;
            if (dataSourceId != null) {
                preparedStatement.setLong(++paramIdx, dataSourceId);
            }

            if (setName != null) {
                preparedStatement.setString(++paramIdx, setName);
            }

            List<TreeItemDTO<KeywordSearchTermParams>> items = new ArrayList<>();
            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String searchTerm = resultSet.getString("search_term");
                        int searchType = resultSet.getInt("search_type");
                        long count = resultSet.getLong("count");
                        boolean hasChildren = resultSet.getBoolean("has_children");
                        // only a unique applicable configuration if no child tree nodes
                        String configuration = resultSet.getString("configuration");

                        TskData.KeywordSearchQueryType searchTypeEnum
                                = Stream.of(TskData.KeywordSearchQueryType.values())
                                        .filter(tp -> tp.getType() == searchType)
                                        .findFirst()
                                        .orElse(TskData.KeywordSearchQueryType.LITERAL);

                        String searchTermModified = getSearchTermDisplayName(searchTerm, searchTypeEnum);

                        TreeDisplayCount displayCount = indeterminateSearchTerms.contains(Pair.of(searchTerm, searchType))
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(count);

                        TreeItemDTO<KeywordSearchTermParams> treeItem = new TreeItemDTO<>(
                                KeywordSearchTermParams.getTypeId(),
                                new KeywordSearchTermParams(setName, searchTerm, TskData.KeywordSearchQueryType.valueOf(searchType), configuration, hasChildren, dataSourceId),
                                searchTermModified,
                                searchTermModified,
                                displayCount
                        );

                        items.add(treeItem);
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching results from result set.", ex);
                }
            });

            return new TreeResultsDTO<>(items);

        } catch (SQLException | NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching set counts", ex);
        }
    }

    /**
     * Returns the UI display name for a search term.
     *
     * @param searchTerm The search term.
     * @param searchType The search type enum value.
     *
     * @return The display name.
     */
    public String getSearchTermDisplayName(String searchTerm, TskData.KeywordSearchQueryType searchType) {
        String searchTermModified;
        switch (searchType) {
            case LITERAL:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_exactMatch(searchTerm == null ? "" : searchTerm);
                break;
            case SUBSTRING:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_substringMatch(searchTerm == null ? "" : searchTerm);
                break;
            case REGEX:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_regexMatch(searchTerm == null ? "" : searchTerm);
                break;
            default:
                logger.log(Level.WARNING, MessageFormat.format("Non-standard search type value: {0}.", searchType));
                searchTermModified = searchTerm == null ? "" : searchTerm;
                break;
        }
        return searchTermModified;
    }

    /**
     * Get counts for string matches of a particular regex/substring search
     * term.
     *
     * @param setName      The set name or null if no set name.
     * @param regexStr     The regex string. Must be non-null.
     * @param searchType   The value for the search type attribute.
     * @param dataSourceId The data source id or null.
     *
     * @return The results
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<? extends KeywordHitSearchParam> getKeywordMatchCounts(String setName, String regexStr, TskData.KeywordSearchQueryType searchType, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        if (dataSourceId != null && dataSourceId <= 0) {
            throw new IllegalArgumentException("Expected data source id to be > 0");
        }

        String dataSourceClause = dataSourceId == null
                ? ""
                : "AND data_source_obj_id = ?\n";

        String setNameClause = setName == null
                ? "res.set_name IS NULL"
                : "res.set_name = ?";

        String query = "keyword, \n"
                + "  MIN(configuration) AS configuration,\n"
                + "  COUNT(*) AS count \n"
                + "FROM (\n"
                + "  SELECT art.artifact_id, \n"
                + "  ar.configuration,"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID() + " LIMIT 1) AS set_name,\n"
                + "  (SELECT value_int32 FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID() + " LIMIT 1) AS search_type,\n"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_REGEXP.getTypeID() + " LIMIT 1) AS regexp_str,\n"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD.getTypeID() + " LIMIT 1) AS keyword\n"
                + "  FROM blackboard_artifacts art\n"
                + "  LEFT JOIN tsk_analysis_results ar ON art.artifact_obj_id = ar.artifact_obj_id\n"
                + "  WHERE art.artifact_type_id = " + BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID() + "\n"
                + dataSourceClause
                + ") res\n"
                + "-- TODO replace\n"
                + "WHERE " + setNameClause + "\n"
                + "AND res.regexp_str = ?\n"
                + "AND res.search_type = ?\n"
                + "GROUP BY keyword";

        Set<String> indeterminateMatches = new HashSet<>();
        for (AnalysisResultEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof KeywordHitEvent
                    && (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                    && evt.getArtifactType().equals(BlackboardArtifact.Type.TSK_KEYWORD_HIT)) {

                KeywordHitEvent keywordEvt = (KeywordHitEvent) evt;
                if (Objects.equals(keywordEvt.getSetName(), setName)
                        && Objects.equals(keywordEvt.getSearchString(), regexStr)
                        && keywordEvt.getSearchType() == searchType) {

                    indeterminateMatches.add(keywordEvt.getMatch());
                }

            }
        }

        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(query)) {
            // get artifact types and counts
            int paramIdx = 0;
            if (dataSourceId != null) {
                preparedStatement.setLong(++paramIdx, dataSourceId);
            }

            if (setName != null) {
                preparedStatement.setString(++paramIdx, setName);
            }

            preparedStatement.setString(++paramIdx, regexStr);
            preparedStatement.setInt(++paramIdx, searchType.getType());

            List<TreeItemDTO<KeywordHitSearchParam>> items = new ArrayList<>();
            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String keyword = resultSet.getString("keyword");
                        String configuration = resultSet.getString("configuration");
                        long count = resultSet.getLong("count");

                        TreeDisplayCount displayCount = indeterminateMatches.contains(keyword)
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(count);

                        items.add(createKWHitsTreeItem(dataSourceId, setName, keyword, regexStr, searchType, configuration, displayCount));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching results from result set.", ex);
                }
            });

            return new TreeResultsDTO<>(items);
        } catch (NoCurrentCaseException | TskCoreException | SQLException ex) {
            throw new ExecutionException("An error occurred while fetching keyword counts", ex);
        }
    }

    private static TreeItemDTO<KeywordHitSearchParam> createKWHitsTreeItem(
            Long dataSourceId, String setName, String keyword, String regexStr,
            TskData.KeywordSearchQueryType searchType, String configuration, TreeDisplayCount displayCount) {

        return new TreeItemDTO<>(
                KeywordHitSearchParam.getTypeId(),
                new KeywordHitSearchParam(dataSourceId, setName, keyword, regexStr, searchType, configuration),
                keyword == null ? "" : keyword,
                keyword == null ? "" : keyword,
                displayCount
        );
    }

    @Override
    void clearCaches() {
        this.analysisResultCache.invalidateAll();
        this.keywordHitCache.invalidateAll();
        this.configHitCache.invalidateAll();
        this.handleIngestComplete();
    }

    /**
     * Returns key data for keyword hit artifacts to be used for clearing caches
     * and generating events.
     *
     * @param art The keyword hit artifact.
     *
     * @return A pair of the KeywordMatchParams with a null data source and the
     *         data source id. The params have a null data source so that they
     *         can be indexed quickly.
     *
     * @throws TskCoreException
     */
    private Pair<KeywordHitSearchParam, Long> getKeywordEvtData(BlackboardArtifact art) throws TskCoreException {
        long dataSourceId = art.getDataSourceObjectID();
        String setName = null;
        String searchTerm = null;
        String keywordMatch = null;
        // assume literal unless otherwise specified
        TskData.KeywordSearchQueryType searchType = TskData.KeywordSearchQueryType.LITERAL;

        for (BlackboardAttribute attr : art.getAttributes()) {
            if (BlackboardAttribute.Type.TSK_SET_NAME.equals(attr.getAttributeType())) {
                setName = attr.getValueString();
            } else if (BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.equals(attr.getAttributeType())) {
                try {
                    searchType = TskData.KeywordSearchQueryType.valueOf(attr.getValueInt());
                } catch (IllegalArgumentException ex) {
                    logger.log(Level.WARNING, "An error occurred while getting search type value for value: " + attr.getValueInt(), ex);
                }
            } else if (BlackboardAttribute.Type.TSK_KEYWORD_REGEXP.equals(attr.getAttributeType())) {
                searchTerm = attr.getValueString();
            } else if (BlackboardAttribute.Type.TSK_KEYWORD.equals(attr.getAttributeType())) {
                keywordMatch = attr.getValueString();
            }
        }

        String configuration = (art instanceof AnalysisResult) ? ((AnalysisResult) art).getConfiguration() : null;

        // data source id is null for KeywordHitSearchParam so that key lookups can be done without data source id.
        return Pair.of(new KeywordHitSearchParam(null, setName, keywordMatch, searchTerm, searchType, configuration), dataSourceId);
    }

    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {

        if (evt.getPropertyName().equals(Case.Events.ANALYSIS_RESULT_DELETED.toString())) {
            clearCaches();

            Set<DeleteAnalysisResultEvent> events = new HashSet<>();
            events.add(new DeleteAnalysisResultEvent(DAOEvent.Type.RESULT, ((TskDataModelObjectsDeletedEvent) evt).getOldValue()));
            events.add(new DeleteAnalysisResultEvent(DAOEvent.Type.TREE, ((TskDataModelObjectsDeletedEvent) evt).getOldValue()));

            return events;
        }

        // get a grouping of artifacts mapping the artifact type id to data source id.
        Map<Pair<BlackboardArtifact.Type, String>, Set<Long>> configMap = new HashMap<>();
        Map<KeywordHitSearchParam, Set<Long>> keywordHitsMap = new HashMap<>();
        Map<BlackboardArtifact.Type, Set<Long>> analysisResultMap = new HashMap<>();

        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt != null) {
            for (BlackboardArtifact art : dataEvt.getArtifacts()) {
                try {
                    if (art.getArtifactTypeID() == BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID()) {
                        Pair<KeywordHitSearchParam, Long> keywordData = getKeywordEvtData(art);
                        keywordHitsMap.computeIfAbsent(keywordData.getKey(), (k) -> new HashSet<>())
                                .add(keywordData.getValue());
                    } else if (BlackboardArtifact.Category.ANALYSIS_RESULT.equals(art.getType().getCategory())) {
                        analysisResultMap.computeIfAbsent(art.getType(), (k) -> new HashSet<>())
                                .add(art.getDataSourceObjectID());

                        String configuration = (art instanceof AnalysisResult) ? ((AnalysisResult) art).getConfiguration() : null;

                        configMap.computeIfAbsent(Pair.of(art.getType(), configuration), (k) -> new HashSet<>())
                                .add(art.getDataSourceObjectID());
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to fetch necessary information for artifact id: " + art.getId(), ex);
                }
            }
        }

        // don't continue if no relevant items found
        if (analysisResultMap.isEmpty() && configMap.isEmpty() && keywordHitsMap.isEmpty()) {
            return Collections.emptySet();
        }

        SubDAOUtils.invalidateKeys(this.analysisResultCache, ar -> Pair.of(ar.getArtifactType(), ar.getDataSourceId()), analysisResultMap);
        SubDAOUtils.invalidateKeys(this.configHitCache, ar -> Pair.of(Pair.of(ar.getArtifactType(), ar.getConfiguration()), ar.getDataSourceId()), configMap);
        SubDAOUtils.invalidateKeys(this.keywordHitCache, kw -> Pair.of(
                // null data source for lookup
                new KeywordHitSearchParam(null, kw.getSetName(), kw.getKeyword(), kw.getRegex(), kw.getSearchType(), kw.getConfiguration()),
                kw.getDataSourceId()
        ), keywordHitsMap);

        return getResultViewEvents(configMap, keywordHitsMap, IngestManager.getInstance().isIngestRunning());
    }

    /**
     * Generate result view events from digest of Autopsy events.
     *
     * @param resultsWithConfigMap Contains the analysis results that do use a
     *                             set name. A mapping of (analysis result type
     *                             id, set name) to data sources where results
     *                             were created.
     * @param keywordHitsMap       Contains the keyword hits mapping parameters
     *                             to data source. The data source in the
     *                             parameters is null.
     * @param ingestIsRunning      Whether or not ingest is running.
     *
     * @return The list of dao events.
     */
    private Set<? extends DAOEvent> getResultViewEvents(
            Map<Pair<BlackboardArtifact.Type, String>, Set<Long>> resultsWithConfigMap,
            Map<KeywordHitSearchParam, Set<Long>> keywordHitsMap,
            boolean ingestIsRunning) {

        List<AnalysisResultEvent> AnalysisResultEvents = resultsWithConfigMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new AnalysisResultEvent(entry.getKey().getLeft(), entry.getKey().getRight(), dsId)))
                .collect(Collectors.toList());

        // divide into ad hoc searches (null set name) and the rest
        Map<Boolean, List<KeywordHitEvent>> keywordHitEvts = keywordHitsMap.entrySet().stream()
                .flatMap(entry -> {
                    KeywordHitSearchParam params = entry.getKey();
                    String setName = params.getSetName();
                    String searchString = params.getRegex();
                    TskData.KeywordSearchQueryType queryType = params.getSearchType();
                    String match = params.getKeyword();
                    return entry.getValue().stream().map(dsId -> new KeywordHitEvent(setName, searchString, queryType, match, params.getConfiguration(), dsId));
                })
                .collect(Collectors.partitioningBy(kwe -> kwe.getSetName() == null));

        // include config results in regular events.
        List<AnalysisResultEvent> daoEvents = Stream.of(AnalysisResultEvents, keywordHitEvts.get(false))
                .filter(lst -> lst != null)
                .flatMap(s -> s.stream())
                .collect(Collectors.toList());

        // send immediate updates to tree if ingest is not running
        Collection<TreeEvent> treeEvents;
        if (ingestIsRunning) {
            treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                    .map(arEvt -> new TreeEvent(getTreeItem(arEvt, TreeDisplayCount.INDETERMINATE), false))
                    .collect(Collectors.toList());
        } else {
            treeEvents = daoEvents.stream()
                    .map(arEvt -> new TreeEvent(getTreeItem(arEvt, TreeDisplayCount.UNSPECIFIED), true))
                    .collect(Collectors.toList());
        }

        List<KeywordHitEvent> adHocEvts = keywordHitEvts.get(true);
        if (CollectionUtils.isEmpty(adHocEvts)) {
            adHocEvts = Collections.emptyList();
        }

        // ad hoc events are always immediate updates.
        Collection<TreeEvent> adHocTreeEvents = adHocEvts.stream()
                .map(kwEvt -> new TreeEvent(getTreeItem(kwEvt, TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toList());

        return Stream.of(daoEvents, treeEvents, adHocEvts, adHocTreeEvents)
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toSet());
    }

    /**
     * Creates a TreeItemDTO instance based on the analysis result event and
     * whether or not this event should trigger a full refresh of counts.
     *
     * @param arEvt        The analysis result event.
     * @param displayCount The count to display.
     *
     * @return The tree event.
     */
    private TreeItemDTO<?> getTreeItem(AnalysisResultEvent arEvt, TreeDisplayCount displayCount) {
        if (arEvt instanceof KeywordHitEvent) {
            KeywordHitEvent khEvt = (KeywordHitEvent) arEvt;
            return createKWHitsTreeItem(
                    khEvt.getDataSourceId(),
                    khEvt.getSetName(),
                    khEvt.getMatch(),
                    khEvt.getSearchString(),
                    khEvt.getSearchType(),
                    khEvt.getConfiguration(),
                    displayCount
            );
        } else {
            return getConfigTreeItem(
                    arEvt.getArtifactType(),
                    arEvt.getDataSourceId(),
                    arEvt.getConfiguration(),
                    StringUtils.isBlank(arEvt.getConfiguration()) ? arEvt.getArtifactType().getDisplayName() : arEvt.getConfiguration(),
                    displayCount);
        }
    }

    private TreeItemDTO<AnalysisResultSearchParam> getConfigTreeItem(BlackboardArtifact.Type type,
            Long dataSourceId, String configuration, String displayName, TreeDisplayCount displayCount) {

        return new TreeItemDTO<>(
                AnalysisResultSearchParam.getTypeId(),
                new AnalysisResultSearchParam(type, configuration, dataSourceId),
                configuration == null ? 0 : configuration,
                displayName,
                displayCount);
    }

    private TreeItemDTO<KeywordListSearchParam> getKeywordListTreeItem(
            Long dataSourceId, String setName, String displayName, TreeDisplayCount displayCount) {

        return new TreeItemDTO<>(
                KeywordListSearchParam.getTypeId(),
                // there are one to many for keyword lists to configuration so leave as null
                new KeywordListSearchParam(dataSourceId, null, setName),
                setName == null ? 0 : setName,
                displayName,
                displayCount);
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return SubDAOUtils.getIngestCompleteEvents(this.treeCounts, (arEvt, count) -> getTreeItem(arEvt, count));
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(this.treeCounts, (arEvt, count) -> getTreeItem(arEvt, count));

    }

    /**
     * Returns all the configurations for keyword hits for the given filtering
     * parameters.
     *
     * @param setName      The set name as defined by TSK_SET_NAME. If null,
     *                     assumed to be ad hoc result.
     * @param dataSourceId The data source object id. If null, no filtering by
     *                     data source occurs.
     *
     * @return The distinct configurations.
     *
     * @throws ExecutionException
     */
    public List<String> getKeywordHitConfigurations(String setName, Long dataSourceId) throws ExecutionException {
        String kwHitClause = "art.artifact_type_id = " + BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID();

        String setNameClause = setName == null
                // if set name is null, then there should be no set name attribute associated with this 
                ? "(SELECT "
                + " COUNT(*) FROM blackboard_attributes attr "
                + " WHERE attr.artifact_id = art.artifact_id "
                + " AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID()
                + " AND attr.value_text IS NOT NULL "
                + " AND attr.value_text <> '' "
                + " LIMIT 1) = 0 "
                // otherwise, see if the set name attribute matches expected value
                : "? IN (SELECT attr.value_text FROM blackboard_attributes attr "
                + " WHERE attr.artifact_id = art.artifact_id "
                + " AND attr.attribute_type_id = " + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID()
                + " )";

        String dataSourceClause = dataSourceId == null
                ? null
                : "art.data_source_obj_id = ?";

        String clauses = Stream.of(kwHitClause, setNameClause, dataSourceClause)
                .filter(s -> s != null)
                .map(s -> " (" + s + ") ")
                .collect(Collectors.joining("AND\n"));

        String query = "DISTINCT(ar.configuration) AS configuration \n"
                + "FROM tsk_analysis_results ar\n"
                + "LEFT JOIN blackboard_artifacts art ON ar.artifact_obj_id = art.artifact_obj_id\n"
                + "WHERE " + clauses;

        // get artifact types and counts
        try (CaseDbPreparedStatement preparedStatement = getCase().getCaseDbAccessManager().prepareSelect(query)) {

            int paramIdx = 0;

            if (setName != null) {
                preparedStatement.setString(++paramIdx, setName);
            }

            if (dataSourceId != null) {
                preparedStatement.setLong(++paramIdx, dataSourceId);
            }

            List<String> configurations = new ArrayList<>();
            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        configurations.add(resultSet.getString("configuration"));
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching results from result set.", ex);
                }
            });

            return configurations;

        } catch (SQLException | NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException(MessageFormat.format(
                    "An error occurred while fetching configurations for counts where setName = {0}",
                    setName == null ? "<null>" : setName),
                    ex);
        }
    }

    /**
     * A tree item for an analysis result that can indicate if it has child tree
     * nodes due to configuration.
     */
    public static class AnalysisResultTreeItem extends TreeItemDTO<AnalysisResultSearchParam> {

        private final Optional<Boolean> hasChildren;

        public AnalysisResultTreeItem(BlackboardArtifact.Type type, String configuration, Long dataSourceId, TreeDisplayCount displayCount, Boolean hasChildren) {
            super(AnalysisResultSearchParam.getTypeId(),
                    new AnalysisResultSearchParam(type, configuration, dataSourceId),
                    type.getTypeID(),
                    type.getDisplayName(),
                    displayCount);

            this.hasChildren = Optional.ofNullable(hasChildren);
        }

        /**
         * @return Present if known; true if there are nested tree children.
         */
        public Optional<Boolean> getHasChildren() {
            return hasChildren;
        }
    }

    /**
     * Handles fetching and paging of analysis results.
     */
    public static class AnalysisResultFetcher extends DAOFetcher<AnalysisResultSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AnalysisResultFetcher(AnalysisResultSearchParam params) {
            super(params);
        }

        protected AnalysisResultDAO getDAO() {
            return MainDAO.getInstance().getAnalysisResultDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getAnalysisResultsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isAnalysisResultsInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of configuration filtered results.
     */
    public static class AnalysisResultConfigFetcher extends DAOFetcher<AnalysisResultSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AnalysisResultConfigFetcher(AnalysisResultSearchParam params) {
            super(params);
        }

        protected AnalysisResultDAO getDAO() {
            return MainDAO.getInstance().getAnalysisResultDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getAnalysisResultConfigResults(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isAnalysisResultsConfigInvalidating(this.getParameters(), evt);
        }
    }

    /**
     * Handles fetching and paging of keyword hits.
     */
    public static class KeywordHitResultFetcher extends DAOFetcher<KeywordHitSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public KeywordHitResultFetcher(KeywordHitSearchParam params) {
            super(params);
        }

        protected AnalysisResultDAO getDAO() {
            return MainDAO.getInstance().getAnalysisResultDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getKeywordHitsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isKeywordHitInvalidating(this.getParameters(), evt);
        }
    }
}
