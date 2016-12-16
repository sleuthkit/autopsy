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

import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
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
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extractor of text from TIKA supported AbstractFile content. Extracted text
 * will be divided into chunks and indexed with Solr. Protects against Tika
 * parser hangs (for unexpected/corrupt content) using a timeout mechanism. If
 * Tika extraction succeeds, chunks are indexed with Solr.
 *
 * This Tika extraction/chunking utility is useful for large files of Tika
 * parsers-supported content type.
 */
class TikaTextExtractor extends FileTextExtractor {

    private static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private final ExecutorService tikaParseExecutor = Executors.newSingleThreadExecutor();

    private static final List<String> TIKA_SUPPORTED_TYPES
            = new Tika().getParser().getSupportedTypes(new ParseContext())
            .parallelStream()
            .map(mt -> mt.getType() + "/" + mt.getSubtype())
            .collect(Collectors.toList());

    @Override
    void logWarning(final String msg, Exception ex) {
        KeywordSearch.getTikaLogger().log(Level.WARNING, msg, ex);
        super.logWarning(msg, ex);
    }

    @Override
    Reader getReader(final InputStream stream, AbstractFile sourceFile) throws IngesterException, MissingResourceException {
        Metadata metadata = new Metadata();
        //Parse the file in a task, a convenient way to have a timeout...
        final Future<Reader> future = tikaParseExecutor.submit(() -> new Tika().parse(stream, metadata));
        try {
            final Reader tikaReader = future.get(getTimeout(sourceFile.getSize()), TimeUnit.SECONDS);
            CharSource metaDataCharSource = getMetaDataCharSource(metadata);
            //concatenate parsed content and meta data into a single reader.
            return CharSource.concat(new ReaderCharSource(tikaReader), metaDataCharSource).openStream();
        } catch (TimeoutException te) {
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.tikaParseTimeout.text", sourceFile.getId(), sourceFile.getName());
            logWarning(msg, te);
            throw new IngesterException(msg);
        } catch (Exception ex) {
            KeywordSearch.getTikaLogger().log(Level.WARNING, "Exception: Unable to Tika parse the content" + sourceFile.getId() + ": " + sourceFile.getName(), ex.getCause()); //NON-NLS
            final String msg = NbBundle.getMessage(this.getClass(), "AbstractFileTikaTextExtract.index.exception.tikaParse.msg", sourceFile.getId(), sourceFile.getName());
            logWarning(msg, ex);
            throw new IngesterException(msg, ex);
        }
    }

    /**
     * Get a CharSource that wraps a formated representation of the given
     * Metadata.
     *
     * @param metadata The Metadata to wrap as a CharSource
     *
     * @returna CharSource for the given MetaData
     */
    static private CharSource getMetaDataCharSource(Metadata metadata) {
        return CharSource.wrap(
                new StringBuilder("\n\n------------------------------METADATA------------------------------\n\n")
                .append(Stream.of(metadata.names()).sorted()
                        .map(key -> key + ": " + metadata.get(key))
                        .collect(Collectors.joining("\n"))
                ));
    }

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        if (detectedFormat == null
                || FileTextExtractor.BLOB_MIME_TYPES.contains(detectedFormat) //any binary unstructured blobs (string extraction will be used)
                || FileTextExtractor.ARCHIVE_MIME_TYPES.contains(detectedFormat)
                || (detectedFormat.startsWith("video/") && !detectedFormat.equals("video/x-flv")) //skip video other than flv (tika supports flv only) //NON-NLS
                || detectedFormat.equals("application/x-font-ttf")) {   // Tika currently has a bug in the ttf parser in fontbox; It will throw an out of memory exception//NON-NLS

            return false;
        }

        //TODO might need to add more mime-types to ignore
        //then accept all formats supported by Tika
        return TIKA_SUPPORTED_TYPES.contains(detectedFormat);
    }

    @Override
    InputStream getInputStream(AbstractFile sourceFile1) {
        return new ReadContentInputStream(sourceFile1);
    }

    @Override
    boolean noExtractionOptionsAreEnabled() {
        return false;
    }

    /**
     * Return timeout that should be used to index the content.
     *
     * @param size size of the content
     *
     * @return time in seconds to use a timeout
     */
    static int getTimeout(long size) {
        if (size < 1024 * 1024L) //1MB
        {
            return 60;
        } else if (size < 10 * 1024 * 1024L) //10MB
        {
            return 1200;
        } else if (size < 100 * 1024 * 1024L) //100MB
        {
            return 3600;
        } else {
            return 3 * 3600;
        }

    }

    /**
     * An implementation of CharSource that just wraps an existing reader and
     * returns it in openStream().
     */
    private static class ReaderCharSource extends CharSource {

        private final Reader reader;

        public ReaderCharSource(Reader reader) {
            this.reader = reader;
        }

        @Override
        public Reader openStream() throws IOException {
            return reader;
        }
    }
}
