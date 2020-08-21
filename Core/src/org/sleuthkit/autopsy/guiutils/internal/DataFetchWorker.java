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
package org.sleuthkit.autopsy.guiutils.internal;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A Swing worker data-fetching with result-handler class.
 */
public class DataFetchWorker<A, R> extends SwingWorker<R, Void> {

    /**
     * Holds the functions necessary for a DataFetchWorker. Includes the
     * processor and result handler. The args are not included since they are
     * likely dynamic.
     */
    public static class DataFetchComponents<A1, R1> {

        private final DataProcessor<A1, R1> processor;
        private final Consumer<DataLoadingResult<R1>> resultHandler;

        /**
         * Main constructor.
         *
         * @param processor     The processor to be used as an argument for the
         *                      DataFetchWorker.
         * @param resultHandler The result handler to be used as an argument for
         *                      the DataFetchWorker.
         */
        public DataFetchComponents(DataProcessor<A1, R1> processor, Consumer<DataLoadingResult<R1>> resultHandler) {
            this.processor = processor;
            this.resultHandler = resultHandler;
        }

        /**
         * @return The function that processes or fetches the data.
         */
        public DataProcessor<A1, R1> getProcessor() {
            return processor;
        }

        /**
         * @return When those results are received, this function handles
         *         presenting the results in the UI.
         */
        public Consumer<DataLoadingResult<R1>> getResultHandler() {
            return resultHandler;
        }
    }

    private static final Logger logger = Logger.getLogger(DataFetchWorker.class.getName());

    private final A args;
    private final DataProcessor<A, R> processor;
    private final Consumer<DataLoadingResult<R>> resultHandler;

    /**
     * Main constructor for this swing worker.
     *
     * @param components Accepts a components arg which provides a data
     *                   processor and a results consumer.
     * @param args       The argument to be provided to the data processor.
     */
    public DataFetchWorker(DataFetchComponents<A, R> components, A args) {
        this(components.getProcessor(), components.getResultHandler(), args);
    }

    /**
     * Main constructor for this swing worker.
     *
     * @param processor     The function that will do the processing of the data
     *                      provided the given args.
     * @param resultHandler The ui function that will handle the result of the
     *                      data processing.
     * @param args          The args provided to the data processor.
     */
    public DataFetchWorker(
            DataProcessor<A, R> processor,
            Consumer<DataLoadingResult<R>> resultHandler,
            A args) {

        this.args = args;
        this.processor = processor;
        this.resultHandler = resultHandler;
    }

    @Override
    protected R doInBackground() throws Exception {
        if (Thread.interrupted() || isCancelled()) {
            throw new InterruptedException();
        }

        R result = processor.process(args);

        if (Thread.interrupted() || isCancelled()) {
            throw new InterruptedException();
        }

        return result;
    }

    @Override
    protected void done() {
        R result = null;
        try {
            result = get();
        } catch (InterruptedException ignored) {
            // if cancelled, set not loaded andt return
            resultHandler.accept(DataLoadingResult.getNotLoaded());
            return;
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "There was an error while fetching results.", ex);
            Throwable inner = ex.getCause();
            if (inner != null && inner instanceof DataProcessorException) {
                resultHandler.accept(DataLoadingResult.getLoadError((DataProcessorException) inner));
            }
            return;
        }

        if (Thread.interrupted() || isCancelled()) {
            resultHandler.accept(DataLoadingResult.getNotLoaded());
            return;
        }

        resultHandler.accept(DataLoadingResult.getLoaded(result));
    }
}
