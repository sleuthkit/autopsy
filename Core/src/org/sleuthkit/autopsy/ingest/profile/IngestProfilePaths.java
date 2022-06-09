/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.profile;

/**
 * Path information for ingest profiles.
 */
public class IngestProfilePaths {

    private static final IngestProfilePaths instance = new IngestProfilePaths();

    private static final String INGEST_PROFILE_PREFIX = "IngestProfiles.";

    private IngestProfilePaths() {
    }

    /**
     * @return An instance of this class.
     */
    public static IngestProfilePaths getInstance() {
        return instance;
    }

    /**
     * @return The prefix in front of all ingest profiles to differentiate
     *         between this and other ingest settings (i.e. command line, add
     *         image ingest wizard, etc.).
     */
    public String getIngestProfilePrefix() {
        return INGEST_PROFILE_PREFIX;
    }
}
