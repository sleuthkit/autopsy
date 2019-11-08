/*
 * Central Repository
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import org.sleuthkit.autopsy.exceptions.AutopsyException;

/**
 * An exception to be thrown by an artifact manager.
 */
public class EamDbException extends AutopsyException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an exception to be thrown by an artifact manager.
     *
     * @param message The exception message.
     */
    public EamDbException(String message) {
        super(message);
    }

    /**
     * Constructs an exception to be thrown by an artifact manager with a user exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     */
    public EamDbException(String message, String userMessage) {
        super(message, userMessage);
    }

    /**
     * Constructs an exception to be thrown by an artifact manager with a user
     * exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     * @param cause       Exception cause.
     */
    public  EamDbException(String message, String userMessage, Throwable cause) {
        super(message, userMessage, cause);
    }

    /**
     * Constructs an exception to be thrown by an artifact manager.
     *
     * @param message The exception message.
     * @param cause   The exception cause.
     */
    public EamDbException(String message, Throwable cause) {
        super(message, cause);
    }
}
