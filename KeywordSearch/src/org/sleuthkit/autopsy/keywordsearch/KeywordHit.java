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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Stores the fact that file or an artifact associated with a file had a keyword
 * hit. All instances make both the document id of the Solr document where the
 * keyword was found and the file available to clients. Artifact keyword hits
 * also make the artifact available to clients.
 */
class KeywordHit implements Comparable<KeywordHit> {

    private final String solrDocumentId;
    private final long solrObjectId;
    private final int chunkId;
    private final String snippet;
    private final Content content;
    private final BlackboardArtifact artifact;
    private final String hit;

    public String getHit() {
        return hit;
    }

    KeywordHit(String solrDocumentId, String snippet) throws TskCoreException {
        this(solrDocumentId, snippet, null);
    }

    KeywordHit(String solrDocumentId, String snippet, String hit) throws TskCoreException {
        /**
         * Store the Solr document id.
         */
        this.solrDocumentId = solrDocumentId;

        /**
         * Parse the Solr document id to get the Solr object id and chunk id.
         * The Solr object id will either be a file id or an artifact id from
         * the case database.
         *
         * For every object (file or artifact) there will at least two Solr
         * documents. One contains object metadata (chunk #1) and the second and
         * subsequent documents contain chunks of the text.
         */
        final int separatorIndex = solrDocumentId.indexOf(Server.CHUNK_ID_SEPARATOR);
        if (-1 != separatorIndex) {
            this.solrObjectId = Long.parseLong(solrDocumentId.substring(0, separatorIndex));
            this.chunkId = Integer.parseInt(solrDocumentId.substring(separatorIndex + 1));
        } else {
            this.solrObjectId = Long.parseLong(solrDocumentId);
            this.chunkId = 0;
        }

        /**
         * Look up the file associated with the keyword hit. If the high order
         * bit of the object id is set, the hit was for an artifact. In this
         * case, look up the artifact as well.
         */
        SleuthkitCase caseDb = Case.getCurrentCase().getSleuthkitCase();
        long fileId;
        if (this.solrObjectId < 0) {
            this.artifact = caseDb.getBlackboardArtifact(this.solrObjectId);
            fileId = artifact.getObjectID();
        } else {
            this.artifact = null;
            fileId = this.solrObjectId;
        }
        this.content = caseDb.getContentById(fileId);

        /**
         * Store the text snippet.
         */
        this.snippet = snippet;
        this.hit = hit;
    }

    String getSolrDocumentId() {
        return this.solrDocumentId;
    }

    long getSolrObjectId() {
        return this.solrObjectId;
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

    Content getContent() {
        return this.content;
    }

    /**
     * Is this hit in the indexed text of an artifact.
     *
     * @return
     */
    boolean isArtifactHit() {
        return (null != this.artifact);
    }

    /**
     * If this hit is in the indexed text of an artifact, get that artifact.
     *
     * @return The artifact whose indexed text this hit is in, or null if it is
     *         not an artifacts hit.
     */
    BlackboardArtifact getArtifact() {
        return this.artifact;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KeywordHit other = (KeywordHit) obj;
        return (this.solrObjectId == other.solrObjectId && this.chunkId == other.chunkId);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (int) this.solrObjectId + this.chunkId;
        return hash;
    }

    @Override
    public int compareTo(KeywordHit o) {
        if (this.solrObjectId < o.solrObjectId) {
            // Out object id is less than the other object id
            return -1;
        } else if (this.solrObjectId == o.solrObjectId) {
            // Hits have same object id
            if (this.chunkId < o.chunkId) {
                // Our chunk id is lower than the other chunk id
                return -1;
            } else {
                // Our chunk id is either greater than or equal to the other chunk id
                return this.chunkId == o.chunkId ? 0 : 1;
            }
        } else {
            // Our object id is greater than the other object id
            return 1;
        }
    }
}
