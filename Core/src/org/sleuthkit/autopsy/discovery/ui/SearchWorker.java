/*
 * Autopsy
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.discovery.search.AbstractFilter;
import java.util.LinkedHashMap;
import javax.swing.SwingWorker;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.Group;
import org.sleuthkit.autopsy.discovery.search.FileSearch;
import org.sleuthkit.autopsy.discovery.search.DiscoveryException;
import org.sleuthkit.autopsy.discovery.search.DomainSearch;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter;
import org.sleuthkit.autopsy.discovery.search.SearchCancellationException;
import org.sleuthkit.autopsy.discovery.search.SearchContext;
import org.sleuthkit.autopsy.discovery.search.SearchData;

/**
 * SwingWorker to perform search on a background thread.
 */
final class SearchWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(SearchWorker.class.getName());
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    private final List<AbstractFilter> filters;
    private final DiscoveryAttributes.AttributeType groupingAttr;
    private final ResultsSorter.SortingMethod fileSort;
    private final Group.GroupSortingAlgorithm groupSortAlgorithm;
    private final CentralRepository centralRepoDb;
    private final SearchData.Type searchType;
    private final Map<GroupKey, Integer> results = new LinkedHashMap<>();

    /**
     * Create a SwingWorker which performs a search.
     *
     * @param centralRepo       The central repository being used for the
     *                          search.
     * @param searchfilters     The FileFilters to use for the search.
     * @param groupingAttribute The AttributeType to group by.
     * @param groupSort         The Algorithm to sort groups by.
     * @param fileSortMethod    The SortingMethod to use for files.
     */
    SearchWorker(CentralRepository centralRepo, SearchData.Type type, List<AbstractFilter> searchfilters, DiscoveryAttributes.AttributeType groupingAttribute, Group.GroupSortingAlgorithm groupSort, ResultsSorter.SortingMethod fileSortMethod) {
        centralRepoDb = centralRepo;
        searchType = type;
        filters = searchfilters;
        groupingAttr = groupingAttribute;
        groupSortAlgorithm = groupSort;
        fileSort = fileSortMethod;
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            // Run the search
            SearchContext context = new SwingWorkerSearchContext(this);
            if (searchType == SearchData.Type.DOMAIN) {
                DomainSearch domainSearch = new DomainSearch();
                results.putAll(domainSearch.getGroupSizes(System.getProperty(USER_NAME_PROPERTY), filters,
                        groupingAttr,
                        groupSortAlgorithm,
                        fileSort,
                        Case.getCurrentCase().getSleuthkitCase(), centralRepoDb, context));
            } else {
                results.putAll(FileSearch.getGroupSizes(System.getProperty(USER_NAME_PROPERTY), filters,
                        groupingAttr,
                        groupSortAlgorithm,
                        fileSort,
                        Case.getCurrentCase().getSleuthkitCase(), centralRepoDb, context));
            }
        } catch (DiscoveryException ex) {
            logger.log(Level.SEVERE, "Error running file search test.", ex);
            cancel(true);
        } catch (SearchCancellationException ex) {
            //search cancellation exceptions should indicate that the user chose to cancell this search 
            //so would not be a problem but we might be curious what was being done when it was cancelled
            logger.log(Level.INFO, "Discovery search was cancelled.", ex);
        }
        return null;
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.SearchCancelledEvent());
        } else {
            DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.SearchCompleteEvent(results, filters, groupingAttr, groupSortAlgorithm, fileSort));
        }
    }
}
