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

import java.util.LinkedHashMap;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Display content with just raw text
 *
 */
class RawText implements IndexedText {

    private int numPages = 0;
    private int currentPage = 0;
    private boolean hasChunks = false;
    private final Content content;
    private final BlackboardArtifact blackboardArtifact;
    private final long objectId;
    //keep last content cached
    private String cachedString;
    private int cachedChunk;
    private static final Logger logger = Logger.getLogger(RawText.class.getName());

    /**
     * Construct a new RawText object for the given content and object id. This
     * constructor needs both a content object and an object id because the
     * RawText implementation attempts to provide useful messages in the text
     * content viewer for (a) the case where a file has not been indexed because
     * known files are being skipped and (b) the case where the file content has
     * not yet been indexed.
     *
     * @param content  Used to get access to file names and "known" status.
     * @param objectId Either a file id or an artifact id.
     */
    RawText(Content content, long objectId) {
        this.content = content;
        this.blackboardArtifact = null;
        this.objectId = objectId;
        initialize();
    }

    RawText(BlackboardArtifact bba, long objectId) {
        this.content = null;
        this.blackboardArtifact = bba;
        this.objectId = objectId;
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
        return currentPage < numPages;
    }

    @Override
    public boolean hasPreviousPage() {
        return currentPage > 1;
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
            if (this.content != null) {
                if (hasChunks) {
                    return getIndexedTextForPage(currentPage);
                } else {
                    String msg = NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getSolrContent.noTxtYetMsg", content.getName());
                    return NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getSolrContent.txtBodyItal", msg);
                }
            } else if (this.blackboardArtifact != null) {
                return KeywordSearch.getServer().getSolrContent(this.objectId, 1);
            }
        } catch (SolrServerException | NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get extracted content.", ex); //NON-NLS
        }
        return NbBundle.getMessage(this.getClass(), "RawText.getText.error.msg");
    }

    @Override
    public String toString() {
        return NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.toString");
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
    public LinkedHashMap<Integer, Integer> getHitsPages() {
        return null;
    }

    @Override
    public int getNumberPages() {
        return numPages;
    }

    /**
     * Set the internal values, such as pages and chunks
     */
    private void initialize() {
        final Server solrServer = KeywordSearch.getServer();

        try {
            //add to page tracking if not there yet		
            numPages = solrServer.queryNumFileChunks(this.objectId);
            if (numPages == 0) {
                numPages = 1;
                hasChunks = false;
            } else {
                hasChunks = true;
            }
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not get number of chunks: ", ex); //NON-NLS		
        }
    }

    /**
     * Gets the indexed text from Solr for a given page in the content viewer.
     *
     * @param pageNumber The page number.
     *
     * @return The indexed text for the page.
     *
     * @throws NoOpenCoreException if there is no open Solr core.
     * @throws SolrServerException if there is a problem querying the Solr
     *                             server for the indexed text.
     */
    private String getIndexedTextForPage(int pageNumber) throws SolrServerException, NoOpenCoreException {
        /*
         * Each page displays a single chunk from the Solr document, so the page
         * number is the chunk id.
         */
        int chunkId = pageNumber;

        /*
         * Is the indexed text for this page in the cache?
         */
        if (cachedString != null && cachedChunk == chunkId) {
            return cachedString;
        }

        /*
         * Get the indexed text form the Solr server, format it, and cache it.
         */
        Server solrServer = KeywordSearch.getServer();
        String indexedText = solrServer.getSolrContent(this.objectId, chunkId);
        cachedString = EscapeUtil.escapeHtml(indexedText).trim();
        StringBuilder sb = new StringBuilder(cachedString.length() + 20);
        sb.append("<pre>").append(cachedString).append("</pre>"); //NON-NLS
        cachedString = sb.toString();
        cachedChunk = pageNumber;
        return cachedString;
    }
}
