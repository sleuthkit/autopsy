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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Performs a normal string (i.e. non-regexp) query to SOLR/Lucene.
 * By default, matches in all fields. 
 */
public class LuceneQuery implements KeywordSearchQuery {

    private static final Logger logger = Logger.getLogger(LuceneQuery.class.getName());
    private String keywordString; //original unescaped query
    private String keywordStringEscaped;
    private boolean isEscaped;
    private Keyword keywordQuery = null;
    private final List <KeywordQueryFilter> filters = new ArrayList<KeywordQueryFilter>();
    private String field = null;
    private static final int MAX_RESULTS = 20000;
    static final int SNIPPET_LENGTH = 50;
    //can use different highlight schema fields for regex and literal search
    static final String HIGHLIGHT_FIELD_LITERAL = Server.Schema.CONTENT.toString();
    static final String HIGHLIGHT_FIELD_REGEX = Server.Schema.CONTENT.toString();
    //TODO use content_ws stored="true" in solr schema for perfect highlight hits
    //static final String HIGHLIGHT_FIELD_REGEX = Server.Schema.CONTENT_WS.toString()
    
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    /**
     * Constructor with query to process.
     * @param keywordQuery 
     */
    public LuceneQuery(Keyword keywordQuery) {
        this(keywordQuery.getQuery());
        this.keywordQuery = keywordQuery;
    }

    /**
     * Constructor with keyword string to process
     * @param queryStr Keyword to search for
     */
    public LuceneQuery(String queryStr) {
        this.keywordString = queryStr;
        this.keywordStringEscaped = queryStr;
        isEscaped = false;
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
    public Collection<Term> getTerms() {
        return null;
    }

    @Override
    public Map<String, List<ContentHit>> performQuery() throws NoOpenCoreException {
        Map<String, List<ContentHit>> results = new HashMap<String, List<ContentHit>>();
        //in case of single term literal query there is only 1 term
        results.put(keywordString, performLuceneQuery());

        return results;
    }


    @Override
    public boolean validate() {
        return keywordString != null && !keywordString.equals("");
    }

    @Override
    public KeywordWriteResult writeToBlackBoard(String termHit, AbstractFile newFsHit, String snippet, String listName) {
        final String MODULE_NAME = KeywordSearchIngestModule.MODULE_NAME;

        KeywordWriteResult writeResult = null;
        Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
        BlackboardArtifact bba = null;
        try {
            bba = newFsHit.newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            writeResult = new KeywordWriteResult(bba);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error adding bb artifact for keyword hit", e);
            return null;
        }

        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, snippet));
        }
        //keyword
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID(), MODULE_NAME, termHit));
        //list
        if (listName == null) {
            listName = "";
        }
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, listName));
        //bogus - workaround the dir tree table issue
        //attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, "", ""));

        //selector
        if (keywordQuery != null) {
            BlackboardAttribute.ATTRIBUTE_TYPE selType = keywordQuery.getType();
            if (selType != null) {
                attributes.add(new BlackboardAttribute(selType.getTypeID(), MODULE_NAME, termHit));
            }
        }

        try {
            bba.addAttributes(attributes); //write out to bb
            writeResult.add(attributes);
            return writeResult;
        } catch (TskException e) {
            logger.log(Level.WARNING, "Error adding bb attributes to artifact", e);
        }
        return null;
    }

    
    /**
     * Perform the query and return result
     * @return list of ContentHit objects
     * @throws NoOpenCoreException
     */
    private List<ContentHit> performLuceneQuery() throws NoOpenCoreException {

        List<ContentHit> matches = new ArrayList<ContentHit>();

        boolean allMatchesFetched = false;

        final Server solrServer = KeywordSearch.getServer();

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
        for (KeywordQueryFilter filter : filters) {
            q.addFilterQuery(filter.toString());
        }

        for (int start = 0; !allMatchesFetched; start = start + MAX_RESULTS) {
            q.setStart(start);

            try {
                QueryResponse response = solrServer.query(q, METHOD.POST);
                SolrDocumentList resultList = response.getResults();
                long results = resultList.getNumFound();
                allMatchesFetched = start + MAX_RESULTS >= results;
                SleuthkitCase sc = null;
                try {
                    sc = Case.getCurrentCase().getSleuthkitCase();
                } catch (IllegalStateException ex) {
                    //no case open, must be just closed
                    return matches;
                }

                for (SolrDocument resultDoc : resultList) {
                    final String resultID = (String) resultDoc.getFieldValue(Server.Schema.ID.toString());

                    final int sepIndex = resultID.indexOf(Server.ID_CHUNK_SEP);

                    if (sepIndex != -1) {
                        //file chunk result
                        final long fileID = Long.parseLong(resultID.substring(0, sepIndex));
                        final int chunkId = Integer.parseInt(resultID.substring(sepIndex + 1));
                        //logger.log(Level.INFO, "file id: " + fileID + ", chunkID: " + chunkId);

                        try {
                            AbstractFile resultAbstractFile = sc.getAbstractFileById(fileID);
                            matches.add(new ContentHit(resultAbstractFile, chunkId));

                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Could not get the AbstractFile for keyword hit, ", ex);
                            //something wrong with case/db
                            return matches;
                        }

                    } else {
                        final long fileID = Long.parseLong(resultID);

                        try {
                            AbstractFile resultAbstractFile = sc.getAbstractFileById(fileID);
                            matches.add(new ContentHit(resultAbstractFile));
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Could not get the AbstractFile for keyword hit, ", ex);
                            //something wrong with case/db
                            return matches;
                        }
                    }

                }


            } catch (NoOpenCoreException ex) {
                logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + keywordString, ex);
                throw ex;
            } catch (KeywordSearchModuleException ex) {
                logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + keywordString, ex);
            }

        }
        return matches;
    }

    /**
     * return snippet preview context
     * @param query the keyword query for text to highlight. Lucene special cahrs should already be escaped.
     * @param contentID content id associated with the file
     * @param isRegex whether the query is a regular expression (different Solr fields are then used to generate the preview)
     * @param group whether the query should look for all terms grouped together in the query order, or not
     * @return 
     */
    public static String querySnippet(String query, long contentID, boolean isRegex, boolean group) throws NoOpenCoreException {
        return querySnippet(query, contentID, 0, isRegex, group);
    }

    /**
     * return snippet preview context
     * @param query the keyword query for text to highlight. Lucene special cahrs should already be escaped.
     * @param contentID content id associated with the hit
     * @param chunkID chunk id associated with the content hit, or 0 if no chunks
     * @param isRegex whether the query is a regular expression (different Solr fields are then used to generate the preview)
     * @param group whether the query should look for all terms grouped together in the query order, or not
     * @return 
     */
    public static String querySnippet(String query, long contentID, int chunkID, boolean isRegex, boolean group) throws NoOpenCoreException {
        Server solrServer = KeywordSearch.getServer();

        String highlightField = null;
        if (isRegex) {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_REGEX;
        } else {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_LITERAL;
        }

        SolrQuery q = new SolrQuery();

        String queryStr = null;
        
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

        String contentIDStr = null;

        if (chunkID == 0) {
            contentIDStr = Long.toString(contentID);
        } else {
            contentIDStr = Server.getChunkIdString(contentID, chunkID);
        }

        String idQuery = Server.Schema.ID.toString() + ":" + contentIDStr;
        q.setShowDebugInfo(DEBUG); //debug
        q.addFilterQuery(idQuery);
        q.addHighlightField(highlightField);
        q.setHighlightSnippets(1);
        q.setHighlightFragsize(SNIPPET_LENGTH);
        
        //tune the highlighter
        q.setHighlightSimplePre("&laquo;");
        q.setHighlightSimplePost("&laquo;");
        
        //docs says makes sense for the original Highlighter only, but not really
        //analyze all content SLOW! consider lowering
        q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); 

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
            logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + query, ex);
            throw ex;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + query, ex);
            return "";
        }
    }
}
