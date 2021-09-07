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

import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A Swing worker that accepts an argument of a data fetcher and a result
 * handler. If the data fetcher throws an InterruptedException, it is treated as
 * a cancellation and not passed to the result handler.
 */
public class DataFetchWorker<A, R> extends SwingWorker<R, Void> {

    /**
     * Holds the functions necessary for a DataFetchWorker. Includes the fetcher
     * and result handler. The args are not included since they are likely
     * dynamic.
     */
    public static class DataFetchComponents<A1, R1> {

        private final DataFetcher<A1, R1> fetcher;
        private final Consumer<DataFetchResult<R1>> resultHandler;

        /**
         * Main constructor.
         *
         * @param fetcher       The fetcher to be used as an argument for the
         *                      DataFetchWorker.
         * @param resultHandler The result handler to be used as an argument for
         *                      the DataFetchWorker.
         */
        public DataFetchComponents(DataFetcher<A1, R1> fetcher, Consumer<DataFetchResult<R1>> resultHandler) {
            this.fetcher = fetcher;
            this.resultHandler = resultHandler;
        }

        /**
         * @return The function that fetches the data.
         */
        public DataFetcher<A1, R1> getFetcher() {
            return fetcher;
        }

        /**
         * @return When those results are received, this function handles
         *         presenting the results in the UI.
         */
        public Consumer<DataFetchResult<R1>> getResultHandler() {
            return resultHandler;
        }
    }

    private static final Logger logger = Logger.getLogger(DataFetchWorker.class.getName());
    private static final int MAX_INNER_EXCEPTION_DEPTH = 100;

    private final A args;
    private final DataFetcher<A, R> processor;
    private final Consumer<DataFetchResult<R>> resultHandler;

    /**
     * Main constructor for this swing worker.
     *
     * @param components Accepts a components arg which provides a data
     *                   processor and a results consumer.
     * @param args       The argument to be provided to the data processor.
     */
    public DataFetchWorker(DataFetchComponents<A, R> components, A args) {
        this(components.getFetcher(), components.getResultHandler(), args);
    }

    /**
     * Main constructor for this swing worker.
     *
     * @param processor     The function that will do the fetching of the data
     *                      provided the given args. InterruptedException's are
     *                      treated as cancellations and are not passed to the
     *                      result handler.
     * @param resultHandler The ui function that will handle the result of the
     *                      data processing.
     * @param args          The args provided to the data processor.
     */
    public DataFetchWorker(
            DataFetcher<A, R> processor,
            Consumer<DataFetchResult<R>> resultHandler,
            A args) {

        this.args = args;
        this.processor = processor;
        this.resultHandler = resultHandler;
    }

    @Override
    protected R doInBackground() throws Exception {
        return processor.runQuery(args);
    }

    @Override
    protected void done() {
        // if cancelled, simply return
        if (Thread.interrupted() || isCancelled()) {
            return;
        }

        R result = null;
        try {
            result = get();
        } catch (InterruptedException ignored) {
            // if cancelled, simply return
            return;
        } catch (ExecutionException ex) {
            Throwable inner = ex.getCause();
            for (int i = 0; i < MAX_INNER_EXCEPTION_DEPTH; i++) {
                if (inner == null) {
                    break;
                } else if (inner instanceof InterruptedException) {
                    // if cancelled during operation, simply return
                    return;
                } else {
                    inner = inner.getCause();
                }
            }

            // and pass the result to the client
            resultHandler.accept(DataFetchResult.getErrorResult(ex.getCause()));
            return;
        }

        // if cancelled, simply return
        if (Thread.interrupted() || isCancelled()) {
            return;
        }

        // if the data is loaded, send the data to the consumer.
        resultHandler.accept(DataFetchResult.getSuccessResult(result));
    }
}
