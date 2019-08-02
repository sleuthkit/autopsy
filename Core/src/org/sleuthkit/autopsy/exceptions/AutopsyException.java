/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.exceptions;

public class AutopsyException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String userMessage;

    /**
     * Constructs an exception to be thrown by a autopsy without a user message.
     *
     * @param message Exception message.
     */
    public AutopsyException(String message) {
        super(message);
        this.userMessage = message;
    }

    /**
     * Constructs an exception to be thrown by a autopsy with a user exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     */
    public AutopsyException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    /**
     * Constructs an exception to be thrown by a autopsy without a user message.
     *
     * @param message Exception message.
     * @param cause   Exception cause.
     */
    public AutopsyException(String message, Throwable cause) {
        super(message, cause);
        this.userMessage = message;
    }

    /**
     * Constructs an exception to be thrown by a autopsy with a user exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     * @param cause       Exception cause.
     */
    public AutopsyException(String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
    }

    /**
     * Get the user friendly message if one exists.
     *
     * @return the user friendly message if one exists, otherwise returns the exceptions normal message
     */
    public String getUserMessage() {
        return userMessage;
    }

}
