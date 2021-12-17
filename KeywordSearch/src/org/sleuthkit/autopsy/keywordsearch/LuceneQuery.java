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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Performs a normal string (i.e. non-regexp) query to SOLR/Lucene. By default,
 * matches in all fields.
 */
class LuceneQuery implements KeywordSearchQuery {

    private static final Logger logger = Logger.getLogger(LuceneQuery.class.getName());
    private String keywordStringEscaped;
    private boolean isEscaped;
    private final Keyword originalKeyword;
    private final KeywordList keywordList;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();
    private String field = null;
    private static final int MAX_RESULTS_PER_CURSOR_MARK = 512;
    static final int SNIPPET_LENGTH = 50;
    static final String HIGHLIGHT_FIELD = Server.Schema.TEXT.toString();

    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    /**
     * Constructor with query to process.
     *
     * @param keyword
     */
    LuceneQuery(KeywordList keywordList, Keyword keyword) {
        this.keywordList = keywordList;
        this.originalKeyword = keyword;
        this.keywordStringEscaped = this.originalKeyword.getSearchTerm();
    }

    @Override
    public void addFilter(KeywordQueryFilter filter) {
        this.filters.add(filter);
    }

    @Override
    public void setField(String field) {
        this.field = field;
    }

    @Override
    public void setSubstringQuery() {
        // Note that this is not a full substring search. Normally substring
        // searches will be done with TermComponentQuery objects instead.
        keywordStringEscaped += "*";
    }

    @Override
    public void escape() {
        keywordStringEscaped = KeywordSearchUtil.escapeLuceneQuery(originalKeyword.getSearchTerm());
        isEscaped = true;
    }

    @Override
    public boolean isEscaped() {
        return isEscaped;
    }

    @Override
    public boolean isLiteral() {
        return originalKeyword.searchTermIsLiteral();
    }

    @Override
    public String getEscapedQueryString() {
        return this.keywordStringEscaped;
    }

    @Override
    public String getQueryString() {
        return this.originalKeyword.getSearchTerm();
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    @Override
    public QueryResults performQuery() throws KeywordSearchModuleException, NoOpenCoreException {

        final Server solrServer = KeywordSearch.getServer();
        double indexSchemaVersion = NumberUtils.toDouble(solrServer.getIndexInfo().getSchemaVersion());

        SolrQuery solrQuery = createAndConfigureSolrQuery(KeywordSearchSettings.getShowSnippets());

        final String strippedQueryString = StringUtils.strip(getQueryString(), "\"");

        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        boolean allResultsProcessed = false;
        List<KeywordHit> matches = new ArrayList<>();
        LanguageSpecificContentQueryHelper.QueryResults languageSpecificQueryResults = new LanguageSpecificContentQueryHelper.QueryResults();
        while (!allResultsProcessed) {
            solrQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrServer.query(solrQuery, SolrRequest.METHOD.POST);
            SolrDocumentList resultList = response.getResults();
            // objectId_chunk -> "text" -> List of previews
            Map<String, Map<String, List<String>>> highlightResponse = response.getHighlighting();

            if (2.2 <= indexSchemaVersion) {
                languageSpecificQueryResults.highlighting.putAll(response.getHighlighting());
            }

            for (SolrDocument resultDoc : resultList) {
                if (2.2 <= indexSchemaVersion) {
                    Object language = resultDoc.getFieldValue(Server.Schema.LANGUAGE.toString());
                    if (language != null) {
                        LanguageSpecificContentQueryHelper.updateQueryResults(languageSpecificQueryResults, resultDoc);
                    }
                }

                try {
                    /*
                     * for each result doc, check that the first occurence of
                     * that term is before the window. if all the ocurences
                     * start within the window, don't record them for this
                     * chunk, they will get picked up in the next one.
                     */
                    final String docId = resultDoc.getFieldValue(Server.Schema.ID.toString()).toString();
                    final Integer chunkSize = (Integer) resultDoc.getFieldValue(Server.Schema.CHUNK_SIZE.toString());
                    final Collection<Object> content = resultDoc.getFieldValues(Server.Schema.CONTENT_STR.toString());

                    // if the document has language, it should be hit in language specific content fields. So skip here.
                    if (resultDoc.containsKey(Server.Schema.LANGUAGE.toString())) {
                        continue;
                    }

                    if (indexSchemaVersion < 2.0) {
                        //old schema versions don't support chunk_size or the content_str fields, so just accept hits
                        matches.add(createKeywordtHit(highlightResponse, docId));
                    } else {
                        //check against file name and actual content seperately.
                        for (Object content_obj : content) {
                            String content_str = (String) content_obj;
                            //for new schemas, check that the hit is before the chunk/window boundary.
                            int firstOccurence = StringUtils.indexOfIgnoreCase(content_str, strippedQueryString);
                            //there is no chunksize field for "parent" entries in the index
                            if (chunkSize == null || chunkSize == 0 || (firstOccurence > -1 && firstOccurence < chunkSize)) {
                                matches.add(createKeywordtHit(highlightResponse, docId));
                            }
                        }
                    }
                } catch (TskException ex) {
                    throw new KeywordSearchModuleException(ex);
                }
            }
            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
                allResultsProcessed = true;
            }
            cursorMark = nextCursorMark;
        }

        List<KeywordHit> mergedMatches;
        if (2.2 <= indexSchemaVersion) {
            mergedMatches = LanguageSpecificContentQueryHelper.mergeKeywordHits(matches, originalKeyword, languageSpecificQueryResults);
        } else {
            mergedMatches = matches;
        }

        QueryResults results = new QueryResults(this);
        //in case of single term literal query there is only 1 term
        results.addResult(new Keyword(originalKeyword.getSearchTerm(), true, true, originalKeyword.getListName(), originalKeyword.getOriginalTerm()), mergedMatches);

        return results;
    }

    @Override
    public boolean validate() {
        return StringUtils.isNotBlank(originalKeyword.getSearchTerm());
    }

    /**
     *Add a keyword hit artifact for a given keyword hit.
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
    @Override
    public BlackboardArtifact createKeywordHitArtifact(Content content, Keyword foundKeyword, KeywordHit hit, String snippet, String listName, Long ingestJobId) {
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, foundKeyword.getSearchTerm()));
        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }

        if (originalKeyword != null) {
            BlackboardAttribute.ATTRIBUTE_TYPE selType = originalKeyword.getArtifactAttributeType();
            if (selType != null) {
                attributes.add(new BlackboardAttribute(selType, MODULE_NAME, foundKeyword.getSearchTerm()));
            }

            if (originalKeyword.searchTermIsWholeWord()) {
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.LITERAL.ordinal()));
            } else {
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.SUBSTRING.ordinal()));
            }
        }

        hit.getArtifactID().ifPresent(artifactID
                -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactID))
        );

        try {
            return content.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_LIKELY_NOTABLE, 
                    null, listName, null, 
                    attributes)
                    .getAnalysisResult();
        } catch (TskCoreException e) {
            logger.log(Level.WARNING, "Error adding bb artifact for keyword hit", e); //NON-NLS
            return null;
        }
    }


    /*
     * Create the query object for the stored keyword
     *
     * @param snippets True if query should request snippets
     *
     * @return
     */
    private SolrQuery createAndConfigureSolrQuery(boolean snippets) throws NoOpenCoreException, KeywordSearchModuleException {
        double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());

        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug
        // Wrap the query string in quotes if this is a literal search term.
        String queryStr = originalKeyword.searchTermIsLiteral()
            ? KeywordSearchUtil.quoteQuery(keywordStringEscaped) : keywordStringEscaped;

        // Run the query against an optional alternative field. 
        if (field != null) {
            //use the optional field
            queryStr = field + ":" + queryStr;
            q.setQuery(queryStr);
        } else if (2.2 <= indexSchemaVersion && originalKeyword.searchTermIsLiteral()) {
            q.setQuery(LanguageSpecificContentQueryHelper.expandQueryString(queryStr));
        } else {
            q.setQuery(queryStr);
        }
        q.setRows(MAX_RESULTS_PER_CURSOR_MARK);
        // Setting the sort order is necessary for cursor based paging to work.
        q.setSort(SolrQuery.SortClause.asc(Server.Schema.ID.toString()));

        q.setFields(Server.Schema.ID.toString(),
                Server.Schema.CHUNK_SIZE.toString(),
                Server.Schema.CONTENT_STR.toString());

        if (2.2 <= indexSchemaVersion && originalKeyword.searchTermIsLiteral()) {
            q.addField(Server.Schema.LANGUAGE.toString());
            LanguageSpecificContentQueryHelper.configureTermfreqQuery(q, keywordStringEscaped);
        }

        for (KeywordQueryFilter filter : filters) {
            q.addFilterQuery(filter.toString());
        }

        if (snippets) {
            configurwQueryForHighlighting(q);
        }

        return q;
    }

    /**
     * Configure the given query to return highlighting information. Must be
     * called after setQuery() has been invoked on q.
     *
     * @param q The SolrQuery to configure.
     */
    private static void configurwQueryForHighlighting(SolrQuery q) throws NoOpenCoreException {
        double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());
        if (2.2 <= indexSchemaVersion) {
            for (Server.Schema field : LanguageSpecificContentQueryHelper.getQueryFields()) {
                q.addHighlightField(field.toString());
            }
        } else {
            q.addHighlightField(HIGHLIGHT_FIELD);
        }

        q.setHighlightSnippets(1);
        q.setHighlightFragsize(SNIPPET_LENGTH);

        //tune the highlighter
        q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one NON-NLS
        q.setParam("hl.tag.pre", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.tag.post", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.fragListBuilder", "simple"); //makes sense for FastVectorHighlighter only NON-NLS

        //Solr bug if fragCharSize is smaller than Query string, StringIndexOutOfBoundsException is thrown.
        q.setParam("hl.fragCharSize", Integer.toString(q.getQuery().length())); //makes sense for FastVectorHighlighter only NON-NLS

        //docs says makes sense for the original Highlighter only, but not really
        //analyze all content SLOW! consider lowering
        q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); //NON-NLS
    }

    private KeywordHit createKeywordtHit(Map<String, Map<String, List<String>>> highlightResponse, String docId) throws TskException {
        /**
         * Get the first snippet from the document if keyword search is
         * configured to use snippets.
         */
        String snippet = "";
        if (KeywordSearchSettings.getShowSnippets()) {
            List<String> snippetList = highlightResponse.get(docId).get(Server.Schema.TEXT.toString());
            // list is null if there wasn't a snippet
            if (snippetList != null) {
                snippet = EscapeUtil.unEscapeHtml(snippetList.get(0)).trim();
            }
        }

        return new KeywordHit(docId, snippet, originalKeyword.getSearchTerm());
    }

    /**
     * return snippet preview context
     *
     * @param query        the keyword query for text to highlight. Lucene
     *                     special chars should already be escaped.
     * @param solrObjectId The Solr object id associated with the file or
     *                     artifact
     * @param isRegex      whether the query is a regular expression (different
     *                     Solr fields are then used to generate the preview)
     * @param group        whether the query should look for all terms grouped
     *                     together in the query order, or not
     *
     * @return
     */
    static String querySnippet(String query, long solrObjectId, boolean isRegex, boolean group) throws NoOpenCoreException {
        return querySnippet(query, solrObjectId, 0, isRegex, group);
    }

    /**
     * return snippet preview context
     *
     * @param query        the keyword query for text to highlight. Lucene
     *                     special chars should already be escaped.
     * @param solrObjectId Solr object id associated with the hit
     * @param chunkID      chunk id associated with the content hit, or 0 if no
     *                     chunks
     * @param isRegex      whether the query is a regular expression (different
     *                     Solr fields are then used to generate the preview)
     * @param group        whether the query should look for all terms grouped
     *                     together in the query order, or not
     *
     * @return
     */
    static String querySnippet(String query, long solrObjectId, int chunkID, boolean isRegex, boolean group) throws NoOpenCoreException {
        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug

        String queryStr;
        if (isRegex) {
            queryStr = HIGHLIGHT_FIELD + ":"
                    + (group ? KeywordSearchUtil.quoteQuery(query)
                            : query);
        } else {
            /*
             * simplify query/escaping and use default field always force
             * grouping/quotes
             */
            queryStr = KeywordSearchUtil.quoteQuery(query);
        }
        q.setQuery(queryStr);

        String contentIDStr = (chunkID == 0)
                ? Long.toString(solrObjectId)
                : Server.getChunkIdString(solrObjectId, chunkID);
        String idQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentIDStr);
        q.addFilterQuery(idQuery);

        configurwQueryForHighlighting(q);

        Server solrServer = KeywordSearch.getServer();

        try {
            QueryResponse response = solrServer.query(q, METHOD.POST);
            Map<String, Map<String, List<String>>> responseHighlight = response.getHighlighting();
            Map<String, List<String>> responseHighlightID = responseHighlight.get(contentIDStr);
            if (responseHighlightID == null) {
                return "";
            }
            double indexSchemaVersion = NumberUtils.toDouble(solrServer.getIndexInfo().getSchemaVersion());
            List<String> contentHighlights;
            if (2.2 <= indexSchemaVersion) {
                contentHighlights = LanguageSpecificContentQueryHelper.getHighlights(responseHighlightID).orElse(null);
            } else {
                contentHighlights = responseHighlightID.get(LuceneQuery.HIGHLIGHT_FIELD);
            }
            if (contentHighlights == null) {
                return "";
            } else {
                // extracted content is HTML-escaped, but snippet goes in a plain text field
                return EscapeUtil.unEscapeHtml(contentHighlights.get(0)).trim();
            }
        } catch (NoOpenCoreException ex) {
            logger.log(Level.SEVERE, "Error executing Lucene Solr Query: " + query + ". Solr doc id " + solrObjectId + ", chunkID " + chunkID, ex); //NON-NLS
            throw ex;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Error executing Lucene Solr Query: " + query + ". Solr doc id " + solrObjectId + ", chunkID " + chunkID, ex); //NON-NLS
            return "";
        }
    }
}
