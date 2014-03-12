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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.openide.util.Exceptions;
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
class LuceneQuery implements KeywordSearchQuery {

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
        boolean showSnippets = KeywordSearchSettings.getShowSnippets();
        results.put(keywordString, performLuceneQuery(showSnippets));

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
    private List<ContentHit> performLuceneQuery(boolean snippets) throws NoOpenCoreException {

        List<ContentHit> matches = new ArrayList<>();
        boolean allMatchesFetched = false;
        final Server solrServer = KeywordSearch.getServer();
        
        SolrQuery q = createAndConfigureSolrQuery(snippets);

        for (int start = 0; !allMatchesFetched; start = start + MAX_RESULTS) {
            q.setStart(start);

            try {
                QueryResponse response = solrServer.query(q, METHOD.POST);
                SolrDocumentList resultList = response.getResults();
                Map<String, Map<String, List<String>>> highlightResponse = response.getHighlighting();
                Set<SolrDocument> solrDocumentsWithMatches = filterDuplicateSolrDocuments(resultList);
                
                allMatchesFetched = start + MAX_RESULTS >= resultList.getNumFound();
                
                SleuthkitCase sleuthkitCase;
                try {
                    sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
                } catch (IllegalStateException ex) {
                    //no case open, must be just closed
                    return matches;
                }

                for (SolrDocument resultDoc : solrDocumentsWithMatches) {
                    ContentHit contentHit;
                    try {
                        contentHit = createContentHitFromQueryResults(resultDoc, highlightResponse, snippets, sleuthkitCase);
                    } catch (TskException ex) {
                        return matches;
                    }
                    matches.add(contentHit);
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
        
        if (snippets) {
            q.setFields(Server.Schema.ID.toString(), Server.Schema.CONTENT.toString());
        } else {
            q.setFields(Server.Schema.ID.toString());
        }
        
        for (KeywordQueryFilter filter : filters) {
            q.addFilterQuery(filter.toString());
        }
        
        if (snippets) {
            q.addHighlightField(Server.Schema.CONTENT.toString());
            //q.setHighlightSimplePre("&laquo;"); //original highlighter only
            //q.setHighlightSimplePost("&raquo;");  //original highlighter only
            q.setHighlightSnippets(1);
            q.setHighlightFragsize(SNIPPET_LENGTH);

            //tune the highlighter
            q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one
            q.setParam("hl.tag.pre", "&laquo;"); //makes sense for FastVectorHighlighter only
            q.setParam("hl.tag.post", "&laquo;"); //makes sense for FastVectorHighlighter only
            q.setParam("hl.fragListBuilder", "simple"); //makes sense for FastVectorHighlighter only

             //Solr bug if fragCharSize is smaller than Query string, StringIndexOutOfBoundsException is thrown.
            q.setParam("hl.fragCharSize", Integer.toString(theQueryStr.length())); //makes sense for FastVectorHighlighter only

            //docs says makes sense for the original Highlighter only, but not really
            //analyze all content SLOW! consider lowering
            q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED);
        }
        
        return q;
    }

    private Set<SolrDocument> filterDuplicateSolrDocuments(SolrDocumentList resultList) {
        Set<SolrDocument> solrDocumentsWithMatches = new TreeSet<>(new SolrDocumentComparator());
        solrDocumentsWithMatches.addAll(resultList);
        return solrDocumentsWithMatches;
    }

    private ContentHit createContentHitFromQueryResults(SolrDocument resultDoc, Map<String, Map<String, List<String>>> highlightResponse, boolean snippets, SleuthkitCase sc) throws TskException {
        ContentHit chit;
        final String resultID = resultDoc.getFieldValue(Server.Schema.ID.toString()).toString();
        final int sepIndex = resultID.indexOf(Server.ID_CHUNK_SEP);
        String snippet = "";
        if (snippets) {
            List<String> snippetList = highlightResponse.get(resultID).get(Server.Schema.CONTENT.toString());
            // list is null if there wasn't a snippet
            if (snippetList != null) {
                snippet = EscapeUtil.unEscapeHtml(snippetList.get(0)).trim();
            }
        }
        if (sepIndex != -1) {
            //file chunk result
            final long fileID = Long.parseLong(resultID.substring(0, sepIndex));
            final int chunkId = Integer.parseInt(resultID.substring(sepIndex + 1));
            //logger.log(Level.INFO, "file id: " + fileID + ", chunkID: " + chunkId);

            try {
                AbstractFile resultAbstractFile = sc.getAbstractFileById(fileID);
                chit = new ContentHit(resultAbstractFile, chunkId);
                if (snippet.isEmpty() == false) {
                    chit.setSnippet(snippet);
                }
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Could not get the AbstractFile for keyword hit, ", ex);
                //something wrong with case/db
                throw ex;
            }

        } else {
            final long fileID = Long.parseLong(resultID);

            try {
                AbstractFile resultAbstractFile = sc.getAbstractFileById(fileID);
                chit = new ContentHit(resultAbstractFile);
                if (snippet.isEmpty() == false) {
                    chit.setSnippet(snippet);
                }
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Could not get the AbstractFile for keyword hit, ", ex);
                //something wrong with case/db
                throw ex;
            }
        }
        return chit;
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
        //q.setHighlightSimplePre("&laquo;"); //original highlighter only
        //q.setHighlightSimplePost("&raquo;");  //original highlighter only
        q.setHighlightSnippets(1);
        q.setHighlightFragsize(SNIPPET_LENGTH);
        
        
        
        //tune the highlighter
        q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one
        q.setParam("hl.tag.pre", "&laquo;"); //makes sense for FastVectorHighlighter only
        q.setParam("hl.tag.post", "&laquo;"); //makes sense for FastVectorHighlighter only
        q.setParam("hl.fragListBuilder", "simple"); //makes sense for FastVectorHighlighter only
        
         //Solr bug if fragCharSize is smaller than Query string, StringIndexOutOfBoundsException is thrown.
        q.setParam("hl.fragCharSize", Integer.toString(queryStr.length())); //makes sense for FastVectorHighlighter only
        
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
    
    /**
     * Compares SolrDocuments based on their ID's. Two SolrDocuments with
     * different chunk numbers are considered equal.
     */
    private class SolrDocumentComparator implements Comparator<SolrDocument> {
        @Override
        public int compare(SolrDocument left, SolrDocument right) {
            String idName = Server.Schema.ID.toString();
            String leftID = left.getFieldValue(idName).toString();
            int index = leftID.indexOf(Server.ID_CHUNK_SEP);
            if (index != -1) {
                leftID = leftID.substring(0, index);
            }

            String rightID = right.getFieldValue(idName).toString();
            index = rightID.indexOf(Server.ID_CHUNK_SEP);
            if (index != -1) {
                rightID = rightID.substring(0, index);
            }

            return leftID.compareTo(rightID);
        }
    }
}
