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

/**
 * Stores the fact that file or an artifact associated had a keyword hit.
 * <p>
 * Instances of this class are immutable, so they are thread-safe.
 */
class KeywordHit {

    private final String documentId;
    private final long objectId;
    private final int chunkId;
    private final String snippet;

    KeywordHit(String documentId, String snippet) {
        this.documentId = documentId;
        final int separatorIndex = documentId.indexOf(Server.ID_CHUNK_SEP);
        if (separatorIndex != -1) {
            this.objectId = Long.parseLong(documentId.substring(0, separatorIndex));
            this.chunkId = Integer.parseInt(documentId.substring(separatorIndex + 1));
        } else {
            this.objectId = Long.parseLong(documentId);
            this.chunkId = 0;
        }
        this.snippet = snippet;
    }

    String getDocumentId() {
        return this.documentId;
    }

    long getObjectId() {
        return this.objectId;
    }

    int getChunkId() {
        return this.chunkId;
    }

    boolean isChunk() {
        return this.chunkId != 0;
    }

    boolean hasSnippet() {
        return !this.snippet.isEmpty();
    }

    String getSnippet() {
        return this.snippet;
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
        return (this.objectId == other.objectId && this.chunkId == other.chunkId && this.snippet.equals(other.snippet));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (int) this.objectId + this.chunkId;
        return hash;
    }

}
