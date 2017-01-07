/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2013 Basis Technology Corp.
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extractor of text from HTML supported AbstractFile content. Extracted text is
 * divided into chunks and indexed with Solr. If HTML extraction succeeds,
 * chunks are indexed with Solr.
 */
class HtmlTextExtractor implements TextExtractor {

    private static final Logger logger = Logger.getLogger(HtmlTextExtractor.class.getName());
    private static Ingester ingester;
    static final Charset outCharset = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace    
    private static final int MAX_SIZE = 50000000;
    //private static final String UTF16BOM = "\uFEFF"; disabled prepending of BOM
    private final char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];
    private AbstractFile sourceFile;
    private int numChunks = 0;

    static final List<String> WEB_MIME_TYPES = Arrays.asList(
            "application/javascript", //NON-NLS
            "application/xhtml+xml", //NON-NLS
            "application/json", //NON-NLS
            "text/css", //NON-NLS
            "text/html", //NON-NLS NON-NLS
            "text/javascript" //NON-NLS
    //"application/xml",
    //"application/xml-dtd",
    );

    HtmlTextExtractor() {
        ingester = Ingester.getDefault();
    }

    @Override
    public boolean setScripts(List<SCRIPT> extractScripts) {
        return false;
    }

    @Override
    public List<SCRIPT> getScripts() {
        return null;
    }

    @Override
    public Map<String, String> getOptions() {
        return null;
    }

    @Override
    public void setOptions(Map<String, String> options) {
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
    public boolean index(AbstractFile sourceFile, IngestJobContext context) throws IngesterException {
        this.sourceFile = sourceFile;
        numChunks = 0; //unknown until indexing is done

        boolean success = false;
        Reader reader = null;

        final InputStream stream = new ReadContentInputStream(sourceFile);

        try {
            // Parse the stream with Jericho
            JerichoParserWrapper jpw = new JerichoParserWrapper(stream);
            jpw.parse();
            reader = jpw.getReader();

            // In case there is an exception or parse() isn't called
            if (reader == null) {
                logger.log(Level.WARNING, "No reader available from HTML parser"); //NON-NLS
                return false;
            }

            success = true;
            long readSize;
            long totalRead = 0;
            boolean eof = false;
            //we read max 1024 chars at time, this seems to max what this Reader would return
            while (!eof && (readSize = reader.read(textChunkBuf, 0, SINGLE_READ_CHARS)) != -1) {
                if (context.fileIngestIsCancelled()) {
                    ingester.ingest(this);
                    return true;
                }
                totalRead += readSize;

                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while ((totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(textChunkBuf, (int) totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(textChunkBuf[(int) totalRead - 1])
                            && (readSize = reader.read(textChunkBuf, (int) totalRead, 1)) != -1) {
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
                //set initial size to chars read + bom - try to prevent from resizing
                StringBuilder sb = new StringBuilder((int) totalRead + 1000);
                //inject BOM here (saves byte buffer realloc later), will be converted to specific encoding BOM
                //sb.append(UTF16BOM); disabled BOM, not needing as bypassing Tika
                if (totalRead < MAX_EXTR_TEXT_CHARS) {
                    sb.append(textChunkBuf, 0, (int) totalRead);
                } else {
                    sb.append(textChunkBuf);
                }

                //reset for next chunk
                totalRead = 0;
                extracted = sb.toString();

                //converts BOM automatically to charSet encoding
                byte[] encodedBytes = extracted.getBytes(outCharset);
                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);
                try {
                    chunk.index(ingester, encodedBytes, encodedBytes.length, outCharset);
                    ++this.numChunks;
                } catch (Ingester.IngesterException ingEx) {
                    success = false;
                    logger.log(Level.WARNING, "Ingester had a problem with extracted HTML from file '" //NON-NLS
                            + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx); //NON-NLS
                    throw ingEx; //need to rethrow/return to signal error and move on
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex); //NON-NLS
            success = false;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unexpected error, can't read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex); //NON-NLS
            success = false;
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to close content stream from " + sourceFile.getId(), ex); //NON-NLS
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to close content reader from " + sourceFile.getId(), ex); //NON-NLS
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
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        if (detectedFormat == null) {
            return false;
        } else if (WEB_MIME_TYPES.contains(detectedFormat) && file.getSize() <= MAX_SIZE) {
            return true;
        } else {
            return false;
        }

    }
}
