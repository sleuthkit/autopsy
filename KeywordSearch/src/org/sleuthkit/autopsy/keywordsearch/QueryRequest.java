/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import java.util.Map;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;


/**
 * Stores data about a search before it is done.  
 */
class QueryRequest  {

    private KeywordSearchQuery query;
    private String queryString;
    private Map<String, Object> map;
            
    KeywordSearchQuery getQuery() {
        return query;
    }

    /**
     * NOTE: The below descriptions are based on how it is used in teh code.
     * @param queryString Query string
     * @param map Map that stores settings to use during the search
     * @param id ID that callers simply increment from 0
     * @param query Query that will be performed. 
     */
    public QueryRequest(String queryString, Map<String, Object> map, int id, KeywordSearchQuery query) {
        this.queryString = queryString;
        this.map = map;
        this.query = query;
    }
    
    public String getQueryString() {
        return queryString;
    }
    
    public Map<String, Object> getProperties() {
        return map;
    }
    
    public static Collection<BlackboardArtifact> writeAllHitsToBlackBoard(QueryResults hits, KeywordSearchQuery query, String listName, ProgressHandle progress) {
        final Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();

        progress.start(hits.getKeywords().size());
        int processedFiles = 0;
        for (final Keyword hit : hits.getKeywords()) {
            progress.progress(hit.toString(), ++processedFiles);
            
            ///@todo we need a way to cancel this loop
//            if (this.isCancelled()) {
//                break;
//            }
            Map<AbstractFile, Integer> flattened = hits.getUniqueFiles(hit);
            for (AbstractFile f : flattened.keySet()) {
                int chunkId = flattened.get(f);
                final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(hit.toString());
                String snippet;
                try {
                    snippet = LuceneQuery.querySnippet(snippetQuery, f.getId(), chunkId, !query.isLiteral(), true);
                } catch (NoOpenCoreException e) {
                    //logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    //no reason to continue
                    break;
                } catch (Exception e) {
                    //logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    continue;
                }
                if (snippet != null) {
                    KeywordWriteResult written = query.writeToBlackBoard(hit.toString(), f, snippet, listName);
                    if (written != null) {
                        newArtifacts.add(written.getArtifact());
                    }
                }
            }
        }        
        return newArtifacts;
    }
}
