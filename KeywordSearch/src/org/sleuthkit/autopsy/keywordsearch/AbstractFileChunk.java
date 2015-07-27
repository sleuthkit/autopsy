/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;

/**
 * Represents each string chunk to be indexed, a derivative of TextExtractor file
 */
class AbstractFileChunk {
    private int chunkID;
    private TextExtractor parent;
    private final CharsetDecoder charsetDecoder = Charset.forName("utf-8").newDecoder(); // NON-NLS
    private final String replacement_string = "?";
    private static final Logger logger = Logger.getLogger(AbstractFileChunk.class.getName());

    AbstractFileChunk(TextExtractor parent, int chunkID) {
        this.parent = parent;
        this.chunkID = chunkID;
        this.charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);
        this.charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetDecoder.replaceWith(replacement_string); // white questionmark in black diamond - Replacement Character U+FFFD
    }

    public TextExtractor getParent() {
        return parent;
    }

    public int getChunkId() {
        return chunkID;
    }

    /**
     * return String representation of the absolute id (parent and child)
     *
     * @return
     */
    public String getIdString() {
        return Server.getChunkIdString(this.parent.getSourceFile().getId(), this.chunkID);
    }

    public boolean index(Ingester ingester, byte[] content, long contentSize, Charset indexCharset) throws IngesterException {
        boolean success = true;
        // content need to to sanitized for invalid utf-8 data
        byte[] sanitizedContent = {};
        try {
            sanitizedContent = this.charsetDecoder.decode(ByteBuffer.wrap(content)).toString().getBytes();
        } catch (CharacterCodingException ex) {
            logger.log(Level.WARNING, "Error sanitizing the " + ByteBuffer.wrap(content).toString(), ex);
            throw new IngesterException(NbBundle.getMessage(this.getClass(), "AbstractFileChunk.index.charCodingException.msg", parent.getSourceFile().getName()), ex);
        }
        ByteContentStream bcs = new ByteContentStream(sanitizedContent, sanitizedContent.length, parent.getSourceFile(), indexCharset);
        try {
            ingester.ingest(this, bcs, sanitizedContent.length);
            //logger.log(Level.INFO, "Ingesting string chunk: " + this.getName() + ": " + chunkID);
        } catch (Exception ingEx) {
            success = false;
            throw new IngesterException(NbBundle.getMessage(this.getClass(), "AbstractFileChunk.index.exception.msg",
                                                            parent.getSourceFile().getId(), chunkID), ingEx);
        }
        return success;
    }
    
}
