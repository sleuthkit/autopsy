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
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import static javax.swing.SwingUtilities.isDescendingFrom;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 * Visualation for the messages of the currently selected accounts.
 */
@ServiceProvider(service = RelationshipsViewer.class)
public final class MessagesViewer extends JPanel implements RelationshipsViewer, ExplorerManager.Provider, Lookup.Provider {

    private final ExplorerManager tableEM;
    private final Outline outline;
    private final ModifiableProxyLookup proxyLookup;
    private final PropertyChangeListener focusPropertyListener;
    private final MessagesChildNodeFactory nodeFactory;

    @Messages({
        "MessageViewer_tabTitle=Messages",
        "MessageViewer_columnHeader_From=From",
        "MessageViewer_columnHeader_To=To",
        "MessageViewer_columnHeader_Date=Date",
        "MessageViewer_columnHeader_Subject=Subject",
        "MessageViewer_columnHeader_Attms=Attachments"
    })

    /**
     * Visualation for the messages of the currently selected accounts.
     */
    public MessagesViewer() {
        tableEM = new ExplorerManager();
        proxyLookup = new ModifiableProxyLookup(createLookup(tableEM, getActionMap()));
        nodeFactory = new MessagesChildNodeFactory(null);

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
                } else if (isDescendingFrom(newFocusOwner, MessagesViewer.this)) {
                    //... or if it is within the Results table.
                    proxyLookup.setNewLookups(createLookup(tableEM, getActionMap()));

                }
            }
        };

        initComponents();

        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                "From", Bundle.MessageViewer_columnHeader_From(),
                "To", Bundle.MessageViewer_columnHeader_To(),
                "Date", Bundle.MessageViewer_columnHeader_Date(),
                "Subject", Bundle.MessageViewer_columnHeader_Subject(),
                "Attms", Bundle.MessageViewer_columnHeader_Attms(),
                "Type", "Type"
        );
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel("Type");

        tableEM.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] nodes = tableEM.getSelectedNodes();

                if (nodes != null && nodes.length == 1) {
                    contentViewer.setNode(nodes[0]);
                }
                else {
                    contentViewer.setNode(null);
                }
            }
        });
        
         tableEM.setRootContext(new TableFilterNode(new DataResultFilterNode(new AbstractNode(Children.create(nodeFactory, true)), getExplorerManager()), true));
    }

    @Override
    public String getDisplayName() {
        return Bundle.MessageViewer_tabTitle();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        nodeFactory.refresh(info);
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
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        outlineView = new org.openide.explorer.view.OutlineView();
        contentViewer = new MessageDataContent();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(contentViewer, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(contentViewer, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.contentviewers.MessageContentViewer contentViewer;
    private org.openide.explorer.view.OutlineView outlineView;
    // End of variables declaration//GEN-END:variables
}
