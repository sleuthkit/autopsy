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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents the fact that a file or an artifact associated with a file had a
 * keyword hit. All instances make both the document id of the Solr document
 * where the keyword was found and the object id of the file available to
 * clients. Keyword hits on the indexed text of an artifact also make the
 * artifact available to clients.
 */
class KeywordHit implements Comparable<KeywordHit> {

    private static final String GET_CONTENT_ID_FROM_ARTIFACT_ID = "SELECT obj_id FROM blackboard_artifacts WHERE artifact_id = ";

    private final long solrObjectId;
    private final int chunkId;
    private final String snippet;
    private final String hit;

    /**
     * Constructor
     *
     * @param solrDocumentId The id of the document this hit is in.
     * @param snippet        A small amount of text from the document containing
     *                       the hit.
     * @param hit            The exact text from the document that was the hit.
     *                       For some searches (ie substring, regex) this will be
     *                       different than the search term.
     *
     */
    KeywordHit(String solrDocumentId, String snippet, String hit) {
        this.snippet = StringUtils.stripToEmpty(snippet);
        this.hit = hit;

        /*
         * Parse the Solr document id to get the Solr object id and chunk id.
         * The Solr object id will either be the object id of a file id or an
         * artifact id from the case database.
         *
         * For every object (file or artifact) there will at least two Solr
         * documents. One contains object metadata (chunk #1) and the second and
         * subsequent documents contain chunks of the text.
         */
        String[] split = solrDocumentId.split(Server.CHUNK_ID_SEPARATOR);
        if (split.length == 1) {
            //chunk 0 has only the bare document id without the chunk id.
            this.solrObjectId = Long.parseLong(solrDocumentId);
            this.chunkId = 0;
        } else {
            this.solrObjectId = Long.parseLong(split[0]);
            this.chunkId = Integer.parseInt(split[1]);
        }
    }

    String getHit() {
        return hit;
    }

    String getSolrDocumentId() {
        return Long.toString(solrObjectId) + Server.CHUNK_ID_SEPARATOR + Long.toString(chunkId);
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

    /**
     * Get the content id associated with the content underlying hit. 
     * For hits on files this will be the same as the object id associated 
     * with the file. For hits on artifacts we look up the id of the object
     * that produced the artifact.
     * 
     * @return The id of the underlying content associated with the hit.
     * @throws TskCoreException If there is a problem getting the underlying
     *                          content associated with a hit on the text of an
     *                          artifact.
     */
    long getContentID() throws TskCoreException {
        if (isArtifactHit()) {
            // If the hit was in an artifact, look up the source content for the artifact.
            SleuthkitCase caseDb;
            try {
                caseDb = Case.getOpenCase().getSleuthkitCase();
            } catch (NoCurrentCaseException ex) {
                throw new TskCoreException("Exception while getting open case.", ex);
            }
            try (SleuthkitCase.CaseDbQuery executeQuery =
                    caseDb.executeQuery(GET_CONTENT_ID_FROM_ARTIFACT_ID + this.solrObjectId);
                    ResultSet resultSet = executeQuery.getResultSet();) {
                if (resultSet.next()) {
                    return resultSet.getLong("obj_id");
                } else {
                    throw new TskCoreException("Failed to get obj_id for artifact with artifact_id =" + this.solrObjectId + ".  No matching artifact was found.");
                }
            } catch (SQLException ex) {
                throw new TskCoreException("Error getting obj_id for artifact with artifact_id =" + this.solrObjectId, ex);
            }
        } else {
            //else the object id is for content.
            return this.solrObjectId;
        }
    }

    /**
     * Is this hit in the indexed text of an artifact.
     *
     * @return
     */
    boolean isArtifactHit() {
        // artifacts have negative obj ids
        return this.solrObjectId < 0;
    }

    /**
     * If this hit is in the indexed text of an artifact, get that artifact.
     *
     * @return The artifact whose indexed text this hit is in.
     */
    Optional<Long> getArtifactID() {
        if (isArtifactHit()) {
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
