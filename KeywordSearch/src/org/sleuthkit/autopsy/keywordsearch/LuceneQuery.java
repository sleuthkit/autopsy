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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchResultFactory.ResultWriter;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

public class LuceneQuery implements KeywordSearchQuery {

    private static final Logger logger = Logger.getLogger(LuceneQuery.class.getName());
    private String query; //original unescaped query
    private String queryEscaped;
    private boolean isEscaped;
    private Keyword keywordQuery = null;
    //use different highlight Solr fields for regex and literal search
    static final String HIGHLIGHT_FIELD_LITERAL = "content";
    static final String HIGHLIGHT_FIELD_REGEX = "content";
    //TODO use content_ws stored="true" in solr schema for perfect highlight hits
    //static final String HIGHLIGHT_FIELD_REGEX = "content_ws";

    public LuceneQuery(Keyword keywordQuery) {
        this(keywordQuery.getQuery());
        this.keywordQuery = keywordQuery;
    }

    public LuceneQuery(String queryStr) {
        this.query = queryStr;
        this.queryEscaped = queryStr;
        isEscaped = false;
    }

    @Override
    public void escape() {
        queryEscaped = KeywordSearchUtil.escapeLuceneQuery(query, true, false);
        isEscaped = true;
    }

    @Override
    public boolean isEscaped() {
        return isEscaped;
    }

    @Override
    public String getEscapedQueryString() {
        return this.queryEscaped;
    }

    @Override
    public String getQueryString() {
        return this.query;
    }

    @Override
    public Collection<Term> getTerms() {
        return null;
    }

    

    @Override
    public Map<String, List<FsContent>> performQuery() {
        Map<String, List<FsContent>> results = new HashMap<String, List<FsContent>>();
        //in case of single term literal query there is only 1 term
        results.put(query, performLuceneQuery());

        return results;
    }

    @Override
    public void execute() {
        escape();
        Set<FsContent>fsMatches = new HashSet<FsContent>();
        final Map<String, List<FsContent>> matches = performQuery();
        for (String key : matches.keySet()) {
            fsMatches.addAll(matches.get(key));
        }
     
        String pathText = "Keyword query: " + query;

        if (matches.isEmpty()) {
            KeywordSearchUtil.displayDialog("Keyword Search", "No results for keyword: " + query, KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            return;
        }

        //get listname
        String listName = "";
        //KeywordSearchList list = KeywordSearchListsXML.getCurrent().getListWithKeyword(query);
        //if (list != null) {
        //    listName = list.getName();
        //}
        final String theListName = listName;

        Node rootNode = new KeywordSearchNode(new ArrayList(fsMatches), queryEscaped);
        Node filteredRootNode = new TableFilterNode(rootNode, true);

        TopComponent searchResultWin = DataResultTopComponent.createInstance("Keyword search", pathText, filteredRootNode, matches.size());
        searchResultWin.requestActive(); // make it the active top component

        //write to bb
        new ResultWriter(matches, this, listName).execute();
 
    }

    @Override
    public boolean validate() {
        return query != null && !query.equals("");
    }

    private Collection<KeywordWriteResult> writeToBlackBoard(FsContent newFsHit, String listName) {
        List<KeywordWriteResult> ret = new ArrayList<KeywordWriteResult>();
        KeywordWriteResult written = writeToBlackBoard(query, newFsHit, listName);
        if (written != null) {
            ret.add(written);
        }
        return ret;
    }

    @Override
    public KeywordWriteResult writeToBlackBoard(String termHit, FsContent newFsHit, String listName) {
        final String MODULE_NAME = KeywordSearchIngestService.MODULE_NAME;

        KeywordWriteResult writeResult = null;
        Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
        BlackboardArtifact bba = null;
        try {
            bba = newFsHit.newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            writeResult = new KeywordWriteResult(bba);
        } catch (Exception e) {
            logger.log(Level.INFO, "Error adding bb artifact for keyword hit", e);
            return null;
        }

        String snippet = null;
        try {
            snippet = LuceneQuery.querySnippet(queryEscaped, newFsHit.getId(), false, true);
        } catch (Exception e) {
            logger.log(Level.INFO, "Error querying snippet: " + query, e);
            return null;
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, "", snippet));
        }
        //keyword
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID(), MODULE_NAME, "", termHit));
        //list
        if (listName == null) {
            listName = "";
        }
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_SET.getTypeID(), MODULE_NAME, "", listName));
        //bogus - workaround the dir tree table issue
        //attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, "", ""));

        //selector
        if (keywordQuery != null) {
            BlackboardAttribute.ATTRIBUTE_TYPE selType = keywordQuery.getType();
            if (selType != null) {
                attributes.add(new BlackboardAttribute(selType.getTypeID(), MODULE_NAME, "", termHit));
            }
        }

        try {
            bba.addAttributes(attributes); //write out to bb
            writeResult.add(attributes);
            return writeResult;
        } catch (TskException e) {
            logger.log(Level.INFO, "Error adding bb attributes to artifact", e);
        }
        return null;
    }

    
    /**
     * Just perform the query and return result without updating the GUI
     * This utility is used in this class, can be potentially reused by other classes
     * @param query
     * @return matches List
     */
    private List<FsContent> performLuceneQuery() throws RuntimeException {

        List<FsContent> matches = new ArrayList<FsContent>();

        boolean allMatchesFetched = false;
        final int ROWS_PER_FETCH = 10000;

        Server.Core solrCore = null;

        try {
            solrCore = KeywordSearch.getServer().getCore();
        } catch (SolrServerException e) {
            logger.log(Level.INFO, "Could not get Solr core", e);
        }
        
        if (solrCore == null) {
            return matches;
        }

        SolrQuery q = new SolrQuery();

        q.setQuery(queryEscaped);
        q.setRows(ROWS_PER_FETCH);
        q.setFields("id");

        for (int start = 0; !allMatchesFetched; start = start + ROWS_PER_FETCH) {

            q.setStart(start);

            try {
                QueryResponse response = solrCore.query(q, METHOD.POST);
                SolrDocumentList resultList = response.getResults();
                long results = resultList.getNumFound();

                allMatchesFetched = start + ROWS_PER_FETCH >= results;

                for (SolrDocument resultDoc : resultList) {
                    long id = Long.parseLong((String) resultDoc.getFieldValue("id"));

                    SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

                    // TODO: has to be a better way to get files. Also, need to 
                    // check that we actually get 1 hit for each id
                    ResultSet rs = sc.runQuery("select * from tsk_files where obj_id=" + id);
                    matches.addAll(sc.resultSetToFsContents(rs));
                    final Statement s = rs.getStatement();
                    rs.close();
                    if (s != null) {
                        s.close();
                    }
                }

            } catch (SolrServerException ex) {
                logger.log(Level.WARNING, "Error executing Lucene Solr Query: " + query.substring(0, Math.min(query.length() - 1, 200)), ex);
                throw new RuntimeException(ex);
                // TODO: handle bad query strings, among other issues
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error interpreting results from Lucene Solr Query: " + query, ex);
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
    public static String querySnippet(String query, long contentID, boolean isRegex, boolean group) {
        final int SNIPPET_LENGTH = 45;

        Server.Core solrCore = null;
        try {
            solrCore = KeywordSearch.getServer().getCore();
        } catch (SolrServerException ex) {
            logger.log(Level.INFO, "Could not get Solr core", ex);
        }
        
        if (solrCore == null)
            return "";

        String highlightField = null;
        if (isRegex) {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_REGEX;
        } else {
            highlightField = LuceneQuery.HIGHLIGHT_FIELD_LITERAL;
        }

        SolrQuery q = new SolrQuery();

        if (isRegex) {
            StringBuilder sb = new StringBuilder();
            sb.append(highlightField).append(":");
            if (group)
                sb.append("\"");
            sb.append(query);
            if (group)
                sb.append("\"");
            
            q.setQuery(sb.toString());
        } else {
            //simplify query/escaping and use default field
            //quote only if user supplies quotes
            q.setQuery(query); 
        }
        q.addFilterQuery("id:" + contentID);
        q.addHighlightField(highlightField);
        q.setHighlightSimplePre("&laquo;");
        q.setHighlightSimplePost("&raquo;");
        q.setHighlightSnippets(1);
        q.setHighlightFragsize(SNIPPET_LENGTH);

        try {
            QueryResponse response = solrCore.query(q);
            Map<String, Map<String, List<String>>> responseHighlight = response.getHighlighting();
            Map<String, List<String>> responseHighlightID = responseHighlight.get(Long.toString(contentID));
            if (responseHighlightID == null) {
                return "";
            }
            List<String> contentHighlights = responseHighlightID.get(highlightField);
            if (contentHighlights == null) {
                return "";
            } else {
                // extracted content is HTML-escaped, but snippet goes in a plain text field
                return StringEscapeUtils.unescapeHtml(contentHighlights.get(0)).trim();
            }
        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        }
    }
}
