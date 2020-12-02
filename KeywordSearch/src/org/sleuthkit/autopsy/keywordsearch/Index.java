/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
import org.apache.commons.lang.math.NumberUtils;

/**
 * This class encapsulates KWS index data.
 */
final class Index {

    private final String indexPath;
    private final String schemaVersion;
    private final String solrVersion;
    private final String indexName;
    private static final String DEFAULT_CORE_NAME = "text_index"; //NON-NLS

    /**
     * Constructs a representation of a text index.
     *
     * @param indexPath     The path to the index.
     * @param solrVersion   The Solr version of the index.
     * @param schemaVersion The Solr schema version of the index.
     * @param coreName      The core name, may be the empty string or null if
     *                      the corename should be generated.
     * @param caseName      The name of the case, ignored if coreName does not
     *                      need to be generated.
     */
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
        String coreName = sanitizeCoreName(caseName);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        return coreName + "_" + dateFormat.format(date);
    }

    /**
     * Sanitizes the case name for Solr cores.
     *
     * Solr:
     * http://stackoverflow.com/questions/29977519/what-makes-an-invalid-core-name
     * may not be / \ : Starting Solr6: core names must consist entirely of
     * periods, underscores, hyphens, and alphanumerics as well not start with a
     * hyphen. may not contain space characters.
     *
     * @param coreName A candidate core name.
     *
     * @return The sanitized core name.
     */
    static private String sanitizeCoreName(String coreName) {

        String result;
        
        // Allow these characters: '-', '.', '0'-'9', 'A'-'Z', '_', and 'a'-'z'.
        // Replace all else with '_'.
        result = coreName.replaceAll("[^-.0-9A-Z_a-z]", "_"); // NON-NLS

        // Make it all lowercase
        result = result.toLowerCase();

        // Must not start with hyphen
        if (result.length() > 0 && (result.codePointAt(0) == '-')) {
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

    /**
     * Is the current Index instance compatible with the given version number
     *
     * @param version The version number to compare the current Index against
     *
     * @return true if the current major version number is equal to the given
     *         major version number, otherwise false
     */
    boolean isCompatible(String version) {
        // Versions are compatible if they have the same major version no
        int currentMajorVersion = NumberUtils.toInt(schemaVersion.substring(0, schemaVersion.indexOf('.')));
        int givenMajorVersion = NumberUtils.toInt(version.substring(0, version.indexOf('.')));

        return currentMajorVersion == givenMajorVersion;
    }
}
