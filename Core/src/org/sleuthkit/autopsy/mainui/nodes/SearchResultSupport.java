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
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeMimeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.SearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.Content;

/**
 * Provides functionality to handle paging and fetching of search result data.
 */
public class SearchResultSupport {

    private int pageSize;
    private int pageIdx = 0;

    private SearchResultsDTO currentSearchResults = null;
    private DataFetcher<?, ?> pageFetcher = null;
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
        return fetchResults(false);
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
        if (this.pageFetcher == null) {
            throw new IllegalArgumentException("No current page fetcher");
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
     * Determines if a refresh is required for the currently selected item.
     *
     * @param evt The ingest module event.
     *
     * @return True if an update is required.
     */
    public synchronized boolean isRefreshRequired(PropertyChangeEvent evt) {
        return isRefreshRequired(this.pageFetcher, evt);
    }

    /**
     * Determines if a refresh is required for the currently selected item.
     *
     * @param dataFetcher The data fetcher.
     * @param evt         The ingest module event.
     *
     * @return True if an update is required.
     */
    private synchronized <S extends SearchParams, E> boolean isRefreshRequired(DataFetcher<S, E> dataFetcher, PropertyChangeEvent evt) {
        if (dataFetcher == null) {
            return false;
        }

        E evtData = dataFetcher.extractEvtData(evt);

        if (evtData == null) {
            return false;
        }

        S curKey = dataFetcher.getParams(pageSize, pageIdx);
        return dataFetcher.isRefreshRequired(curKey, evtData);
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
        return fetchResults(this.pageFetcher, hardRefresh);
    }

    private synchronized <S extends SearchParams> SearchResultsDTO fetchResults(DataFetcher<S, ?> dataFetcher, boolean hardRefresh) throws ExecutionException {
        SearchResultsDTO newResults = null;
        if (dataFetcher != null) {
            S searchParams = dataFetcher.getParams(this.pageSize, this.pageIdx);
            newResults = dataFetcher.fetch(searchParams, hardRefresh);
        }

        this.currentSearchResults = newResults;
        return newResults;

    }

    private synchronized void resetPaging() {
        this.pageIdx = 0;
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

        this.pageFetcher = new DataFetcher<DataArtifactSearchParam, ModuleDataEvent>() {
            @Override
            public DataArtifactSearchParam getParams(int pageSize, int pageIdx) {
                return new DataArtifactSearchParam(
                        dataArtifactParameters.getArtifactType(),
                        dataArtifactParameters.getDataSourceId(),
                        pageIdx * pageSize,
                        (long) pageSize);
            }

            @Override
            public SearchResultsDTO fetch(DataArtifactSearchParam searchParams, boolean hardRefresh) throws ExecutionException {
                return dao.getDataArtifactsDAO().getDataArtifactsForTable(searchParams, hardRefresh);
            }

            @Override
            public ModuleDataEvent extractEvtData(PropertyChangeEvent evt) {
                return getModuleDataFromEvt(evt);
            }

            @Override
            public boolean isRefreshRequired(DataArtifactSearchParam searchParams, ModuleDataEvent evtData) {
                return dao.getDataArtifactsDAO().isDataArtifactInvalidating(searchParams, evtData);
            }
        };

        return fetchResults(false);
    }

    /**
     * Sets the search parameters to the analysis result search parameters.
     * Subsequent calls that don't change search parameters (i.e. page size
     * changes, page index changes) will use these search parameters to return
     * results.
     *
     * @param analysisResultParameters The data artifact search parameters.
     *
     * @return The results of querying with current paging parameters.
     *
     * @throws ExecutionException
     */
    public synchronized SearchResultsDTO setAnalysisResult(final AnalysisResultSearchParam analysisResultParameters) throws ExecutionException {
        resetPaging();

        this.pageFetcher = new DataFetcher<AnalysisResultSearchParam, ModuleDataEvent>() {
            @Override
            public AnalysisResultSearchParam getParams(int pageSize, int pageIdx) {
                return new AnalysisResultSearchParam(
                        analysisResultParameters.getArtifactType(),
                        analysisResultParameters.getDataSourceId(),
                        pageIdx * pageSize,
                        (long) pageSize);
            }

            @Override
            public SearchResultsDTO fetch(AnalysisResultSearchParam searchParams, boolean hardRefresh) throws ExecutionException {
                return dao.getAnalysisResultDAO().getAnalysisResultsForTable(searchParams, hardRefresh);
            }

            @Override
            public ModuleDataEvent extractEvtData(PropertyChangeEvent evt) {
                return getModuleDataFromEvt(evt);
            }

            @Override
            public boolean isRefreshRequired(AnalysisResultSearchParam searchParams, ModuleDataEvent evtData) {
                return dao.getAnalysisResultDAO().isAnalysisResultsInvalidating(searchParams, evtData);
            }
        };

        return fetchResults(false);
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
        this.pageFetcher = new DataFetcher<FileTypeExtensionsSearchParams, Content>() {
            @Override
            public FileTypeExtensionsSearchParams getParams(int pageSize, int pageIdx) {
                return new FileTypeExtensionsSearchParams(
                        fileExtParameters.getFilter(),
                        fileExtParameters.getDataSourceId(),
                        pageIdx * pageSize,
                        (long) pageSize);
            }

            @Override
            public SearchResultsDTO fetch(FileTypeExtensionsSearchParams searchParams, boolean hardRefresh) throws ExecutionException {
                return dao.getViewsDAO().getFilesByExtension(searchParams, hardRefresh);
            }

            @Override
            public Content extractEvtData(PropertyChangeEvent evt) {
                return getContentFromEvt(evt);
            }

            @Override
            public boolean isRefreshRequired(FileTypeExtensionsSearchParams searchParams, Content evtData) {
                return dao.getViewsDAO().isFilesByExtInvalidating(searchParams, evtData);
            }
        };

        return fetchResults(false);
    }

    /**
     * Sets the search parameters to the file mime type search parameters.
     * Subsequent calls that don't change search parameters (i.e. page size
     * changes, page index changes) will use these search parameters to return
     * results.
     *
     * @param fileMimeKey The file mime type search parameters.
     *
     * @return The results of querying with current paging parameters.
     *
     * @throws ExecutionException
     * @throws IllegalArgumentException
     */
    public synchronized SearchResultsDTO setFileMimes(FileTypeMimeSearchParams fileMimeKey) throws ExecutionException, IllegalArgumentException {
        resetPaging();
        this.pageFetcher = new DataFetcher<FileTypeMimeSearchParams, Content>() {
            @Override
            public FileTypeMimeSearchParams getParams(int pageSize, int pageIdx) {
                return new FileTypeMimeSearchParams(
                        fileMimeKey.getMimeType(),
                        fileMimeKey.getDataSourceId(),
                        pageIdx * pageSize,
                        (long) pageSize);
            }

            @Override
            public SearchResultsDTO fetch(FileTypeMimeSearchParams searchParams, boolean hardRefresh) throws ExecutionException {
                return dao.getViewsDAO().getFilesByMime(searchParams, hardRefresh);
            }

            @Override
            public Content extractEvtData(PropertyChangeEvent evt) {
                return getContentFromEvt(evt);
            }

            @Override
            public boolean isRefreshRequired(FileTypeMimeSearchParams searchParams, Content evtData) {
                return dao.getViewsDAO().isFilesByMimeInvalidating(searchParams, evtData);
            }
        };

        return fetchResults(false);
    }

    /**
     * Returns the content from the ModuleContentEvent. If the event does not
     * contain a ModuleContentEvent or the event does not contain Content, null
     * is returned.
     *
     * @param evt The event
     *
     * @return The inner content or null if no content.
     */
    private static Content getContentFromEvt(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        if (IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleContentEvent)
                && ((ModuleContentEvent) evt.getOldValue()).getSource() instanceof Content) {

            return (Content) ((ModuleContentEvent) evt.getOldValue()).getSource();

        } else {
            return null;
        }
    }

    /**
     * Returns the ModuleDataEvent in the event if there is a child
     * ModuleDataEvent. If not, null is returned.
     *
     * @param evt The event.
     *
     * @return The inner ModuleDataEvent or null.
     */
    private static ModuleDataEvent getModuleDataFromEvt(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        if (IngestManager.IngestModuleEvent.DATA_ADDED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleDataEvent)) {

            return (ModuleDataEvent) evt.getOldValue();
        } else {
            return null;
        }
    }

    /**
     * Means of fetching data based on paging settings.
     */
    private interface DataFetcher<S extends SearchParams, D> {

        /**
         * Returns the search parameters based on the page size and page index.
         *
         * @param pageSize The number of items per page.
         * @param pageIdx  The page index.
         *
         * @return The search parameters.
         */
        S getParams(int pageSize, int pageIdx);

        /**
         * Fetches search results data based on paging settings.
         *
         *
         * @param searchParams The search parameters.
         * @param hardRefresh  Whether or not to perform a hard refresh.
         *
         * @return The retrieved data.
         *
         * @throws ExecutionException
         */
        SearchResultsDTO fetch(S searchParams, boolean hardRefresh) throws ExecutionException;

        /**
         * Extracts pertinent data from the property change event.
         *
         * @param evt The event.
         *
         * @return The extracted data. If null, refresh will not be required.
         */
        D extractEvtData(PropertyChangeEvent evt);

        /**
         * Returns true if the ingest module event will require a refresh in the
         * data.
         *
         * @param searchParams The search parameters.
         * @param evtData      The event data.
         *
         * @return True if the
         */
        boolean isRefreshRequired(S searchParams, D evtData);
    }
}
