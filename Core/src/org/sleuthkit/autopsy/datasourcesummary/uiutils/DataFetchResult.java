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
 * The result of a loading process.
 */
public final class DataFetchResult<R> {

    /**
     * The type of result.
     */
    public enum ResultType {
        SUCCESS, ERROR
    }

    /**
     * Creates a DataFetchResult of loaded data including the data.
     *
     * @param data The data.
     *
     * @return The loaded data result.
     */
    public static <R> DataFetchResult<R> getSuccessResult(R data) {
        return new DataFetchResult<>(ResultType.SUCCESS, data, null);
    }

    /**
     * Returns an error result.
     *
     * @param e The exception (if any) present with the error.
     *
     * @return The error result.
     */
    public static <R> DataFetchResult<R> getErrorResult(Throwable e) {
        return new DataFetchResult<>(ResultType.ERROR, null, e);
    }

    private final ResultType state;
    private final R data;
    private final Throwable exception;

    /**
     * Main constructor for the DataLoadingResult.
     *
     * @param state     The state of the result.
     * @param data      If the result is SUCCESS, the data related to this
     *                  result.
     * @param exception If the result is ERROR, the related exception.
     */
    private DataFetchResult(ResultType state, R data, Throwable exception) {
        this.state = state;
        this.data = data;
        this.exception = exception;
    }

    /**
     * @return The current loading state.
     */
    public ResultType getResultType() {
        return state;
    }

    /**
     * @return The data if the state is SUCCESS.
     */
    public R getData() {
        return data;
    }

    /**
     * @return The exception if the state is ERROR.
     */
    public Throwable getException() {
        return exception;
    }
}
