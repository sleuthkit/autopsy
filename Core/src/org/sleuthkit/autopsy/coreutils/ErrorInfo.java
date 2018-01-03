/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

/**
 * Encapsulates an error message and an associated exception, if any.
 */
final public class ErrorInfo {

    private final String errorSource;
    private final String message;
    private final Exception exception;

    public ErrorInfo(String errorSource, String message) {
        this.errorSource = errorSource;
        this.message = message;
        this.exception = null;
    }

    public ErrorInfo(String errorSource, String message, Exception exception) {
        this.errorSource = errorSource;
        this.message = message;
        this.exception = exception;
    }

    public String getErrroSource() {
        return this.errorSource;
    }

    public String getMessage() {
        return this.message;
    }

    public boolean hasException() {
        return exception != null;
    }

    public Exception getException() {
        return this.exception;
    }
}
