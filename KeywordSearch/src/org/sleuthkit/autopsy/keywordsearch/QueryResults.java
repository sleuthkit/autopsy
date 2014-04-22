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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Stores the results from running a SOLR query. 
 * 
 */
class QueryResults {
    // maps keyword string to its hits -> This is here until we move all code to use Keyword instead
    private Map<String, List<ContentHit>> results = new HashMap<>();
    
    // maps Keyword object to its hits -> This is the long-term idea
    private Map<Keyword, List<ContentHit>> resultsK = new HashMap<>();
    
   
    @Deprecated
    void addResult(String query, List<ContentHit> hits) {
        results.put(query, hits);
    }
    
    void addResult(Keyword keyword, List<ContentHit> hits) {
        resultsK.put(keyword, hits);
    }
    
    @Deprecated
    List<ContentHit> getResults(String query) {
        return results.get(query);
    }
    
    List<ContentHit> getResults(Keyword keyword) {
        return resultsK.get(keyword);
    }
    
    @Deprecated
    Set<String> getKeywords() {
        return results.keySet();        
    }
    
    Set<Keyword> getKeywordsK() {
        return resultsK.keySet();        
    }
    
    /**
     * Get the unique set of files across all keywords in the results
     * @param results
     * @return 
     */
    LinkedHashMap<AbstractFile, ContentHit> getUniqueFiles() {
        LinkedHashMap<AbstractFile, ContentHit> flattened = new LinkedHashMap<>();

        for (String keyWord : getKeywords()) {
            for (ContentHit hit : getResults(keyWord)) {
                AbstractFile abstractFile = hit.getContent();
                //flatten, record first chunk encountered
                if (!flattened.containsKey(abstractFile)) {
                    flattened.put(abstractFile, hit);
                }
            }
        }
        return flattened;
    }
    

    /**
     * Get the unique set of files for a specific keyword
     * @param keyword
     * @return 
     */
    Map<AbstractFile, Integer> getUniqueFiles(Keyword keyword) {
        Map<AbstractFile, Integer> ret = new LinkedHashMap<>();
        for (ContentHit h : getResults(keyword)) {
            AbstractFile f = h.getContent();
            if (!ret.containsKey(f)) {
                ret.put(f, h.getChunkId());
            }
        }

        return ret;
    }
    
    /**
     * Get the unique set of files for a specific keyword
     * @param keyword
     * @return 
     */
    Map<AbstractFile, Integer> getUniqueFiles(String keyword) {
        Map<AbstractFile, Integer> ret = new LinkedHashMap<>();
        for (ContentHit h : getResults(keyword)) {
            AbstractFile f = h.getContent();
            if (!ret.containsKey(f)) {
                ret.put(f, h.getChunkId());
            }
        }

        return ret;
    }   
}
