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

import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * DAO for providing data about data artifacts to populate the results viewer.
 */
public class DataArtifactDAO extends BlackboardArtifactDAO implements EventUpdatableCache<DataArtifactSearchParam, DataArtifactTableSearchResultsDTO, ModuleDataEvent> {

    private static DataArtifactDAO instance = null;

    synchronized static DataArtifactDAO getInstance() {
        if (instance == null) {
            instance = new DataArtifactDAO();
        }

        return instance;
    }

    private final DataArtifactCache dataArtifactCache = new DataArtifactCache();

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof DataArtifact)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be a data artifact");
        }
        return new DataArtifactRowDTO((DataArtifact) artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id);
    }

    @Override
    protected void dropCache() {
        this.dataArtifactCache.invalidateAll();
    }

    @Override
    protected void onModuleData(ModuleDataEvent evt) {
        this.dataArtifactCache.invalidate(evt);
    }

    @Override
    public DataArtifactTableSearchResultsDTO getValue(DataArtifactSearchParam key) throws IllegalArgumentException, ExecutionException {
        return dataArtifactCache.getValue(key);
    }

    @Override
    public DataArtifactTableSearchResultsDTO getValue(DataArtifactSearchParam key, boolean hardRefresh) throws IllegalArgumentException, ExecutionException {
        return dataArtifactCache.getValue(key, hardRefresh);
    }

    @Override
    public boolean isInvalidatingEvent(DataArtifactSearchParam key, ModuleDataEvent eventData) {
        return dataArtifactCache.isInvalidatingEvent(key, eventData);
    }

    private class DataArtifactCache extends EventUpdatableCacheImpl<DataArtifactSearchParam, DataArtifactTableSearchResultsDTO, ModuleDataEvent> {

        @Override
        protected DataArtifactTableSearchResultsDTO fetch(DataArtifactSearchParam cacheKey) throws Exception {
            Blackboard blackboard = getCase().getBlackboard();

            Long dataSourceId = cacheKey.getDataSourceId();
            BlackboardArtifact.Type artType = cacheKey.getArtifactType();

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
        public boolean isInvalidatingEvent(DataArtifactSearchParam key, ModuleDataEvent eventData) {
            return key.getArtifactType().equals(eventData.getBlackboardArtifactType());
        }

        @Override
        protected void validateCacheKey(DataArtifactSearchParam key) throws IllegalArgumentException {
            BlackboardArtifact.Type artType = key.getArtifactType();

            if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                    || (key.getDataSourceId() != null && key.getDataSourceId() < 0)) {
                throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                        + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                        + "Received artifact type: {0}; data source id: {1}", artType, key.getDataSourceId() == null ? "<null>" : key.getDataSourceId()));
            }
        }

        @Override
        protected boolean isCacheRelevantEvent(ModuleDataEvent eventData) {
            return eventData.getBlackboardArtifactType().getCategory() == Category.DATA_ARTIFACT;
        }
    }
}
