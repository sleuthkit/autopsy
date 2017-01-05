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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

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
class TikaTextExtractor implements TextExtractor {

    private static final Logger logger = Logger.getLogger(TikaTextExtractor.class.getName());
    private static final Ingester ingester = Server.getIngester();

    private static final Charset OUTPUT_CHARSET = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    private static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace

    private final char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];
    private AbstractFile sourceFile; //currently processed file
    private int numChunks = 0;
    private final ExecutorService tikaParseExecutor = Executors.newSingleThreadExecutor();

    private static final List<String> TIKA_SUPPORTED_TYPES;

    static {
        TIKA_SUPPORTED_TYPES
                = new Tika().getParser().getSupportedTypes(new ParseContext())
                .parallelStream()
                .map(mt -> mt.getType() + "/" + mt.getSubtype())
                .collect(Collectors.toList());
    }

    @Override
    public boolean setScripts(List<StringExtract.StringExtractUnicodeTable.SCRIPT> extractScripts) {
        return false;
    }

    @Override
    public List<StringExtract.StringExtractUnicodeTable.SCRIPT> getScripts() {
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
    public boolean index(AbstractFile sourceFile, IngestJobContext context) throws Ingester.IngesterException {
        this.sourceFile = sourceFile;
        this.numChunks = 0; //unknown until indexing is done

        Metadata meta = new Metadata();
        try (final InputStream stream = new ReadContentInputStream(sourceFile);
                Reader reader = getReader(stream, meta, sourceFile);) {

            // break the results into chunks and index
            long readSize;

            boolean eof = false;
            //we read max 1024 chars at time, this seems to max what this Reader would return
            while (!eof) {
                int totalRead = 0;
                if (context.fileIngestIsCancelled()) {
                    ingester.ingest(this); //ingest partially chunked file?
                    return true;
                }
                if ((readSize = reader.read(textChunkBuf, 0, SINGLE_READ_CHARS)) == -1) {
                    eof = true;
                } else {
                    totalRead += readSize;
                }
                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while (!eof && (totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(textChunkBuf, totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read char-by-char until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(textChunkBuf[totalRead - 1])
                            && (readSize = reader.read(textChunkBuf, totalRead, 1)) != -1) {
                        totalRead += readSize;
                    }
                    if (readSize == -1) {   //this is the last chunk
                        eof = true;
                    }
                }

                // Sanitize by replacing non-UTF-8 characters with caret '^'
                for (int i = 0; i < totalRead; ++i) {
                    if (!TextUtil.isValidSolrUTF8(textChunkBuf[i])) {
                        textChunkBuf[i] = '^';
                    }
                }

                StringBuilder sb = new StringBuilder(totalRead + 1000);
                sb.append(textChunkBuf, 0, totalRead);

                //append meta data if last chunk
                if (eof) {
                    sb.append("\n\n------------------------------METADATA------------------------------\n\n"); //NON-NLS
                    Stream.of(meta.names()).sorted().forEach(key -> {
                        sb.append(key).append(": ").append(meta.get(key)).append("\n");
                    });
                }

                // Encode from UTF-8 charset to bytes
                byte[] encodedBytes = sb.toString().getBytes(OUTPUT_CHARSET);
                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);
                try {
                    chunk.index(ingester, encodedBytes, encodedBytes.length, OUTPUT_CHARSET);
                    this.numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    logger.log(Level.WARNING, "Ingester had a problem with extracted strings from file '" //NON-NLS
                            + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx); //NON-NLS
                    throw ingEx; //need to rethrow to signal error and move on
                }
            }

        } catch (IOException ex) {
            final String msg = "Exception: Unable to read Tika content stream from " + sourceFile.getId() + ": " + sourceFile.getName(); //NON-NLS
            KeywordSearch.getTikaLogger().log(Level.WARNING, msg, ex);
            logger.log(Level.WARNING, msg);
            return false;
        } catch (Exception ex) {
            final String msg = "Exception: Unexpected error, can't read Tika content stream from " + sourceFile.getId() + ": " + sourceFile.getName(); //NON-NLS
            KeywordSearch.getTikaLogger().log(Level.WARNING, msg, ex);
            logger.log(Level.WARNING, msg);
            return false;
        } finally {
            //after all chunks, ingest the parent file without content itself, and store numChunks
            ingester.ingest(this);
        }
        return true;
    }

    private Reader getReader(final InputStream stream, Metadata meta, AbstractFile sourceFile) throws IngesterException, MissingResourceException {
        //Parse the file in a task
        final Future<Reader> future = tikaParseExecutor.submit(() -> new Tika().parse(stream, meta));
        try {
            return future.get(Ingester.getTimeout(sourceFile.getSize()), TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.tikaParseTimeout.text", sourceFile.getId(), sourceFile.getName());
            KeywordSearch.getTikaLogger().log(Level.WARNING, msg, te);
            logger.log(Level.WARNING, msg);
            throw new IngesterException(msg);
        } catch (Exception ex) {
            KeywordSearch.getTikaLogger().log(Level.WARNING, "Exception: Unable to Tika parse the content" + sourceFile.getId() + ": " + sourceFile.getName(), ex.getCause()); //NON-NLS
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.exception.tikaParse.msg", sourceFile.getId(), sourceFile.getName());
            KeywordSearch.getTikaLogger().log(Level.WARNING, msg, ex);
            logger.log(Level.WARNING, msg);
            throw new IngesterException(msg);
        }
    }

    @Override

    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        if (detectedFormat == null
                || TextExtractor.BLOB_MIME_TYPES.contains(detectedFormat) //any binary unstructured blobs (string extraction will be used)
                || TextExtractor.ARCHIVE_MIME_TYPES.contains(detectedFormat)
                || (detectedFormat.startsWith("video/") && !detectedFormat.equals("video/x-flv")) //skip video other than flv (tika supports flv only) //NON-NLS
                || detectedFormat.equals("application/x-font-ttf")) {   // Tika currently has a bug in the ttf parser in fontbox; It will throw an out of memory exception//NON-NLS

            return false;
        }

        //TODO might need to add more mime-types to ignore
        //then accept all formats supported by Tika
        return TIKA_SUPPORTED_TYPES.contains(detectedFormat);
    }
}
