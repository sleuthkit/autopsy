/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

/**
 * Encapsulates an exception thrown by an ingest module during an operation such
 * as startup or shut down with an exception object for the error that occurred.
 */
public final class IngestManagerException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Create exception containing the error message
     *
     * @param message the message
     */
    IngestManagerException(String message) {
        super(message);
    }

    /**
     * Create exception containing the error message
     *
     * @param message the message
     * @param ex      cause exception
     */
    public IngestManagerException(String message, Exception ex) {
        super(message, ex);
    }
}
