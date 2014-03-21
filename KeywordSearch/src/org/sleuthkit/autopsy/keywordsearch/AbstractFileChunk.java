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

import java.nio.charset.Charset;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;

/**
 * Represents each string chunk to be indexed, a derivative of AbstractFileExtract file
 */
class AbstractFileChunk {
    private int chunkID;
    private AbstractFileExtract parent;

    AbstractFileChunk(AbstractFileExtract parent, int chunkID) {
        this.parent = parent;
        this.chunkID = chunkID;
    }

    public AbstractFileExtract getParent() {
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
        ByteContentStream bcs = new ByteContentStream(content, contentSize, parent.getSourceFile(), indexCharset);
        try {
            ingester.ingest(this, bcs, content.length);
            //logger.log(Level.INFO, "Ingesting string chunk: " + this.getName() + ": " + chunkID);
        } catch (Exception ingEx) {
            success = false;
            throw new IngesterException(NbBundle.getMessage(this.getClass(), "AbstractFileChunk.index.exception.msg",
                                                            parent.getSourceFile().getId(), chunkID), ingEx);
        }
        return success;
    }
    
}
