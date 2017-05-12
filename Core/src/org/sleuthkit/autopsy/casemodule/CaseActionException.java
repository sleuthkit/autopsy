/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

/**
 * Exception thrown when a case action (e.g., create, open, close, delete)
 * experiences an error condition.
 */
public class CaseActionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an exception thrown when a case action (e.g., create, open,
     * close, delete) experiences an error condition.
     *
     * @param message An error message.
     */
    public CaseActionException(String message) {
        super(message);
    }

    /**
     * Constructs an exception thrown when a case action (e.g., create, open,
     * close, delete) experiences an error condition.
     *
     * @param message An error message.
     * @param cause   An excception that caused this exception to be thrown.
     */
    public CaseActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
