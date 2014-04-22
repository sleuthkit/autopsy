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

import org.sleuthkit.datamodel.AbstractFile;

/**
 * Stores the fact that the given file had a keyword hit with a single snippet
 * at a given chunk. 
 */
class ContentHit {

    private AbstractFile content;
    private int chunkID = 0;
    private String snippet = "";    // single snippet for chunk (chunk could have more hits)
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
}
