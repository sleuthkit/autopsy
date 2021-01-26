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

import org.sleuthkit.autopsy.ingest.IngestJobSettings;

/**
 * Interface for a module that performs configuration. Implementers of this
 * class must have a no-parameter constructor in order to be instantiated
 * properly.
 */
public interface ConfigurationModule<T> {

    /**
     * Configures the autopsy environment and updates the current ingest job
     * settings to augment with any additional templates that may need to be
     * added or any other ingest job setting changes.
     *
     * @param curSettings The current IngestJobSettings.
     * @param parameters The parameters object for this configuration module.
     * @return The new IngestJobSettings or 'curSettings' if no
     * IngestJobSettings need to be made.
     */
    IngestJobSettings configure(IngestJobSettings curSettings, T parameters);

    /**
     * In the event that settings outside the IngestJobSettings were altered,
     * this provides a means of reverting those changes when the test completes.
     * Configuration changes in the 'configure' method can be captured in this
     * object for revert to utilize.
     */
    default void revert() {
    }
}
