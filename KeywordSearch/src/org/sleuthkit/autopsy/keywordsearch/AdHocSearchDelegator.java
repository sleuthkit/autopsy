/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Responsible for running a keyword search query and displaying the results.
 * Delegates the actual work to the various implementations of
 * KeywordSearchQuery.
 */
class AdHocSearchDelegator {

    private final List<KeywordList> keywordLists;
    private List<KeywordSearchQuery> queryDelegates;
    private final Set<Long> dataSourceIds;
    private static int resultWindowCount = 0; //keep track of unique window ids to display
    private static final Logger logger = Logger.getLogger(AdHocSearchDelegator.class.getName());

    public AdHocSearchDelegator(List<KeywordList> keywordLists, Set<Long> dataSourceIds) {
        this.keywordLists = keywordLists;
        this.dataSourceIds = dataSourceIds;
        init();
    }

    private void init() {
        // make a query for each keyword
        queryDelegates = new ArrayList<>();

        for (KeywordList keywordList : keywordLists) {
            for (Keyword keyword : keywordList.getKeywords()) {
                KeywordSearchQuery query = KeywordSearchUtil.getQueryForKeyword(keyword, keywordList);

                //Limit search to a set of data sources
                if (dataSourceIds != null && !dataSourceIds.isEmpty()) {
                    final KeywordQueryFilter dataSourceFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.DATA_SOURCE, dataSourceIds);
                    query.addFilter(dataSourceFilter);
                }

                queryDelegates.add(query);
            }
        }
    }

    /**
     * Execute the keyword search based on keywords passed into constructor.
     * Post results into a new DataResultViewer.
     *
     * @param saveResults Flag whether to save search results as KWS artifacts.
     */
    public void execute(boolean saveResults) {
        Collection<AdHocQueryRequest> queryRequests = new ArrayList<>();
        int queryID = 0;
        StringBuilder queryConcat = new StringBuilder();    // concatenation of all query strings
        for (KeywordSearchQuery q : queryDelegates) {
            Map<String, Object> kvs = new LinkedHashMap<>();
            final String queryStr = q.getQueryString();
            queryConcat.append(queryStr).append(" ");
            queryRequests.add(new AdHocQueryRequest(kvs, ++queryID, q));
        }

        String queryConcatStr = queryConcat.toString();
        final int queryConcatStrLen = queryConcatStr.length();
        final String queryStrShort = queryConcatStrLen > 15 ? queryConcatStr.substring(0, 14) + "..." : queryConcatStr;
        final String windowTitle = NbBundle.getMessage(this.getClass(), "KeywordSearchQueryManager.execute.exeWinTitle", ++resultWindowCount, queryStrShort);
        DataResultTopComponent searchResultWin = DataResultTopComponent.createInstance(windowTitle);

        Node rootNode;
        if (queryRequests.size() > 0) {
            Children childNodes =
                    Children.create(new AdHocSearchChildFactory(queryRequests, saveResults), true);

            rootNode = new AbstractNode(childNodes);
        } else {
            rootNode = Node.EMPTY;
        }

        final String pathText = NbBundle.getMessage(this.getClass(), "KeywordSearchQueryManager.pathText.text");

        DataResultTopComponent.initInstance(pathText, new TableFilterNode(rootNode, true, KeywordSearch.class.getName()),
                queryRequests.size(), searchResultWin);

        searchResultWin.requestActive();
    }

    /**
     * validate the queries before they are run
     *
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
