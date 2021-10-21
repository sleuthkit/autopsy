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

import com.google.common.collect.ImmutableList;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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

    // TODO We can probably combine all the caches at some point
    private final AnalysisResultCache analysisResultCache = new AnalysisResultCache();
    private final AnalysisResultSetCache<HashHitSearchParam> hashHitCache = new AnalysisResultSetCache<>(BlackboardArtifact.Type.TSK_HASHSET_HIT);
    private final AnalysisResultSetCache<KeywordHitSearchParam> keywordHitCache = new AnalysisResultSetCache<>(BlackboardArtifact.Type.TSK_KEYWORD_HIT);

    private final List<EventUpdatableCache<?, ?, ModuleDataEvent>> caches = ImmutableList.of(analysisResultCache, hashHitCache, keywordHitCache);

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

    @Override
    protected void dropCache() {
        caches.forEach((cache) -> cache.invalidateAll());
    }

    @Override
    protected void onModuleData(ModuleDataEvent evt) {
        caches.forEach((cache) -> cache.invalidate(evt));
    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultsForTable(AnalysisResultSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        return analysisResultCache.getValue(artifactKey);
    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultsForTable(AnalysisResultSearchParam artifactKey, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        return analysisResultCache.getValue(artifactKey, hardRefresh);
    }

    public boolean isAnalysisResultsInvalidating(AnalysisResultSearchParam artifactKey, ModuleDataEvent evt) {
        return analysisResultCache.isInvalidatingEvent(artifactKey, evt);
    }

    public AnalysisResultTableSearchResultsDTO getHashHitsForTable(HashHitSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        return hashHitCache.getValue(artifactKey);
    }

    public AnalysisResultTableSearchResultsDTO getHashHitsForTable(HashHitSearchParam artifactKey, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        return hashHitCache.getValue(artifactKey, hardRefresh);
    }

    public boolean isHashHitsInvalidating(HashHitSearchParam artifactKey, ModuleDataEvent evt) {
        return hashHitCache.isInvalidatingEvent(artifactKey, evt);
    }

    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        return keywordHitCache.getValue(artifactKey);
    }

    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        return keywordHitCache.getValue(artifactKey, hardRefresh);
    }

    public boolean isKeywordHitsInvalidating(KeywordHitSearchParam artifactKey, ModuleDataEvent evt) {
        return keywordHitCache.isInvalidatingEvent(artifactKey, evt);
    }

    private class AnalysisResultCache extends EventUpdatableCache<AnalysisResultSearchParam, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> {

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

            List<BlackboardArtifact> pagedArtifacts = getPaged(arts, cacheKey);

            TableData tableData = createTableData(artType, pagedArtifacts);

            return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), arts.size());
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
            EventUpdatableCache<K, AnalysisResultTableSearchResultsDTO, ModuleDataEvent> {

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

            List<BlackboardArtifact> pagedArtifacts = getPaged(arts, cacheKey);

            TableData tableData = createTableData(artType, pagedArtifacts);

            return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), arts.size());
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
