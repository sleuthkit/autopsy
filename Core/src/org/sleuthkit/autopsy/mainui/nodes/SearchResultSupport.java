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

import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Provides functionality to handle paging and fetching of search result data.
 */
public class SearchResultSupport {

    private int pageSize;
    private int pageIdx = 0;

    private SearchResultsDTO currentSearchResults = null;
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
    public synchronized int getPageSize() {
        return pageSize;
    }

    /**
     * @param pageSize The page size when handling paging.
     */
    public synchronized void setPageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be > 0 but was " + pageSize);
        }

        this.pageSize = pageSize;
    }

    /**
     * @return The index of the page to be viewed.
     */
    public synchronized int getPageIdx() {
        return pageIdx;
    }

    /**
     * @return True if there is a previous page of results.
     */
    public synchronized boolean hasPrevPage() {
        return pageIdx > 0;
    }

    /**
     * @return True if there is another page.
     */
    public synchronized boolean hasNextPage() {
        if (this.currentSearchResults == null) {
            return false;
        } else {
            return (this.pageIdx + 1) * this.pageSize < this.currentSearchResults.getTotalResultsCount();
        }
    }

    /**
     * @return The total number of pages based on the current search results.
     */
    public synchronized int getTotalPages() {
        if (this.currentSearchResults == null) {
            return 0;
        } else {
            return (int) Math.ceil(((double) this.currentSearchResults.getTotalResultsCount()) / this.pageSize);
        }
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
     * @return The last accessed search results or null.
     */
    public SearchResultsDTO getCurrentSearchResults() {
        return currentSearchResults;
    }

    /**
     * Updates the page size, clears page index t0 0, and returns the results
     * after updating the page size.
     *
     * @param pageSize The page size.
     *
     * @return The search results or null if no search parameters provided.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO updatePageSize(int pageSize) throws IllegalArgumentException, ExecutionException {
        this.pageIdx = 0;
        setPageSize(pageSize);
        return fetchResults();
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
        return fetchResults();
    }

    /**
     * Increments page index or throws an exception if not possible.
     *
     * @return The search results after incrementing.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO incrementPageIdx() throws IllegalArgumentException, ExecutionException {
        if (this.currentSearchResults == null) {
            throw new IllegalArgumentException("No current results");
        } else if ((this.pageIdx + 1) * this.pageSize >= this.currentSearchResults.getTotalResultsCount()) {
            throw new IllegalArgumentException(MessageFormat.format("Page index cannot be incremented. [pageSize: {0}, pageIdx: {1}, total results: {2}]",
                    this.pageSize, this.pageIdx, this.currentSearchResults.getTotalResultsCount()));
        }

        return updatePageIdx(this.pageIdx + 1);
    }

    /**
     * Decrements page index or throws an exception if not possible.
     *
     * @return The search results after decrementing.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO decrementPageIdx() throws IllegalArgumentException, ExecutionException {
        if (this.pageFetcher == null) {
            throw new IllegalArgumentException("No current page fetcher");
        } else if (this.pageIdx < 1) {
            throw new IllegalArgumentException("Page index cannot be decremented.");
        }

        return updatePageIdx(this.pageIdx - 1);
    }

    /**
     * Clears any search parameters. Calls that return search results DTO's will
     * return null until a call is made that updates the search parameters (i.e.
     * display data artifacts).
     */
    public synchronized void clearSearchParameters() {
        resetPaging();
        this.pageFetcher = null;
        this.currentSearchResults = null;
    }

    /**
     * Fetches results using current page fetcher or returns null if no current
     * page fetcher. Also stores current results in local variable.
     *
     * @return The current search results or null if no current page fetcher.
     *
     * @throws ExecutionException
     */
    private synchronized SearchResultsDTO fetchResults() throws ExecutionException {
        SearchResultsDTO newResults = (this.pageFetcher != null)
                ? this.pageFetcher.fetch(this.pageSize, this.pageIdx)
                : null;

        this.currentSearchResults = newResults;
        return newResults;
    }

    private synchronized void resetPaging() {
        this.pageIdx = 0;
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
    public synchronized SearchResultsDTO setFileExtensions(final FileTypeExtensionsSearchParams fileExtParameters) throws ExecutionException {
        resetPaging();
        this.pageFetcher = (pageSize, pageIdx) -> {
            FileTypeExtensionsSearchParams searchParams = new FileTypeExtensionsSearchParams(
                    fileExtParameters.getFilter(),
                    fileExtParameters.getDataSourceId(),
                    pageIdx * pageSize,
                    (long) pageSize);
            return dao.getViewsDAO().getFilesByExtension(searchParams);
        };

        return fetchResults();
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

        return fetchResults();
    }

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
