/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Performs a normal string (i.e. non-regexp) query to SOLR/Lucene. By default,
 * matches in all fields.
 */
class LuceneQuery implements KeywordSearchQuery {

    private static final Logger logger = Logger.getLogger(LuceneQuery.class.getName());
    private final String keywordString; //original unescaped query
    private String keywordStringEscaped;
    private boolean isEscaped;
    private Keyword keyword = null;
    private KeywordList keywordList = null;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();
    private String field = null;
    private static final int MAX_RESULTS = 20000;
    static final int SNIPPET_LENGTH = 50;
    //can use different highlight schema fields for regex and literal search
    static final String HIGHLIGHT_FIELD_LITERAL = Server.Schema.TEXT.toString();
    static final String HIGHLIGHT_FIELD_REGEX = Server.Schema.TEXT.toString();
    //TODO use content_ws stored="true" in solr schema for perfect highlight hits
    //static final String HIGHLIGHT_FIELD_REGEX = Server.Schema.CONTENT_WS.toString()

    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    /**
     * Constructor with query to process.
     *
     * @param keyword
     */
    public LuceneQuery(KeywordList keywordList, Keyword keyword) {
        this.keywordList = keywordList;
        this.keyword = keyword;

        // @@@ BC: Long-term, we should try to get rid of this string and use only the
        // keyword object.  Refactoring did not make its way through this yet.
        this.keywordString = keyword.getSearchTerm();
        this.keywordStringEscaped = this.keywordString;
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
        keywordStringEscaped = keywordStringEscaped + "*";
    }

    @Override
    public void escape() {
        keywordStringEscaped = KeywordSearchUtil.escapeLuceneQuery(keywordString);
        isEscaped = true;
    }

    @Override
    public boolean isEscaped() {
        return isEscaped;
    }

    @Override
    public boolean isLiteral() {
        return true;
    }

    @Override
    public String getEscapedQueryString() {
        return this.keywordStringEscaped;
    }

    @Override
    public String getQueryString() {
        return this.keywordString;
    }

    @Override
    public QueryResults performQuery() throws KeywordSearchModuleException, NoOpenCoreException {
        QueryResults results = new QueryResults(this, keywordList);
        //in case of single term literal query there is only 1 term
        boolean showSnippets = KeywordSearchSettings.getShowSnippets();
        results.addResult(new Keyword(keywordString, true), performLuceneQuery(showSnippets));

        return results;
    }

    @Override
    public boolean validate() {
        return keywordString != null && !keywordString.equals("");
    }

    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName) {
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        BlackboardArtifact bba;
        KeywordCachedArtifact writeResult;
        try {
            bba = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            writeResult = new KeywordCachedArtifact(bba);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error adding bb artifact for keyword hit", e); //NON-NLS
            return null;
        }

        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, termHit));
        if ((listName != null) && (listName.equals("") == false)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }

        //bogus - workaround the dir tree table issue
        //attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, "", ""));
        //selector
        if (keyword != null) {
            BlackboardAttribute.ATTRIBUTE_TYPE selType = keyword.getArtifactAttributeType();
            if (selType != null) {
                attributes.add(new BlackboardAttribute(selType, MODULE_NAME, termHit));
            }
        }

        if (hit.isArtifactHit()) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
        }

        try {
            bba.addAttributes(attributes); //write out to bb
            writeResult.add(attributes);
            return writeResult;
        } catch (TskException e) {
            logger.log(Level.WARNING, "Error adding bb attributes to artifact", e); //NON-NLS
        }
        return null;
    }

    /**
     * Perform the query and return results of unique files.
     *
     * @param snippets True if results should have a snippet
     *
     * @return list of ContentHit objects. One per file with hit (ignores
     *         multiple hits of the word in the same doc)
     *
     * @throws NoOpenCoreException
     */
    private List<KeywordHit> performLuceneQuery(boolean snippets) throws KeywordSearchModuleException, NoOpenCoreException {
        List<KeywordHit> matches = new ArrayList<>();
        boolean allMatchesFetched = false;
        final Server solrServer = KeywordSearch.getServer();

        SolrQuery q = createAndConfigureSolrQuery(snippets);
        QueryResponse response;
        SolrDocumentList resultList;
        Map<String, Map<String, List<String>>> highlightResponse;

        response = solrServer.query(q, METHOD.POST);

        resultList = response.getResults();

        // objectId_chunk -> "text" -> List of previews
        highlightResponse = response.getHighlighting();

        // cycle through results in sets of MAX_RESULTS
        for (int start = 0; !allMatchesFetched; start = start + MAX_RESULTS) {
            q.setStart(start);

            allMatchesFetched = start + MAX_RESULTS >= resultList.getNumFound();

            SleuthkitCase sleuthkitCase;
            try {
                sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
            } catch (IllegalStateException ex) {
                //no case open, must be just closed
                return matches;
            }
            for (SolrDocument resultDoc : resultList) {
                KeywordHit contentHit;
                try {
                    contentHit = createKeywordtHit(resultDoc, highlightResponse, sleuthkitCase);
                } catch (TskException ex) {
                    return matches;
                }
                matches.add(contentHit);
            }
        }
        return matches;
    }

    /**
     * Create the query object for the stored keyword
     *
     * @param snippets True if query should request snippets
     *
     * @return
     */
    private SolrQuery createAndConfigureSolrQuery(boolean snippets) {
        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug
        //set query, force quotes/grouping around all literal queries
        final String groupedQuery = KeywordSearchUtil.quoteQuery(keywordStringEscaped);
        String theQueryStr = groupedQuery;
        if (field != null) {
            //use the optional field
            StringBuilder sb = new StringBuilder();
            sb.append(field).append(":").append(groupedQuery);
            theQueryStr = sb.toString();
        }
        q.setQuery(theQueryStr);
        q.setRows(MAX_RESULTS);

        q.setFields(Server.Schema.ID.toString());
        q.addSort(Server.Schema.ID.toString(), SolrQuery.ORDER.asc);
        for (KeywordQueryFilter filter : filters) {
            q.addFilterQuery(filter.toString());
        }

        if (snippets) {
            q.addHighlightField(Server.Schema.TEXT.toString());
            //q.setHighlightSimplePre("&laquo;"); //original highlighter only
            //q.setHighlightSimplePost("&raquo;");  //original highlighter only
            q.setHighlightSnippets(1);
            q.setHighlightFragsize(SNIPPET_LENGTH);

            //tune the highlighter
            q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one NON-NLS
            q.setParam("hl.tag.pre", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
            q.setParam("hl.tag.post", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
            q.setParam("hl.fragListBuilder", "simple"); //makes sense for FastVectorHighlighter only NON-NLS

            //Solr bug if fragCharSize is smaller than Query string, StringIndexOutOfBoundsException is thrown.
            q.setParam("hl.fragCharSize", Integer.toString(theQueryStr.length())); //makes sense for FastVectorHighlighter only NON-NLS

            //docs says makes sense for the original Highlighter only, but not really
            //analyze all content SLOW! consider lowering
            q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); //NON-NLS
        }

        return q;
    }

    private KeywordHit createKeywordtHit(SolrDocument solrDoc, Map<String, Map<String, List<String>>> highlightResponse, SleuthkitCase caseDb) throws TskException {
        /**
         * Get the first snippet from the document if keyword search is
         * configured to use snippets.
         */
        final String docId = solrDoc.getFieldValue(Server.Schema.ID.toString()).toString();
        String snippet = "";
        if (KeywordSearchSettings.getShowSnippets()) {
            List<String> snippetList = highlightResponse.get(docId).get(Server.Schema.TEXT.toString());
            // list is null if there wasn't a snippet
            if (snippetList != null) {
                snippet = EscapeUtil.unEscapeHtml(snippetList.get(0)).trim();
            }
        }
        return new KeywordHit(docId, snippet);
    }

    /**
     * return snippet preview context
     *
     * @param query        the keyword query for text to highlight. Lucene
     *                     special cahrs should already be escaped.
     * @param solrObjectId The Solr object id associated with the file or
     *                     artifact
     * @param isRegex      whether the query is a regular expression (different
     *                     Solr fields are then used to generate the preview)
     * @param group        whether the query should look for all terms grouped
     *                     together in the query order, or not
     *
     * @return
     */
    public static String querySnippet(String query, long solrObjectId, boolean isRegex, boolean group) throws NoOpenCoreException {
        return querySnippet(query, solrObjectId, 0, isRegex, group);
    }

    /**
     * return snippet preview context
     *
     * @param query        the keyword query for text to highlight. Lucene
     *                     special cahrs should already be escaped.
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
    public static String querySnippet(String query, long solrObjectId, int chunkID, boolean isRegex, boolean group) throws NoOpenCoreException {
        Server solrServer = KeywordSearch.getServer();

        String highlightField;
        if (isRegex) {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_REGEX;
        } else {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_LITERAL;
        }

        SolrQuery q = new SolrQuery();

        String queryStr;

        if (isRegex) {
            StringBuilder sb = new StringBuilder();
            sb.append(highlightField).append(":");
            if (group) {
                sb.append("\"");
            }
            sb.append(query);
            if (group) {
                sb.append("\"");
            }

            queryStr = sb.toString();
        } else {
            //simplify query/escaping and use default field
            //always force grouping/quotes
            queryStr = KeywordSearchUtil.quoteQuery(query);
        }

        q.setQuery(queryStr);

        String contentIDStr;

        if (chunkID == 0) {
            contentIDStr = Long.toString(solrObjectId);
        } else {
            contentIDStr = Server.getChunkIdString(solrObjectId, chunkID);
        }

        String idQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentIDStr);
        q.setShowDebugInfo(DEBUG); //debug
        q.addFilterQuery(idQuery);
        q.addHighlightField(highlightField);
        //q.setHighlightSimplePre("&laquo;"); //original highlighter only
        //q.setHighlightSimplePost("&raquo;");  //original highlighter only
        q.setHighlightSnippets(1);
        q.setHighlightFragsize(SNIPPET_LENGTH);

        //tune the highlighter
        q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one NON-NLS
        q.setParam("hl.tag.pre", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.tag.post", "&laquo;"); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.fragListBuilder", "simple"); //makes sense for FastVectorHighlighter only NON-NLS

        //Solr bug if fragCharSize is smaller than Query string, StringIndexOutOfBoundsException is thrown.
        q.setParam("hl.fragCharSize", Integer.toString(queryStr.length())); //makes sense for FastVectorHighlighter only NON-NLS

        //docs says makes sense for the original Highlighter only, but not really
        //analyze all content SLOW! consider lowering
        q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED);  //NON-NLS

        try {
            QueryResponse response = solrServer.query(q, METHOD.POST);
            Map<String, Map<String, List<String>>> responseHighlight = response.getHighlighting();
            Map<String, List<String>> responseHighlightID = responseHighlight.get(contentIDStr);
            if (responseHighlightID == null) {
                return "";
            }
            List<String> contentHighlights = responseHighlightID.get(highlightField);
            if (contentHighlights == null) {
                return "";
            } else {
                // extracted content is HTML-escaped, but snippet goes in a plain text field
                return EscapeUtil.unEscapeHtml(contentHighlights.get(0)).trim();
            }
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + query, ex); //NON-NLS
            throw ex;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + query, ex); //NON-NLS
            return "";
        }
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    /**
     * Compares SolrDocuments based on their ID's. Two SolrDocuments with
     * different chunk numbers are considered equal.
     */
    private class SolrDocumentComparatorIgnoresChunkId implements Comparator<SolrDocument> {

        @Override
        public int compare(SolrDocument left, SolrDocument right) {
            // ID is in the form of ObjectId_Chunk

            final String idName = Server.Schema.ID.toString();

            // get object id of left doc
            String leftID = left.getFieldValue(idName).toString();
            int index = leftID.indexOf(Server.CHUNK_ID_SEPARATOR);
            if (index != -1) {
                leftID = leftID.substring(0, index);
            }

            // get object id of right doc
            String rightID = right.getFieldValue(idName).toString();
            index = rightID.indexOf(Server.CHUNK_ID_SEPARATOR);
            if (index != -1) {
                rightID = rightID.substring(0, index);
            }

            Long leftLong = new Long(leftID);
            Long rightLong = new Long(rightID);
            return leftLong.compareTo(rightLong);
        }
    }

}
