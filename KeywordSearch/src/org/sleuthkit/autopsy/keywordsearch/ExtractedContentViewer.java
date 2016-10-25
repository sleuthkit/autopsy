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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A content viewer that displays the indexed text associated with a file or an
 * artifact, possibly marked up with HTML to highlight keyword hits.
 */
@ServiceProvider(service = DataContentViewer.class, position = 4)
public class ExtractedContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(ExtractedContentViewer.class.getName());

    private static final long INVALID_DOCUMENT_ID = 0L;
    private static final BlackboardAttribute.Type TSK_ASSOCIATED_ARTIFACT_TYPE = new BlackboardAttribute.Type(TSK_ASSOCIATED_ARTIFACT);

    private ExtractedContentPanel panel;
    private volatile Node currentNode = null;
    private IndexedText currentSource = null;

    /**
     * Constructs a content viewer that displays the indexed text associated
     * with a file or an artifact, possibly marked up with HTML to highlight
     * keyword hits.
     */
    public ExtractedContentViewer() {
    }

    /**
     * Sets the node displayed by the content viewer.
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
        if (node == currentNode) {
            return;
        } else {
            currentNode = node;
        }

        Lookup nodeLookup = node.getLookup();
        Content content = nodeLookup.lookup(Content.class);


        /*
         * Assemble a collection of all of the indexed text "sources" associated
         * with the node.
         */
        List<IndexedText> sources = new ArrayList<>();
        IndexedText highlightedHitText = null;
        IndexedText rawContentText = null;
        IndexedText rawArtifactText = null;

        /*
         * First add the text marked up with HTML to highlight keyword hits that
         * will be present in the selected node's lookup if the node is for a
         * keyword hit artifact or account.
         */
        sources.addAll(nodeLookup.lookupAll(IndexedText.class));

        if (!sources.isEmpty()) {
            //if the look up had any sources use them and don't make a new one.
            highlightedHitText = sources.get(0);
        } else if (null != content && solrHasContent(content.getId())) {//if the lookup didn't have any sources, and solr has indexed the content...
            /*
             * get all the credit card artifacts and make a AccountsText object
             * that will highlight them.
             */
            String solrDocumentID = String.valueOf(content.getId()); //grab the object id as the solrDocumentID
            Set<String> accountNumbers = new HashSet<>();
            try {
                //if the node had artifacts in the lookup use them, other wise look up all credit card artifacts for the content.
                Collection<? extends BlackboardArtifact> artifacts = nodeLookup.lookupAll(BlackboardArtifact.class);
                artifacts = (artifacts == null || artifacts.isEmpty())
                        ? content.getArtifacts(TSK_ACCOUNT)
                        : artifacts;

                /*
                 * For each artifact add the account number to the list of
                 * accountNumbers to highlight, and use the solrDocumentId
                 * attribute(in place of the content's object Id) if it exists
                 *
                 * NOTE: this assumes all the artifacts will be from the same
                 * solrDocumentId
                 */
                for (BlackboardArtifact artifact : artifacts) {
                    try {
                        BlackboardAttribute solrIDAttr = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID));
                        if (solrIDAttr != null) {
                            String valueString = solrIDAttr.getValueString();
                            if (StringUtils.isNotBlank(valueString)) {
                                solrDocumentID = valueString;
                            }
                        }

                        BlackboardAttribute keyWordAttr = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CARD_NUMBER));
                        if (keyWordAttr != null) {
                            String valueString = keyWordAttr.getValueString();
                            if (StringUtils.isNotBlank(valueString)) {
                                accountNumbers.add(valueString);
                            }
                        }

                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Failed to retrieve Blackboard Attributes", ex); //NON-NLS
                    }
                }
                if (accountNumbers.isEmpty() == false) {
                    highlightedHitText = new AccountsText(solrDocumentID, accountNumbers);
                    sources.add(highlightedHitText);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to retrieve Blackboard Artifacts", ex); //NON-NLS
            }
        }

        /*
         * Next, add the "raw" (not highlighted) text, if any, for any content
         * associated with the node.
         */
        if (null != content && solrHasContent(content.getId())) {
            rawContentText = new RawText(content, content.getId());
            sources.add(rawContentText);
        }

        /*
         * Finally, add the "raw" (not highlighted) text, if any, for any
         * artifact associated with the node.
         */
        BlackboardArtifact artifact = nodeLookup.lookup(BlackboardArtifact.class);
        if (null != artifact) {
            /*
             * For keyword hit artifacts, add the text of the artifact that hit,
             * not the hit artifact; otherwise add the text for the artifact.
             */
            if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID() || artifact.getArtifactTypeID() == TSK_ACCOUNT.getTypeID()) {
                try {
                    BlackboardAttribute attribute = artifact.getAttribute(TSK_ASSOCIATED_ARTIFACT_TYPE);
                    if (attribute != null) {
                        long artifactId = attribute.getValueLong();
                        BlackboardArtifact associatedArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(artifactId);
                        rawArtifactText = new RawText(associatedArtifact, associatedArtifact.getArtifactID());
                        sources.add(rawArtifactText);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting associated artifact attributes", ex); //NON-NLS
                }
            } else {
                rawArtifactText = new RawText(artifact, artifact.getArtifactID());
                sources.add(rawArtifactText);
            }

        }

        // Now set the default source to be displayed.
        if (null != highlightedHitText) {
            currentSource = highlightedHitText;
        } else if (null != rawContentText) {
            currentSource = rawContentText;
        } else {
            currentSource = rawArtifactText;
        }

        // Push the text sources into the panel.
        for (IndexedText source : sources) {
            int currentPage = source.getCurrentPage();
            if (currentPage == 0 && source.hasNextPage()) {
                source.nextPage();
            }
        }
        updatePageControls();
        setPanel(sources);
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
    public DataContentViewer createInstance() {
        return new ExtractedContentViewer();
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
        setPanel(new ArrayList<>());
        panel.resetDisplay();
        currentNode = null;
        currentSource = null;
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        /**
         * Is there any marked up indexed text in the look up of this node? This
         * will be the case if the node is for a keyword hit artifact produced
         * by either an ad hoc keyword search result (keyword search toolbar
         * widgets) or a keyword search by the keyword search ingest module.
         */
        Collection<? extends IndexedText> sources = node.getLookup().lookupAll(IndexedText.class);
        if (sources.isEmpty() == false) {
            return true;
        }

        /*
         * Is there a credit card artifact in the lookup
         */
        Collection<? extends BlackboardArtifact> artifacts = node.getLookup().lookupAll(BlackboardArtifact.class);
        if (artifacts != null) {
            for (BlackboardArtifact art : artifacts) {
                if (art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                    return true;
                }
            }
        }

        /*
         * No highlighted text for a keyword hit, so is there any indexed text
         * at all for this node?
         */
        long documentID = getDocumentId(node);
        if (INVALID_DOCUMENT_ID == documentID) {
            return false;
        }

        return solrHasContent(documentID);
    }

    @Override
    public int isPreferred(Node node) {
        BlackboardArtifact art = node.getLookup().lookup(BlackboardArtifact.class);

        if (art == null) {
            return 4;
        } else if (art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
                || art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
            return 6;
        } else {
            return 4;
        }
    }

    /**
     * Set the MarkupSources for the panel to display (safe to call even if the
     * panel hasn't been created yet)
     *
     * @param sources
     */
    private void setPanel(List<IndexedText> sources) {
        if (panel != null) {
            panel.setSources(sources);
        }
    }

    /**
     * Check if Solr has extracted content for a given node
     *
     * @param objectId
     *
     * @return true if Solr has content, else false
     */
    private boolean solrHasContent(Long objectId) {
        final Server solrServer = KeywordSearch.getServer();
        try {
            return solrServer.queryIsIndexed(objectId);
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Error querying Solr server", ex); //NON-NLS
            return false;
        }
    }

    /**
     * Gets the object ID to use as the document ID for accessing any indexed
     * text for the given node.
     *
     * @param node The node.
     *
     * @return The document ID or zero, which is an invalid document ID.
     */
    private Long getDocumentId(Node node) {
        /**
         * If the node is a Blackboard artifact node for anything other than a
         * keyword hit, the document ID for the text extracted from the artifact
         * (the concatenation of its attributes) is the artifact ID, a large,
         * negative integer. If it is a keyword hit, see if there is an
         * associated artifact. If there is, get the associated artifact's ID
         * and return it.
         */
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        if (null != artifact) {
            if (artifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                return artifact.getArtifactID();
            } else {
                try {
                    // Get the associated artifact attribute and return its value as the ID
                    BlackboardAttribute blackboardAttribute = artifact.getAttribute(TSK_ASSOCIATED_ARTIFACT_TYPE);
                    if (blackboardAttribute != null) {
                        return blackboardAttribute.getValueLong();
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting associated artifact attributes", ex); //NON-NLS
                }
            }
        }

        /*
         * For keyword search hit artifact nodes and all other nodes, the
         * document ID for the extracted text is the ID of the associated
         * content, if any, unless there is an associated artifact, which is
         * handled above.
         */
        Content content = node.getLookup().lookup(Content.class);
        if (content != null) {
            return content.getId();
        }

        /*
         * No extracted text, return an invalid docuemnt ID.
         */
        return 0L;
    }

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
            int indexVal = 0;
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

    private class PrevFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            IndexedText source = panel.getSelectedSource();
            final boolean hasPreviousItem = source.hasPreviousItem();
            final boolean hasPreviousPage = source.hasPreviousPage();
            int indexVal = 0;
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

    private class SourceChangeActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {

            currentSource = panel.getSelectedSource();

            if (currentSource == null) {
                //TODO might need to reset something
                return;
            }

            updatePageControls();
            updateSearchControls();

        }
    }

    private void updateSearchControls() {
        panel.updateSearchControls(currentSource);
    }

    private void updatePageControls() {
        panel.updateControls(currentSource);
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
            ExtractedContentViewer.this.scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasNextPage()) {
                panel.enableNextPageControl(false);
            }
            if (currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(true);
            }

            updateSearchControls();
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
            ExtractedContentViewer.this.scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(false);
            }
            if (currentSource.hasNextPage()) {
                panel.enableNextPageControl(true);
            }

            updateSearchControls();

        }
    }

    class NextPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            nextPage();
        }
    }

    private class PrevPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            previousPage();
        }
    }
}
