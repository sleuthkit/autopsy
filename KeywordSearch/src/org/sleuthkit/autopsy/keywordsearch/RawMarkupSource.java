/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskData;

/**
 * Display content with just raw text, no markup
 *
 */
class RawMarkupSource implements MarkupSource {
    private int numPages = 0;
    private int currentPage = 0;
    private boolean hasChunks = false;
    
    private Content currentContent;
    //keep last content cached
    private String cachedString;
    private int cachedChunk;
    private static final Logger logger = Logger.getLogger(RawMarkupSource.class.getName());

    RawMarkupSource(Content content) {
        currentContent = content;
        initialize();
    }
    
    /**
     * Return the ID that this object is associated with -- to help with caching
     * @return 
     */
    public long getContentId() {
        return currentContent.getId();
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
    public String getMarkup() {
        try {
            return getSolrContent(currentPage, hasChunks);
        } catch (SolrServerException ex) {
            logger.log(Level.WARNING, "Couldn't get extracted content.", ex);
            return "";
        }
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
            final long contentID = currentContent.getId();
            numPages = solrServer.queryNumFileChunks(contentID);
            if (numPages == 0) {
                numPages = 1;
                hasChunks = false;
            } else {
                hasChunks = true;
            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Could not get number of chunks: ", ex);

        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not get number of chunks: ", ex);
        }
    }


    /**
     * Get extracted content for a node from Solr
     *
     * @param node a node that has extracted content in Solr (check with
     * solrHasContent(ContentNode))
     * @param currentPage currently used page
     * @param hasChunks true if the content behind the node has multiple chunks.
     * This means we need to address the content pages specially.
     * @return the extracted content
     * @throws SolrServerException if something goes wrong
     */
    private String getSolrContent(int currentPage, boolean hasChunks) throws SolrServerException {
        final Server solrServer = KeywordSearch.getServer();

        if (hasChunks == false) {
            //if no chunks, it is safe to assume there is no text content
            //because we are storing extracted text in chunks only
            //and the non-chunk stores meta-data only
            String name = currentContent.getName();
            String msg = null;
            if (currentContent instanceof AbstractFile) {
                //we know it's AbstractFile, but do quick check to make sure if we index other objects in future
                boolean isKnown = TskData.FileKnown.KNOWN.equals(((AbstractFile) currentContent).getKnown());
                if (isKnown && KeywordSearchSettings.getSkipKnown()) {
                    msg = NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getSolrContent.knownFileMsg", name);
                }
            }
            if (msg == null) {
                msg = NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getSolrContent.noTxtYetMsg", name);
            }
            String htmlMsg = NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getSolrContent.txtBodyItal", msg);
            return htmlMsg;
        }

        int chunkId = currentPage;
        
        //check if cached
        if (cachedString != null) {
            if (cachedChunk == chunkId) {
                return cachedString;
            }
        }

        //not cached
        try {
            String content = solrServer.getSolrContent(currentContent, chunkId);
            if (content == null) {
                
            }
            cachedString = EscapeUtil.escapeHtml(content).trim();
            StringBuilder sb = new StringBuilder(cachedString.length() + 20);
            sb.append("<pre>").append(cachedString).append("</pre>");
            cachedString = sb.toString();
            cachedChunk = chunkId;
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get text content.", ex);
            return "";
        }
        return cachedString;
    }
}
