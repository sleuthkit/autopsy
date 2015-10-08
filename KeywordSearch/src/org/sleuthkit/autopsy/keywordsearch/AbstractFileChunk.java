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
 * Represents each string chunk to be indexed, a derivative of TextExtractor
 * file
 */
class AbstractFileChunk {

    private int chunkID;
    private TextExtractor parent;

    AbstractFileChunk(TextExtractor parent, int chunkID) {
        this.parent = parent;
        this.chunkID = chunkID;
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
        byte[] saitizedContent = sanitize(content);
        ByteContentStream bcs = new ByteContentStream(saitizedContent, contentSize, parent.getSourceFile(), indexCharset);
        try {
            ingester.ingest(this, bcs, content.length);
        } catch (Exception ingEx) {
            throw new IngesterException(NbBundle.getMessage(this.getClass(), "AbstractFileChunk.index.exception.msg",
                    parent.getSourceFile().getId(), chunkID), ingEx);
        }
        return true;
    }

    // Given a byte array, filter out all occurances non-characters 
    // http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[:Noncharacter_Code_Point=True:]
    // and non-printable control characters except tabulator, new line and carriage return
    // and replace them with the question mark character (?)
    private static byte[] sanitize(byte[] input) {
        Charset charset = Charset.forName("UTF-8"); // NON-NLS
        String inputString = new String(input, charset);
        StringBuilder sanitized = new StringBuilder(inputString.length());
        char ch;
        for (int i = 0; i < inputString.length(); i++) {
            ch = inputString.charAt(i);
            if (charIsValidSolrUTF8(ch)) {
                sanitized.append(ch);
            } else {
                sanitized.append("?"); // NON-NLS
            }
        }

        byte[] output = sanitized.toString().getBytes(charset);
        return output;
    }

    // Is the given character a valid UTF-8 character
    // return true if it is, false otherwise
    private static boolean charIsValidSolrUTF8(char ch) {
        return (ch % 0x10000 != 0xffff && // 0xffff - 0x10ffff range step 0x10000
                ch % 0x10000 != 0xfffe && // 0xfffe - 0x10fffe range
                (ch <= 0xfdd0 || ch >= 0xfdef) && // 0xfdd0 - 0xfdef
                (ch > 0x1F || ch == 0x9 || ch == 0xa || ch == 0xd));
    }

}
