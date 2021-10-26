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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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

    private final Cache<DataArtifactSearchParam, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(DataArtifactSearchParam cacheKey) throws NoCurrentCaseException, TskCoreException {
        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = cacheKey.getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getArtifactType();

        // get data artifacts
        List<DataArtifact> arts = (dataSourceId != null)
                ? blackboard.getDataArtifacts(artType.getTypeID(), dataSourceId)
                : blackboard.getDataArtifacts(artType.getTypeID());

        Stream<DataArtifact> pagedStream = arts.stream()
                .sorted(Comparator.comparing(art -> art.getId()))
                .skip(cacheKey.getStartItem());

        if (cacheKey.getMaxResultsCount() != null) {
            pagedStream = pagedStream.limit(cacheKey.getMaxResultsCount());
        }

        List<DataArtifact> pagedArtifacts = pagedStream.collect(Collectors.toList());

        Map<Long, Map<BlackboardAttribute.Type, Object>> artifactAttributes = new HashMap<>();
        for (DataArtifact art : pagedArtifacts) {
            Map<BlackboardAttribute.Type, Object> attrs = art.getAttributes().stream()
                    .filter(attr -> isRenderedAttr(artType, attr.getAttributeType()))
                    .collect(Collectors.toMap(attr -> attr.getAttributeType(), attr -> getAttrValue(attr), (attr1, attr2) -> attr1));

            artifactAttributes.put(art.getId(), attrs);
        }

        // NOTE: this has to be in the same order as values are added
        List<BlackboardAttribute.Type> attributeTypeKeys = artifactAttributes.values().stream()
                .flatMap(attrs -> attrs.keySet().stream())
                .distinct()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        List<ColumnKey> columnKeys = new ArrayList<>();
        columnKeys.add(SRC_FILE_COL);
        // GVDTODO translated file name
        columnKeys.add(S_COL);
        // GVDTODO only show if central repository enabled
        columnKeys.add(C_COL);
        columnKeys.add(O_COL);
        columnKeys.addAll(attributeTypeKeys.stream()
                .map(attrType -> new ColumnKey(attrType.getTypeName(), attrType.getDisplayName(), attrType.getDisplayName()))
                .collect(Collectors.toList()));
        columnKeys.add(DATASOURCE_COL);

        // determine all different attribute types present as well as row data for each artifact
        List<RowDTO> rows = new ArrayList<>();

        for (DataArtifact artifact : pagedArtifacts) {
            List<Object> cellValues = new ArrayList<>();

            Content srcContent = artifact.getParent();
            cellValues.add(srcContent.getName());
            // GVDTODO handle translated filename here
            // GVDTODO handle SCO
            cellValues.add(null);
            cellValues.add(null);
            cellValues.add(null);

            long id = artifact.getId();
            Map<BlackboardAttribute.Type, Object> attrValues = artifactAttributes.getOrDefault(id, Collections.emptyMap());
            // NOTE: this has to be in the same order as attribute keys
            for (BlackboardAttribute.Type colAttrType : attributeTypeKeys) {
                cellValues.add(attrValues.get(colAttrType));
            }

            String dataSourceName = getDataSourceName(srcContent);
            cellValues.add(dataSourceName);

            Object linkedId = attrValues.get(BlackboardAttribute.Type.TSK_PATH_ID);
            AbstractFile linkedFile = linkedId instanceof Long && ((Long) linkedId) >= 0
                    ? skCase.getAbstractFileById((Long) linkedId)
                    : null;

            boolean isTimelineSupported = isTimelineSupported(attrValues.keySet());

            rows.add(new DataArtifactRowDTO(artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id));
        }

        return new DataArtifactTableSearchResultsDTO(artType, columnKeys, rows, cacheKey.getStartItem(), arts.size());
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof DataArtifact)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be a data artifact");
        }
        return new DataArtifactRowDTO((DataArtifact) artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id);
    }

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactSearchParam artifactKey) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        return dataArtifactCache.get(artifactKey, () -> fetchDataArtifactsForTable(artifactKey));
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
    public SearchResultsDTO getDataArtifactsCounts(Long dataSourceId) throws ExecutionException {
        try {
            // get artifact types and counts
            SleuthkitCase skCase = getCase();
            String query = "SELECT artifact_type_id, COUNT(*) AS count "
                    + "FROM blackboard_artifacts "
                    + (dataSourceId == null ? "" : "data_source_obj_id = " + dataSourceId)
                    + "GROUP BY artifact_type_id";
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
            List<RowDTO> typeCountRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return new CountsRowDTO<>(
                                BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                                new DataArtifactSearchParam(entry.getKey(), dataSourceId),
                                entry.getKey().getTypeID(),
                                entry.getKey().getDisplayName(),
                                entry.getValue());
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new BaseSearchResultsDTO(
                    BlackboardArtifact.Category.DATA_ARTIFACT.name(),
                    BlackboardArtifact.Category.DATA_ARTIFACT.getDisplayName(),
                    CountsRowDTO.getDefaultColumnKeys(),
                    typeCountRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }
}
