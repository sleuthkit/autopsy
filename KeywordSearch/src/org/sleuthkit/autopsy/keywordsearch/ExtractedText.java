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

/** ELTODO
 * A "source" for the extracted abstractFile viewer that displays "raw" (not
 * highlighted) indexed text for a file or an artifact.
 */
class ExtractedText implements IndexedText {

    private int numPages = 0;
    private int currentPage = 0;
    private final AbstractFile abstractFile;
    private final long objectId;
    private Chunker chunker = null;
    private static final Logger logger = Logger.getLogger(ExtractedText.class.getName());

    /**
     * Construct a new ExtractedText object for the given content and object id.
     * This constructor needs both a content object and an object id because the
     * ExtractedText implementation attempts to provide useful messages in the
     * text content viewer for (a) the case where a file has not been indexed
     * because known files are being skipped and (b) the case where the file
     * content has not yet been indexed.
     *
     * @param file     Abstract file.
     * @param objectId Either a file id or an artifact id.
     */
    ExtractedText(AbstractFile file, long objectId) throws TextExtractorFactory.NoTextExtractorFound, TextExtractor.InitReaderException {
        this.abstractFile = file;
        this.objectId = objectId;
        this.numPages = -1; // We don't know how many pages there are until we reach end of the document
        initialize();
    }

    /**
     * Return the ID that this object is associated with -- to help with caching
     *
     * @return
     */
    public long getObjectId() {
        return this.objectId;
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
     * Set the internal values, such as pages
     */
    private void initialize() throws TextExtractorFactory.NoTextExtractorFound, TextExtractor.InitReaderException {
        TextExtractor extractor = TextExtractorFactory.getExtractor(abstractFile, null);

        Map<String, String> extractedMetadata = new HashMap<>();
        Reader sourceReader = getTikaOrTextExtractor(extractor, abstractFile, extractedMetadata);

        //Get a reader for the content of the given source
        BufferedReader reader = new BufferedReader(sourceReader);
        chunker = new Chunker(reader);
    }

    /**
     * Extract text from abstractFile
     *
     * @param node        a node that has extracted abstractFile
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
                // Creating the metadata artifact here causes occasional problems
                // when indexing the text, so we save the metadata map to 
                // use after this method is complete.
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
        //divide into chunks and index
        return finalReader;

    }

}
