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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.coreutils.StringExtract;

/**
 * Extractor of text from TIKA supported AbstractFile content. Extracted text is
 * divided into chunks and indexed with Solr. Protects against Tika parser hangs
 * (for unexpected/corrupt content) using a timeout mechanism. If Tika
 * extraction succeeds, chunks are indexed with Solr.
 *
 * This Tika extraction/chunking utility is useful for large files of Tika
 * parsers-supported content type.
 *
 */
public class AbstractFileTikaTextExtract implements AbstractFileExtract {

    private static final Logger logger = Logger.getLogger(IngestServiceAbstractFile.class.getName());
    private static final Charset OUTPUT_CHARSET = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace
    private static final char[] TEXT_CHUNK_BUF = new char[MAX_EXTR_TEXT_CHARS];
    private Tika tika;
    private KeywordSearchIngestService service;
    private static Ingester ingester;
    private AbstractFile sourceFile; //currently processed file
    private int numChunks = 0;
    private static final String UTF16BOM = "\uFEFF";
    private final ExecutorService tikaParseExecutor = Executors.newSingleThreadExecutor();
    // TODO: use a more robust method than checking file extension
    // supported extensions list from http://www.lucidimagination.com/devzone/technical-articles/content-extraction-tika
    static final String[] SUPPORTED_EXTENSIONS = {"tar", "jar", "zip", "gzip", "bzip2",
        "gz", "tgz", "odf", "doc", "xls", "ppt", "rtf", "pdf", "html", "htm", "xhtml", "txt", "log", "manifest",
        "bmp", "gif", "png", "jpeg", "jpg", "tiff", "mp3", "aiff", "au", "midi", "wav",
        "pst", "xml", "class", "dwg", "eml", "emlx", "mbox", "mht"};

    AbstractFileTikaTextExtract() {
        this.service = KeywordSearchIngestService.getDefault();
        ingester = Server.getIngester();
        tika = new Tika();
        //tika.setMaxStringLength(MAX_EXTR_TEXT_CHARS); //for getting back string only
    }

    @Override
    public boolean setScript(StringExtract.StringExtractUnicodeTable.SCRIPT extractScript) {
        return false;
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
    public boolean index(AbstractFile sourceFile) throws Ingester.IngesterException {
        this.sourceFile = sourceFile;
        this.numChunks = 0; //unknown until indexing is done

        boolean success = false;
        Reader reader = null;


        final InputStream stream = new ReadContentInputStream(sourceFile);
        try {
            Metadata meta = new Metadata();
            /* Tika parse request with timeout -- disabled for now
             ParseRequestTask parseTask = new ParseRequestTask(tika, stream, meta, sourceFile);
             final Future<?> future = tikaParseExecutor.submit(parseTask);
             try {
             future.get(Ingester.getTimeout(sourceFile.getSize()), TimeUnit.SECONDS);
             } catch (TimeoutException te) {
             final String msg = "Tika parse timeout for content: " + sourceFile.getId() + ", " + sourceFile.getName();
             logger.log(Level.WARNING, msg);
             throw new IngesterException(msg);
             }
             catch (Exception ex) {
             final String msg = "Unexpected exception from Tika parse task execution for file: " + sourceFile.getId() + ", " + sourceFile.getName();
             logger.log(Level.WARNING, msg, ex);
             throw new IngesterException(msg);
             }
            
             reader = parseTask.getReader();
             */
            try {
                reader = tika.parse(stream, meta);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to Tika parse the content" + sourceFile.getId() + ": " + sourceFile.getName(), ex);
                reader = null;
            }

            if (reader == null) {
                //likely due to exception in parse()
                logger.log(Level.WARNING, "No reader available from Tika parse");
                return false;
            }

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
                //set initial size to chars read + bom + metadata (roughly) - try to prevent from resizing
                StringBuilder sb = new StringBuilder((int) totalRead + 1000);
                //inject BOM here (saves byte buffer realloc later), will be converted to specific encoding BOM
                sb.append(UTF16BOM);
                if (totalRead < MAX_EXTR_TEXT_CHARS) {
                    sb.append(TEXT_CHUNK_BUF, 0, (int) totalRead);
                } else {
                    sb.append(TEXT_CHUNK_BUF);
                }

                //reset for next chunk
                totalRead = 0;

                //append meta data if last chunk
                if (eof) {
                    //sort meta data keys
                    List<String> sortedKeyList = Arrays.asList(meta.names());
                    Collections.sort(sortedKeyList);
                    sb.append("\n\n------------------------------METADATA------------------------------\n\n");
                    for (String key : sortedKeyList) {
                        String value = meta.get(key);
                        sb.append(key).append(": ").append(value).append("\n");
                    }
                }

                extracted = sb.toString();

                //converts BOM automatically to charSet encoding
                byte[] encodedBytes = extracted.getBytes(OUTPUT_CHARSET);
                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);
                try {
                    chunk.index(ingester, encodedBytes, encodedBytes.length, OUTPUT_CHARSET);
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
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unexpected error, can't read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex);
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

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        String fileNameLower = file.getName().toLowerCase();
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; ++i) {
            if (fileNameLower.endsWith(SUPPORTED_EXTENSIONS[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runnable and timeable task that calls tika to parse the content using
     * streaming
     */
    private static class ParseRequestTask implements Runnable {

        //in
        private Tika tika;
        private InputStream stream;
        private Metadata meta;
        private AbstractFile sourceFile;
        //out
        private Reader reader;

        ParseRequestTask(Tika tika, InputStream stream, Metadata meta, AbstractFile sourceFile) {
            this.tika = tika;
            this.stream = stream;
            this.meta = meta;
            this.sourceFile = sourceFile;
        }

        @Override
        public void run() {
            try {
                reader = tika.parse(stream, meta);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to Tika parse the content" + sourceFile.getId() + ": " + sourceFile.getName(), ex);
                reader = null;
            }
        }

        public Reader getReader() {
            return reader;
        }
    }
}
