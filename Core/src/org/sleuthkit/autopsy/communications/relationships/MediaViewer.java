/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import static javax.swing.SwingUtilities.isDescendingFrom;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * A Panel that shows the media (thumbnails) for the selected account.
 */
final class MediaViewer extends JPanel implements RelationshipsViewer, ExplorerManager.Provider, Lookup.Provider {

    private static final Logger logger = Logger.getLogger(MediaViewer.class.getName());
    private static final long serialVersionUID = 1L;

    private final ExplorerManager tableEM = new ExplorerManager();
    private PropertyChangeListener focusPropertyListener;

    private final ModifiableProxyLookup proxyLookup;

    private final MessageDataContent contentViewer;

    private MediaViewerWorker worker;
    private SelectionWorker selectionWorker;

    @Messages({
        "MediaViewer_Name=Media Attachments"
    })
    /**
     * Creates new form ThumbnailViewer
     */
    MediaViewer() {
        initComponents();

        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);

        contentViewer = new MessageDataContent();
        contentViewer.setPreferredSize(new java.awt.Dimension(450, 400));
        splitPane.setRightComponent(contentViewer);

        proxyLookup = new ModifiableProxyLookup(createLookup(tableEM, getActionMap()));

        tableEM.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                handleNodeSelectionChange();
            }
        });

        thumbnailViewer.resetComponent();
    }

    @Override
    public String getDisplayName() {
        return Bundle.MediaViewer_Name();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        contentViewer.setNode(null);
        thumbnailViewer.setNode(null);

        if (worker != null) {
            worker.cancel(true);
        }
        
        if(selectionWorker != null) {
            selectionWorker.cancel(true);
        }

        worker = new MediaViewerWorker(info);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return tableEM;
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }

    @Override
    public void addNotify() {
        super.addNotify();

        if (focusPropertyListener == null) {
            // See org.sleuthkit.autopsy.timeline.TimeLineTopComponent for a detailed
            // explaination of focusPropertyListener
            focusPropertyListener = (final PropertyChangeEvent focusEvent) -> {
                if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                    handleFocusChange((Component) focusEvent.getNewValue());
                }
            };

        }
        //add listener that maintains correct selection in the Global Actions Context
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusPropertyListener);
    }

    /**
     * Handle the switching of the proxyLookup due to focus change.
     *
     * @param newFocusOwner
     */
    private void handleFocusChange(Component newFocusOwner) {
        if (newFocusOwner == null) {
            return;
        }
        if (isDescendingFrom(newFocusOwner, contentViewer)) {
            //if the focus owner is within the MessageContentViewer (the attachments table)
            proxyLookup.setNewLookups(createLookup(contentViewer.getExplorerManager(), getActionMap()));
        } else if (isDescendingFrom(newFocusOwner, this)) {
            //... or if it is within the Results table.
            proxyLookup.setNewLookups(createLookup(tableEM, getActionMap()));

        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();

        if (focusPropertyListener != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removePropertyChangeListener("focusOwner", focusPropertyListener);
        }
    }

    /**
     * Handle the change in thumbnail node selection.
     */
    private void handleNodeSelectionChange() {
        final Node[] nodes = tableEM.getSelectedNodes();
        contentViewer.setNode(null);
        
        if(selectionWorker != null) {
            selectionWorker.cancel(true);
        }

        if (nodes != null && nodes.length == 1) {
            AbstractContent thumbnail = nodes[0].getLookup().lookup(AbstractContent.class);
            if (thumbnail != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                selectionWorker = new SelectionWorker(thumbnail);
                selectionWorker.execute();
            }
        }
    }

    /**
     * A SwingWorker to get the artifact associated with the selected thumbnail.
     */
    private class SelectionWorker extends SwingWorker<BlackboardArtifact, Void> {

        private final AbstractContent thumbnail;

        // Construct a SelectionWorker.
        SelectionWorker(AbstractContent thumbnail) {
            this.thumbnail = thumbnail;
        }

        @Override
        protected BlackboardArtifact doInBackground() throws Exception {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            List<BlackboardArtifact> artifactsList = skCase.getBlackboardArtifacts(TSK_ASSOCIATED_OBJECT, thumbnail.getId());
            for (BlackboardArtifact contextArtifact : artifactsList) {
                BlackboardAttribute associatedArtifactAttribute = contextArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (associatedArtifactAttribute != null) {
                    long artifactId = associatedArtifactAttribute.getValueLong();
                    return contextArtifact.getSleuthkitCase().getBlackboardArtifact(artifactId);
                }
            }
            return null;
        }

        @Override
        protected void done() {
            if (isCancelled()) {
                return;
            }

            try {
                BlackboardArtifact artifact = get();
                if (artifact != null) {
                    contentViewer.setNode(new BlackboardArtifactNode(artifact));
                } else {
                    contentViewer.setNode(null);
                }
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Failed message viewer based on thumbnail selection. thumbnailID = " + thumbnail.getId(), ex);
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }

    /**
     * Swing worker for gathering the data needed to populate the media viewer.
     */
    private class MediaViewerWorker extends SwingWorker<TableFilterNode, Void> {

        private final SelectionInfo selectionInfo;

        MediaViewerWorker(SelectionInfo info) {
            selectionInfo = info;
        }

        @Override
        protected TableFilterNode doInBackground() throws Exception {
            Set<Content> relationshipSources;
            Set<BlackboardArtifact> artifactList = new HashSet<>();

            if (selectionInfo != null) {
                relationshipSources = selectionInfo.getRelationshipSources();

                relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {
                    artifactList.add((BlackboardArtifact) content);
                });
            }

            return new TableFilterNode(new DataResultFilterNode(new AbstractNode(new AttachmentThumbnailsChildren(artifactList)), tableEM), true, this.getClass().getName());
        }

        @Messages({
            "MediaViewer_selection_failure_msg=Failed to get media attachments for selected accounts.",
            "MediaViewer_selection_failure_title=Selection Failed"
        })
        @Override
        protected void done() {
            try {
                if (isCancelled()) {
                    return;
                }
                thumbnailViewer.setNode(get());
            } catch (ExecutionException | InterruptedException ex) {
                String accounts = selectionInfo.getAccounts().stream().map(Account::getTypeSpecificID).collect(Collectors.joining(","));
                logger.log(Level.WARNING, "Unable to update cvt media viewer for " + accounts, ex);
                
                JOptionPane.showMessageDialog(MediaViewer.this, Bundle.MediaViewer_selection_failure_msg(), Bundle.MediaViewer_selection_failure_title(), JOptionPane.ERROR_MESSAGE);
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        splitPane = new javax.swing.JSplitPane();
        thumbnailViewer = new org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail(tableEM);

        setLayout(new java.awt.GridBagLayout());

        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        thumbnailViewer.setMinimumSize(new java.awt.Dimension(350, 102));
        thumbnailViewer.setPreferredSize(new java.awt.Dimension(450, 400));
        splitPane.setLeftComponent(thumbnailViewer);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(splitPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane splitPane;
    private org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail thumbnailViewer;
    // End of variables declaration//GEN-END:variables

}
