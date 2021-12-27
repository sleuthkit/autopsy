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

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Interface for kewyord search queries.
 */
interface KeywordSearchQuery {

    KeywordList getKeywordList();

    /**
     * validate the query pre execution
     *
     * @return true if the query passed validation
     */
    boolean validate();

    /**
     * execute query and return results without publishing them return results
     * for all matching terms
     *
     * @throws KeywordSearchModuleException error while executing Solr term
     *                                      query
     * @throws NoOpenCoreException          if query failed due to server error,
     *                                      this could be a notification to stop
     *                                      processing
     * @return
     */
    QueryResults performQuery() throws KeywordSearchModuleException, NoOpenCoreException;

    /**
     * Set an optional filter to narrow down the search Adding multiple filters
     * ANDs them together. For OR, add multiple ids to a single filter
     *
     * @param filter filter to set on the query
     */
    void addFilter(KeywordQueryFilter filter);

    /**
     * Set an optional SOLR field to narrow down the search
     *
     * @param field field to set on the query
     */
    void setField(String field);

    /**
     * Modify the query string to be searched as a substring instead of a whole
     * word
     */
    void setSubstringQuery();

    /**
     * escape the query string and use the escaped string in the query
     */
    void escape();

    /**
     *
     * @return true if query was escaped
     */
    boolean isEscaped();

    /**
     *
     * @return true if query is a literal query (non regex)
     */
    boolean isLiteral();

    /**
     * return original keyword/query string
     *
     * @return the query String supplied originally
     */
    String getQueryString();

    /**
     * return escaped keyword/query string if escaping was done
     *
     * @return the escaped query string, or original string if no escaping done
     */
    String getEscapedQueryString();

    /**
     * Adds a keyword hit artifact for a given keyword hit.
     *
     * @param content      The text source object for the hit.
     * @param foundKeyword The keyword that was found by the search, this may be
     *                     different than the Keyword that was searched if, for
     *                     example, it was a RegexQuery.
     * @param hit          The keyword hit.
     * @param snippet      A snippet from the text that contains the hit.
     * @param listName     The name of the keyword list that contained the
     *                     keyword for which the hit was found.
     *
     *
     * @return The newly created artifact or null if there was a problem
     *         creating it.
     */
    BlackboardArtifact createKeywordHitArtifact(Content content, Keyword foundKeyword, KeywordHit hit, String snippet, String listName, Long ingestJobId);
}
