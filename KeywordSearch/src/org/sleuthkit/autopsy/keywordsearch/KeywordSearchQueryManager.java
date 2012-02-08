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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.datamodel.FsContent;

/**
 * Query manager responsible for running appropriate queries and displaying results
 * for single, multi keyword queries, with detailed or collapsed results
 */
public class KeywordSearchQueryManager implements KeywordSearchQuery {

    public enum Presentation {

        COLLAPSE, DETAIL
    };
    private List<Keyword> queries;
    private Presentation presentation;
    private List<KeywordSearchQuery> queryDelegates;
    private QueryType queryType;
    private static Logger logger = Logger.getLogger(KeywordSearchQueryManager.class.getName());

    public KeywordSearchQueryManager(List<Keyword> queries, Presentation presentation) {
        this.queries = queries;
        this.presentation = presentation;
        queryType = QueryType.REGEX;
        init();
    }

    public KeywordSearchQueryManager(String query, QueryType qt, Presentation presentation) {
        queries = new ArrayList<Keyword>();
        queries.add(new Keyword(query, false));
        this.presentation = presentation;
        queryType = qt;
        init();
    }

    public KeywordSearchQueryManager(String query, boolean isLiteral, Presentation presentation) {
        queries = new ArrayList<Keyword>();
        queries.add(new Keyword(query, isLiteral));
        this.presentation = presentation;
        queryType = QueryType.REGEX;
        init();
    }

    private void init() {
        queryDelegates = new ArrayList<KeywordSearchQuery>();
        for (Keyword query : queries) {
            KeywordSearchQuery del = null;
            switch (queryType) {
                case WORD:
                    del = new LuceneQuery(query.getQuery());
                    break;
                case REGEX:
                    if (query.isLiteral()) {
                        del = new LuceneQuery(query.getQuery());
                    } else {
                        del = new TermComponentQuery(query.getQuery());
                    }
                    break;
                default:
                    ;
            }
            if (query.isLiteral()) {
                del.escape();
            }
            queryDelegates.add(del);

        }
        //escape();

    }

    @Override
    public void execute() {
        //execute and present the query
        //delegate query to query objects and presentation child factories
        if (queryType == QueryType.WORD || presentation == Presentation.DETAIL) {
            for (KeywordSearchQuery q : queryDelegates) {
                q.execute();
            }
        } else {
            //Collapsed view
            Collection<KeyValue> things = new ArrayList<KeyValue>();
            int queryID = 0;
            for (KeywordSearchQuery q : queryDelegates) {
                Map<String, Object> kvs = new LinkedHashMap<String, Object>();
                final String queryStr = q.getQueryString();
                things.add(new KeyValueQuery(queryStr, kvs, ++queryID, q));
            }

            Node rootNode = null;

            if (things.size() > 0) {
                Children childThingNodes =
                        Children.create(new KeywordSearchResultFactory(queries, things, Presentation.COLLAPSE), true);

                rootNode = new AbstractNode(childThingNodes);
            } else {
                rootNode = Node.EMPTY;
            }

            final String pathText = "Keyword search";
            TopComponent searchResultWin = DataResultTopComponent.createInstance("Keyword search", pathText, rootNode, things.size());
            searchResultWin.requestActive();
        }
    }

    @Override
    public void escape() {
    }

    @Override
    public List<FsContent> performQuery() {
        //not done here
        return null;
    }

    @Override
    public boolean validate() {
        boolean allValid = true;
        for (KeywordSearchQuery tcq : queryDelegates) {
            if (!tcq.validate()) {
                logger.log(Level.WARNING, "Query has invalid syntax: " + tcq.getQueryString());
                allValid = false;
                break;
            }
        }
        return allValid;
    }

    @Override
    public String getEscapedQueryString() {
        StringBuilder sb = new StringBuilder();
        final String SEP = queryType == QueryType.WORD ? " " : "|";
        for (KeywordSearchQuery q : queryDelegates) {
            sb.append(q.getEscapedQueryString()).append(SEP);
        }
        return sb.toString();
    }

    @Override
    public boolean isEscaped() {
        return false;
    }

    @Override
    public String getQueryString() {
        StringBuilder sb = new StringBuilder();
        final String SEP = queryType == QueryType.WORD ? " " : "|";
        for (KeywordSearchQuery q : queryDelegates) {
            sb.append(q.getQueryString()).append(SEP);
        }
        return sb.toString();
    }

    @Override
    public Collection<Term> getTerms() {
        return null;
    }
}

/**
 * custom KeyValue that also stores query object  to execute
 */
class KeyValueQuery extends KeyValue {

    private KeywordSearchQuery query;

    KeywordSearchQuery getQuery() {
        return query;
    }

    public KeyValueQuery(String name, Map<String, Object> map, int id, KeywordSearchQuery query) {
        super(name, map, id);
        this.query = query;
    }
}

/**
 * representation of Keyword input from user
 */
class Keyword {

    private String query;
    private boolean isLiteral;

    Keyword(String query, boolean isLiteral) {
        this.query = query;
        this.isLiteral = isLiteral;
    }

    String getQuery() {
        return query;
    }

    boolean isLiteral() {
        return isLiteral;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Keyword other = (Keyword) obj;
        if ((this.query == null) ? (other.query != null) : !this.query.equals(other.query)) {
            return false;
        }
        if (this.isLiteral != other.isLiteral) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (this.query != null ? this.query.hashCode() : 0);
        hash = 17 * hash + (this.isLiteral ? 1 : 0);
        return hash;
    }
}
