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
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author wschaefer
 */
final class PageWorker extends SwingWorker<Void, Void> {

    private final static Logger logger = Logger.getLogger(PageWorker.class.getName());
    private final FileSearchData.FileType resultType;
    private final EamDb centralRepo;
    private final List<FileSearchFiltering.FileFilter> searchfilters;
    private final FileSearch.AttributeType groupingAttribute;
    private final FileGroup.GroupSortingAlgorithm groupSort;
    private final FileSorter.SortingMethod fileSortMethod;
    private final String groupName;
    private final int startingEntry;
    private final int pageSize;

    PageWorker(FileSearchData.FileType resultType, EamDb centralRepo, List<FileSearchFiltering.FileFilter> searchfilters,
            FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort,
            FileSorter.SortingMethod fileSortMethod, String groupName, int startingEntry, int pageSize) {
        this.resultType = resultType;
        this.centralRepo = centralRepo;
        this.searchfilters = searchfilters;
        this.groupingAttribute = groupingAttribute;
        this.groupSort = groupSort;
        this.fileSortMethod = fileSortMethod;
        this.groupName = groupName;
        this.startingEntry = startingEntry;
        this.pageSize = pageSize;
    }

    @Override
    protected Void doInBackground() throws Exception {

        try {
            // Run the search
            List<AbstractFile> results = FileSearch.getFilesInGroup(searchfilters,
                    groupingAttribute,
                    groupSort,
                    fileSortMethod, groupName, startingEntry, pageSize,
                    Case.getCurrentCase().getSleuthkitCase(), centralRepo);
            int currentPage = startingEntry / pageSize; //integer division should round down to get page number correctly
            DiscoveryEvents.getDiscoveryEventBus().post(new DiscoveryEvents.PageRetrievedEvent(resultType, currentPage, results));
        } catch (FileSearchException ex) {
            logger.log(Level.SEVERE, "Error running file search test", ex);
        }
        return null;
    }

    @Override
    protected void done() {

    }

}
