/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
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
 * General Purpose class for panels that need OutlineView of message nodes at
 * the top with a MessageDataContent at the bottom.
 */
public class MessagesPanel extends javax.swing.JPanel implements Lookup.Provider {

    private static final long serialVersionUID = 1L;

    private final Outline outline;
    private final ModifiableProxyLookup proxyLookup;
    private PropertyChangeListener focusPropertyListener;

    private final MessageDataContent messageContentViewer;

    /**
     * Creates new form MessagesPanel
     */
    public MessagesPanel() {
        initComponents();

        messageContentViewer = new MessageDataContent();
        splitPane.setRightComponent(messageContentViewer);

        proxyLookup = new ModifiableProxyLookup(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

        outline = outlineViewPanel.getOutlineView().getOutline();
        // When changing this column this, if the from and to columns pos is
        // effected make sure to modify the renderer code below.
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
                } else {
                    messageContentViewer.setNode(null);
                }
                
            }
        });
        
        // This is a trick to get the first message to be selected after the ChildFactory has added
        // new data to the table.
        outlineViewPanel.getOutlineView().getOutline().getOutlineModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.INSERT) {
                    outline.setRowSelectionInterval(0, 0);
                }
            }
        });
        
        TableColumn column = outline.getColumnModel().getColumn(1);
        column.setCellRenderer(new NodeTableCellRenderer());
        
        column = outline.getColumnModel().getColumn(2);
        column.setCellRenderer(new NodeTableCellRenderer());
        
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
        outlineViewPanel.setTableColumnsWidth(5, 10, 10, 15, 50, 10);
    }

    MessagesPanel(ChildFactory<?> nodeFactory) {
        this();
        setChildFactory(nodeFactory);
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }
    
    /**
     * Return the explorerManager for the table.
     * 
     * @return The explorer manager for the table.
     */
    ExplorerManager getExplorerManager() {
        return outlineViewPanel.getExplorerManager();
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

    private void handleFocusChange(Component newFocusOwner) {
        if (newFocusOwner == null) {
            return;
        }
        if (isDescendingFrom(newFocusOwner, messageContentViewer)) {
            //if the focus owner is within the MessageContentViewer (the attachments table)
            proxyLookup.setNewLookups(createLookup((messageContentViewer).getExplorerManager(), getActionMap()));
        } else if (isDescendingFrom(newFocusOwner, MessagesPanel.this)) {
            //... or if it is within the Results table.
            proxyLookup.setNewLookups(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

        }
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
                                outlineViewPanel.getExplorerManager()), true));
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new javax.swing.JSplitPane();
        outlineViewPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();

        setLayout(new java.awt.BorderLayout());

        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setLeftComponent(outlineViewPanel);

        add(splitPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel outlineViewPanel;
    private javax.swing.JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables
}
