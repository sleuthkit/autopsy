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

import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Provides functionality to handle paging and fetching of search result data.
 */
public class SearchManager {

    private final MainDAO dao = MainDAO.getInstance();
    private final DAOFetcher<?> daoFetcher;
    private final int pageSize;
    
    
    private SearchResultsDTO currentSearchResults = null;
    private int pageIdx = 0;

    /**
     * Main constructor.
     * @param daoFetcher Means of fetching data from the DAO.
     * @param pageSize The size of a page.
     */
    public SearchManager(DAOFetcher<?> daoFetcher, int pageSize) {
        this.daoFetcher = daoFetcher;
        this.pageSize = pageSize;
    }

    
    
    /**
     * @return The page size when handing paging.
     */
    public synchronized int getPageSize() {
        return pageSize;
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
    private synchronized void setPageIdx(int pageIdx) throws IllegalArgumentException {
        if (pageIdx < 0 || pageIdx >= getTotalPages()) {
            throw new IllegalArgumentException(MessageFormat.format("Page idx must be >= 0 and less than {0} but was {1}", getTotalPages(), pageIdx));
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
        return fetchResults(false);
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
        if (this.daoFetcher == null) {
            throw new IllegalArgumentException("No current page fetcher");
        }

        return updatePageIdx(this.pageIdx - 1);
    }

    /**
     * Determines if a refresh is required for the currently selected item.
     *
     * @param evt The ingest module event.
     *
     * @return True if an update is required.
     */
    public synchronized boolean isRefreshRequired(PropertyChangeEvent evt) {
        return isRefreshRequired(this.daoFetcher, evt);
    }

    /**
     * Determines if a refresh is required for the currently selected item.
     *
     * @param dataFetcher The data fetcher.
     * @param evt         The ingest module event.
     *
     * @return True if an update is required.
     */
    private synchronized <P> boolean isRefreshRequired(DAOFetcher<P> dataFetcher, PropertyChangeEvent evt) {
        if (dataFetcher == null) {
            return false;
        }

        return dataFetcher.isRefreshRequired(evt);
    }

    /**
     * Forces a refresh of data based on current search parameters.
     *
     * @return The refreshed data.
     *
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO getRefreshedData() throws ExecutionException {
        return fetchResults(true);
    }

    /**
     * Fetches results using current page fetcher or returns null if no current
     * page fetcher. Also stores current results in local variable.
     *
     * @return The current search results or null if no current page fetcher.
     *
     * @throws ExecutionException
     */
    private synchronized SearchResultsDTO fetchResults(boolean hardRefresh) throws ExecutionException {
        return fetchResults(this.daoFetcher, hardRefresh);
    }

    private synchronized SearchResultsDTO fetchResults(DAOFetcher<?> dataFetcher, boolean hardRefresh) throws ExecutionException {
        SearchResultsDTO newResults = null;
        if (dataFetcher != null) {
            newResults = dataFetcher.getSearchResults(this.pageSize, this.pageIdx, hardRefresh);
        }

        this.currentSearchResults = newResults;
        return newResults;

    }
}
