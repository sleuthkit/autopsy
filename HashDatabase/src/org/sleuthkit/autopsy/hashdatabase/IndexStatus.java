/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

/**
 * The status of a HashDb as determined from its indexExists(),
 * databaseExists(), and isOutdated() methods
 * @author pmartel
 */
enum IndexStatus {

    /**
     * The index exists but the database does not. This indicates a text index
     * without an accompanying text database.
     */
    INDEX_ONLY("Index only"),
    /**
     * The database exists but the index does not. This indicates a text database
     * with no index.
     */
    NO_INDEX("No index"),
    /**
     * The index is currently being generated.
     */
    INDEXING("Index is currently being generated"),
    /**
     * The index is generated.
     */
    INDEXED("Indexed"),
    /**
     * An error occurred while determining status.
     */
    UNKNOWN("Error determining status");
    private String message;

    /**
     * @param message Short description of the state represented
     */
    private IndexStatus(String message) {
        this.message = message;
    }

    /**
     * Get status message
     * @return a short description of the state represented
     */
    String message() {
        return this.message;
    }
    
    public static boolean isIngestible(IndexStatus status) {
        return status == INDEX_ONLY || status == INDEXED;
    }
}
