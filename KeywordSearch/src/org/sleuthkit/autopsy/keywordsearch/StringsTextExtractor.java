/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
 * Takes an AbstractFile, extract strings, converts into chunks (associated with
 * the original source file) up to 1MB then and indexes chunks as text with Solr
 */
class StringsTextExtractor implements TextExtractor {

    private static Ingester ingester;
    private static final Logger logger = Logger.getLogger(StringsTextExtractor.class.getName());
    private static final long MAX_STRING_CHUNK_SIZE = 1 * 31 * 1024L;
    //private static final int BOM_LEN = 3; 
    private static final int BOM_LEN = 0;  //disabled prepending of BOM
    private static final Charset INDEX_CHARSET = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    private static final SCRIPT DEFAULT_SCRIPT = SCRIPT.LATIN_2;
    private AbstractFile sourceFile;
    private int numChunks = 0;
    private final List<SCRIPT> extractScripts = new ArrayList<>();
    private Map<String, String> extractOptions = new HashMap<>();

    //disabled prepending of BOM
    //static {
    //prepend UTF-8 BOM to start of the buffer
    //stringChunkBuf[0] = (byte) 0xEF;
    //stringChunkBuf[1] = (byte) 0xBB;
    //stringChunkBuf[2] = (byte) 0xBF;
    //}
    public StringsTextExtractor() {
        ingester = Server.getIngester();
        extractScripts.add(DEFAULT_SCRIPT);
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
        boolean success = false;

        final boolean extractUTF8
                = Boolean.parseBoolean(extractOptions.get(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString()));

        final boolean extractUTF16
                = Boolean.parseBoolean(extractOptions.get(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString()));

        if (extractUTF8 == false && extractUTF16 == false) {
            //nothing to do
            return true;
        }

        InputStream stringStream;
        //check which extract stream to use
        if (extractScripts.size() == 1 && extractScripts.get(0).equals(SCRIPT.LATIN_1)) {
            //optimal for english, english only
            stringStream = new AbstractFileStringStream(sourceFile, INDEX_CHARSET);
        } else {
            stringStream = new AbstractFileStringIntStream(
                    sourceFile, extractScripts, extractUTF8, extractUTF16, INDEX_CHARSET);
        }

        try {
            success = true;
            //break input stream into chunks 

            final byte[] stringChunkBuf = new byte[(int) MAX_STRING_CHUNK_SIZE];
            long readSize;
            while ((readSize = stringStream.read(stringChunkBuf, BOM_LEN, (int) MAX_STRING_CHUNK_SIZE - BOM_LEN)) != -1) {
                if (context.fileIngestIsCancelled()) {
                    ingester.ingest(this);
                    return true;
                }
                //FileOutputStream debug = new FileOutputStream("c:\\temp\\" + sourceFile.getName() + Integer.toString(this.numChunks+1));
                //debug.write(stringChunkBuf, 0, (int)readSize);

                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);

                try {
                    chunk.index(ingester, stringChunkBuf, readSize + BOM_LEN, INDEX_CHARSET);
                    ++this.numChunks;
                } catch (IngesterException ingEx) {
                    success = false;
                    logger.log(Level.WARNING, "Ingester had a problem with extracted strings from file '" + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx); //NON-NLS
                    throw ingEx; //need to rethrow/return to signal error and move on
                }

                //debug.close();    
            }

            //after all chunks, ingest the parent file without content itself, and store numChunks
            ingester.ingest(this);

        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read input stream to divide and send to Solr, file: " + sourceFile.getName(), ex); //NON-NLS
            success = false;
        } finally {
            try {
                stringStream.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error closing input stream stream, file: " + sourceFile.getName(), ex); //NON-NLS
            }
        }

        return success;
    }

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
        // strings can be run on anything. 
        return true;
    }
}
