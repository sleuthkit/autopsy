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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Provides functionality to handle paging and fetching of search result data.
 */
public class SearchResultSupport {

    private int pageSize;
    private int pageIdx = 0;

    private PageFetcher pageFetcher = null;
    private final MainDAO dao = MainDAO.getInstance();

    /**
     * Main constructor.
     *
     * @param initialPageSize The initial page size to support.
     */
    public SearchResultSupport(int initialPageSize) {
        setPageSize(initialPageSize);
    }

    /**
     * @return The page size when handing paging.
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @param pageSize The page size when handling paging.
     */
    private synchronized void setPageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be > 0 but was " + pageSize);
        }

        this.pageSize = pageSize;
    }

    /**
     * @return The index of the page to be viewed.
     */
    public int getPageIdx() {
        return pageIdx;
    }

    /**
     * @param pageIdx The index of the page to be viewed.
     */
    private synchronized void setPageIdx(int pageIdx) {
        if (pageIdx < 0) {
            throw new IllegalArgumentException("Page idx must be >= 0 but was " + pageIdx);
        }

        this.pageIdx = pageIdx;
    }

    /**
     * Updates the page size and returns the results after updating the page
     * size.
     *
     * @param pageSize The page size.
     *
     * @return The search results or null if no search parameters provided.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO updatePageSize(int pageSize) throws IllegalArgumentException, ExecutionException {
        setPageSize(pageSize);
        return (this.pageFetcher != null)
                ? this.pageFetcher.fetch(this.pageSize, this.pageIdx)
                : null;
    }

    /**
     * Updates the page index and returns the results after updating the page
     * index.
     *
     * @param pageIdx The page index.
     *
     * @return The search results or null if no search parameters provided.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO updatePageIdx(int pageIdx) throws IllegalArgumentException, ExecutionException {
        setPageIdx(pageIdx);
        return (this.pageFetcher != null)
                ? this.pageFetcher.fetch(this.pageSize, this.pageIdx)
                : null;
    }

    /**
     * Clears any search parameters. Calls that return search results DTO's will
     * return null until a call is made that updates the search parameters (i.e.
     * display data artifacts).
     */
    public synchronized void clearSearchParameters() {
        this.pageFetcher = null;
    }

    /**
     * Sets the search parameters to the data artifact search parameters.
     * Subsequent calls that don't change search parameters (i.e. page size
     * changes, page index changes) will use these search parameters to return
     * results.
     *
     * @param dataArtifactParameters The data artifact search parameters.
     *
     * @return The results of querying with current paging parameters.
     *
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO setDataArtifact(final DataArtifactSearchParam dataArtifactParameters) throws ExecutionException {
        resetPaging();
        this.pageFetcher = (pageSize, pageIdx) -> {
            DataArtifactSearchParam searchParams = new DataArtifactSearchParam(
                    dataArtifactParameters.getArtifactType(),
                    dataArtifactParameters.getDataSourceId(),
                    pageIdx * pageSize,
                    (long) pageSize);
            return dao.getDataArtifactsDAO().getDataArtifactsForTable(searchParams);
        };

        return this.pageFetcher.fetch(this.pageSize, this.pageIdx);
    }

    /**
     * Sets the search parameters to the file type extension search parameters.
     * Subsequent calls that don't change search parameters (i.e. page size
     * changes, page index changes) will use these search parameters to return
     * results.
     *
     * @param fileExtParameters The file type extension search parameters.
     *
     * @return The results of querying with current paging parameters.
     *
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO setFileExtensions(final FileTypeExtensionsSearchParam fileExtParameters) throws ExecutionException {
        resetPaging();
        this.pageFetcher = (pageSize, pageIdx) -> {
            FileTypeExtensionsSearchParam searchParams = new FileTypeExtensionsSearchParam(
                    fileExtParameters.getFilter(),
                    fileExtParameters.getDataSourceId(),
                    fileExtParameters.isKnownShown(),
                    pageIdx * pageSize,
                    (long) pageSize);
            return dao.getViewsDAO().getFilesByExtension(searchParams);
        };

        return this.pageFetcher.fetch(this.pageSize, this.pageIdx);
    }

    private synchronized void resetPaging() {
        this.pageIdx = 0;
    }

//
//    private void displaySearchResults(SearchResultsDTO searchResults) {
//        dataResultPanel.setNode(new SearchResultRootNode(searchResults), searchResults);
//        dataResultPanel.setNumberOfChildNodes(
//                searchResults.getTotalResultsCount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) searchResults.getTotalResultsCount());
//    }
//    
    /**
     * Means of fetching data based on paging settings.
     */
    private interface PageFetcher {

        /**
         * Fetches search results data based on paging settings.
         *
         * @param pageSize The page size.
         * @param pageIdx  The page index.
         *
         * @return The retrieved data.
         *
         * @throws ExecutionException
         */
        SearchResultsDTO fetch(int pageSize, int pageIdx) throws ExecutionException;
    }
}
