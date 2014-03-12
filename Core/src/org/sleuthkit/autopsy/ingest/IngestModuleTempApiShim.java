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
 * This is a temporary shim interface for use until the IngestJobContext class
 * is fully implemented
 */
public interface IngestModuleTempApiShim {

    /**
     * Gets the display name of a module. The name returned should be the same
     * name that is returned by the IngestFactoru.getModuleDisplayName() of the
     * module ingest factory that created the module.
     *
     * @return The display name
     */
    String getName();
}
