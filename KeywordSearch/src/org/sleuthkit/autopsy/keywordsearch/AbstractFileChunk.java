/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.nio.charset.Charset;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;

/**
 * A representation of a chunk of text from a file that can be used, when
 * supplied with an Ingester, to index the chunk for search.
 */
final class AbstractFileChunk {

    private final int chunkNumber;
    private final TextExtractor textExtractor;

    /**
     * Constructs a representation of a chunk of text from a file that can be
     * used, when supplied with an Ingester, to index the chunk for search.
     *
     * @param textExtractor A TextExtractor for the file.
     * @param chunkNumber   A sequence number for the chunk.
     */
    AbstractFileChunk(TextExtractor textExtractor, int chunkNumber) {
        this.textExtractor = textExtractor;
        this.chunkNumber = chunkNumber;
    }

    /**
     * Gets the TextExtractor for the source file of the text chunk.
     *
     * @return A reference to the TextExtractor.
     */
    TextExtractor getTextExtractor() {
        return textExtractor;
    }

    /**
     * Gets the sequence number of the text chunk.
     *
     * @return The chunk number.
     */
    int getChunkNumber() {
        return chunkNumber;
    }

    /**
     * Gets the id of the text chunk.
     *
     * @return An id of the form [source file object id]_[chunk number]
     */
    String getChunkId() {
        return Server.getChunkIdString(this.textExtractor.getSourceFile().getId(), this.chunkNumber);
    }

    /**
     * Indexes the text chunk.
     *
     * @param ingester   An Ingester to do the indexing.
     * @param chunkBytes The raw bytes of the text chunk.
     * @param chunkSize  The size of the text chunk in bytes.
     * @param charSet    The char set to use during indexing.
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    void index(Ingester ingester, byte[] chunkBytes, long chunkSize, Charset charSet) throws IngesterException {
        ByteContentStream bcs = new ByteContentStream(chunkBytes, chunkSize, textExtractor.getSourceFile(), charSet);
        try {
            ingester.ingest(this, bcs, chunkBytes.length);
        } catch (Exception ex) {
            throw new IngesterException(String.format("Error ingesting (indexing) file chunk: %s", getChunkId()), ex);
        }
    }

}
