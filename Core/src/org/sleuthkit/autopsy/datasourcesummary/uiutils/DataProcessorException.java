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
 * An Exception that is thrown when there is an issue processing data in a
 * DataProcessor.
 */
public class DataProcessorException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Main constructor.
     *
     * @param string The error message.
     */
    public DataProcessorException(String string) {
        super(string);
    }

    /**
     * Main constructor.
     *
     * @param string The error message.
     * @param thrwbl The inner exception.
     */
    public DataProcessorException(String string, Throwable thrwbl) {
        super(string, thrwbl);
    }
}
