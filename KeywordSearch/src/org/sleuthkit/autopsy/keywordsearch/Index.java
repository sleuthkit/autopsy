/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

/**
 * This class encapsulates KWS index data.
 */
class Index {
    
    private final String indexPath;
    private final String schemaVersion;
    private final String solrVersion;
    private boolean newIndex;
    
    Index(String indexPath, String solrVersion, String schemaVersion) {
        this.indexPath = indexPath;
        this.solrVersion = solrVersion;
        this.schemaVersion = schemaVersion;
        newIndex = false;
    }   

    /**
     * @return the indexPath
     */
    String getIndexPath() {
        return indexPath;
    }

    /**
     * @return the schemaVersion
     */
    String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * @return the solrVersion
     */
    String getSolrVersion() {
        return solrVersion;
    }

    /**
     * @return the newIndex
     */
    boolean isNewIndex() {
        return newIndex;
    }

    /**
     * @param newIndex the newIndex to set
     */
    void setNewIndex(boolean newIndex) {
        this.newIndex = newIndex;
    }
}
