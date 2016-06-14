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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskData;

/**
 * A "source" for the extracted content viewer that displays "raw" (not
 * highlighted) indexed text for a file or an artifact.
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
                return getContentText(currentPage, hasChunks);
            } else if (this.blackboardArtifact != null) {
                return getArtifactText();
            }
        } catch (SolrServerException ex) {
            logger.log(Level.SEVERE, "Couldn't get extracted content", ex); //NON-NLS
        } catch (KeywordSearchSettingsManager.KeywordSearchSettingsManagerException ex) {
            logger.log(Level.SEVERE, "Couldn't read settings", ex); //NON-NLS
        }
        return NbBundle.getMessage(this.getClass(), "RawText.getText.error.msg");
    }

    @NbBundle.Messages({
        "RawText.FileText=File Text",
        "RawText.ResultText=Result Text"})
    @Override
    public String toString() {
        if (null != content) {
            return Bundle.RawText_FileText();
        } else {
            return Bundle.RawText_ResultText();
        }
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
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Could not get number of chunks: ", ex); //NON-NLS		

        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not get number of chunks: ", ex); //NON-NLS		
        }
    }

    /**
     * Get extracted content for a node from Solr
     *
     * @param node        a node that has extracted content in Solr (check with
     *                    solrHasContent(ContentNode))
     * @param currentPage currently used page
     * @param hasChunks   true if the content behind the node has multiple
     *                    chunks. This means we need to address the content
     *                    pages specially.
     *
     * @return the extracted content
     *
     * @throws SolrServerException if something goes wrong
     */
    private String getContentText(int currentPage, boolean hasChunks) throws SolrServerException, KeywordSearchSettingsManager.KeywordSearchSettingsManagerException {
        final Server solrServer = KeywordSearch.getServer();
        KeywordSearchSettingsManager manager = KeywordSearchSettingsManager.getInstance();

        if (hasChunks == false) {
            //if no chunks, it is safe to assume there is no text content
            //because we are storing extracted text in chunks only
            //and the non-chunk stores meta-data only
            String name = content.getName();
            String msg = null;
            if (content instanceof AbstractFile) {
                //we know it's AbstractFile, but do quick check to make sure if we index other objects in future
                boolean isKnown = TskData.FileKnown.KNOWN.equals(((AbstractFile) content).getKnown());
                if (isKnown && manager.getSkipKnown()) {
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
            String indexedText = solrServer.getSolrContent(this.objectId, chunkId);
            cachedString = EscapeUtil.escapeHtml(indexedText).trim();
            StringBuilder sb = new StringBuilder(cachedString.length() + 20);
            sb.append("<pre>").append(cachedString).append("</pre>"); //NON-NLS
            cachedString = sb.toString();
            cachedChunk = chunkId;
        } catch (NoOpenCoreException ex) {
            logger.log(Level.SEVERE, "No open core", ex); //NON-NLS
            return "";
        }
        return cachedString;
    }

    private String getArtifactText() throws SolrServerException {
        try {
            String indexedText = KeywordSearch.getServer().getSolrContent(this.objectId, 1);
            indexedText = EscapeUtil.escapeHtml(indexedText).trim();
            StringBuilder sb = new StringBuilder(indexedText.length() + 20);
            sb.append("<pre>").append(indexedText).append("</pre>"); //NON-NLS
            return sb.toString();
        } catch (NoOpenCoreException ex) {
            logger.log(Level.SEVERE, "No open core", ex); //NON-NLS
            return "";
        }
    }

}
