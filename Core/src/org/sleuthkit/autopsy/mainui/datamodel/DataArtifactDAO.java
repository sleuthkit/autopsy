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
import java.sql.SQLException;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.python.google.common.collect.Sets;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_DATA_SOURCE_USAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_GEN_INFO;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_TL_EVENT;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * DAO for providing data about data artifacts to populate the results viewer.
 */
public class DataArtifactDAO extends BlackboardArtifactDAO {

    /**
     * Types that should not be shown in the tree.
     */
    @SuppressWarnings("deprecation")
    private static final Set<BlackboardArtifact.Type> IGNORED_TYPES = Sets.newHashSet(
            // these are shown in other parts of the UI (and different node types)
            TSK_DATA_SOURCE_USAGE,
            TSK_GEN_INFO,
            new BlackboardArtifact.Type(TSK_DOWNLOAD_SOURCE),
            TSK_TL_EVENT,
            //This is not meant to be shown in the UI at all. It is more of a meta artifact.
            TSK_ASSOCIATED_OBJECT
    );

    private static final String IGNORED_TYPES_SQL_SET = IGNORED_TYPES.stream()
            .map(tp -> Integer.toString(tp.getTypeID()))
            .collect(Collectors.joining(", "));

    private static Logger logger = Logger.getLogger(DataArtifactDAO.class.getName());

    private static DataArtifactDAO instance = null;

    synchronized static DataArtifactDAO getInstance() {
        if (instance == null) {
            instance = new DataArtifactDAO();
        }

        return instance;
    }

    private final Cache<SearchParams<DataArtifactSearchParam>, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(SearchParams<DataArtifactSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {
        Blackboard blackboard = getCase().getBlackboard();

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        // get analysis results
        List<BlackboardArtifact> arts = new ArrayList<>();
        if (dataSourceId != null) {
            arts.addAll(blackboard.getDataArtifacts(artType.getTypeID(), dataSourceId));
        } else {
            arts.addAll(blackboard.getDataArtifacts(artType.getTypeID()));
        }

        List<BlackboardArtifact> pagedArtifacts = getPaged(arts, cacheKey);
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new DataArtifactTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), arts.size());
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof DataArtifact)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be a data artifact");
        }
        return new DataArtifactRowDTO((DataArtifact) artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id);
    }

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactSearchParam artifactKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<DataArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        if (hardRefresh) {
            this.dataArtifactCache.invalidate(searchParams);
        }

        return dataArtifactCache.get(searchParams, () -> fetchDataArtifactsForTable(searchParams));
    }

    public boolean isDataArtifactInvalidating(DataArtifactSearchParam key, ModuleDataEvent eventData) {
        return key.getArtifactType().equals(eventData.getBlackboardArtifactType());
    }

    public void dropDataArtifactCache() {
        dataArtifactCache.invalidateAll();
    }

    /**
     * Returns a search results dto containing rows of counts data.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The results where rows are CountsRowDTO of
     *         DataArtifactSearchParam.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<DataArtifactSearchParam> getDataArtifactCounts(Long dataSourceId) throws ExecutionException {
        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = "artifact_type_id, COUNT(*) AS count "
                    + " FROM blackboard_artifacts "
                    + " WHERE artifact_type_id NOT IN (" + IGNORED_TYPES_SQL_SET + ") "
                    + " AND artifact_type_id IN "
                    + " (SELECT artifact_type_id FROM blackboard_artifact_types WHERE category_type = " + BlackboardArtifact.Category.DATA_ARTIFACT.getID() + ")"
                    + (dataSourceId == null ? "" : (" AND data_source_obj_id = " + dataSourceId + " "))
                    + " GROUP BY artifact_type_id";
            Map<BlackboardArtifact.Type, Long> typeCounts = new HashMap<>();
            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
                try {
                    while (resultSet.next()) {
                        int artifactTypeId = resultSet.getInt("artifact_type_id");
                        BlackboardArtifact.Type type = skCase.getBlackboard().getArtifactType(artifactTypeId);
                        long count = resultSet.getLong("count");
                        typeCounts.put(type, count);
                    }
                } catch (TskCoreException | SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
                }
            });

            // get row dto's sorted by display name
            List<TreeItemDTO<DataArtifactSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return new TreeItemDTO<>(
                                BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                                new DataArtifactSearchParam(entry.getKey(), dataSourceId),
                                entry.getKey().getTypeID(),
                                entry.getKey().getDisplayName(),
                                entry.getValue());
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    /*
     * Handles fetching and paging of data artifacts.
     */
    public static class DataArtifactFetcher extends DAOFetcher<DataArtifactSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public DataArtifactFetcher(DataArtifactSearchParam params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getDataArtifactsDAO().getDataArtifactsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            ModuleDataEvent dataEvent = this.getModuleDataFromEvt(evt);
            if (dataEvent == null) {
                return false;
            }

            return MainDAO.getInstance().getDataArtifactsDAO().isDataArtifactInvalidating(this.getParameters(), dataEvent);
        }
    }
}
