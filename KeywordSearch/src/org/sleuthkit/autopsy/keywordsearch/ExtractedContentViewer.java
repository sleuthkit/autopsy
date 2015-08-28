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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datamodel.TextMarkupLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Displays marked-up (HTML) content for a Node. The sources are all the
 * MarkupSource items in the selected Node's lookup, plus the content that Solr
 * extracted (if there is any).
 */
@ServiceProvider(service = DataContentViewer.class, position = 4)
public class ExtractedContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(ExtractedContentViewer.class.getName());
    private ExtractedContentPanel panel;
    private volatile Node currentNode = null;
    private TextMarkup currentSource = null;
    private final IsDirVisitor isDirVisitor = new IsDirVisitor();

    public ExtractedContentViewer() {
        logger.log(Level.INFO, "Created TextView instance: " + this); //NON-NLS
    }

    @Override
    public void setNode(final Node selectedNode) {
        // to clear the viewer
        if (selectedNode == null) {
            currentNode = null;
            resetComponent();
            return;
        }

        //TODO why setNode() is called twice for the same node sometimes (when selected in dir tree first)
        //for now, do not update second time
        if (selectedNode == currentNode) {
            return;
        } else {
            currentNode = selectedNode;
        }

        /*
         * Sources contain implementations that will markup the text in
         * different ways. The original behavior for this was a source for the
         * text markedup by SOLR and another that just displayed raw text.
         */
        final List<TextMarkup> sources = new ArrayList<TextMarkup>();

        // See if the node has any sources attached to it and add them to our
        // internal list
        sources.addAll(selectedNode.getLookup().lookupAll(TextMarkup.class));

        // Q: Can this be moved up? Is is possible to have "sources" when the
        // node doesn't have a content object or the content size is 0?
        Content content = selectedNode.getLookup().lookup(Content.class);
        if (content == null || content.getSize() == 0) {
            setPanel(sources);
            return;
        }

        long objectId = getDocumentId(selectedNode);
        boolean isDir = content.accept(isDirVisitor);

        if (!isDir && solrHasContent(objectId) == false) {
            setPanel(sources);
            return;
        }

        // make a new source for the raw content
        TextMarkup rawSource = new RawTextMarkup(content, objectId);

        currentSource = rawSource;
        sources.add(rawSource);

        //init pages
        int currentPage = currentSource.getCurrentPage();
        if (currentPage == 0 && currentSource.hasNextPage()) {
            currentSource.nextPage();
        }
        updatePageControls();

        // first source will be the default displayed
        setPanel(sources);
    }

    private void scrollToCurrentHit() {
        final TextMarkup source = panel.getSelectedSource();
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
        setPanel(new ArrayList<TextMarkup>());
        panel.resetDisplay();
        currentNode = null;
        currentSource = null;
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        // see if the node has a MarkupSource object in it
        // BC @@@ This seems to be added from the upper right search.
        Collection<? extends TextMarkup> sources = node.getLookup().lookupAll(TextMarkup.class);
        if (sources.isEmpty() == false) {
            return true;
        }

        // see if the node has a Highlight object in it.  
        // BC @@@ This seems to be added by BlackboardArtifactNode from the tree
        if (node.getLookup().lookup(TextMarkupLookup.class) != null) {
            return true;
        }

        return solrHasContent(getDocumentId(node));
    }

    @Override
    public int isPreferred(Node node) {
        BlackboardArtifact art = node.getLookup().lookup(BlackboardArtifact.class);

        if (art == null) {
            return 4;
        } else if (art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
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
    private void setPanel(List<TextMarkup> sources) {
        if (panel != null) {
            panel.setSources(sources);
        }
    }

    private class IsDirVisitor extends ContentVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(Content cntnt) {
            return false;
        }

        @Override
        public Boolean visit(Directory d) {
            return true;
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
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't determine whether content is supported.", ex); //NON-NLS
            return false;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Couldn't determine whether content is supported.", ex); //NON-NLS
            return false;
        }
    }

    /**
     * Get the correct document id for the given node. If the node contains a
     * HighlightedTextMarkup object, its object id will have been set. Otherwise
     * the document id is obtained from the Content object.
     *
     * @param node
     *
     * @return Either the artifact id, file id or 0.
     */
    private Long getDocumentId(Node node) {
        HighlightedTextMarkup markup = node.getLookup().lookup(HighlightedTextMarkup.class);

        if (markup != null) {
            return markup.getObjectId();
        }

        Content content = node.getLookup().lookup(Content.class);

        if (content != null) {
            return content.getId();
        }

        return 0L;
    }

    private class NextFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            TextMarkup source = panel.getSelectedSource();
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
            TextMarkup source = panel.getSelectedSource();
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
