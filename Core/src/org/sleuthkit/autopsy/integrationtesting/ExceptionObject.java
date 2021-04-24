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
package org.sleuthkit.autopsy.integrationtesting;

import org.apache.commons.lang.exception.ExceptionUtils;

/**
 * An object representing an exception to be serialized.
 */
class ExceptionObject {

    private final String message;
    private final String stackTrace;
    private final ExceptionObject innerException;

    ExceptionObject(Throwable t) {
        this.message = t.getMessage();
        this.stackTrace = ExceptionUtils.getStackTrace(t);
        this.innerException = (t.getCause() == null) ? null : new ExceptionObject(t.getCause());
    }

    /**
     * @return The message of the exception.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return The stacktrace as a string.
     */
    public String getStackTrace() {
        return stackTrace;
    }

    /**
     * @return The inner exception (if any).
     */
    public ExceptionObject getInnerException() {
        return innerException;
    }
}
