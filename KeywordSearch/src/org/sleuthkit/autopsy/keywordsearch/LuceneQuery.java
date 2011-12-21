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
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

public class LuceneQuery implements KeywordSearchQuery {

    private String query;

    public LuceneQuery(String query) {
        this.query = query;
    }

    @Override
    public void execute() {
        List<FsContent> matches = new ArrayList<FsContent>();

        boolean allMatchesFetched = false;
        final int ROWS_PER_FETCH = 10000;

        Server.Core solrCore = KeywordSearch.getServer().getCore();

        SolrQuery q = new SolrQuery();
        q.setQuery(query);
        q.setRows(ROWS_PER_FETCH);
        q.setFields("id");

        for (int start = 0; !allMatchesFetched; start = start + ROWS_PER_FETCH) {

            q.setStart(start);

            try {
                QueryResponse response = solrCore.query(q);
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
                    rs.close();
                }

            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
                // TODO: handle bad query strings, among other issues
            } catch (SQLException ex) {
                // TODO: handle error getting files from database
            }

        }

        String pathText = "Lucene query: " + query;
        Node rootNode = new KeywordSearchNode(matches, query);

        TopComponent searchResultWin = DataResultTopComponent.createInstance("Keyword search", pathText, rootNode, matches.size());
        searchResultWin.requestActive(); // make it the active top component
    }

    @Override
    public boolean validate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
