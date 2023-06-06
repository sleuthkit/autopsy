/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2023 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.apache.tika.mime.MimeTypes;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.TextViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.keywordsearch.AdHocSearchChildFactory.AdHocQueryResult;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A text viewer that displays the indexed text associated with a file or an
 * artifact, possibly marked up with HTML to highlight keyword hits.
 */
@ServiceProvider(service = TextViewer.class, position = 2)
public class ExtractedTextViewer implements TextViewer {

    private static final Logger logger = Logger.getLogger(ExtractedTextViewer.class.getName());

    private static final BlackboardAttribute.Type TSK_ASSOCIATED_ARTIFACT_TYPE = new BlackboardAttribute.Type(TSK_ASSOCIATED_ARTIFACT);
    private static final BlackboardAttribute.Type TSK_ACCOUNT_TYPE = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE);

    private ExtractedContentPanel panel;
    private volatile Node currentNode = null;
    private IndexedText currentSource = null;
    private FileTypeDetector fileTypeDetector = null;
    
    private long cachedObjId = -1;
    private boolean chachedIsFullyIndexed = false;

    /**
     * Constructs a text viewer that displays the indexed text associated with a
     * file or an artifact, possibly marked up with HTML to highlight keyword
     * hits. If text for the Content has not been fully indexed by Solr then 
     * attempt to extract text using one of text extractors. 
     */
    public ExtractedTextViewer() {
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Failed to initialize FileTypeDetector", ex); //NON-NLS
        }
    }

    /**
     * Sets the node displayed by the text viewer.
     *
     * @param node The node to display
     */
    @Override
    public void setNode(final Node node) {
        // Clear the viewer.
        if (node == null) {
            currentNode = null;
            resetComponent();
            return;
        }

        /*
         * This deals with the known bug with an unknown cause where setNode is
         * sometimes called twice for the same node.
         */
        if (node.equals(currentNode)) {
            return;
        } else {
            currentNode = node;
        }

        /*
         * Assemble a collection of all of the indexed text "sources" for the
         * node.
         */
        List<IndexedText> sources = new ArrayList<>();
        Lookup nodeLookup = node.getLookup();

        /**
         * Pull the search results, file, artifact and report objects (if any)
         * from the lookup.
         */
        AdHocQueryResult adHocQueryResult = nodeLookup.lookup(AdHocQueryResult.class);
        AbstractFile file = nodeLookup.lookup(AbstractFile.class);
        BlackboardArtifact artifact = nodeLookup.lookup(BlackboardArtifact.class);
        Report report = nodeLookup.lookup(Report.class);

        /*
         * First, get text with highlighted hits if this node is for a search
         * result.
         */
        IndexedText highlightedHitText = null;
        if (adHocQueryResult != null) {
            /*
             * The node is an ad hoc search result node.
             */
            highlightedHitText = new HighlightedText(adHocQueryResult.getSolrObjectId(), adHocQueryResult.getResults());
        } else if (artifact != null) {
            if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()) {
                /*
                 * The node is a keyword hit artifact node.
                 */
                try {
                    highlightedHitText = new HighlightedText(artifact);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Failed to create HighlightedText for " + artifact, ex); //NON-NLS
                }
            } else if (artifact.getArtifactTypeID() == TSK_ACCOUNT.getTypeID() && file != null) {
                try {
                    BlackboardAttribute attribute = artifact.getAttribute(TSK_ACCOUNT_TYPE);
                    if (attribute != null && Account.Type.CREDIT_CARD.getTypeName().equals(attribute.getValueString())) {
                        /*
                         * The node is an credit card account node.
                         */
                        highlightedHitText = getAccountsText(file, nodeLookup);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Failed to create AccountsText for " + file, ex); //NON-NLS
                }
            }
        }
        if (highlightedHitText != null) {
            sources.add(highlightedHitText);
        }

        /*
         * Next, add the "raw" (not highlighted) text, if any, for any file
         * associated with the node.
         */
        IndexedText rawContentText = null;
        if (file != null) {

            // see if Solr has fully indexed this file
            if (solrHasFullyIndexedContent(file.getId())) {
                rawContentText = new SolrIndexedText(file, file.getId());
                sources.add(rawContentText);
            } else {
                // Solr does not have fully indexed content. 
                // see if it's a file type for which we can extract text            
                if (ableToExtractTextFromFile(file)) {
                    try {
                        rawContentText = new ExtractedText(file);
                        sources.add(rawContentText);
                    } catch (TextExtractorFactory.NoTextExtractorFound | TextExtractor.InitReaderException ex) {
                        // do nothing
                    }
                }
            }
        }

        /*
         * Add the "raw" (not highlighted) text, if any, for any report
         * associated with the node.
         */
        if (report != null) {
            rawContentText = new SolrIndexedText(report, report.getId());
            sources.add(rawContentText);
        }

        /*
         * Finally, add the "raw" (not highlighted) text, if any, for any
         * artifact associated with the node.
         */
        IndexedText rawArtifactText = null;
        try {
            rawArtifactText = getRawArtifactText(artifact);
            if (rawArtifactText != null) {
                sources.add(rawArtifactText);
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error creating RawText for " + file, ex); //NON-NLS
        }

        // Now set the default source to be displayed.
        if (highlightedHitText != null) {
            currentSource = highlightedHitText;
        } else if (rawArtifactText != null) {
            currentSource = rawArtifactText;
        } else {
            currentSource = rawContentText;
        }

        // Push the text sources into the panel.
        for (IndexedText source : sources) {
            int currentPage = source.getCurrentPage();
            if (currentPage == 0 && source.hasNextPage()) {
                source.nextPage();
            }
        }
        panel.updateControls(currentSource);

        String contentName = "";
        if (file != null) {
            contentName = file.getName();
        }
        setPanel(contentName, sources);

    }

    static private IndexedText getRawArtifactText(BlackboardArtifact artifact) throws TskCoreException, NoCurrentCaseException {
        IndexedText rawArtifactText = null;
        if (null != artifact) {
            /*
             * For keyword hit artifacts, add the text of the artifact that hit,
             * not the hit artifact; otherwise add the text for the artifact.
             */
            if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()
                    || artifact.getArtifactTypeID() == TSK_ACCOUNT.getTypeID()) {

                BlackboardAttribute attribute = artifact.getAttribute(TSK_ASSOCIATED_ARTIFACT_TYPE);
                if (attribute != null) {
                    long artifactId = attribute.getValueLong();
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(artifactId);
                    rawArtifactText = new SolrIndexedText(associatedArtifact, associatedArtifact.getArtifactID());
                }

            } else {
                rawArtifactText = new SolrIndexedText(artifact, artifact.getArtifactID());
            }
        }
        return rawArtifactText;
    }

    static private IndexedText getAccountsText(Content content, Lookup nodeLookup) throws TskCoreException {
        /*
         * get all the credit card artifacts
         */
        //if the node had artifacts in the lookup use them, other wise look up all credit card artifacts for the content.
        Collection<? extends BlackboardArtifact> artifacts = nodeLookup.lookupAll(BlackboardArtifact.class);
        artifacts = (artifacts == null || artifacts.isEmpty())
                ? content.getArtifacts(TSK_ACCOUNT)
                : artifacts;

        return new AccountsText(content.getId(), artifacts);
    }

    private void scrollToCurrentHit() {
        final IndexedText source = panel.getSelectedSource();
        if (source == null || !source.isSearchable()) {
            return;
        }

        panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(source.currentItem()));
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.getTitle");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "ExtractedContentViewer.toolTip");
    }

    @Override
    public TextViewer createInstance() {
        return new ExtractedTextViewer();
    }

    @Override
    public synchronized Component getComponent() {
        if (panel == null) {
            panel = new ExtractedContentPanel();
            panel.addPrevMatchControlListener(new PrevFindActionListener());
            panel.addNextMatchControlListener(new NextFindActionListener());
            panel.addPrevPageControlListener(new PrevPageActionListener());
            panel.addNextPageControlListener(new NextPageActionListener());
            panel.addSourceComboControlListener(new SourceChangeActionListener());
        }
        return panel;
    }

    @Override
    public void resetComponent() {
        panel.resetDisplay();
        currentNode = null;
        currentSource = null;
        panel.updateControls(currentSource);
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        /*
         * If the lookup of the node contains an ad hoc search result object,
         * then there must be indexed text that produced the hit.
         */
        AdHocQueryResult adHocQueryResult = node.getLookup().lookup(AdHocQueryResult.class);
        if (adHocQueryResult != null) {
            return true;
        }

        /*
         * If the lookup of the node contains either a keyword hit artifact or a
         * credit card account artifact from a credit card account numbers
         * search, then there must be indexed text that produced the hit(s).
         */
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        if (artifact != null) {
            final int artifactTypeID = artifact.getArtifactTypeID();
            if (artifactTypeID == TSK_KEYWORD_HIT.getTypeID()) {
                return true;
            } else if (artifactTypeID == TSK_ACCOUNT.getTypeID()) {
                try {
                    BlackboardAttribute attribute = artifact.getAttribute(TSK_ACCOUNT_TYPE);
                    if (attribute != null && Account.Type.CREDIT_CARD.getTypeName().equals(attribute.getValueString())) {
                        return true;
                    }
                } catch (TskCoreException ex) {
                    /*
                     * If there was an error checking the account type, fall
                     * back to the check below for the file associated with the
                     * account (if there is one).
                     */
                    logger.log(Level.SEVERE, "Error getting TSK_ACCOUNT_TYPE attribute from artifact " + artifact.getArtifactID(), ex);
                }
            }
        }

        /*
         * If the lookup of the node contains a file, check to see if there is
         * indexed text for the file. Note that there should be a file in the
         * lookup of all nodes except artifact nodes that are associated with a
         * data source instead of a file.
         */
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        if (file != null) {
            
            // see if Solr has fully indexed this file
            if (solrHasFullyIndexedContent(file.getId())) {
                return true;
            }

            // Solr does not have fully indexed content. 
            // see if it's a file type for which we can extract text            
            if (ableToExtractTextFromFile(file)) {
                return true;
            }
        }

        /*
         * If the lookup of the node contains an artifact that is neither a
         * keyword hit artifact nor a credit card account artifact, and the
         * artifact is not associated with a file, check to see if there is
         * indexed text for the artifact.
         */
        if (artifact != null) {
            return solrHasFullyIndexedContent(artifact.getArtifactID());
        }

        /*
         * If the lookup of the node contains no artifacts or file but does
         * contain a report, check to see if there is indexed text for the
         * report.
         */
        Report report = node.getLookup().lookup(Report.class);
        if (report != null) {
            return solrHasFullyIndexedContent(report.getId());
        }

        /*
         * If the lookup of the node contains neither ad hoc search results, nor
         * artifacts, nor a file, nor a report, there is no indexed text.
         */
        return false;
    }

    @Override
    public int isPreferred(Node node) {
        return 4;
    }

    /**
     * Set the MarkupSources for the panel to display (safe to call even if the
     * panel hasn't been created yet)
     *
     * @param contentName The name of the content to be displayed
     * @param sources     A list of IndexedText that have different 'views' of
     *                    the content.
     */
    private void setPanel(String contentName, List<IndexedText> sources) {
        if (panel != null) {
            panel.setSources(contentName, sources);
        }
    }

    /**
     * Check if Solr has indexed ALL of the content for a given node. Note that
     * in some situations Solr only indexes parts of a file. This happens when
     * an in-line KWS finds a KW hit in the file - only the chunks with the KW
     * hit (+/- 1 chunk) get indexed by Solr. That is not enough for the
     * purposes of this text viewer as we need to display all of the text in the
     * file.
     *
     * @param objectId
     *
     * @return true if Solr has content, else false
     */
    private boolean solrHasFullyIndexedContent(Long objectId) {
        
        // check if we have cached this decision
        if (objectId == cachedObjId) {
            return chachedIsFullyIndexed;
        }
        
        cachedObjId = objectId;
        final Server solrServer = KeywordSearch.getServer();
        if (solrServer.coreIsOpen() == false) {
            chachedIsFullyIndexed = false;
            return chachedIsFullyIndexed;
        }

        // verify that all of the chunks in the file have been indexed.
        try {
            chachedIsFullyIndexed = solrServer.queryIsFullyIndexed(objectId);
            return chachedIsFullyIndexed;
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Error querying Solr server", ex); //NON-NLS
            chachedIsFullyIndexed = false;
            return chachedIsFullyIndexed;
        }
    }

    /**
     * Check if we can extract text for this file type using one of our text extractors. 
     * NOTE: the logic in this method should be similar and based on the 
     * logic of how KeywordSearchIngestModule decides which files to index.
     *
     * @param file Abstract File
     *
     * @return true if text can be extracted from file, else false
     */
    private boolean ableToExtractTextFromFile(AbstractFile file) {

        TskData.TSK_DB_FILES_TYPE_ENUM fileType = file.getType();
        
        if (fileType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
            return false;
        }

        if ((fileType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || fileType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS))
                || (fileType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED))) {
            return false;
        }
        
        final long size = file.getSize();
        if (file.isDir() || size == 0) {
            return false;
        }
        
        String mimeType = fileTypeDetector.getMIMEType(file).trim().toLowerCase();
        
        if (KeywordSearchIngestModule.ARCHIVE_MIME_TYPES.contains(mimeType)) {
            return false;
        }
        
        if (MimeTypes.OCTET_STREAM.equals(mimeType)) {
            return false;
        }
        
        return true;
    }

    /**
     * Listener to select the next match found in the text
     */
    private class NextFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            IndexedText source = panel.getSelectedSource();
            if (source == null) {
                // reset
                panel.updateControls(null);
                return;
            }
            final boolean hasNextItem = source.hasNextItem();
            final boolean hasNextPage = source.hasNextPage();
            int indexVal;
            if (hasNextItem || hasNextPage) {
                if (!hasNextItem) {
                    //flip the page
                    nextPage();
                    indexVal = source.currentItem();
                } else {
                    indexVal = source.nextItem();
                }

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(source.currentItem());
                panel.updateTotaMatcheslDisplay(source.getNumberHits());

                //update controls if needed
                if (!source.hasNextItem() && !source.hasNextPage()) {
                    panel.enableNextMatchControl(false);
                }
                if (source.hasPreviousItem() || source.hasPreviousPage()) {
                    panel.enablePrevMatchControl(true);
                }
            }
        }
    }

    /**
     * Listener to select the previous match found in the text
     */
    private class PrevFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            IndexedText source = panel.getSelectedSource();
            final boolean hasPreviousItem = source.hasPreviousItem();
            final boolean hasPreviousPage = source.hasPreviousPage();
            int indexVal;
            if (hasPreviousItem || hasPreviousPage) {
                if (!hasPreviousItem) {
                    //flip the page
                    previousPage();
                    indexVal = source.currentItem();
                } else {
                    indexVal = source.previousItem();
                }

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(source.currentItem());
                panel.updateTotaMatcheslDisplay(source.getNumberHits());

                //update controls if needed
                if (!source.hasPreviousItem() && !source.hasPreviousPage()) {
                    panel.enablePrevMatchControl(false);
                }
                if (source.hasNextItem() || source.hasNextPage()) {
                    panel.enableNextMatchControl(true);
                }
            }
        }
    }

    /**
     * Listener to update the text displayed based on the source changing
     */
    private class SourceChangeActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            currentSource = panel.getSelectedSource();

            if (currentSource == null) {
                //TODO might need to reset something
                return;
            }

            panel.updateControls(currentSource);
        }
    }

    private void nextPage() {
        // we should never have gotten here -- reset
        if (currentSource == null) {
            panel.updateControls(null);
            return;
        }

        if (currentSource.hasNextPage()) {
            currentSource.nextPage();

            //set new text
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            panel.refreshCurrentMarkup();
            panel.setCursor(null);

            //update display
            panel.updateCurrentPageDisplay(currentSource.getCurrentPage());

            //scroll to current selection
            scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasNextPage()) {
                panel.enableNextPageControl(false);
            }
            if (currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(true);
            }

            panel.updateSearchControls(currentSource);
        }
    }

    private void previousPage() {
        // reset, we should have never gotten here if null
        if (currentSource == null) {
            panel.updateControls(null);
            return;
        }

        if (currentSource.hasPreviousPage()) {
            currentSource.previousPage();

            //set new text
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            panel.refreshCurrentMarkup();
            panel.setCursor(null);

            //update display
            panel.updateCurrentPageDisplay(currentSource.getCurrentPage());

            //scroll to current selection
            scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(false);
            }
            if (currentSource.hasNextPage()) {
                panel.enableNextPageControl(true);
            }

            panel.updateSearchControls(currentSource);

        }
    }

    /**
     * Listener to go to the next page of the text
     */
    private class NextPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            nextPage();
        }
    }

    /**
     * Listener to go to the previous page of the text
     */
    private class PrevPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            previousPage();
        }
    }
}
