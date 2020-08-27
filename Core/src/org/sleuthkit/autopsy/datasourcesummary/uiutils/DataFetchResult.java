/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
 * The intermediate or end result of a loading process.
 */
public final class DataFetchResult<R> {

    /**
     * The state of loading in the result.
     */
    public enum ProcessorState {
        LOADED, LOAD_ERROR
    }

    /**
     * Creates a DataLoadingResult of loaded data including the data.
     *
     * @param data The data.
     *
     * @return The loaded data result.
     */
    public static <R> DataFetchResult<R> getLoadedResult(R data) {
        return new DataFetchResult<>(ProcessorState.LOADED, data, null);
    }

    /**
     * Returns a load error result.
     *
     * @param e The exception (if any) present with the error.
     *
     * @return
     */
    public static <R> DataFetchResult<R> getLoadErrorResult(DataFetcherException e) {
        return new DataFetchResult<>(ProcessorState.LOAD_ERROR, null, e);
    }

    private final ProcessorState state;
    private final R data;
    private final DataFetcherException exception;

    /**
     * Main constructor for the DataLoadingResult.
     *
     * @param state     The state of the result.
     * @param data      If the result is LOADED, the data related to this
     *                  result.
     * @param exception If the result is LOAD_ERROR, the related exception.
     */
    private DataFetchResult(ProcessorState state, R data, DataFetcherException exception) {
        this.state = state;
        this.data = data;
        this.exception = exception;
    }

    /**
     * @return The current loading state.
     */
    public ProcessorState getState() {
        return state;
    }

    /**
     * @return The data if the state is LOADED.
     */
    public R getData() {
        return data;
    }

    /**
     * @return The exception if the state is LOAD_ERROR.
     */
    public DataFetcherException getException() {
        return exception;
    }
}
