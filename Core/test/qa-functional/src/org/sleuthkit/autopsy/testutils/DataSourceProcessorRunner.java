/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testutils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.datamodel.Content;

/**
 * A utility that runs an auto ingest data source processor, blocking until the
 * processor is finished.
 */
public final class DataSourceProcessorRunner {

    public static ProcessorCallback runDataSourceProcessor(AutoIngestDataSourceProcessor processor, Path dataSourcePath) throws AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, InterruptedException {
        DataSourceProcessorProgressMonitor progressMonitor = new ProgressMonitor();
        Object ingestMonitor = new Object();
        ProcessorCallback callBack = new ProcessorCallback(ingestMonitor);
        synchronized (ingestMonitor) {
            processor.process(UUID.randomUUID().toString(), dataSourcePath, progressMonitor, callBack);
            ingestMonitor.wait();
        }
        return callBack;
    }

    /**
     * DataSourceProcessorRunner instances cannot be instatiated.
     */
    private DataSourceProcessorRunner() {
    }

    /**
     * A data source processor "callback" that collects the results of running a
     * data source processor on a data source and unblocks the caller's thread
     * when the data source processor finishes running in its own thread.
     */
    @Immutable
    public static final class ProcessorCallback extends DataSourceProcessorCallback {

        private final Object monitor;
        private final List<String> errorMessages = new ArrayList<>();
        private final List<Content> dataSourceContent = new ArrayList<>();

        /**
         * Constructs a data source processor "callback" that collects the
         * results of running a data source processor on a data source and
         * unblocks the caller's thread when the data source processor finishes
         * running in its own thread.
         *
         * @param monitor A monitor for the callback to signal when the data
         *                source processor completes its processing.
         */
        private ProcessorCallback(Object monitor) {
            this.monitor = monitor;
        }

        /**
         * Called by the data source processor when it finishes running in its
         * own thread.
         *
         * @param result            The result code for the processing of the
         *                          data source.
         * @param errorMessages     Any error messages generated during the
         *                          processing of the data source.
         * @param dataSourceContent The content produced by processing the data
         *                          source.
         */
        @Override
        public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSourceContent) {
            this.errorMessages.addAll(errorMessages);
            this.dataSourceContent.addAll(dataSourceContent);
            synchronized (monitor) {
                monitor.notify();
            }
        }

        /**
         * Called by the data source processor when it finishes running in its
         * own thread, if that thread is the AWT (Abstract Window Toolkit) event
         * dispatch thread (EDT).
         *
         * @param result            The result code for the processing of the
         *                          data source.
         * @param errorMessages     Any error messages generated during the
         *                          processing of the data source.
         * @param dataSourceContent The content produced by processing the data
         *                          source.
         */
        @Override
        public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSourceContent) {
            done(result, errorMessages, dataSourceContent);
        }

        /**
         * Gets any error messages emitted by the data source processor.
         *
         * @return A list of error messages, possibly empty.
         */
        public List<String> getErrorMessages() {
            return new ArrayList<>(this.errorMessages);
        }

        /**
         * Gets any data source Content objects produced by the data source
         * processor.
         *
         * @return A list of content objects, possibly empty.
         */
        public List<Content> getDataSourceContent() {
            return new ArrayList<>(this.dataSourceContent);
        }
    }

    /**
     * A do-nothing data source processor progress monitor.
     */
    @Immutable
    private static final class ProgressMonitor implements DataSourceProcessorProgressMonitor {

        /**
         * Switches the progress indicator to indeterminate mode (the total
         * number of work units to be completed is unknown) or determinate mode
         * (the total number of work units to be completed is unknown).
         *
         * @param indeterminate True for indeterminate mode, false for
         *                      determinate mode.
         */
        @Override
        public void setIndeterminate(final boolean indeterminate) {
        }

        /**
         * Updates the progress indicator with the number of work units
         * completed so far when in determinate mode (the total number of work
         * units to be completed is known).
         *
         * @param workUnitsCompleted Number of work units completed so far.
         */
        @Override
        public void setProgress(final int workUnitsCompleted) {
        }

        /**
         * Updates the progress indicator with a progress message.
         *
         * @param message The progress message.
         */
        @Override
        public void setProgressText(final String message) {
        }
    }

}
