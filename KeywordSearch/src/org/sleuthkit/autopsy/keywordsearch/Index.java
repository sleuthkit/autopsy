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
    
    private String indexPath;
    private String schemaVersion;
    private String solrVersion;
    
    Index() {
        this.indexPath = "";
        this.solrVersion = "";
        this.schemaVersion = "";
    }
    
    Index(String indexPath, String solrVersion, String schemaVersion) {
        this.indexPath = indexPath;
        this.solrVersion = solrVersion;
        this.schemaVersion = schemaVersion;
    }

    /**
     * @return the indexPath
     */
    String getIndexPath() {
        return indexPath;
    }

    /**
     * @param indexPath the indexPath to set
     */
    void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

    /**
     * @return the schemaVersion
     */
    String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * @param schemaVersion the schemaVersion to set
     */
    void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /**
     * @return the solrVersion
     */
    String getSolrVersion() {
        return solrVersion;
    }

    /**
     * @param solrVersion the solrVersion to set
     */
    void setSolrVersion(String solrVersion) {
        this.solrVersion = solrVersion;
    }
    
    /**
     * @param true if all Index fields are set, false otherwise
     */
    boolean isIndexDataPopulated() {
        if (!this.indexPath.isEmpty() && !this.solrVersion.isEmpty() && !this.schemaVersion.isEmpty()) {
            return true;
        }
        return false;
    }
}
