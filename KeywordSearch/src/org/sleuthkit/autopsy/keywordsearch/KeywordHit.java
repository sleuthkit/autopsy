/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Comparator;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents the fact that a file or an artifact associated with a file had a
 * keyword hit. All instances make both the document id of the Solr document
 * where the keyword was found and the object Id of the file available to
 * clients. Artifact keyword hits also make the artifact available to clients.
 */
class KeywordHit implements Comparable<KeywordHit> {

    private final String solrDocumentId;
    private final long solrObjectId;
    private final int chunkId;
    private final String snippet;
    private final long contentID;
    private final boolean hitOnArtifact;
    private final String hit;

    public String getHit() {
        return hit;
    }

    KeywordHit(String solrDocumentId, String snippet, String hit) throws TskCoreException {
        this.snippet = StringUtils.stripToEmpty(snippet);
        this.hit = hit;
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
        hitOnArtifact = this.solrObjectId < 0;

        /*
         * If the high order bit of the object id is set (ie, it is negative),
         * the hit was in an artifact, look up the artifact.
         */
        if (hitOnArtifact) {
            SleuthkitCase caseDb = Case.getCurrentCase().getSleuthkitCase();
            try (SleuthkitCase.CaseDbQuery executeQuery = caseDb.executeQuery("select obj_id from blackboard_artifacts where artifact_id = " + this.solrObjectId);
                    ResultSet resultSet = executeQuery.getResultSet();) {
                if (resultSet.next()) {
                    contentID = resultSet.getLong("obj_id");
                } else {
                    throw new TskCoreException("Failed to get obj_id for artifact with artifact_id =" + this.solrObjectId + ".  No matching artifact was found.");
                }
            } catch (SQLException ex) {
                throw new TskCoreException("Error getting obj_id for artifact with artifact_id =" + this.solrObjectId, ex);
            }
        } else {
            //else the object id is for content.
            contentID = this.solrObjectId;
        }
    }

    String getSolrDocumentId() {
        return this.solrDocumentId;
    }

    long getSolrObjectId() {
        return this.solrObjectId;
    }

    int getChunkId() {
        return this.chunkId;
    }

    boolean hasSnippet() {
        return StringUtils.isNotBlank(this.snippet);
    }

    String getSnippet() {
        return this.snippet;
    }

    long getContentID() {
        return this.contentID;
    }

    /**
     * Is this hit in the indexed text of an artifact.
     *
     * @return
     */
    boolean isArtifactHit() {
        return hitOnArtifact;
    }

    /**
     * If this hit is in the indexed text of an artifact, get that artifact.
     *
     * @return The artifact whose indexed text this hit is in.
     */
    Optional<Long> getArtifactID() {
        if (hitOnArtifact) {
            return Optional.of(solrObjectId);
        } else {
            return Optional.empty();
        }
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
        return this.compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (int) this.solrObjectId + this.chunkId;
        return hash;
    }

    @Override
    public int compareTo(KeywordHit o) {
        return Comparator.comparing(KeywordHit::getSolrObjectId)
                .thenComparing(KeywordHit::getChunkId)
                .compare(this, o);
    }
}
