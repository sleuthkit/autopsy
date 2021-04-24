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

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.lang.math.NumberUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.appservices.AutopsyService;

/**
 * This class handles the task of finding and identifying KWS index folders.
 */
class IndexFinder {

    private static final String KWS_OUTPUT_FOLDER_NAME = "keywordsearch";
    private static final String KWS_DATA_FOLDER_NAME = "data";
    private static final String INDEX_FOLDER_NAME = "index";
    private static final String CURRENT_SOLR_VERSION = "8";
    private static final int CURRENT_SOLR_VERSION_INT = 8;
    private static final String CURRENT_SOLR_SCHEMA_VERSION = "2.3";

    static String getCurrentSolrVersion() {
        return CURRENT_SOLR_VERSION;
    }

    static String getCurrentSchemaVersion() {
        return CURRENT_SOLR_SCHEMA_VERSION;
    }

    static Index findLatestVersionIndex(List<Index> allIndexes) {
        for (Index index : allIndexes) {
            if (index.getSolrVersion().equals(CURRENT_SOLR_VERSION) && index.getSchemaVersion().equals(CURRENT_SOLR_SCHEMA_VERSION)) {
                return index;
            }
        }
        return null;
    }

    static Index createLatestVersionIndex(Case theCase) throws AutopsyService.AutopsyServiceException {
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema" + CURRENT_SOLR_SCHEMA_VERSION;
        // new index should be stored in "\ModuleOutput\keywordsearch\data\solrX_schemaY\index"
        File targetDirPath = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, indexFolderName, INDEX_FOLDER_NAME).toFile(); //NON-NLS
        return new Index(targetDirPath.getAbsolutePath(), CURRENT_SOLR_VERSION, CURRENT_SOLR_SCHEMA_VERSION, "", theCase.getName());
    }

    static Index identifyIndexToUse(List<Index> allIndexes) {
        /*
         * NOTE: All of the following paths are valid multi-user index paths:
         * (Solr 4, schema 1.8)
         * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema2.0\index
         */
        Index bestCandidateIndex = null;
        double solrVerFound = 0.0;
        double schemaVerFound = 0.0;
        for (Index index : allIndexes) {            
            if (NumberUtils.toDouble(index.getSolrVersion()) > CURRENT_SOLR_VERSION_INT) {
                // "legacy" Solr server cannot open "future" versions of Solr indexes
                continue;
            }
            // higher Solr version takes priority because it may negate index upgrade
            if (NumberUtils.toDouble(index.getSolrVersion()) >= solrVerFound) {
                // if same solr version, pick the one with highest schema version
                if (NumberUtils.toDouble(index.getSchemaVersion()) >= schemaVerFound) {
                    bestCandidateIndex = index;
                    solrVerFound = NumberUtils.toDouble(index.getSolrVersion());
                    schemaVerFound = NumberUtils.toDouble(index.getSchemaVersion());
                }
            }
        }
        return bestCandidateIndex;
    }

    /**
     * Checks if a the list of indexes contains an index from a "future" version
     * of Solr. This happens when a "legacy" version of Autopsy attempts to open
     * a Solr index created by Autopsy that uses later version of Solr.
     *
     * @param allIndexes List of Index objects
     *
     * @return Version number of "future" index if present, empty string otherwise
     */
    static String isFutureIndexPresent(List<Index> allIndexes) {
        for (Index index : allIndexes) {
            if (NumberUtils.toDouble(index.getSolrVersion()) > CURRENT_SOLR_VERSION_INT) {
                // "legacy" Solr server cannot open "future" versions of Solr indexes
                return index.getSolrVersion();
            }
        }
        return "";
    }  
}
