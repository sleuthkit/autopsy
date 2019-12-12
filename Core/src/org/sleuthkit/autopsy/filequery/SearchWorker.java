/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import java.util.LinkedHashMap;
import javax.swing.SwingWorker;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.filequery.FileSearch.GroupKey;

/**
 * SwingWorker to perform search on a background thread
 */
final class SearchWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(SearchWorker.class.getName());
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    private final List<FileSearchFiltering.FileFilter> filters;
    private final FileSearch.AttributeType groupingAttr;
    private final FileSorter.SortingMethod fileSort;
    private final FileGroup.GroupSortingAlgorithm groupSortAlgorithm;
    private final EamDb centralRepoDb;
    private final Map<GroupKey, Integer> results = new LinkedHashMap<>();

    /**
     * Create a SwingWorker which performs a search
     *
     * @param centralRepo       the central repository being used for the search
     * @param searchfilters     the FileFilters to use for the search
     * @param groupingAttribute the AttributeType to group by
     * @param groupSort         the Algorithm to sort groups by
     * @param fileSortMethod    the SortingMethod to use for files
     */
    SearchWorker(EamDb centralRepo, List<FileSearchFiltering.FileFilter> searchfilters, FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort, FileSorter.SortingMethod fileSortMethod) {
        centralRepoDb = centralRepo;
        filters = searchfilters;
        groupingAttr = groupingAttribute;
        groupSortAlgorithm = groupSort;
        fileSort = fileSortMethod;
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            // Run the search
            results.putAll(FileSearch.getGroupSizes(System.getProperty(USER_NAME_PROPERTY), filters,
                    groupingAttr,
                    groupSortAlgorithm,
                    fileSort,
                    Case.getCurrentCase().getSleuthkitCase(), centralRepoDb));
        } catch (FileSearchException ex) {
            logger.log(Level.SEVERE, "Error running file search test", ex);
            cancel(true);
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
