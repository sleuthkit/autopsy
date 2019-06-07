/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.relationships;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import static javax.swing.SwingUtilities.isDescendingFrom;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 *
 * 
 */
public class MessagesPanel extends javax.swing.JPanel implements Lookup.Provider {

    private final Outline outline;
    private final ModifiableProxyLookup proxyLookup;
    private final PropertyChangeListener focusPropertyListener;
    
    /**
     * Creates new form ThreadMessagesPanel
     */
    public MessagesPanel() {
        initComponents();
        
        proxyLookup = new ModifiableProxyLookup(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

        // See org.sleuthkit.autopsy.timeline.TimeLineTopComponent for a detailed
        // explaination of focusPropertyListener
        focusPropertyListener = (final PropertyChangeEvent focusEvent) -> {
            if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                final Component newFocusOwner = (Component) focusEvent.getNewValue();

                if (newFocusOwner == null) {
                    return;
                }
                if (isDescendingFrom(newFocusOwner, messageContentViewer)) {
                    //if the focus owner is within the MessageContentViewer (the attachments table)
                    proxyLookup.setNewLookups(createLookup(((MessageDataContent) messageContentViewer).getExplorerManager(), getActionMap()));
                } else if (isDescendingFrom(newFocusOwner, MessagesPanel.this)) {
                    //... or if it is within the Results table.
                    proxyLookup.setNewLookups(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

                }
            }
        };

        outline = outlineViewPanel.getOutlineView().getOutline();
        outlineViewPanel.getOutlineView().setPropertyColumns(
                "From", Bundle.MessageViewer_columnHeader_From(),
                "To", Bundle.MessageViewer_columnHeader_To(),
                "Date", Bundle.MessageViewer_columnHeader_Date(),
                "Subject", Bundle.MessageViewer_columnHeader_Subject(),
                "Attms", Bundle.MessageViewer_columnHeader_Attms()
        );
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel("Type");

        outlineViewPanel.getExplorerManager().addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] nodes = outlineViewPanel.getExplorerManager().getSelectedNodes();

                if (nodes != null && nodes.length == 1) {
                    messageContentViewer.setNode(nodes[0]);
                }
                else {
                    messageContentViewer.setNode(null);
                }
            }
        });
        
    }
    
    public MessagesPanel(ChildFactory<?> nodeFactory) {
        this();
        setChildFactory(nodeFactory);
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

    final void setChildFactory(ChildFactory<?> nodeFactory) {
        outlineViewPanel.getExplorerManager().setRootContext(
                new TableFilterNode(
                        new DataResultFilterNode(
                                new AbstractNode(
                                        Children.create(nodeFactory, true)),
                                outlineViewPanel.getExplorerManager()),true));
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        outlineViewPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();
        messageContentViewer = new MessageDataContent();

        setLayout(new java.awt.BorderLayout());

        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setLeftComponent(outlineViewPanel);
        jSplitPane1.setRightComponent(messageContentViewer);

        add(jSplitPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane jSplitPane1;
    private org.sleuthkit.autopsy.contentviewers.MessageContentViewer messageContentViewer;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel outlineViewPanel;
    // End of variables declaration//GEN-END:variables
}
