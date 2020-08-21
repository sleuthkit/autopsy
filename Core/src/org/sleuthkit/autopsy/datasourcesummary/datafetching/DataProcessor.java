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
package org.sleuthkit.autopsy.datasourcesummary.datafetching;

/**
 * A function that accepts input of type I and outputs type O. This function is
 * meant to be utilized with DataFetchWorker and can therefore, throw an
 * interrupted exception if the processing is cancelled or a
 * DataProcessorException in the event that the processing encountered an error.
 */
@FunctionalInterface
public interface DataProcessor<I, O> {

    /**
     * Wraps a DataProcessor in statements looking to see if the thread has been
     * interrupted and throws an InterruptedException in that event.
     *
     * @param toBeWrapped The data processor to be wrapped.
     *
     * @return The wrapped data processor that will throw an interrupted
     *         exception before or after the toBeWrapped data processor has been
     *         run.
     */
    public static <I1, O1> DataProcessor<I1, O1> wrap(DataProcessor<I1, O1> toBeWrapped) {
        return new DataProcessor<I1, O1>() {
            @Override
            public O1 process(I1 input) throws InterruptedException, DataProcessorException {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                O1 output = toBeWrapped.process(input);
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                return output;
            }
        };
    }

    /**
     * A function that accepts an input argument and outputs a result. Since it
     * is meant to be used with the DataFetchWorker, it throws an interrupted
     * exception if the thread has been interrupted and data processing
     * exception if there is an error during processing.
     *
     * @param input The input argument.
     *
     * @return The output result.
     *
     * @throws InterruptedException
     * @throws DataProcessorException
     */
    O process(I input) throws InterruptedException, DataProcessorException;
}
