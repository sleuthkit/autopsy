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

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * SwingWorker to retrieve the contents of a page.
 */
final class PageWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(PageWorker.class.getName());
    private final List<FileSearchFiltering.FileFilter> searchfilters;
    private final FileSearch.AttributeType groupingAttribute;
    private final FileGroup.GroupSortingAlgorithm groupSort;
    private final FileSorter.SortingMethod fileSortMethod;
    private final String groupName;
    private final int startingEntry;
    private final int pageSize;
    private final FileSearchData.FileType resultType;
    private final EamDb centralRepo;
    private final List<ResultFile> results = new ArrayList<>();

    /**
     * Construct a new PageWorker.
     *
     * @param searchfilters     The search filters which were used by the
     *                          search.
     * @param groupingAttribute The grouping attribute used by the search.
     * @param groupSort         The sorting algorithm used for groups.
     * @param fileSortMethod    The sorting method used for files.
     * @param groupName         The name of the group which was selected.
     * @param startingEntry     The first entry in the group to include in this
     *                          page.
     * @param pageSize          The number of files to include in this page.
     * @param resultType        The type of files which exist in the group.
     * @param centralRepo       The central repository to be used.
     */
    PageWorker(List<FileSearchFiltering.FileFilter> searchfilters, FileSearch.AttributeType groupingAttribute,
            FileGroup.GroupSortingAlgorithm groupSort, FileSorter.SortingMethod fileSortMethod, String groupName,
            int startingEntry, int pageSize, FileSearchData.FileType resultType, EamDb centralRepo) {
        this.searchfilters = searchfilters;
        this.groupingAttribute = groupingAttribute;
        this.groupSort = groupSort;
        this.fileSortMethod = fileSortMethod;
        this.groupName = groupName;
        this.startingEntry = startingEntry;
        this.pageSize = pageSize;
        this.resultType = resultType;
        this.centralRepo = centralRepo;
    }

    @Override
    protected Void doInBackground() throws Exception {

        try {
            // Run the search
            results.addAll(FileSearch.getFilesInGroup(searchfilters,
                    groupingAttribute,
                    groupSort,
                    fileSortMethod, groupName, startingEntry, pageSize,
                    Case.getCurrentCase().getSleuthkitCase(), centralRepo));
        } catch (FileSearchException ex) {
            logger.log(Level.SEVERE, "Error running file search test", ex);
            cancel(true);
        }
        return null;
    }

    @Override
    protected void done() {
        if (!isCancelled()) {
            int currentPage = startingEntry / pageSize; //integer division should round down to get page number correctly
            DiscoveryEvents.getDiscoveryEventBus().post(new DiscoveryEvents.PageRetrievedEvent(resultType, currentPage, results));
        }
    }

}
