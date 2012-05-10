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
     * The index and database both exist, and the index is older.
     */
    INDEX_OUTDATED("Index is older than database."),
    /**
     * The index and database both exist, and the index is not older.
     */
    INDEX_CURRENT("Database has index."),
    /**
     * The index exists but the database does not.
     */
    NO_DB("Only an index exists."),
    /**
     * The database exists but the index does not.
     */
    NO_INDEX("Database does not have index."),
    /**
     * Neither the index nor the database exists.
     */
    NONE("No index or database."),
    /**
     * The index is currently being generated
     */
    INDEXING("The index is currently being generated");
    
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
        return status == NO_DB || status == INDEX_CURRENT || status == INDEX_OUTDATED;
    }
}
