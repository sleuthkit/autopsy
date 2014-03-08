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

import java.io.Serializable;

/**
 * Interface for resources configuration for ingest modules. The configuration
 * is serializable to support its persistence between invocations of the
 * application.
 */
public interface IngestModuleResourcesConfig extends Serializable {

    /**
     * Determines whether the resources configuration is valid.
     *
     * @return True if the configuration is valid, false otherwise
     */
    boolean isValid();
}
