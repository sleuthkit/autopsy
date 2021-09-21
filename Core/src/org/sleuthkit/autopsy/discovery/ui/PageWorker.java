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
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.Group;
import org.sleuthkit.autopsy.discovery.search.FileSearch;
import org.sleuthkit.autopsy.discovery.search.SearchData;
import org.sleuthkit.autopsy.discovery.search.DiscoveryException;
import org.sleuthkit.autopsy.discovery.search.DomainSearch;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter;
import org.sleuthkit.autopsy.discovery.search.Result;
import org.sleuthkit.autopsy.discovery.search.SearchCancellationException;
import org.sleuthkit.autopsy.discovery.search.SearchContext;

/**
 * SwingWorker to retrieve the contents of a page.
 */
final class PageWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(PageWorker.class.getName());
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    private final List<AbstractFilter> searchfilters;
    private final DiscoveryAttributes.AttributeType groupingAttribute;
    private final Group.GroupSortingAlgorithm groupSort;
    private final ResultsSorter.SortingMethod fileSortMethod;
    private final GroupKey groupKey;
    private final int startingEntry;
    private final int pageSize;
    private final SearchData.Type resultType;
    private final CentralRepository centralRepo;
    private final List<Result> results = new ArrayList<>();

    /**
     * Construct a new PageWorker.
     *
     * @param searchfilters     The search filters which were used by the
     *                          search.
     * @param groupingAttribute The grouping attribute used by the search.
     * @param groupSort         The sorting algorithm used for groups.
     * @param fileSortMethod    The sorting method used for files.
     * @param groupKey          The key which uniquely identifies the group
     *                          which was selected.
     * @param startingEntry     The first entry in the group to include in this
     *                          page.
     * @param pageSize          The number of files to include in this page.
     * @param resultType        The type of files which exist in the group.
     * @param centralRepo       The central repository to be used.
     */
    PageWorker(List<AbstractFilter> searchfilters, DiscoveryAttributes.AttributeType groupingAttribute,
            Group.GroupSortingAlgorithm groupSort, ResultsSorter.SortingMethod fileSortMethod, GroupKey groupKey,
            int startingEntry, int pageSize, SearchData.Type resultType, CentralRepository centralRepo) {
        this.searchfilters = searchfilters;
        this.groupingAttribute = groupingAttribute;
        this.groupSort = groupSort;
        this.fileSortMethod = fileSortMethod;
        this.groupKey = groupKey;
        this.startingEntry = startingEntry;
        this.pageSize = pageSize;
        this.resultType = resultType;
        this.centralRepo = centralRepo;
    }

    @Override
    protected Void doInBackground() throws Exception {
        SearchContext context = new SwingWorkerSearchContext(this);
        try {
            // Run the search
            if (resultType == SearchData.Type.DOMAIN) {
                DomainSearch domainSearch = new DomainSearch();
                results.addAll(domainSearch.getDomainsInGroup(System.getProperty(USER_NAME_PROPERTY), searchfilters,
                        groupingAttribute,
                        groupSort,
                        fileSortMethod, groupKey, startingEntry, pageSize,
                        Case.getCurrentCase().getSleuthkitCase(), centralRepo, context));
            } else {
                results.addAll(FileSearch.getFilesInGroup(System.getProperty(USER_NAME_PROPERTY), searchfilters,
                        groupingAttribute,
                        groupSort,
                        fileSortMethod, groupKey, startingEntry, pageSize,
                        Case.getCurrentCase().getSleuthkitCase(), centralRepo, context));
            }
        } catch (DiscoveryException ex) {
            logger.log(Level.SEVERE, "Error running file search test", ex);
            cancel(true);
        } catch (SearchCancellationException ex) {
            //The user does not explicitly have a way to cancel the loading of a page 
            //but they could have cancelled the search during the loading of the first page
            //So this may or may not be an issue depending on when this occurred.
            logger.log(Level.WARNING, "Search was cancelled while retrieving data for results page with starting entry: " + startingEntry, ex);
        }
        return null;
    }

    @Override
    protected void done() {
        if (!isCancelled()) {
            int currentPage = startingEntry / pageSize; //integer division should round down to get page number correctly
            DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PageRetrievedEvent(resultType, currentPage, results));
        }
    }

}
