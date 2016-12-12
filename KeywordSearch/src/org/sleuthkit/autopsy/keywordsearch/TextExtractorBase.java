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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;

public abstract class TextExtractorBase<AppendixProvder> implements TextExtractor {

    static final int MAX_EXTR_TEXT_CHARS = 512 * 1024; //chars
    static final int SINGLE_READ_CHARS = 1024;
    static final int EXTRA_CHARS = 128; //for whitespace
    static final int MAX_STRING_CHUNK_SIZE = 1 * 1024 * 1024;  //in bytes


    abstract void logWarning(final String msg, Exception ex);

    void appendDataToFinalChunk(StringBuilder sb, AppendixProvder dataProvider) {
        //no-op
    }

    /**
     * Sanitize the given chars by replacing non-UTF-8 characters with caret '^'
     *
     * @param totalRead    the number of chars in textChunkBuf
     * @param textChunkBuf the characters to sanitize
     */
    static void sanitizeToUTF8(StringBuilder sb) {
        final int length = sb.length();

        // Sanitize by replacing non-UTF-8 characters with caret '^'
        for (int i = 0; i < length; i++) {
            if (!TextUtil.isValidSolrUTF8(sb.charAt(i))) {
                sb.replace(i, i + 1, "^'");
            }
        }
    }

    abstract boolean noExtractionOptionsAreEnabled();

    abstract AppendixProvder newAppendixProvider();

    @Override
    public boolean index(AbstractFile sourceFile, IngestJobContext context) throws Ingester.IngesterException {
        int numChunks = 0; //unknown until indexing is done

        if (noExtractionOptionsAreEnabled()) {
            return true;
        }
        AppendixProvder appendix = newAppendixProvider();
        try (final InputStream stream = getInputStream(sourceFile);
                Reader reader = getReader(stream, sourceFile, appendix);) {

            //we read max 1024 chars at time, this seems to max what this Reader would return
            char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];
            long readSize;
            boolean eof = false;
            while (!eof) {
                int totalRead = 0;
                if (context.fileIngestIsCancelled()) {
                    return true;
                }
                if ((readSize = reader.read(textChunkBuf, 0, SINGLE_READ_CHARS)) == -1) {
                    eof = true;
                } else {
                    totalRead += readSize;
                }

                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while ((totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(textChunkBuf, totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read char-by-char until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(textChunkBuf[totalRead - 1])
                            && (readSize = reader.read(textChunkBuf, totalRead, 1)) != -1) {
                        totalRead += readSize;
                    }
                    if (readSize == -1) {
                        //this is the last chunk
                        eof = true;
                    }
                }

                StringBuilder sb = new StringBuilder(totalRead + 1000)
                        .append(textChunkBuf, 0, totalRead);

                if (eof) {
                    appendDataToFinalChunk(sb, appendix);
                }
                sanitizeToUTF8(sb);

                final String chunkString = sb.toString();

                //encode to bytes as UTF-8 to index as byte stream
                byte[] encodedBytes = chunkString.getBytes(Server.DEFAULT_INDEXED_TEXT_CHARSET);
                String chunkId = Server.getChunkIdString(sourceFile.getId(), numChunks + 1);
                try {
                    getIngester().indexChunk(sourceFile, encodedBytes, encodedBytes.length, chunkId);
                    numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    logWarning("Ingester had a problem with extracted string from file '" //NON-NLS
                            + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx);//NON-NLS

                    throw ingEx; //need to rethrow to signal error and move on
                }
            }
        } catch (IOException ex) {
            logWarning("Unable to read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex);//NON-NLS
            return false;
        } catch (Exception ex) {
            logWarning("Unexpected error, can't read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex);//NON-NLS
            return false;
        } finally {
            //after all chunks, ingest the parent file without content itself, and store numChunks
            getIngester().recordNumberOfChunks(sourceFile, numChunks);
        }
        return true;
    }

    abstract InputStream getInputStream(AbstractFile sourceFile1);

    abstract Reader getReader(InputStream stream, AbstractFile sourceFile, AppendixProvder appendix) throws Ingester.IngesterException;
}
