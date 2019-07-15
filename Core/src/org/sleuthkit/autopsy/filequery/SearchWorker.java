/*
 * Autopsy Forensic Browser
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

import javax.swing.JButton;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;

final class SearchWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(SearchWorker.class.getName());
    private boolean runAnotherSearch = false;
    private final JButton searchButtonToEnable;
    private final List<FileSearchFiltering.FileFilter> filters;
    private final FileSearch.AttributeType groupingAttr;
    private final FileSorter.SortingMethod fileSort;
    private final FileGroup.GroupSortingAlgorithm groupSortAlgorithm;
    private final EamDb centralRepoDb;

    SearchWorker(EamDb centralRepo, JButton searchButton, List<FileSearchFiltering.FileFilter> searchfilters, FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort, FileSorter.SortingMethod fileSortMethod) {
        centralRepoDb = centralRepo;
        searchButtonToEnable = searchButton;
        filters = searchfilters;
        groupingAttr = groupingAttribute;
        groupSortAlgorithm = groupSort;
        fileSort = fileSortMethod;
    }

    @Override
    protected Void doInBackground() throws Exception {
        runAnotherSearch = true;
        // For testing, allow the user to run different searches in loop
        if (searchCancelled()) {
            return null;
        }

        try {
            // Make a list of attributes that we want to add values for. This ensures the
            // ResultFile objects will have all needed fields set when it's time to group
            // and sort them. For example, if we're grouping by central repo frequency, we need
            // to make sure we've loaded those values before grouping.
            List<FileSearch.AttributeType> attrsForGroupingAndSorting = new ArrayList<>();
            attrsForGroupingAndSorting.add(groupingAttr);
            attrsForGroupingAndSorting.addAll(fileSort.getRequiredAttributes());

            // Run the search
            SearchResults results = FileSearch.runFileSearchDebug(filters,
                    groupingAttr,
                    groupSortAlgorithm,
                    fileSort,
                    attrsForGroupingAndSorting,
                    Case.getCurrentCase().getSleuthkitCase(), centralRepoDb);
            DiscoveryEvents.getDiscoveryEventBus().post(new DiscoveryEvents.SearchCompleteEvent(results));
        } catch (FileSearchException ex) {
            logger.log(Level.SEVERE, "Error running file search test", ex);
        }
        return null;
    }

    @Override
    protected void done() { 
        if (searchButtonToEnable != null) {
            searchButtonToEnable.setEnabled(true);
        }
    }

    /**
     * Check whether the user chose to run the search or cancel
     *
     * @return true if the search was cancelled, false otherwise
     */
    boolean searchCancelled() {
        return (!runAnotherSearch);
    }

}
