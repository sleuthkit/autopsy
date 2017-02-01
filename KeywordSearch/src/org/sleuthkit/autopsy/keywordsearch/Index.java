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

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class encapsulates KWS index data.
 */
class Index {
    
    private final String indexPath;
    private final String schemaVersion;
    private final String solrVersion;
    private final String indexName;
    private static final String DEFAULT_CORE_NAME = "coreCase"; //NON-NLS
    
    Index(String indexPath, String solrVersion, String schemaVersion, String coreName, String caseName) {
        this.indexPath = indexPath;
        this.solrVersion = solrVersion;
        this.schemaVersion = schemaVersion;
        if (coreName == null || coreName.isEmpty()) {
            // come up with a new core name
            coreName = createCoreName(caseName);
        }
        this.indexName = coreName;
    }
    
    /**
     * Create and sanitize a core name.
     *
     * @param caseName Case name
     *
     * @return The sanitized Solr core name
     */
    private String createCoreName(String caseName) {
        if (caseName.isEmpty()) {
            caseName = DEFAULT_CORE_NAME;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        String coreName = caseName + "_" + dateFormat.format(date);
        return sanitizeCoreName(coreName);
    }
    
    /**
     * Sanitizes the case name for Solr cores.
     *
     * Solr:
     * http://stackoverflow.com/questions/29977519/what-makes-an-invalid-core-name
     * may not be / \ :
     * Starting Solr6: core names must consist entirely of periods, underscores, hyphens, and alphanumerics as well not start with a hyphen. may not contain space characters.
     *
     * @param coreName A candidate core name.
     *
     * @return The sanitized core name.
     */
    static private String sanitizeCoreName(String coreName) {

        String result;

        // Remove all non-ASCII characters
        result = coreName.replaceAll("[^\\p{ASCII}]", "_"); //NON-NLS

        // Remove all control characters
        result = result.replaceAll("[\\p{Cntrl}]", "_"); //NON-NLS

        // Remove spaces / \ : ? ' "
        result = result.replaceAll("[ /?:'\"\\\\]", "_"); //NON-NLS
        
        // Make it all lowercase
        result = result.toLowerCase();

        // Must not start with hyphen
        if (result.length() > 0 && !(Character.isLetter(result.codePointAt(0))) && !(result.codePointAt(0) == '-')) {
            result = "_" + result;
        }

        if (result.isEmpty()) {
            result = DEFAULT_CORE_NAME;
        }

        return result;
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
     * @return the indexName
     */
    String getIndexName() {
        return indexName;
    }
}
