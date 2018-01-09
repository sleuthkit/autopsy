/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

package org.sleuthkit.autopsy.events;

/**
 * Provides a system exception for the Message Service.
 */
public class MessageServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with null as its message.
     */
    public MessageServiceException() {
        super();
    }

    /**
     * Constructs a new exception with the specified message.
     *
     * @param message The message.
     */
    public MessageServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message The message.
     * @param cause   The cause.
     */
    public MessageServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
