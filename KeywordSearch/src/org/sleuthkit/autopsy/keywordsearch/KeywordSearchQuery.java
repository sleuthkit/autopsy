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
package org.sleuthkit.autopsy.keywordsearch;

import org.sleuthkit.autopsy.keywordsearch.KeywordSearchSettingsManager.KeywordSearchSettingsManagerException;

/**
 * Interface for a search query. Implemented by various engines or methods of
 * using the same engine. One of these is created for each query.
 */
interface KeywordSearchQuery {

    KeywordList getKeywordList();

    /**
     * validate the query pre execution
     *
     * @return true if the query passed validation
     */
    public boolean validate();

    /**
     * execute query and return results without publishing them return results
     * for all matching terms
     *
     * @throws NoOpenCoreException                   if query failed due to
     *                                               server error, this could be
     *                                               a notification to stop
     *                                               processing
     * @throws KeywordSearchSettingsManagerException if the settings cannot be
     *                                               loaded
     * @return
     */
    public QueryResults performQuery() throws NoOpenCoreException, KeywordSearchSettingsManagerException;

    /**
     * Set an optional filter to narrow down the search Adding multiple filters
     * ANDs them together. For OR, add multiple ids to a single filter
     *
     * @param filter filter to set on the query
     */
    public void addFilter(KeywordQueryFilter filter);

    /**
     * Set an optional SOLR field to narrow down the search
     *
     * @param field field to set on the query
     */
    public void setField(String field);

    /**
     * Modify the query string to be searched as a substring instead of a whole
     * word
     *
     * @param isSubstring
     */
    public void setSubstringQuery();

    /**
     * escape the query string and use the escaped string in the query
     */
    public void escape();

    /**
     *
     * @return true if query was escaped
     */
    public boolean isEscaped();

    /**
     *
     * @return true if query is a literal query (non regex)
     */
    public boolean isLiteral();

    /**
     * return original keyword/query string
     *
     * @return the query String supplied originally
     */
    public String getQueryString();

    /**
     * return escaped keyword/query string if escaping was done
     *
     * @return the escaped query string, or original string if no escaping done
     */
    public String getEscapedQueryString();

    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName);

}
