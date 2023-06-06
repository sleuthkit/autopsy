/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A "source" for abstractFile viewer that displays "raw" extracted text for a
 * file. Only supports file types for which there are text extractors. Uses
 * chunking algorithm used by KeywordSearchIngestModule. The readers used in
 * chunking don't have ability to go backwards or to fast forward to a specific
 * offset. Therefore there is no way to scroll pages back, or to determine how
 * many total pages there are.
 */
class ExtractedText implements IndexedText {

    private int numPages = 0;
    private int currentPage = 0;
    private final AbstractFile abstractFile;
    private Chunker chunker = null;
    private static final Logger logger = Logger.getLogger(ExtractedText.class.getName());

    /**
     * Construct a new ExtractedText object for the given abstract file.
     *
     * @param file Abstract file.
     */
    ExtractedText(AbstractFile file) throws TextExtractorFactory.NoTextExtractorFound, TextExtractor.InitReaderException {
        this.abstractFile = file;
        this.numPages = -1; // We don't know how many pages there are until we reach end of the document
        
        TextExtractor extractor = TextExtractorFactory.getExtractor(abstractFile, null);

        Map<String, String> extractedMetadata = new HashMap<>();
        Reader sourceReader = getTikaOrTextExtractor(extractor, abstractFile, extractedMetadata);

        //Get a reader for the content of the given source
        BufferedReader reader = new BufferedReader(sourceReader);
        this.chunker = new Chunker(reader);
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public boolean hasNextPage() {
        if (chunker.hasNext()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean hasPreviousPage() {
        return false;
    }

    @Override
    public int nextPage() {
        if (!hasNextPage()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.nextPage.exception.msg"));
        }
        ++currentPage;
        return currentPage;
    }

    @Override
    public int previousPage() {
        if (!hasPreviousPage()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.previousPage.exception.msg"));
        }
        --currentPage;
        return currentPage;
    }

    @Override
    public boolean hasNextItem() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.hasNextItem.exception.msg"));
    }

    @Override
    public boolean hasPreviousItem() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.hasPreviousItem.exception.msg"));
    }

    @Override
    public int nextItem() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.nextItem.exception.msg"));
    }

    @Override
    public int previousItem() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.previousItem.exception.msg"));
    }

    @Override
    public int currentItem() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.currentItem.exception.msg"));
    }

    @Override
    public String getText() {
        try {
            return getContentText(currentPage);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Couldn't get extracted text", ex); //NON-NLS
        }
        return Bundle.IndexedText_errorMessage_errorGettingText();
    }

    @NbBundle.Messages({
        "ExtractedText.FileText=File Text"})
    @Override
    public String toString() {
        return Bundle.ExtractedText_FileText();
    }

    @Override
    public boolean isSearchable() {
        return false;
    }

    @Override
    public String getAnchorPrefix() {
        return "";
    }

    @Override
    public int getNumberHits() {
        return 0;
    }

    @Override
    public int getNumberPages() {
        return numPages;
    }

    /**
     * Extract text from abstractFile
     *
     * @param currentPage currently used page
     *
     * @return the extracted text
     */
    private String getContentText(int currentPage) throws TextExtractor.InitReaderException, IOException, Exception {
        String indexedText;
        if (chunker.hasNext()) {
            Chunker.Chunk chunk = chunker.next();
            chunk.setChunkId(currentPage);

            if (chunker.hasException()) {
                logger.log(Level.WARNING, "Error chunking content from " + abstractFile.getId() + ": " + abstractFile.getName(), chunker.getException());
                throw chunker.getException();
            }

            indexedText = chunk.toString();
        } else {
            return Bundle.IndexedText_errorMessage_errorGettingText();
        }

        indexedText = EscapeUtil.escapeHtml(indexedText).trim();
        StringBuilder sb = new StringBuilder(indexedText.length() + 20);
        sb.append("<pre>").append(indexedText).append("</pre>"); //NON-NLS
        return sb.toString();
    }

    private Reader getTikaOrTextExtractor(TextExtractor extractor, AbstractFile aFile,
            Map<String, String> extractedMetadata) throws TextExtractor.InitReaderException {

        Reader fileText = extractor.getReader();
        Reader finalReader;
        try {
            Map<String, String> metadata = extractor.getMetadata();
            if (!metadata.isEmpty()) {
                // save the metadata map to use after this method is complete.
                extractedMetadata.putAll(metadata);
            }
            CharSource formattedMetadata = KeywordSearchIngestModule.getMetaDataCharSource(metadata);
            //Append the metadata to end of the file text
            finalReader = CharSource.concat(new CharSource() {
                //Wrap fileText reader for concatenation
                @Override
                public Reader openStream() throws IOException {
                    return fileText;
                }
            }, formattedMetadata).openStream();
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Could not format extracted metadata for file %s [id=%d]",
                    aFile.getName(), aFile.getId()), ex);
            //Just send file text.
            finalReader = fileText;
        }
        //divide into chunks
        return finalReader;
    }

}
