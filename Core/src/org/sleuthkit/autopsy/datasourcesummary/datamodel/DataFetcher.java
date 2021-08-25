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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

/**
 * A function that accepts input of type I and outputs type O. This function is
 * meant to be utilized with DataFetchWorker and can therefore, throw an
 * interrupted exception if the processing is cancelled or an Exception of on
 * another type in the event that the fetching encountered an error.
 */
@FunctionalInterface
public interface DataFetcher<I, O> {

    /**
     * A function that accepts an input argument and outputs a result. Since it
     * is meant to be used with the DataFetchWorker, it may throw an interrupted
     * exception if the thread has been interrupted. It throws another type of
     * exception if there is an error during fetching.
     *
     * @param input The input argument.
     *
     * @return The output result.
     *
     * @throws Exception
     */
    O runQuery(I input) throws Exception;
}
