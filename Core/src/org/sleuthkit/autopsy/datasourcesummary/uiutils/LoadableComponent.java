/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.List;

/**
 *
 * @author gregd
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
    void showResults(List<T> data);
    
    
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
    void showDataFetchResult(DataFetchResult<List<T>> result, String errorMessage, String noResultsMessage);

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the DEFAULT_ERROR_MESSAGE will be displayed. If the operation
     * completed successfully and no data is present, DEFAULT_NO_RESULTS_MESSAGE
     * will be shown. Otherwise, the data will be shown as rows in the table.
     *
     * @param result The DataFetchResult.
     */
    public void showDataFetchResult(DataFetchResult<List<T>> result);
}
