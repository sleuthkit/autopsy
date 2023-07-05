/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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

import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.sun.xml.bind.v2.TODO;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION_UNITS;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_SIZE;
import org.sleuthkit.autopsy.mainui.datamodel.BlackboardArtifactDAO.TableData;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import org.sleuthkit.autopsy.mainui.datamodel.events.ScoreContentEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Score.Priority;
import org.sleuthkit.datamodel.Score.Significance;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
@Messages({
    "ScoreDAO_columns_sourceLbl=Source",
    "ScoreDAO_columns_typeLbl=Type",
    "ScoreDAO_columns_pathLbl=Path",
    "ScoreDAO_columns_createdDateLbl=Created Date",
    "ScoreDAO_columns_noDescription=No Description",
    "ScoreDAO_types_filelbl=File"
})
public class ScoreDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(ScoreDAO.class.getName());

    private static final List<ColumnKey> RESULT_SCORE_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.ScoreDAO_columns_sourceLbl()),
            getFileColumnKey(Bundle.ScoreDAO_columns_typeLbl()),
            getFileColumnKey(Bundle.ScoreDAO_columns_pathLbl()),
            getFileColumnKey(Bundle.ScoreDAO_columns_createdDateLbl())
    );

    private static ScoreDAO instance = null;

    synchronized static ScoreDAO getInstance() {
        if (instance == null) {
            instance = new ScoreDAO();
        }

        return instance;
    }

    private final Cache<SearchParams<Object>, SearchResultsDTO> searchParamsCache
            = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    private final TreeCounts<DAOEvent> treeCounts = new TreeCounts<>();

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.ScoreDAO_columns_noDescription());
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public SearchResultsDTO getFilesByScore(ScoreViewSearchParams key, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        SearchParams<Object> searchParams = new SearchParams<>(key, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchScoreSearchResultsDTOs(key.getFilter(), key.getDataSourceId(), startItem, maxCount));
    }

    private boolean isScoreContentInvalidating(ScoreViewSearchParams params, DAOEvent eventData) {
        if (!(eventData instanceof ScoreContentEvent)) {
            return false;
        }

        ScoreContentEvent scoreContentEvt = (ScoreContentEvent) eventData;

        ScoreViewFilter evtFilter = scoreContentEvt.getFilter();
        ScoreViewFilter paramsFilter = params.getFilter();

        Long evtDsId = scoreContentEvt.getDataSourceId();
        Long paramsDsId = params.getDataSourceId();

        return (evtFilter == null || evtFilter.equals(paramsFilter))
                && (paramsDsId == null || evtDsId == null
                || Objects.equals(paramsDsId, evtDsId));
    }

    private static String getScoreFilter(ScoreViewFilter filter) throws IllegalArgumentException {
        switch (filter) {
            case SUSPICIOUS:
                return " tsk_aggregate_score.significance = " + Significance.LIKELY_NOTABLE.getId()
                        + " AND (tsk_aggregate_score.priority = " + Priority.NORMAL.getId() + " OR tsk_aggregate_score.priority = " + Priority.OVERRIDE.getId() + " )";
            case BAD:
                return " tsk_aggregate_score.significance = " + Significance.NOTABLE.getId()
                        + " AND (tsk_aggregate_score.priority = " + Priority.NORMAL.getId() + " OR tsk_aggregate_score.priority = " + Priority.OVERRIDE.getId() + " )";
            default:
                throw new IllegalArgumentException(MessageFormat.format("Unsupported filter type to get suspect content: {0}", filter));
        }
    }

    /**
     * Returns counts for deleted content categories.
     *
     * @param dataSourceId The data source object id or null if no data source
     * filtering should occur.
     *
     * @return The results.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public TreeResultsDTO<ScoreViewSearchParams> getScoreContentCounts(Long dataSourceId) throws IllegalArgumentException, ExecutionException {
        Set<ScoreViewFilter> indeterminateFilters = new HashSet<>();
        for (DAOEvent evt : this.treeCounts.getEnqueued()) {
            if (evt instanceof ScoreContentEvent) {
                ScoreContentEvent scoreEvt = (ScoreContentEvent) evt;
                if (dataSourceId == null || scoreEvt.getDataSourceId() == null || Objects.equals(scoreEvt.getDataSourceId(), dataSourceId)) {
                    if (scoreEvt.getFilter() == null) {
                        // if null filter, indicates full refresh and all file sizes need refresh.
                        indeterminateFilters.addAll(Arrays.asList(ScoreViewFilter.values()));
                        break;
                    } else {
                        indeterminateFilters.add(scoreEvt.getFilter());
                    }
                }
            }
        }

        String queryStr = Stream.of(ScoreViewFilter.values())
                .map((filter) -> {
                    return " SELECT COUNT(tsk_aggregate_score.obj_id) AS " + filter.name() + " FROM tsk_aggregate_score WHERE\n"
                            + getScoreFilter(filter) + "\n"
                            + ((dataSourceId == null) ? "AND tsk_aggregate_score.data_source_obj_id = " + dataSourceId + "\n" : "")
                            + " AND tsk_aggregate_score.obj_id IN\n"
                            + " (SELECT tsk_files.obj_id AS obj_id FROM tsk_files UNION\n"
                            + "     SELECT blackboard_artifacts.artifact_obj_id AS obj_id FROM blackboard_artifacts WHERE blackboard_artifacts.artifact_type_id IN\n"
                            + "         (SELECT artifact_type_id FROM blackboard_artifact_types WHERE category_type = " + Category.DATA_ARTIFACT.getID() + ")) ";
                })
                .collect(Collectors.joining(", \n"));

        try {
            SleuthkitCase skCase = getCase();

            List<TreeItemDTO<ScoreViewSearchParams>> treeList = new ArrayList<>();
            skCase.getCaseDbAccessManager().select(queryStr, (resultSet) -> {
                try {
                    if (resultSet.next()) {
                        for (ScoreViewFilter filter : ScoreViewFilter.values()) {
                            long count = resultSet.getLong(filter.name());
                            TreeDisplayCount displayCount = indeterminateFilters.contains(filter)
                                    ? TreeDisplayCount.INDETERMINATE
                                    : TreeDisplayCount.getDeterminate(count);

                            treeList.add(createScoreContentTreeItem(filter, dataSourceId, displayCount));
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching file type counts.", ex);
                }
            });

            return new TreeResultsDTO<>(treeList);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching file counts with query:\n" + queryStr, ex);
        }
    }

    private static TreeItemDTO<ScoreViewSearchParams> createScoreContentTreeItem(ScoreViewFilter filter, Long dataSourceId, TreeDisplayCount displayCount) {
        return new TreeItemDTO<>(
                "SCORE_CONTENT",
                new ScoreViewSearchParams(filter, dataSourceId),
                filter,
                filter == null ? "" : filter.getDisplayName(),
                displayCount);
    }

    private SearchResultsDTO fetchScoreSearchResultsDTOs(ScoreViewFilter filter, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String scoreWhereClause = filter.getScores().stream()
                .map(s -> MessageFormat.format(
                " (aggr_score.significance = {0} AND aggr_score.priority = {1}) ",
                s.getSignificance().getId(),
                s.getPriority().getId()))
                .collect(Collectors.joining(" OR "));

        String baseQuery
                = "FROM tsk_aggregate_score aggr_score\n"
                + "INNER JOIN (\n"
                + "	SELECT obj_id, data_source_obj_id, 'f' AS type FROM tsk_files\n"
                + "	UNION SELECT artifact_obj_id AS obj_id, data_source_obj_id, 'a' AS type FROM blackboard_artifacts\n"
                + "          WHERE blackboard_artifacts.artifact_type_id IN "
                + "               (SELECT artifact_type_id FROM blackboard_artifact_types WHERE category_type = " + Category.DATA_ARTIFACT.getID() + ")\n"
                + ") art_files ON aggr_score.obj_id = art_files.obj_id\n"
                + "WHERE (" + scoreWhereClause + ")\n"
                + ((dataSourceId != null && dataSourceId > 0) ? "AND art_files.data_source_obj_id = " + dataSourceId + "\n" : "")
                + "ORDER BY art_files.obj_id";

        String countQuery = " COUNT(art_files.obj_id) AS count\n" + baseQuery;

        AtomicLong totalCountRef = new AtomicLong(0);
        AtomicReference<SQLException> countException = new AtomicReference<>(null);
        getCase().getCaseDbAccessManager()
                .select(countQuery, (rs) -> {
                    try {
                        if (rs.next()) {
                            totalCountRef.set(rs.getLong("count"));
                        }
                    } catch (SQLException ex) {
                        countException.set(ex);
                    }
                }
                );

        SQLException sqlEx = countException.get();
        if (sqlEx != null) {
            throw new TskCoreException(
                    MessageFormat.format("A sql exception occurred fetching results with query: SELECT {0}", countQuery),
                    sqlEx);
        }

        String objIdQuery = " art_files.obj_id, art_files.type\n" + baseQuery
                + "\n"
                + "ORDER BY obj_id ASC"
                + (maxResultCount != null && maxResultCount > 0 ? " LIMIT " + maxResultCount : "")
                + (startItem > 0 ? " OFFSET " + startItem : "");;

        List<Long> fileIds = new ArrayList<>();
        List<Long> artifactIds = new ArrayList<>();
        AtomicReference<SQLException> objIdException = new AtomicReference<>(null);
        getCase().getCaseDbAccessManager()
                .select(objIdQuery, (rs) -> {
                    try {
                        while (rs.next()) {
                            String type = rs.getString("type");
                            if ("f".equalsIgnoreCase(type)) {
                                fileIds.add(rs.getLong("obj_id"));
                            } else {
                                artifactIds.add(rs.getLong("obj_id"));
                            }
                        }
                    } catch (SQLException ex) {
                        objIdException.set(ex);
                    }
                }
                );

        sqlEx = objIdException.get();
        if (sqlEx != null) {
            throw new TskCoreException(
                    MessageFormat.format("A sql exception occurred fetching results with query: SELECT {0}", objIdQuery),
                    sqlEx);
        }

        List<RowDTO> dataRows = new ArrayList<>();

        if (!fileIds.isEmpty()) {
            String joinedFileIds = fileIds.stream()
                    .map(l -> Long.toString(l))
                    .collect(Collectors.joining(", "));

            List<AbstractFile> files = getCase().findAllFilesWhere("obj_id IN (" + joinedFileIds + ")");

            for (AbstractFile file : files) {
                dataRows.add(new FileRowDTO(
                        file,
                        file.getId(),
                        file.getName(),
                        file.getNameExtension(),
                        MediaTypeUtils.getExtensionMediaType(file.getNameExtension()),
                        file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.ALLOC),
                        file.getType(),
                        Arrays.asList(
                                file.getName(), // source
                                Bundle.ScoreDAO_types_filelbl(),
                                file.getParentPath(),
                                file.getCrtime()
                        )));
            }
        }

        if (!artifactIds.isEmpty()) {
            String joinedArtifactIds = artifactIds.stream()
                    .map(l -> Long.toString(l))
                    .collect(Collectors.joining(", "));

            List<DataArtifact> dataArtifacts = getCase().getBlackboard().getDataArtifactsWhere("obj_id IN (" + joinedArtifactIds + ")");
            TableData artTableData = MainDAO.getInstance().getDataArtifactsDAO().createTableData(null, dataArtifacts);
            dataRows.addAll(artTableData.rows);
        }

        //
        return new BaseSearchResultsDTO(
                SCORE_TYPE_ID,
                SCORE_DISPLAY_NAME,
                RESULT_SCORE_COLUMNS,
                dataRows,
                signature,
                startItem,
                totalCountRef.get());
    }

    private TreeItemDTO<?> createTreeItem(DAOEvent daoEvent, TreeDisplayCount count) {

        if (daoEvent instanceof ScoreContentEvent) {
            ScoreContentEvent scoreEvt = (ScoreContentEvent) daoEvent;
            return createScoreContentTreeItem(scoreEvt.getFilter(), scoreEvt.getDataSourceId(), count);
        } else {
            return null;
        }
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        SubDAOUtils.invalidateKeys(this.searchParamsCache,
                (searchParams) -> searchParamsMatchEvent(true, null, true, searchParams));

        Set<? extends DAOEvent> treeEvts = SubDAOUtils.getIngestCompleteEvents(this.treeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count));

        Set<? extends DAOEvent> fileViewRefreshEvents = getFileViewRefreshEvents(null);

        List<? extends DAOEvent> fileViewRefreshTreeEvents = fileViewRefreshEvents.stream()
                .map(evt -> new TreeEvent(createTreeItem(evt, TreeDisplayCount.UNSPECIFIED), true))
                .collect(Collectors.toList());

        return Stream.of(treeEvts, fileViewRefreshEvents, fileViewRefreshTreeEvents)
                .flatMap(c -> c.stream())
                .collect(Collectors.toSet());
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return SubDAOUtils.getRefreshEvents(this.treeCounts,
                (daoEvt, count) -> createTreeItem(daoEvt, count));
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        AbstractFile af;
        if (Case.Events.DATA_SOURCE_ADDED.toString().equals(evt.getPropertyName())) {
            Long dsId = evt.getNewValue() instanceof Long ? (Long) evt.getNewValue() : null;
            return invalidateScoreParamsAndReturnEvents(dsId);

        } else if ((af = DAOEventUtils.getFileFromFileEvent(evt)) != null) {
            return invalidateScoreParamsAndReturnEvents(af.getDataSourceObjectId());
        }

        ModuleDataEvent dataEvt;
        if (Case.Events.CONTENT_TAG_ADDED.toString().equals(evt.getPropertyName()) && (evt instanceof ContentTagAddedEvent) && ((ContentTagAddedEvent) evt).getAddedTag().getContent() instanceof AbstractFile) {
            ContentTagAddedEvent tagAddedEvt = (ContentTagAddedEvent) evt;
            return invalidateScoreParamsAndReturnEvents(((AbstractFile) tagAddedEvt.getAddedTag().getContent()).getDataSourceObjectId());
        } else if (Case.Events.CONTENT_TAG_DELETED.toString().equals(evt.getPropertyName())) {
            return invalidateScoreParamsAndReturnEvents(null);
        } else if (Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString().equals(evt.getPropertyName()) && (evt instanceof BlackBoardArtifactTagAddedEvent)) {
            BlackBoardArtifactTagAddedEvent artifactAddedEvt = (BlackBoardArtifactTagAddedEvent) evt;
            return invalidateScoreParamsAndReturnEvents(artifactAddedEvt.getAddedTag().getArtifact().getDataSourceObjectID());
        } else if (Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString().equals(evt.getPropertyName())) {
            return invalidateScoreParamsAndReturnEvents(null);
        } else if ((dataEvt = DAOEventUtils.getModuelDataFromArtifactEvent(evt)) != null
                && Category.ANALYSIS_RESULT.equals(dataEvt.getBlackboardArtifactType().getCategory())) {
            Set<Long> dsIds = dataEvt.getArtifacts().stream().map(ar -> ar.getDataSourceObjectID()).distinct().collect(Collectors.toSet());
            return invalidateScoreParamsAndReturnEventsFromSet(dsIds);

        } else {
            return Collections.emptySet();
        }
    }

    private Set<DAOEvent> invalidateScoreParamsAndReturnEvents(Long dataSourceId) {
        return invalidateScoreParamsAndReturnEventsFromSet(dataSourceId != null ? Collections.singleton(dataSourceId) : null);
    }

    private Set<DAOEvent> invalidateScoreParamsAndReturnEventsFromSet(Set<Long> dataSourceIds) {

        SubDAOUtils.invalidateKeys(this.searchParamsCache,
                (searchParams) -> {
                    if (searchParams instanceof ScoreViewSearchParams) {
                        ScoreViewSearchParams scoreParams = (ScoreViewSearchParams) searchParams;
                        return (CollectionUtils.isEmpty(dataSourceIds) || scoreParams.getDataSourceId() == null || dataSourceIds.contains(scoreParams.getDataSourceId()));
                    } else {
                        return false;
                    }
                });

        return CollectionUtils.isEmpty(dataSourceIds)
                ? Collections.singleton(new ScoreContentEvent(null, null))
                : dataSourceIds.stream().map(dsId -> new ScoreContentEvent(null, dsId)).collect(Collectors.toSet());
    }

    private boolean searchParamsMatchEvent(
            boolean invalidatesScore,
            Long dsId,
            boolean dataSourceAdded,
            Object searchParams) {

        if (searchParams instanceof ScoreViewSearchParams) {
            ScoreViewSearchParams scoreParams = (ScoreViewSearchParams) searchParams;
            return (dataSourceAdded || (invalidatesScore && (scoreParams.getDataSourceId() == null || dsId == null || Objects.equals(scoreParams.getDataSourceId(), dsId))));
        } else {
            return false;
        }
    }

    /**
     * Returns events for when a full refresh is required because module content
     * events will not necessarily provide events for files (i.e. data source
     * added, ingest cancelled/completed).
     *
     * @param dataSourceId The data source id or null if not applicable.
     *
     * @return The set of events that apply in this situation.
     */
    private Set<DAOEvent> getFileViewRefreshEvents(Long dataSourceId) {
        return ImmutableSet.of(
                new ScoreContentEvent(null, dataSourceId)
        );

    }

    /**
     * Handles fetching and paging of data for deleted content.
     */
    public static class ScoreContentFetcher extends DAOFetcher<ScoreViewSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public ScoreContentFetcher(ScoreViewSearchParams params) {
            super(params);
        }

        protected ScoreDAO getDAO() {
            return MainDAO.getInstance().getScoreDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getFilesByScore(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isScoreContentInvalidating(this.getParameters(), evt);
        }

    }
}
