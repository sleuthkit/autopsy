/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;

/**
 * Responsible for running a keyword search query and displaying
 * the results. 
 */
class KeywordSearchQueryManager {
    private List<Keyword> keywords;
    private List<KeywordSearchQuery> queryDelegates;
    private QueryType queryType;
    private boolean queryWholeword;
    private static int resultWindowCount = 0; //keep track of unique window ids to display
    private static Logger logger = Logger.getLogger(KeywordSearchQueryManager.class.getName());

    /**
     * 
     * @param queries Keywords to search for
     */
    public KeywordSearchQueryManager(List<Keyword> queries, boolean wholeword) {
        queryType = QueryType.REGEX;
        queryWholeword = wholeword;
        keywords = queries;
        init();
    }

    /**
     * KeywordSearchQueryManager will change a literal keyword to regex unless wholeword is set
     * @param query Keyword to search for
     * @param qt Query type
     */
    public KeywordSearchQueryManager(String query, QueryType qt, boolean wholeword) {
        queryType = qt;
        queryWholeword = wholeword;
        keywords = new ArrayList<>();
        keywords.add(new Keyword(query, ((queryType == QueryType.LITERAL) && queryWholeword) ? true : false));
        init();
    }

    /**
     * 
     * @param query Keyword to search for
     * @param isLiteral false if reg-exp
     * @param presentation Presentation layout
     */
    public KeywordSearchQueryManager(String query, boolean isLiteral, boolean wholeword) {
        queryType = isLiteral ? QueryType.LITERAL : QueryType.REGEX;
        queryWholeword = wholeword;
        keywords = new ArrayList<>();
        keywords.add(new Keyword(query, isLiteral));
        init();
    }

    /**
     * Initialize internal settings based on constructor arguments.
     * Create a list of queries to later run
     */
    private void init() {
        queryDelegates = new ArrayList<>();
        for (Keyword keyword : keywords) {
            KeywordSearchQuery query = null;
            
            /**
             * There are three usable combinations:             
             * Substrings (we wrap with substring regex):
             *     1. Literal query 
             * Whole words (no wrapping):
             *     2. Literal query (Lucene search)
             *     3. Regex query
             */
            if (keyword.isLiteral()) {
                query = new LuceneQuery(keyword);
            } else {            
                query = new TermComponentQuery(keyword);
            }

            if (query != null) {
                if (keyword.isLiteral() || (queryType == QueryType.LITERAL)) {
                    query.escape();
                }
                
                // Wrap the keyword with wildcards (for substrings)
                if (!queryWholeword && (queryType == QueryType.LITERAL)) {
                    query.setSubstringQuery();
                }
                
                queryDelegates.add(query);
            }
        }
    }

    /**
     * Execute the keyword search based on keywords passed into constructor.
     * Post results into a new DataResultViewer.
     */
    public void execute() {
        //execute and present the query
        //delegate query to query objects and presentation child factories
        //if (queryType == QueryType.WORD || presentation == Presentation.DETAIL) {
        //   for (KeywordSearchQuery q : queryDelegates) {
        //       q.execute();
        //  }
        // } else {
        
        //Collapsed view
        Collection<QueryRequest> queryRequests = new ArrayList<>();
        int queryID = 0;
        StringBuilder queryConcat = new StringBuilder();    // concatenation of all query strings
        for (KeywordSearchQuery q : queryDelegates) {
            Map<String, Object> kvs = new LinkedHashMap<>();
            final String queryStr = q.getQueryString();
            final String escQueryStr = q.getEscapedQueryString();
            queryConcat.append(queryStr).append(" ");
            queryRequests.add(new QueryRequest(escQueryStr, kvs, ++queryID, q));
        }

        Node rootNode;
        String queryConcatStr = queryConcat.toString();
        final int queryConcatStrLen = queryConcatStr.length();
        final String queryStrShort = queryConcatStrLen > 15 ? queryConcatStr.substring(0, 14) + "..." : queryConcatStr;
        final String windowTitle = NbBundle.getMessage(this.getClass(), "KeywordSearchQueryManager.execute.exeWinTitle", ++resultWindowCount, queryStrShort);
        DataResultTopComponent searchResultWin = DataResultTopComponent.createInstance(windowTitle);
        if (queryRequests.size() > 0) {
            Children childNodes =
                    Children.create(new KeywordSearchResultFactory(keywords, queryRequests, searchResultWin), true);

            rootNode = new AbstractNode(childNodes);
        } else {
            rootNode = Node.EMPTY;
        }

        final String pathText = NbBundle.getMessage(this.getClass(), "KeywordSearchQueryManager.pathText.text");

        DataResultTopComponent.initInstance(pathText, rootNode, queryRequests.size(), searchResultWin);

        searchResultWin.requestActive();
        // }
    }
    
    /**
     * validate the queries before they are run
     * @return false if any are invalid
     */
    public boolean validate() {
        boolean allValid = true;
        for (KeywordSearchQuery tcq : queryDelegates) {
            if (!tcq.validate()) {
                logger.log(Level.WARNING, "Query has invalid syntax: {0}", tcq.getQueryString()); //NON-NLS
                allValid = false;
                break;
            }
        }
        return allValid;
    }
}
