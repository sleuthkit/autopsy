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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.apache.tika.Tika;
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
    static final int MAX_EXTR_TEXT_CHUNK_SIZE = 1 * 1024 * 1024;
    private static final char[] TEXT_CHUNK_BUF = new char[MAX_EXTR_TEXT_CHUNK_SIZE];
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
        try {
            success = true;
            reader = tika.parse(new ReadContentInputStream(sourceFile));
            long readSize;
            while ((readSize = reader.read(TEXT_CHUNK_BUF, 0, MAX_EXTR_TEXT_CHUNK_SIZE)) != -1) {

                //encode to bytes to index as byte stream
                String extracted;
                if (readSize < MAX_EXTR_TEXT_CHUNK_SIZE) {
                    //trim the 0 bytes
                    StringBuilder sb = new StringBuilder((int) readSize + 5);
                    //inject BOM here (saves byte buffer realloc), will be converted to specific encoding BOM
                    sb.append(UTF16BOM); 
                    sb.append(TEXT_CHUNK_BUF, 0, (int) readSize);
                    extracted = sb.toString();

                } else {
                    StringBuilder sb = new StringBuilder((int) readSize + 5);
                    //inject BOM here (saves byte buffer realloc), will be converted to specific encoding BOM
                    sb.append(UTF16BOM); 
                    sb.append(TEXT_CHUNK_BUF);
                    extracted = sb.toString();
                }
                //converts BOM automatically to charSet encoding
                byte[] encodedBytes = extracted.getBytes(charset);
                
                
                //PrintStream s = new PrintStream("c:\\temp\\ps.txt");
                //for (byte b : encodedBytes) {
                //    s.format("%02x ", b);
                //}
                //s.close();

                //debug
                //FileOutputStream debug = new FileOutputStream("c:\\temp\\" + sourceFile.getName() + Integer.toString(this.numChunks + 1));
                //debug.write(encodedBytes, 0, encodedBytes.length);
                //debug.close();

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
            logger.log(Level.WARNING, "Unable to read content stream from " + sourceFile.getId(), ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to close content stream from " + sourceFile.getId(), ex);
            }
        }

        //after all chunks, ingest the parent file without content itself, and store numChunks
        ingester.ingest(this);

        return success;

    }
}
