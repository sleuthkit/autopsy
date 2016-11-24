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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Takes an AbstractFile, extracts strings, converts into chunks (associated
 * with the original source file) up to 1MB then and indexes chunks as text with
 * Solr.
 */
class StringsTextExtractor implements TextExtractor {

    private static final Logger logger = Logger.getLogger(StringsTextExtractor.class.getName());

    private static final Ingester ingester = Server.getIngester();
    private static final int MAX_STRING_CHUNK_SIZE = 1 * 1024 * 1024;  //in bytes
    private static final Charset INDEX_CHARSET = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    private AbstractFile sourceFile;
    private int numChunks = 0;
    private final List<SCRIPT> extractScripts = new ArrayList<>();
    private Map<String, String> extractOptions = new HashMap<>();

    public StringsTextExtractor() {
        //LATIN_2 is the default script
        extractScripts.add(SCRIPT.LATIN_2);
    }

    @Override
    public boolean setScripts(List<SCRIPT> extractScripts) {
        this.extractScripts.clear();
        this.extractScripts.addAll(extractScripts);
        return true;
    }

    @Override
    public List<SCRIPT> getScripts() {
        return new ArrayList<>(extractScripts);
    }

    @Override
    public int getNumChunks() {
        return this.numChunks;
    }

    @Override
    public AbstractFile getSourceFile() {
        return sourceFile;
    }

    @Override
    public Map<String, String> getOptions() {
        return extractOptions;
    }

    @Override
    public void setOptions(Map<String, String> options) {
        this.extractOptions = options;
    }

    @Override
    public boolean index(AbstractFile sourceFile, IngestJobContext context) throws IngesterException {
        this.sourceFile = sourceFile;
        this.numChunks = 0; //unknown until indexing is done

        boolean extractUTF8 = Boolean.parseBoolean(extractOptions.get(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString()));
        boolean extractUTF16 = Boolean.parseBoolean(extractOptions.get(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString()));

        if ((extractUTF8 || extractUTF16) == false) {
            //nothing to do
            return true;
        }

        try (InputStream stringStream = getInputStream(sourceFile, extractUTF8, extractUTF16);) {
            //break input stream into chunks 
            final byte[] stringChunkBuf = new byte[MAX_STRING_CHUNK_SIZE];
            long readSize;
            while ((readSize = stringStream.read(stringChunkBuf, 0, MAX_STRING_CHUNK_SIZE)) != -1) {
                if (context.fileIngestIsCancelled()) {
                    ingester.ingest(this);   //ingest partially chunked file?
                    return true;
                }

                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);

                try {
                    chunk.index(ingester, stringChunkBuf, readSize, INDEX_CHARSET);
                    ++this.numChunks;
                } catch (IngesterException ingEx) {

                    logger.log(Level.WARNING, "Ingester had a problem with extracted strings from file '" + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx); //NON-NLS
                    throw ingEx; //need to rethrow to signal error and move on
                }
            }

            //after all chunks, ingest the parent file without content itself, and store numChunks
            ingester.ingest(this);

        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read input stream to divide and send to Solr, file: " + sourceFile.getName(), ex); //NON-NLS
            return false;
        }

        return true;
    }

    /**
     * Get the appropriate input stream to read the content of the given
     * AbstractFile.
     *
     * @return an appropriate input stream to read the content of the given
     *         AbstractFile
     *
     * @param sourceFile   The AbstractFile to create an input stream for
     * @param extractUTF8  Should the the stream extract UTF8
     * @param extractUTF16 Should the the stream extract UTF16
     *
     * @return An InputStream for reading the contents of the AbstractFile
     */
    private InputStream getInputStream(AbstractFile sourceFile, boolean extractUTF8, boolean extractUTF16) {
        //check which extract stream to use
        InputStream stringStream = extractScripts.size() == 1 && extractScripts.get(0).equals(SCRIPT.LATIN_1)
                ? new AbstractFileStringStream(sourceFile, INDEX_CHARSET)//optimal for english, english only
                : new AbstractFileStringIntStream(sourceFile, extractScripts, extractUTF8, extractUTF16, INDEX_CHARSET);
        return stringStream;
    }

    @Override
    public boolean isContentTypeSpecific() {
        return false;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        // strings can be run on anything. 
        return true;
    }
}
