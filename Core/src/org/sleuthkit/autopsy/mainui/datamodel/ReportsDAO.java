/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.ReportsEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Dao for reports.
 */
public class ReportsDAO extends AbstractDAO {

    private static ReportsDAO instance = null;

    public static ReportsDAO getInstance() {
        if (instance == null) {
            instance = new ReportsDAO();
        }
        return instance;
    }

    private final Cache<SearchParams<ReportsSearchParams>, SearchResultsDTO> cache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    @Messages({
        "ReportsDAO_reports_tableDisplayName=Reports"
    })
    private SearchResultsDTO fetchReports(SearchParams<ReportsSearchParams> params) throws NoCurrentCaseException, TskCoreException {
        long startItem = params.getStartItem();
        Long totalResultCount = params.getMaxResultsCount();

        List<Report> reports = getCase().getAllReports();

        Stream<? extends ReportsRowDTO> pagedReportsStream = reports.stream()
                .sorted(Comparator.comparing((report) -> report.getId()))
                .map(report -> {
                    return new ReportsRowDTO(
                            report,
                            report.getId(),
                            report.getSourceModuleName(),
                            report.getReportName(),
                            new Date(report.getCreatedTime() * 1000),
                            report.getPath());
                })
                .skip(params.getStartItem());

        if (params.getMaxResultsCount() != null) {
            pagedReportsStream = pagedReportsStream.limit(params.getMaxResultsCount());
        }

        return new BaseSearchResultsDTO(
                ReportsRowDTO.getTypeIdForClass(),
                Bundle.ReportsDAO_reports_tableDisplayName(),
                ReportsRowDTO.COLUMNS, 
                pagedReportsStream.collect(Collectors.toList()), 
                ReportsRowDTO.getTypeIdForClass(), 
                startItem, 
                totalResultCount);
    }

    public SearchResultsDTO getReports(ReportsSearchParams repSearchParams, long startItem, Long maxResultsCount) throws ExecutionException, IllegalArgumentException {
        SearchParams<ReportsSearchParams> searchParams = new SearchParams<>(repSearchParams, startItem, maxResultsCount);
        return cache.get(searchParams, () -> fetchReports(searchParams));
    }

    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(Case.Events.REPORT_ADDED.toString()) || eventType.equals(Case.Events.REPORT_DELETED.toString())) {
            cache.invalidateAll();
            return Collections.singleton(new ReportsEvent());
        }

        return Collections.emptySet();
    }

    @Override
    void clearCaches() {
        cache.invalidateAll();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return Collections.emptySet();
    }

    @Override
    Set<? extends TreeEvent> shouldRefreshTree() {
        return Collections.emptySet();
    }

    private boolean isReportInvalidatingEvent(ReportsSearchParams parameters, DAOEvent evt) {
        return evt instanceof ReportsEvent;
    }

    /**
     * Handles fetching and paging of data for all Reports.
     */
    public static class ReportsFetcher extends DAOFetcher<ReportsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public ReportsFetcher(ReportsSearchParams params) {
            super(params);
        }

        protected ReportsDAO getDAO() {
            return MainDAO.getInstance().getReportsDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getReports(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isReportInvalidatingEvent(this.getParameters(), evt);
        }
    }
}
