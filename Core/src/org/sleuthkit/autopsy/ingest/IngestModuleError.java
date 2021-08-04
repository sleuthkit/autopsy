/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
 * Encapsulates a Throwable thrown by an ingest module with the display name of
 * the module for logging purposes.
 */
public final class IngestModuleError {

    private final String moduleDisplayName;
    private final Throwable throwable;

    /**
     * Constructs an object that encapsulates a Throwable thrown by an ingest
     * module with the display name of the module for logging purposes.
     *
     * @param moduleDisplayName The display name of the module.
     * @param throwable         The throwable.
     */
    IngestModuleError(String moduleDisplayName, Throwable throwable) {
        this.moduleDisplayName = moduleDisplayName;
        this.throwable = throwable;
    }

    /**
     * Gets the module display name.
     *
     * @return The module display name.
     */
    public String getModuleDisplayName() {
        return this.moduleDisplayName;
    }

    /**
     * Gets the throwable.
     *
     * @return The Throwable
     */
    public Throwable getThrowable() {
        return this.throwable;
    }

    /**
     * Gets the throwable.
     *
     * @return The Throwable
     *
     * @deprecated Use getThrowable instead.
     *
     */
    @Deprecated
    public Throwable getModuleError() {
        return this.throwable;
    }
}
