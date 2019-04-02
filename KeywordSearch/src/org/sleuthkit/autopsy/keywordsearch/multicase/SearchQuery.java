/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.keywordsearch.Server;

/**
 * A keyword search query.
 */
@Immutable
final class SearchQuery {

    private static final String SEARCH_TERM_CHARS_TO_ESCAPE = "/+-&|!(){}[]^\"~*?:\\";
    private static final String SOLR_DOC_CONTENT_STR_FIELD = Server.Schema.CONTENT_STR.toString(); //NON-NLS
    private final String searchTerm;

    /**
     * Constructs a multicase keyword search query.
     *
     * @param queryType  The query type.
     * @param searchTerm The search term for the query.
     */
    SearchQuery(QueryType queryType, String searchTerm) {
        switch (queryType) {
            case EXACT_MATCH:
                this.searchTerm = prepareExactMatchSearchTerm(searchTerm);
                break;
            case SUBSTRING:
                this.searchTerm = prepareSubstringSearchTerm(searchTerm);
                break;
            case REGEX:
                this.searchTerm = prepareRegexSearchTerm(searchTerm);
                break;
            default:
                this.searchTerm = searchTerm;
                break;
        }
    }

    /**
     * Gets the search term.
     *
     * @return The query.
     */
    String getSearchTerm() {
        return searchTerm;
    }

    /**
     * Escapes and quotes a given search term as required for an exact match
     * search query.
     *
     * @param searchTerm A "raw" input search term.
     *
     * @return A search term suitable for an exact match query.
     */
    private static String prepareExactMatchSearchTerm(String searchTerm) {
        String escapedSearchTerm = escapeSearchTerm(searchTerm);
        if (!searchTerm.startsWith("\"")) {
            escapedSearchTerm = "\"" + escapedSearchTerm;
        }
        if (!searchTerm.endsWith("\"")) {
            escapedSearchTerm += "\"";
        }
        return escapedSearchTerm;
    }

    /**
     * Adds delimiters and possibly wildcards to a given search terms as
     * required for a regular expression search query.
     *
     * @param searchTerm A "raw" input search term.
     *
     * @return A search term suitable for a regex query.
     */
    private static String prepareRegexSearchTerm(String searchTerm) {
        /*
         * Add slash delimiters and, if necessary, wildcards (.*) at the
         * beginning and end of the search term. The wildcards are added because
         * Lucerne automatically adds a '^' prefix and '$' suffix to the search
         * terms for regex searches. Without the '.*' wildcards, the search term
         * will have to match the entire content_str field, which is not
         * generally the intent of the user.
         */
        String regexSearchTerm = SOLR_DOC_CONTENT_STR_FIELD
                + ":/"
                + (searchTerm.startsWith(".*") ? "" : ".*")
                + searchTerm.toLowerCase()
                + (searchTerm.endsWith(".*") ? "" : ".*")
                + "/";
        return regexSearchTerm;
    }

    /**
     * Escapes and adds delimiters and wpossibly wildcards to a given search
     * term as required for a substring search.
     *
     * @param searchTerm A "raw" input search term.
     *
     * @return A search term suitable for a substring query.
     */
    private static String prepareSubstringSearchTerm(String searchTerm) {
        String escapedSearchTerm = escapeSearchTerm(searchTerm);
        return prepareRegexSearchTerm(escapedSearchTerm);
    }

    /**
     * Escapes a search term as required for a Lucene query.
     *
     * @param searchTerm A "raw" input search term.
     *
     * @return An escaped version of the "raw" input search term.
     */
    public static String escapeSearchTerm(String searchTerm) {
        String rawSearchTerm = searchTerm.trim();
        if (0 == rawSearchTerm.length()) {
            return rawSearchTerm;
        }
        StringBuilder escapedSearchTerm = new StringBuilder(rawSearchTerm.length());
        for (int i = 0; i < rawSearchTerm.length(); ++i) {
            final char nextChar = rawSearchTerm.charAt(i);
            if (SEARCH_TERM_CHARS_TO_ESCAPE.contains(Character.toString(nextChar))) {
                escapedSearchTerm.append("\\");
            }
            escapedSearchTerm.append(nextChar);
        }
        return escapedSearchTerm.toString();
    }

    /**
     * An enumeration of the supported query types for keywod searches.
     */
    enum QueryType {
        EXACT_MATCH,
        SUBSTRING,
        REGEX;
    }

}
