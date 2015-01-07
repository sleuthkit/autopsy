/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Stores the fact that file or an artifact had a keyword hit.
 */
class KeywordHit {

    private final String solrDocumentId;
    private final long objectId;
    private final int chunkId;
    private final String snippet;
    private final AbstractFile file;
    BlackboardArtifact artifact;

    KeywordHit(String solrDocumentId, String snippet) throws TskCoreException {
        /**
         * Store the Solr document id.
         */
        this.solrDocumentId = solrDocumentId;

        /**
         * Parse the Solr document id to get the object id and chunk id. There
         * will only be a chunk if the text in the object was divided into
         * chunks.
         */
        final int separatorIndex = solrDocumentId.indexOf(Server.ID_CHUNK_SEP);
        if (separatorIndex != -1) {
            this.objectId = Long.parseLong(solrDocumentId.substring(0, separatorIndex));
            this.chunkId = Integer.parseInt(solrDocumentId.substring(separatorIndex + 1));
        } else {
            this.objectId = Long.parseLong(solrDocumentId);
            this.chunkId = 0;
        }

        /**
         * Look up the file associated with the keyword hit. If the high order
         * bit of the object id is set, the hit was for an artifact. In this
         * case, look up the artifact as well.
         */
        SleuthkitCase caseDb = Case.getCurrentCase().getSleuthkitCase();
        long fileId;
        if (this.objectId < 0) {
            long artifactId = this.objectId - 0x8000000000000000L;
            this.artifact = caseDb.getBlackboardArtifact(artifactId);
            fileId = artifact.getObjectID();
        } else {
            fileId = this.objectId;
        }
        this.file = caseDb.getAbstractFileById(fileId);
        
        /**
         * Store the text snippet.
         */
        this.snippet = snippet;        
    }

    String getSolrDocumentId() {
        return this.solrDocumentId;
    }

    long getObjectId() {
        return this.objectId;
    }

    boolean hasChunkId() {
        return this.chunkId != 0;
    }

    int getChunkId() {
        return this.chunkId;
    }

    boolean hasSnippet() {
        return !this.snippet.isEmpty();
    }

    String getSnippet() {
        return this.snippet;
    }

    AbstractFile getFile() {
        return this.file;
    }

    BlackboardArtifact getArtifact() {
        return this.artifact;
    }

    @Override
    public boolean equals(Object obj) { // RJCTODO: Fix
        if (null == obj) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KeywordHit other = (KeywordHit) obj;
        return (this.objectId == other.objectId && this.chunkId == other.chunkId && this.snippet.equals(other.snippet));
    }

    @Override
    public int hashCode() { // RJCTODO: Fix
        int hash = 3;
        hash = 41 * hash + (int) this.objectId + this.chunkId;
        return hash;
    }

}
