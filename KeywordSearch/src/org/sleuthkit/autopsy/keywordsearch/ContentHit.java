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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Represents result of keyword search query containing the Content it hit
 * and chunk information, if the result hit is a content chunk
 */
class ContentHit {

    private AbstractFile content;
    private int chunkID = 0;
    private String snippet = "";
    private boolean snippetSet = false;

    ContentHit(AbstractFile content) {
        this.content = content;
    }

    ContentHit(AbstractFile content, int chunkID) {
        this.content = content;
        this.chunkID = chunkID;
    }

    AbstractFile getContent() {
        return content;
    }

    long getId() {
        return content.getId();
    }

    int getChunkId() {
        return chunkID;
    }

    boolean isChunk() {
        return chunkID != 0;
    }

    void setSnippet(String snippet) {
        this.snippet = snippet;
        this.snippetSet = true;
    }
    
    String getSnippet() {
        return snippet;
    }
    
    boolean hasSnippet() {
        return snippetSet;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ContentHit other = (ContentHit) obj;
        if (this.content != other.content && (this.content == null || !this.content.equals(other.content))) {
            return false;
        }
        if (this.chunkID != other.chunkID) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (this.content != null ? this.content.hashCode() : 0);
        hash = 41 * hash + this.chunkID;
        return hash;
    }

    /**
     * Identify the list of files with the first chunk that has a hit
     * @param hits
     * @return 
     */
    static Map<AbstractFile, Integer> flattenResults(List<ContentHit> hits) {
        Map<AbstractFile, Integer> ret = new LinkedHashMap<AbstractFile, Integer>();
        for (ContentHit h : hits) {
            AbstractFile f = h.getContent();
            if (!ret.containsKey(f)) {
                ret.put(f, h.getChunkId());
            }
        }

        return ret;
    }
    
    //flatten results to get unique AbstractFile per hit, with first chunk id encountered
    static LinkedHashMap<AbstractFile, Integer> flattenResults(Map<String, List<ContentHit>> results) {
        LinkedHashMap<AbstractFile, Integer> flattened = new LinkedHashMap<AbstractFile, Integer>();

        for (String key : results.keySet()) {
            for (ContentHit hit : results.get(key)) {
                AbstractFile abstractFile = hit.getContent();
                //flatten, record first chunk encountered
                if (!flattened.containsKey(abstractFile)) {
                    flattened.put(abstractFile, hit.getChunkId());
                }
            }
        }
        return flattened;
    }
}
