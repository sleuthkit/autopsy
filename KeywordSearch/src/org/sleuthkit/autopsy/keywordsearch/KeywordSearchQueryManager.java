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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    //map query->boolean (true if literal, false otherwise)
    private Map<String, Boolean> queries;
    private Presentation presentation;
    private List<KeywordSearchQuery> queryDelegates;
    private QueryType queryType;
    private static Logger logger = Logger.getLogger(KeywordSearchQueryManager.class.getName());

    public KeywordSearchQueryManager(Map<String, Boolean> queries, Presentation presentation) {
        this.queries = queries;
        this.presentation = presentation;
        queryType = QueryType.REGEX;
        init();
    }

    public KeywordSearchQueryManager(String query, QueryType qt, Presentation presentation) {
        queries = new LinkedHashMap<String, Boolean>();
        queries.put(query, false);
        this.presentation = presentation;
        queryType = qt;
        init();
    }

    public KeywordSearchQueryManager(String query, boolean isLiteral, Presentation presentation) {
        queries = new LinkedHashMap<String, Boolean>();
        queries.put(query, isLiteral);
        this.presentation = presentation;
        queryType = QueryType.REGEX;
        init();
    }

    private void init() {
        queryDelegates = new ArrayList<KeywordSearchQuery>();
        for (String query : queries.keySet()) {
            KeywordSearchQuery del = null;
            switch (queryType) {
                case WORD:
                    del = new LuceneQuery(query);
                    break;
                case REGEX:
                    del = new TermComponentQuery(query);
                    break;
                default:
                    ;
            }

            escape();
            queryDelegates.add(del);
        }

    }

    @Override
    public void execute() {

        if (queryType == QueryType.WORD || presentation == Presentation.DETAIL) {
            for (KeywordSearchQuery q : queryDelegates) {
                q.execute();
            }
        } else {
            for (KeywordSearchQuery q : queryDelegates) {
                List<FsContent> fsContents = q.performQuery();
                //TODO create view, send to proper factory
            }
            
        }


    }

    @Override
    public void escape() {
        for (KeywordSearchQuery q : queryDelegates) {
            boolean shouldEscape = queries.get(q.getQueryString());
            if (shouldEscape) {
                q.escape();
            }
        }

    }

    @Override
    public List<FsContent> performQuery() {
        //not done here
        return null;
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
    public String getQueryString() {
        StringBuilder sb = new StringBuilder();
        final String SEP = queryType == QueryType.WORD ? " " : "|";
        for (KeywordSearchQuery q : queryDelegates) {
            sb.append(q.getQueryString()).append(SEP);
        }
        return sb.toString();
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
}
