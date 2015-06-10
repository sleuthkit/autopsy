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
package org.sleuthkit.autopsy.ingest;

/**
 * Encapsulates an exception thrown by an ingest module during an operation such
 * as startup or shut down with an exception object for the error that occurred.
 */
final class IngestModuleError {
    private final String moduleDisplayName;
    private final Throwable error;

    IngestModuleError(String moduleDisplayName, Throwable error) {
        this.moduleDisplayName = moduleDisplayName;
        this.error = error;
    }

    String getModuleDisplayName() {
        return this.moduleDisplayName;
    }

    Throwable getModuleError() {
        return this.error;
    }
}
