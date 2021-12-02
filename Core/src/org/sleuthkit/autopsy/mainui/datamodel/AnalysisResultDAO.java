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

import org.sleuthkit.autopsy.mainui.datamodel.events.AnalysisResultSetEvent;
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
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.python.google.common.collect.ImmutableSet;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.KeywordHitEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbPreparedStatement;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HostAddress;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

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

    @SuppressWarnings("deprecation")
    private static final Set<Integer> STANDARD_SET_TYPES = ImmutableSet.of(
            BlackboardArtifact.Type.TSK_INTERESTING_ITEM.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID(),
            BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID()
    );

    // TODO We can probably combine all the caches at some point
    private final Cache<SearchParams<BlackboardArtifactSearchParam>, AnalysisResultTableSearchResultsDTO> analysisResultCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<SearchParams<AnalysisResultSetSearchParam>, AnalysisResultTableSearchResultsDTO> setHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<SearchParams<KeywordHitSearchParam>, AnalysisResultTableSearchResultsDTO> keywordHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();

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

    private AnalysisResultTableSearchResultsDTO fetchSetNameHitsForTable(SearchParams<? extends AnalysisResultSetSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        // We currently can't make a query on the set name field because need to use a prepared statement
        String originalWhereClause = " artifacts.artifact_type_id = " + artType.getTypeID() + " ";
        if (dataSourceId != null) {
            originalWhereClause += " AND artifacts.data_source_obj_id = " + dataSourceId + " ";
        }

        String expectedSetName = cacheKey.getParamData().getSetName();

        List<BlackboardArtifact> allHashHits = new ArrayList<>();
        allHashHits.addAll(blackboard.getAnalysisResultsWhere(originalWhereClause));
        blackboard.loadBlackboardAttributes(allHashHits);

        // Filter for the selected set
        List<BlackboardArtifact> arts = new ArrayList<>();
        for (BlackboardArtifact art : allHashHits) {
            BlackboardAttribute setNameAttr = art.getAttribute(BlackboardAttribute.Type.TSK_SET_NAME);
            if ((expectedSetName == null && setNameAttr == null)
                    || (expectedSetName != null && setNameAttr != null && expectedSetName.equals(setNameAttr.getValueString()))) {
                arts.add(art);
            }
        }

        List<BlackboardArtifact> pagedArtifacts = getPaged(arts, cacheKey);
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), arts.size());
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

    /**
     * Returns a displayable type string for the given content object.
     *
     * If the content object is a artifact of a custom type then this method may
     * cause a DB call BlackboardArtifact.getType
     *
     * @param source The object to determine the type of.
     *
     * @return A string representing the content type.
     */
    private String getSourceObjType(Content source) throws TskCoreException {
        if (source instanceof BlackboardArtifact) {
            BlackboardArtifact srcArtifact = (BlackboardArtifact) source;
            return srcArtifact.getType().getDisplayName();
        } else if (source instanceof Volume) {
            return TskData.ObjectType.VOL.toString();
        } else if (source instanceof AbstractFile) {
            return TskData.ObjectType.ABSTRACTFILE.toString();
        } else if (source instanceof Image) {
            return TskData.ObjectType.IMG.toString();
        } else if (source instanceof VolumeSystem) {
            return TskData.ObjectType.VS.toString();
        } else if (source instanceof OsAccount) {
            return TskData.ObjectType.OS_ACCOUNT.toString();
        } else if (source instanceof HostAddress) {
            return TskData.ObjectType.HOST_ADDRESS.toString();
        } else if (source instanceof Pool) {
            return TskData.ObjectType.POOL.toString();
        }
        return "";
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
        if (!(eventData instanceof AnalysisResultEvent)) {
            return false;
        }

        AnalysisResultEvent analysisResultEvt = (AnalysisResultEvent) eventData;
        return key.getArtifactType().getTypeID() == analysisResultEvt.getArtifactType().getTypeID()
                && (key.getDataSourceId() == null || key.getDataSourceId() == analysisResultEvt.getDataSourceId());
    }

    private boolean isAnalysisResultsSetInvalidating(AnalysisResultSetSearchParam key, DAOEvent event) {
        if (!(event instanceof AnalysisResultSetEvent)) {
            return false;
        }

        AnalysisResultSetEvent setEvent = (AnalysisResultSetEvent) event;
        return isAnalysisResultsInvalidating((AnalysisResultSearchParam) key, (AnalysisResultEvent) setEvent)
                && Objects.equals(key.getSetName(), setEvent.getSetName());
    }

    // GVDTODO handle keyword hits
    public AnalysisResultTableSearchResultsDTO getAnalysisResultSetHits(AnalysisResultSetSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<AnalysisResultSetSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return setHitCache.get(searchParams, () -> fetchSetNameHitsForTable(searchParams));
    }

    // TODO - JIRA-8117
    // This needs to use more than just the set name
    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<KeywordHitSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return keywordHitCache.get(searchParams, () -> fetchSetNameHitsForTable(searchParams));
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
            Map<BlackboardArtifact.Type, Long> typeCounts = getCounts(BlackboardArtifact.Category.ANALYSIS_RESULT, dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<AnalysisResultSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        TreeDisplayCount displayCount = indeterminateTypes.contains(entry.getKey())
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(entry.getValue());

                        return getTreeItem(entry.getKey(), dataSourceId, displayCount);
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching analysis result counts.", ex);
        }
    }

    private TreeItemDTO<AnalysisResultSearchParam> getTreeItem(BlackboardArtifact.Type type, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                AnalysisResultSearchParam.getTypeId(),
                new AnalysisResultSearchParam(type, dataSourceId),
                type.getTypeID(),
                type.getDisplayName(),
                displayCount);
    }

    /**
     *
     * @param type         The artifact type to filter on.
     * @param setNameAttr  The blackboard attribute denoting the set name.
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return A mapping of set names to their counts.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    Map<String, Long> getSetCountsMap(BlackboardArtifact.Type type, BlackboardAttribute.Type setNameAttr, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        if (dataSourceId != null && dataSourceId <= 0) {
            throw new IllegalArgumentException("Expected data source id to be > 0");
        }

        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = " res.set_name, COUNT(*) AS count \n"
                    + "FROM ( \n"
                    + "  SELECT art.artifact_id, \n"
                    + "  (SELECT value_text \n"
                    + "    FROM blackboard_attributes attr \n"
                    + "    WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = " + setNameAttr.getTypeID() + " LIMIT 1) AS set_name \n"
                    + "	 FROM blackboard_artifacts art \n"
                    + "	 WHERE  art.artifact_type_id = " + type.getTypeID() + " \n"
                    + ((dataSourceId == null) ? "" : "  AND art.data_source_obj_id = " + dataSourceId + " \n")
                    + ") res \n"
                    + "GROUP BY res.set_name";

            Map<String, Long> setCounts = new HashMap<>();
            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String setName = resultSet.getString("set_name");
                        long count = resultSet.getLong("count");
                        setCounts.put(setName, count);
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching set name counts.", ex);
                }
            });

            return setCounts;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching set counts", ex);
        }
    }

    /**
     * Get counts for individual sets of the provided type to be used in the
     * tree view.
     *
     * @param type         The blackboard artifact type.
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
    public TreeResultsDTO<AnalysisResultSetSearchParam> getSetCounts(
            BlackboardArtifact.Type type,
            Long dataSourceId,
            String nullSetName) throws IllegalArgumentException, ExecutionException {

        Set<String> indeterminateSetNames = new HashSet<>();
        for (AnalysisResultEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof AnalysisResultSetEvent
                    && (dataSourceId == null || Objects.equals(evt.getDataSourceId(), dataSourceId))
                    && evt.getArtifactType().equals(type)) {
                indeterminateSetNames.add(((AnalysisResultSetEvent) evt).getSetName());
            }
        }

        List<TreeItemDTO<AnalysisResultSetSearchParam>> allSets
                = getSetCountsMap(type, BlackboardAttribute.Type.TSK_SET_NAME, dataSourceId).entrySet().stream()
                        .filter(entry -> nullSetName != null || entry.getKey() != null)
                        .sorted((a, b) -> compareSetStrings(a.getKey(), b.getKey()))
                        .map(entry -> {
                            TreeDisplayCount displayCount = indeterminateSetNames.contains(entry.getKey())
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeDisplayCount.getDeterminate(entry.getValue());

                            return getSetTreeItem(type,
                                    dataSourceId,
                                    entry.getKey(),
                                    entry.getKey() == null ? nullSetName : entry.getKey(),
                                    displayCount);
                        })
                        .collect(Collectors.toList());

        return new TreeResultsDTO<>(allSets);
    }

    private TreeItemDTO<AnalysisResultSetSearchParam> getSetTreeItem(BlackboardArtifact.Type type,
            Long dataSourceId, String setName, String displayName, TreeDisplayCount displayCount) {

        return new TreeItemDTO<>(
                AnalysisResultSetSearchParam.getTypeId(),
                new AnalysisResultSetSearchParam(type, dataSourceId, setName),
                setName == null ? 0 : setName,
                displayName,
                displayCount);
    }

    /**
     * Compares set strings to properly order for the tree.
     *
     * @param a The first string.
     * @param b The second string.
     *
     * @return The comparator result.
     */
    private int compareSetStrings(String a, String b) {
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

        Set<Pair<String, Integer>> indeterminateSearchTerms = new HashSet<>();
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
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID() + " LIMIT 1) AS set_name,\n"
                + "    (SELECT value_int32 FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID() + " LIMIT 1) AS search_type,\n"
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_REGEXP.getTypeID() + " LIMIT 1) AS regexp_str,\n"
                + "    (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD.getTypeID() + " LIMIT 1) AS keyword\n"
                + "    FROM blackboard_artifacts art\n"
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

                        String searchTermModified = getSearchTermDisplayName(searchTerm, searchType);

                        TreeDisplayCount displayCount = indeterminateSearchTerms.contains(Pair.of(searchTerm, searchType))
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(count);

                        TreeItemDTO<KeywordSearchTermParams> treeItem = new TreeItemDTO<>(
                                KeywordSearchTermParams.getTypeId(),
                                new KeywordSearchTermParams(setName, searchTerm, searchType, hasChildren, dataSourceId),
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
    public String getSearchTermDisplayName(String searchTerm, int searchType) {
        String searchTermModified;
        switch (searchType) {
            case 0:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_exactMatch(searchTerm == null ? "" : searchTerm);
                break;
            case 1:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_substringMatch(searchTerm == null ? "" : searchTerm);
                break;
            case 2:
                searchTermModified = Bundle.AnalysisResultDAO_getKeywordSearchTermCounts_regexMatch(searchTerm == null ? "" : searchTerm);
                break;
            default:
                logger.log(Level.WARNING, MessageFormat.format("Non-standard search type value: {0}.", searchType));
                searchTermModified = searchTerm;
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
    public TreeResultsDTO<? extends KeywordMatchParams> getKeywordMatchCounts(String setName, String regexStr, int searchType, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
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
                + "  COUNT(*) AS count \n"
                + "FROM (\n"
                + "  SELECT art.artifact_id, \n"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_SET_NAME.getTypeID() + " LIMIT 1) AS set_name,\n"
                + "  (SELECT value_int32 FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID() + " LIMIT 1) AS search_type,\n"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD_REGEXP.getTypeID() + " LIMIT 1) AS regexp_str,\n"
                + "  (SELECT value_text FROM blackboard_attributes attr WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = "
                + BlackboardAttribute.Type.TSK_KEYWORD.getTypeID() + " LIMIT 1) AS keyword\n"
                + "  FROM blackboard_artifacts art\n"
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
            preparedStatement.setInt(++paramIdx, searchType);

            List<TreeItemDTO<KeywordMatchParams>> items = new ArrayList<>();
            getCase().getCaseDbAccessManager().select(preparedStatement, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        String keyword = resultSet.getString("keyword");
                        long count = resultSet.getLong("count");

                        TreeDisplayCount displayCount = indeterminateMatches.contains(keyword)
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(count);

                        items.add(new TreeItemDTO<>(
                                KeywordMatchParams.getTypeId(),
                                new KeywordMatchParams(setName, regexStr, keyword, searchType, dataSourceId),
                                keyword,
                                keyword == null ? "" : keyword,
                                displayCount
                        ));
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

    @Override
    void clearCaches() {
        this.analysisResultCache.invalidateAll();
        this.keywordHitCache.invalidateAll();
        this.setHitCache.invalidateAll();
        this.handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        Map<BlackboardArtifact.Type, Set<Long>> analysisResultMap = new HashMap<>();
        Map<Pair<BlackboardArtifact.Type, String>, Set<Long>> setMap = new HashMap<>();
        Map<KeywordMatchParams, Set<Long>> keywordHitsMap = new HashMap<>();

        ModuleDataEvent dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt);
        if (dataEvt != null) {
            for (BlackboardArtifact art : dataEvt.getArtifacts()) {
                try {
                    if (art.getArtifactTypeID() == BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID()) {
                        // GVDTODO handle keyword hits
                    } else if (STANDARD_SET_TYPES.contains(art.getArtifactTypeID())) {
                        BlackboardAttribute setAttr = art.getAttribute(BlackboardAttribute.Type.TSK_SET_NAME);
                        String setName = setAttr == null ? null : setAttr.getValueString();
                        setMap.computeIfAbsent(Pair.of(art.getType(), setName), (k) -> new HashSet<>())
                                .add(art.getDataSourceObjectID());

                    } else if (BlackboardArtifact.Category.ANALYSIS_RESULT.equals(art.getType().getCategory())) {
                        analysisResultMap.computeIfAbsent(art.getType(), (k) -> new HashSet<>())
                                .add(art.getDataSourceObjectID());
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to fetch necessary information for artifact id: " + art.getId(), ex);
                }
            }
        }

        // don't continue if no relevant items found
        if (analysisResultMap.isEmpty() && setMap.isEmpty() && keywordHitsMap.isEmpty()) {
            return Collections.emptySet();
        }

        invalidateKeys(this.analysisResultCache, ar -> Pair.of(ar.getArtifactType(), ar.getDataSourceId()), analysisResultMap);
        invalidateKeys(this.setHitCache, ar -> Pair.of(Pair.of(ar.getArtifactType(), ar.getSetName()), ar.getDataSourceId()), setMap);

        // GVDTODO handle keyword hits
        return getResultViewEvents(analysisResultMap, setMap);
    }

    /**
     * Generate result view events from digest of Autopsy events.
     *
     * @param analysisResultMap Contains the analysis results that do not use a
     *                          set name. A mapping of analysis result type ids
     *                          to data sources where the results were created.
     * @param resultsWithSetMap Contains the anlaysis results that do use a set
     *                          name. A mapping of (analysis result type id, set
     *                          name) to data sources where results were
     *                          created.
     *
     * @return The list of dao events.
     */
    private Set<? extends DAOEvent> getResultViewEvents(Map<BlackboardArtifact.Type, Set<Long>> analysisResultMap, Map<Pair<BlackboardArtifact.Type, String>, Set<Long>> resultsWithSetMap) {
        Stream<AnalysisResultEvent> analysisResultEvts = analysisResultMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new AnalysisResultEvent(entry.getKey(), dsId)));

        Stream<AnalysisResultEvent> analysisResultSetEvts = resultsWithSetMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(dsId -> new AnalysisResultSetEvent(entry.getKey().getRight(), entry.getKey().getLeft(), dsId)));

        // GVDTODO handle keyword hits
        
        List<AnalysisResultEvent> daoEvents = Stream.of(analysisResultEvts, analysisResultSetEvts)
                .flatMap(s -> s)
                .collect(Collectors.toList());

        Collection<TreeEvent> treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                .map(arEvt -> new TreeEvent(getTreeItem(arEvt, TreeDisplayCount.INDETERMINATE), false))
                .collect(Collectors.toList());

        return Stream.of(daoEvents, treeEvents)
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
        // GVDTODO handle keyword items when integrated
        if (arEvt instanceof AnalysisResultSetEvent) {
            AnalysisResultSetEvent setEvt = (AnalysisResultSetEvent) arEvt;
            return getSetTreeItem(setEvt.getArtifactType(), setEvt.getDataSourceId(),
                    setEvt.getSetName(), setEvt.getSetName() == null ? "" : setEvt.getSetName(),
                    displayCount);
        } else {
            return getTreeItem(arEvt.getArtifactType(), arEvt.getDataSourceId(), displayCount);
        }
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return getIngestCompleteEvents(this.treeCounts, (arEvt, count) -> getTreeItem(arEvt, count));
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return getRefreshEvents(this.treeCounts, (arEvt, count) -> getTreeItem(arEvt, count));

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
     * Handles fetching and paging of hashset hits.
     */
    public static class AnalysisResultSetFetcher extends DAOFetcher<AnalysisResultSetSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AnalysisResultSetFetcher(AnalysisResultSetSearchParam params) {
            super(params);
        }

        protected AnalysisResultDAO getDAO() {
            return MainDAO.getInstance().getAnalysisResultDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getAnalysisResultSetHits(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isAnalysisResultsSetInvalidating(this.getParameters(), evt);
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
            // GVDTODO
            return true;
        }
    }
}
