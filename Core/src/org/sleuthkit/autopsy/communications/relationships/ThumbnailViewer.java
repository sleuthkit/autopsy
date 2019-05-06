/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JPanel;
import static javax.swing.SwingUtilities.isDescendingFrom;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class ThumbnailViewer extends JPanel implements RelationshipsViewer, ExplorerManager.Provider, Lookup.Provider {

    private static final Logger logger = Logger.getLogger(ThumbnailChildren.class.getName());

    private final ExplorerManager tableEM = new ExplorerManager();
    private final PropertyChangeListener focusPropertyListener;

    private final ModifiableProxyLookup proxyLookup;

    @Messages({
        "ThumbnailViewer_Name=Media"
    })
    /**
     * Creates new form ThumbnailViewer
     */
    public ThumbnailViewer() {
        proxyLookup = new ModifiableProxyLookup(createLookup(tableEM, getActionMap()));

        // See org.sleuthkit.autopsy.timeline.TimeLineTopComponent for a detailed
        // explaination of focusPropertyListener
        focusPropertyListener = (final PropertyChangeEvent focusEvent) -> {
            if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                final Component newFocusOwner = (Component) focusEvent.getNewValue();

                if (newFocusOwner == null) {
                    return;
                }
                if (isDescendingFrom(newFocusOwner, contentViewer)) {
                    //if the focus owner is within the MessageContentViewer (the attachments table)
                    proxyLookup.setNewLookups(createLookup(((MessageDataContent) contentViewer).getExplorerManager(), getActionMap()));
                } else if (isDescendingFrom(newFocusOwner, ThumbnailViewer.this)) {
                    //... or if it is within the Results table.
                    proxyLookup.setNewLookups(createLookup(tableEM, getActionMap()));

                }
            }
        };

        initComponents();

        tableEM.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                handleNodeSelectionChange();
            }
        });
        
        thumbnailViewer.resetComponent();
    }

    @Override
    public String getDisplayName() {
        return Bundle.ThumbnailViewer_Name();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        final Set<Content> relationshipSources;

        CommunicationsManager communicationManager;
        Set<BlackboardArtifact> artifactList = new HashSet<>();

        try {
            communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
            relationshipSources = communicationManager.getRelationshipSources(info.getAccountDevicesInstances(), info.getCommunicationsFilter());

            relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {
                artifactList.add((BlackboardArtifact) content);
            });

        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to update selection." , ex);
        }
        
        if(artifactList.size() == 0) {
            thumbnailViewer.resetComponent();
        }

        thumbnailViewer.setNode(new TableFilterNode(new DataResultFilterNode(new AbstractNode(new ThumbnailChildren(artifactList)), tableEM), true));
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
        //add listener that maintains correct selection in the Global Actions Context
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusPropertyListener);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("focusOwner", focusPropertyListener);
    }

    /**
     * Handle the change in thumbnail node selection.
     */
    private void handleNodeSelectionChange() {
        final Node[] nodes = tableEM.getSelectedNodes();

        if (nodes != null && nodes.length > 0) {
            AbstractContent thumbnail = nodes[0].getLookup().lookup(AbstractContent.class);
            if (thumbnail != null) {
                try {
                    Content parentContent = thumbnail.getParent();
                    if (parentContent != null && parentContent instanceof BlackboardArtifact) {
                        contentViewer.setNode(new BlackboardArtifactNode((BlackboardArtifact) parentContent));
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to get parent Content from AbstraceContent instance.", ex); //NON-NLS
                }
            }
        } else {
            contentViewer.setNode(null);
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

        thumbnailViewer = new org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail(tableEM);
        contentViewer = new MessageDataContent();
        separator = new javax.swing.JSeparator();

        thumbnailViewer.setMinimumSize(new java.awt.Dimension(350, 102));

        contentViewer.setPreferredSize(new java.awt.Dimension(450, 400));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(thumbnailViewer, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(contentViewer, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(separator)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(thumbnailViewer, javax.swing.GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(separator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(contentViewer, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(3, 3, 3))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.contentviewers.MessageContentViewer contentViewer;
    private javax.swing.JSeparator separator;
    private org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail thumbnailViewer;
    // End of variables declaration//GEN-END:variables

}
