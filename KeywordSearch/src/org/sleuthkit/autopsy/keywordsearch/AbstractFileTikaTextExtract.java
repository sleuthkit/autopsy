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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.keywordsearch.ByteContentStream.Encoding;

/**
 * Extractor of text from TIKA supported AbstractFile content. Extracted text is
 * divided into chunks and indexed with Solr.
 *
 * This is especially useful for large content of supported type that is to be
 * divided into text chunks and indexed as such.
 *
 */
public class AbstractFileTikaTextExtract implements AbstractFileExtract {

    private static final Logger logger = Logger.getLogger(IngestServiceAbstractFile.class.getName());
    private static final Encoding ENCODING = Encoding.UTF8;
    static final Charset charset = Charset.forName(ENCODING.toString());
    static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace
    private static final char[] TEXT_CHUNK_BUF = new char[MAX_EXTR_TEXT_CHARS];
    private static final Tika tika = new Tika();
    private KeywordSearchIngestService service;
    private Ingester ingester;
    private AbstractFile sourceFile;
    private int numChunks = 0;
    private static final String UTF16BOM = "\uFEFF";

    AbstractFileTikaTextExtract(AbstractFile sourceFile) {
        this.sourceFile = sourceFile;
        this.service = KeywordSearchIngestService.getDefault();
        Server solrServer = KeywordSearch.getServer();
        ingester = solrServer.getIngester();
        //tika.setMaxStringLength(MAX_EXTR_TEXT_CHARS); //for getting back string only
    }

    @Override
    public int getNumChunks() {
        return numChunks;
    }

    @Override
    public AbstractFile getSourceFile() {
        return sourceFile;
    }

    @Override
    public boolean index() throws Ingester.IngesterException {
        boolean success = false;
        Reader reader = null;
        final InputStream stream = new ReadContentInputStream(sourceFile);
        try {
            Metadata meta = new Metadata();
            reader = tika.parse(stream, meta);
            success = true;
            long readSize;
            long totalRead = 0;
            boolean eof = false;
            //we read max 1024 chars at time, this seems to max what this Reader would return
            while (!eof && (readSize = reader.read(TEXT_CHUNK_BUF, 0, SINGLE_READ_CHARS)) != -1) {
                totalRead += readSize;

                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while ((totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(TEXT_CHUNK_BUF, (int) totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(TEXT_CHUNK_BUF[(int) totalRead - 1])
                            && (readSize = reader.read(TEXT_CHUNK_BUF, (int) totalRead, 1)) != -1) {
                        totalRead += readSize;
                    }
                    if (readSize == -1) {
                        //this is the last chunk
                        eof = true;
                    }


                }

                //logger.log(Level.INFO, "TOTAL READ SIZE: " + totalRead + " file: " + sourceFile.getName());
                //encode to bytes to index as byte stream
                String extracted;
                //add BOM and trim the 0 bytes
                StringBuilder sb = new StringBuilder((int) totalRead + 5);
                //inject BOM here (saves byte buffer realloc later), will be converted to specific encoding BOM
                sb.append(UTF16BOM);
                if (totalRead < MAX_EXTR_TEXT_CHARS) {
                    sb.append(TEXT_CHUNK_BUF, 0, (int) totalRead);
                } else {;
                    sb.append(TEXT_CHUNK_BUF);
                }

                //sort meta data keys
                List<String> sortedKeyList = Arrays.asList(meta.names());
                Collections.sort(sortedKeyList);

                //append meta data
                sb.append("\n\n-------------------METADATA------------------------------\n\n");
                for (String key : sortedKeyList) {
                    String value = meta.get(key);
                    sb.append(key).append(": ").append(value).append("\n");
                }
                extracted = sb.toString();

                //reset for next chunk
                totalRead = 0;

                //converts BOM automatically to charSet encoding
                byte[] encodedBytes = extracted.getBytes(charset);
                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);
                try {
                    chunk.index(ingester, encodedBytes, encodedBytes.length, ENCODING);
                    ++this.numChunks;
                } catch (Ingester.IngesterException ingEx) {
                    success = false;
                    logger.log(Level.WARNING, "Ingester had a problem with extracted strings from file '"
                            + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx);
                    throw ingEx; //need to rethrow/return to signal error and move on
                }

                //check if need invoke commit/search between chunks
                //not to delay commit if timer has gone off
                service.checkRunCommitSearch();
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex);
            success = false;
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to close content stream from " + sourceFile.getId(), ex);
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to close content reader from " + sourceFile.getId(), ex);
            }
        }

        //after all chunks, ingest the parent file without content itself, and store numChunks
        ingester.ingest(this);

        return success;
    }
}
