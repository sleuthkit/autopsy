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
import com.google.common.collect.ImmutableList;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * DAO for providing data about analysis results to populate the results viewer.
 */
public class AnalysisResultDAO extends BlackboardArtifactDAO {

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
        "AnalysisResultDAO.columnKeys.configuration.description=Configuration"
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

    synchronized static AnalysisResultDAO getInstance() {
        if (instance == null) {
            instance = new AnalysisResultDAO();
        }
        return instance;
    }

    // TODO We can probably combine all the caches at some point
    private final AnalysisResultCache analysisResultCache = new AnalysisResultCache();
    private final AnalysisResultSetCache<HashHitSearchParam> hashHitCache = new AnalysisResultSetCache<>(BlackboardArtifact.Type.TSK_HASHSET_HIT);
    private final AnalysisResultSetCache<KeywordHitSearchParam> keywordHitCache = new AnalysisResultSetCache<>(BlackboardArtifact.Type.TSK_KEYWORD_HIT);

    private final List<EventUpdatableCacheImpl<?, ?, ModuleDataEvent>> caches = ImmutableList.of(analysisResultCache, hashHitCache, keywordHitCache);

    @Override
    void addAnalysisResultColumnKeys(List<ColumnKey> columnKeys) {
        // Make sure these are in the same order as in addAnalysisResultFields()
        columnKeys.add(SCORE_COL);
        columnKeys.add(CONCLUSION_COL);
        columnKeys.add(CONFIGURATION_COL);
        columnKeys.add(JUSTIFICATION_COL);
    }

    @Override
    void addAnalysisResultFields(BlackboardArtifact artifact, List<Object> cells) {
        if (!(artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not add fields for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }

        // Make sure these are in the same order as in addAnalysisResultColumnKeys()
        AnalysisResult analysisResult = (AnalysisResult) artifact;
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

    @Override
    protected void dropCache() {
        caches.forEach((cache) -> cache.invalidateAll());
    }

    @Override
    protected void onModuleData(ModuleDataEvent evt) {
        caches.forEach((cache) -> cache.invalidate(evt));
    }

    public EventUpdatableCache<AnalysisResultSearchParam, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> getAnalysisResultsCache() {
        return this.analysisResultCache;
    }

    public EventUpdatableCache<HashHitSearchParam, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> getHashHitsCache() {
        return this.hashHitCache;
    }

    public EventUpdatableCache<KeywordHitSearchParam, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> getKeywordHitsCache() {
        return this.keywordHitCache;
    }

    private class AnalysisResultCache extends EventUpdatableCacheImpl<AnalysisResultSearchParam, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> {

        @Override
        protected AnalysisResultTableSearchResultsDTO fetch(AnalysisResultSearchParam cacheKey) throws Exception {
            SleuthkitCase skCase = getCase();
            Blackboard blackboard = skCase.getBlackboard();

            Long dataSourceId = cacheKey.getDataSourceId();
            BlackboardArtifact.Type artType = cacheKey.getArtifactType();

            // get analysis results
            List<BlackboardArtifact> arts = new ArrayList<>();
            if (dataSourceId != null) {
                arts.addAll(blackboard.getAnalysisResultsByType(artType.getTypeID(), dataSourceId));
            } else {
                arts.addAll(blackboard.getAnalysisResultsByType(artType.getTypeID()));
            }

            TableData tableData = createTableData(artType, arts);

            return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows);
        }

        @Override
        public boolean isInvalidatingEvent(AnalysisResultSearchParam key, ModuleDataEvent eventData) {
            return key.getArtifactType().equals(eventData.getBlackboardArtifactType());
        }

        @Override
        protected void validateCacheKey(AnalysisResultSearchParam artifactKey) throws IllegalArgumentException {
            BlackboardArtifact.Type artType = artifactKey.getArtifactType();

            if (artType == null
                    || artType.getCategory() != BlackboardArtifact.Category.ANALYSIS_RESULT
                    || artType.getTypeID() == BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID()
                    || artType.getTypeID() == BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID()
                    || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
                throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                        + "Artifact type must be non-null and analysis result.  Data source id must be null or > 0.  "
                        + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
            }
        }

        @Override
        protected boolean isCacheRelevantEvent(ModuleDataEvent eventData) {
            return eventData.getBlackboardArtifactType().getCategory() == BlackboardArtifact.Category.ANALYSIS_RESULT
                    && BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID() != eventData.getBlackboardArtifactType().getTypeID()
                    && BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID() != eventData.getBlackboardArtifactType().getTypeID();
        }
    }

    private class AnalysisResultSetCache<K extends AnalysisResultSetSearchParam> extends
            EventUpdatableCacheImpl<K, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> {

        private final BlackboardArtifact.Type artifactType;

        AnalysisResultSetCache(BlackboardArtifact.Type artifactType) {
            this.artifactType = artifactType;
        }

        @Override
        protected AnalysisResultTableSearchResultsDTO fetch(K cacheKey) throws Exception {
            SleuthkitCase skCase = getCase();
            Blackboard blackboard = skCase.getBlackboard();

            Long dataSourceId = cacheKey.getDataSourceId();
            BlackboardArtifact.Type artType = cacheKey.getArtifactType();

            // Get all hash set hits
            List<AnalysisResult> allHashHits;
            if (dataSourceId != null) {
                allHashHits = blackboard.getAnalysisResultsByType(artType.getTypeID(), dataSourceId);
            } else {
                allHashHits = blackboard.getAnalysisResultsByType(artType.getTypeID());
            }

            // Filter for the selected set
            List<BlackboardArtifact> arts = new ArrayList<>();
            for (AnalysisResult art : allHashHits) {
                BlackboardAttribute setNameAttr = art.getAttribute(BlackboardAttribute.Type.TSK_SET_NAME);
                if ((setNameAttr != null) && cacheKey.getSetName().equals(setNameAttr.getValueString())) {
                    arts.add(art);
                }
            }

            TableData tableData = createTableData(artType, arts);

            return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows);
        }

        @Override
        public boolean isInvalidatingEvent(K key, ModuleDataEvent eventData) {
            return key.getArtifactType().equals(this.artifactType);
        }

        @Override
        protected void validateCacheKey(K artifactKey) throws IllegalArgumentException {
            BlackboardArtifact.Type artType = artifactKey.getArtifactType();

            if (artType == null || !this.artifactType.equals(artType)) {
                throw new IllegalArgumentException(MessageFormat.format("Expected artifact type of: {0} but received: {1}",
                        this.artifactType.getDisplayName(), artType == null ? "<null>" : artType.getDisplayName()));

            } else if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
                throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                        + "Data source id must be null or > 0.  "
                        + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
            }
        }
    }
}
