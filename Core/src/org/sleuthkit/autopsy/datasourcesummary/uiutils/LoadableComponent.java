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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

/**
 * Interface for a loadable component that can show messages, results, or a
 * DataFetchResult.
 */
public interface LoadableComponent<T> {

    /**
     * Clears the results from the underlying JTable and shows the provided
     * message.
     *
     * @param message The message to be shown.
     */
    void showMessage(String message);

    /**
     * Shows a default loading message on the table. This will clear any results
     * in the table.
     */
    void showDefaultLoadingMessage();

    /**
     * Shows the list as rows of data in the table. If overlay message will be
     * cleared if present.
     *
     * @param data The data to be shown where each item represents a row of
     *             data.
     */
    void showResults(T data);

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the errorMessage will be displayed. If the operation completed
     * successfully and no data is present, noResultsMessage will be shown.
     * Otherwise, the data will be shown as rows in the table.
     *
     * @param result           The DataFetchResult.
     * @param errorMessage     The error message to be shown in the event of an
     *                         error.
     * @param noResultsMessage The message to be shown if there are no results
     *                         but the operation completed successfully.
     */
    void showDataFetchResult(DataFetchResult<T> result, String errorMessage, String noResultsMessage);

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the DEFAULT_ERROR_MESSAGE will be displayed. If the operation
     * completed successfully and no data is present, DEFAULT_NO_RESULTS_MESSAGE
     * will be shown. Otherwise, the data will be shown as rows in the table.
     *
     * @param result The DataFetchResult.
     */
    void showDataFetchResult(DataFetchResult<T> result);
}
