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

import org.sleuthkit.autopsy.mainui.datamodel.events.DataArtifactEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEventTimedCache;
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

    private static Logger logger = Logger.getLogger(DataArtifactDAO.class.getName());

    private static DataArtifactDAO instance = null;

    synchronized static DataArtifactDAO getInstance() {
        if (instance == null) {
            instance = new DataArtifactDAO();
        }

        return instance;
    }

    /**
     * @return The set of types that are not shown in the tree.
     */
    public static Set<BlackboardArtifact.Type> getIgnoredTreeTypes() {
        return BlackboardArtifactDAO.getIgnoredTreeTypes();
    }

    private final TreeEventTimedCache<DataArtifactEvent> treeCache = new TreeEventTimedCache<>();
    private final Cache<SearchParams<BlackboardArtifactSearchParam>, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(SearchParams<BlackboardArtifactSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        String pagedWhereClause = getWhereClause(cacheKey);

        List<BlackboardArtifact> arts = new ArrayList<>();
        arts.addAll(blackboard.getDataArtifactsWhere(pagedWhereClause));
        blackboard.loadBlackboardAttributes(arts);

        long totalResultsCount = getTotalResultsCount(cacheKey, arts.size());

        TableData tableData = createTableData(artType, arts);
        return new DataArtifactTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), totalResultsCount);
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof DataArtifact)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be a data artifact");
        }
        return new DataArtifactRowDTO((DataArtifact) artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id);
    }

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactSearchParam artifactKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<BlackboardArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        return dataArtifactCache.get(searchParams, () -> fetchDataArtifactsForTable(searchParams));
    }

    private boolean isDataArtifactInvalidating(DataArtifactSearchParam key, DAOEvent eventData) {
        if (!(eventData instanceof DataArtifactEvent)) {
            return false;
        } else {
            DataArtifactEvent dataArtEvt = (DataArtifactEvent) eventData;
            return key.getArtifactType().getTypeID() == dataArtEvt.getArtifactType().getTypeID()
                    && (key.getDataSourceId() == null || (key.getDataSourceId() == dataArtEvt.getDataSourceId()));
        }
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
            // get row dto's sorted by display name
            Set<BlackboardArtifact.Type> indeterminateTypes = this.treeCache.getEnqueued().stream()
                    .filter(evt -> dataSourceId == null || evt.getDataSourceId() == dataSourceId)
                    .map(evt -> evt.getArtifactType())
                    .collect(Collectors.toSet());

            Map<BlackboardArtifact.Type, Long> typeCounts = getCounts(BlackboardArtifact.Category.DATA_ARTIFACT, dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<DataArtifactSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return getTreeItem(entry.getKey(), dataSourceId,
                                indeterminateTypes.contains(entry.getKey())
                                ? TreeDisplayCount.INDETERMINATE
                                : TreeDisplayCount.getDeterminate(entry.getValue()));
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    @Override
    void clearCaches() {
        this.dataArtifactCache.invalidateAll();
        this.flushEvents();
    }

    @Override
    List<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        // get a grouping of artifacts mapping the artifact type id to data source id.
        ModuleDataEvent dataEvt = DAOEventUtils.getModuleDataFromEvt(evt);
        if (dataEvt == null) {
            return Collections.emptyList();
        }

        Map<BlackboardArtifact.Type, Set<Long>> artifactTypeDataSourceMap = dataEvt.getArtifacts().stream()
                .map((art) -> {
                    try {
                        if (BlackboardArtifact.Category.DATA_ARTIFACT.equals(art.getType().getCategory())) {
                            return Pair.of(art.getType(), art.getDataSourceObjectID());
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Unable to fetch artifact category for artifact with id: " + art.getId(), ex);
                    }
                    return null;
                })
                .filter(pr -> pr != null)
                .collect(Collectors.groupingBy(pr -> pr.getKey(), Collectors.mapping(pr -> pr.getValue(), Collectors.toSet())));

        // don't do anything else if no relevant events
        if (artifactTypeDataSourceMap.isEmpty()) {
            return Collections.emptyList();
        }

        // invalidate cache entries that are affected by events
        ConcurrentMap<SearchParams<BlackboardArtifactSearchParam>, DataArtifactTableSearchResultsDTO> concurrentMap = this.dataArtifactCache.asMap();
        concurrentMap.forEach((k, v) -> {
            Set<Long> dsIds = artifactTypeDataSourceMap.get(k.getParamData().getArtifactType());
            if (dsIds != null) {
                Long searchDsId = k.getParamData().getDataSourceId();
                if (searchDsId == null || dsIds.contains(searchDsId)) {
                    concurrentMap.remove(k);
                }
            }
        });

        // gather dao events based on artifacts
        List<DataArtifactEvent> dataArtifactEvents = new ArrayList<>();
        for (Entry<BlackboardArtifact.Type, Set<Long>> entry : artifactTypeDataSourceMap.entrySet()) {
            BlackboardArtifact.Type artType = entry.getKey();
            for (Long dsObjId : entry.getValue()) {
                DataArtifactEvent newEvt = new DataArtifactEvent(artType, dsObjId);
                dataArtifactEvents.add(newEvt);
            }
        }

        List<TreeEvent> newTreeEvents = this.treeCache.enqueueAll(dataArtifactEvents).stream()
                .map(daoEvt -> new TreeEvent(getTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.INDETERMINATE), false))
                .collect(Collectors.toList());

        return Stream.of(dataArtifactEvents, newTreeEvents)
                .flatMap((lst) -> lst.stream())
                .collect(Collectors.toList());
    }

    private TreeItemDTO<DataArtifactSearchParam> getTreeItem(BlackboardArtifact.Type artifactType, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeResultsDTO.TreeItemDTO<>(
                BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                new DataArtifactSearchParam(artifactType, dataSourceId),
                artifactType.getTypeID(),
                artifactType.getDisplayName(),
                displayCount);
    }

    @Override
    Collection<? extends DAOEvent> flushEvents() {
        return this.treeCache.flushEvents().stream()
                .map(daoEvt -> new TreeEvent(getTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.INDETERMINATE), true))
                .collect(Collectors.toList());
    }

    @Override
    Collection<? extends TreeEvent> shouldRefreshTree() {
        return this.treeCache.getEventTimeouts().stream()
                .map(daoEvt -> new TreeEvent(getTreeItem(daoEvt.getArtifactType(), daoEvt.getDataSourceId(), TreeDisplayCount.INDETERMINATE), true))
                .collect(Collectors.toList());
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

        protected DataArtifactDAO getDAO() {
            return MainDAO.getInstance().getDataArtifactsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getDataArtifactsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isDataArtifactInvalidating(this.getParameters(), evt);
        }
    }
}
