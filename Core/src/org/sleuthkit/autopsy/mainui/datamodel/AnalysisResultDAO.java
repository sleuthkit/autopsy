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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

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
    private final Cache<AnalysisResultSearchParam, AnalysisResultTableSearchResultsDTO> analysisResultCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<HashHitSearchParam, AnalysisResultTableSearchResultsDTO> hashHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<KeywordHitSearchParam, AnalysisResultTableSearchResultsDTO> keywordHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private AnalysisResultTableSearchResultsDTO fetchAnalysisResultsForTable(AnalysisResultSearchParam cacheKey) throws NoCurrentCaseException, TskCoreException {

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
    
    private AnalysisResultTableSearchResultsDTO fetchSetNameHitsForTable(AnalysisResultSetSearchParam cacheKey) throws NoCurrentCaseException, TskCoreException {

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
    void addAnalysisResultColumnKeys(List<ColumnKey> columnKeys) {
         // Make sure these are in the same order as in addAnalysisResultFields()
        columnKeys.add(SCORE_COL);
        columnKeys.add(CONCLUSION_COL);
        columnKeys.add(CONFIGURATION_COL);
        columnKeys.add(JUSTIFICATION_COL);
    }
        
    @Override
    void addAnalysisResultFields(BlackboardArtifact artifact, List<Object> cells) {
        if (! (artifact instanceof AnalysisResult)) {
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
        if (! (artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }
        return new AnalysisResultRowDTO((AnalysisResult)artifact, srcContent, isTimelineSupported, cellValues, id);
    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultsForTable(AnalysisResultSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.ANALYSIS_RESULT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and analysis result.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        return analysisResultCache.get(artifactKey, () -> fetchAnalysisResultsForTable(artifactKey));
    }
    
    public AnalysisResultTableSearchResultsDTO getHashHitsForTable(HashHitSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        return hashHitCache.get(artifactKey, () -> fetchSetNameHitsForTable(artifactKey));
    }    
    
    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        return keywordHitCache.get(artifactKey, () -> fetchSetNameHitsForTable(artifactKey));
    }  

    public void dropAnalysisResultCache() {
        analysisResultCache.invalidateAll();
    }
    
    public void dropHashHitCache() {
        hashHitCache.invalidateAll();
    }
    
    public void dropKeywordHitCache() {
        keywordHitCache.invalidateAll();
    }
    
    @Override
    protected void onDropCache() {
        dropAnalysisResultCache();
        dropHashHitCache();
        dropKeywordHitCache();
    }

    @Override
    protected void onModuleData(ModuleDataEvent evt) {
        // GVDTODO
    }
}
