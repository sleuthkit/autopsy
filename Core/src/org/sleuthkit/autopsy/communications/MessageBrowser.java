/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JPanel;
import static javax.swing.SwingUtilities.isDescendingFrom;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 * The right hand side of the CVT. Has a DataResultPanel to show a listing of
 * messages and other account details, and a ContentViewer to show individual
 * messages.
 */
public final class MessageBrowser extends JPanel implements ExplorerManager.Provider, Lookup.Provider {

    private static final long serialVersionUID = 1L;
    private final ExplorerManager tableEM;
    private final ExplorerManager gacExplorerManager;
    private final DataResultPanel messagesResultPanel;
    /* lookup that will be exposed through the (Global Actions Context) */
    private final ModifiableProxyLookup proxyLookup = new ModifiableProxyLookup();

    private final PropertyChangeListener focusPropertyListener = new PropertyChangeListener() {
        /**
         * Listener that keeps the proxyLookup in sync with the focused area of
         * the UI.
         *
         * Since the embedded MessageContentViewer (attachments panel) is not in
         * its own TopComponenet, its selection does not get proxied into the
         * Global Actions Context (GAC), and many of the available actions don't
         * work on it. Further, we can't put the selection from both the
         * Messages table and the Attachments table in the GAC because they
         * could both include AbstractFiles, muddling the selection seen by the
         * actions. Instead, depending on where the focus is in the window, we
         * want to put different Content in the Global Actions Context to be
         * picked up by, e.g., the tagging actions. The best way I could figure
         * to do this was to listen to all focus events and swap out what is in
         * the lookup appropriately. An alternative to this would be to
         * investigate using the ContextAwareAction interface.
         *
         * @see org.sleuthkit.autopsy.timeline.TimeLineTopComponent for a
         * similar situation and a similar solution.
         *
         * @param focusEvent The focus change event.
         */
        @Override
        public void propertyChange(final PropertyChangeEvent focusEvent) {
            if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                final Component newFocusOwner = (Component) focusEvent.getNewValue();

                if (newFocusOwner == null) {
                    return;
                }
                if (isDescendingFrom(newFocusOwner, messageDataContent)) {
                    //if the focus owner is within the MessageContentViewer ( the attachments table)
                    proxyLookup.setNewLookups(createLookup(messageDataContent.getExplorerManager(), getActionMap()));
                } else if (isDescendingFrom(newFocusOwner, messagesResultPanel)) {
                    //... or if it is within the Messages table.
                    proxyLookup.setNewLookups(createLookup(gacExplorerManager, getActionMap()));
                }

            }
        }
    };

    /**
     * Constructs the right hand side of the Communications Visualization Tool
     * (CVT).
     *
     * @param tableEM            An explorer manager to listen to as the driver
     *                           of the Message Table.
     * @param gacExplorerManager An explorer manager associated with the
     *                           GlobalActionsContext (GAC) so that selections
     *                           in the messages browser can be exposed to
     *                           context-sensitive actions.
     */
    @NbBundle.Messages({"MessageBrowser.DataResultViewerTable.title=Messages"})
    MessageBrowser(final ExplorerManager tableEM, final ExplorerManager gacExplorerManager) {
        this.tableEM = tableEM;
        this.gacExplorerManager = gacExplorerManager;
        initComponents();
        //create an uninitialized DataResultPanel so we can control the ResultViewers that get added.
        messagesResultPanel = DataResultPanel.createInstanceUninitialized("Account", "", Node.EMPTY, 0, messageDataContent);
        splitPane.setTopComponent(messagesResultPanel);
        splitPane.setBottomComponent(messageDataContent);
        messagesResultPanel.addResultViewer(new DataResultViewerTable(gacExplorerManager,
                Bundle.MessageBrowser_DataResultViewerTable_title()));
        messagesResultPanel.open();

        this.tableEM.addPropertyChangeListener(new PropertyChangeListener() {
            /**
             * Listener that pushes selections in the tableEM (the Accounts
             * table) into the Messages table.
             *
             * @param pce The ExplorerManager event.
             */
            @Override
            public void propertyChange(PropertyChangeEvent pce) {
                if (pce.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                    final Node[] selectedNodes = MessageBrowser.this.tableEM.getSelectedNodes();
                    messagesResultPanel.setNumMatches(0);
                    messagesResultPanel.setNode(null);
                    messagesResultPanel.setPath("");
                    if (selectedNodes.length > 0) {
                        Node rootNode;
                        final Node selectedNode = selectedNodes[0];

                        if (selectedNode instanceof AccountDeviceInstanceNode) {
                            rootNode = makeRootNodeFromAccountDeviceInstanceNodes(selectedNodes);
                        } else {
                            rootNode = selectedNode;
                        }
                        messagesResultPanel.setPath(rootNode.getDisplayName());
                        messagesResultPanel.setNode(new TableFilterNode(new DataResultFilterNode(rootNode, gacExplorerManager), true));
                    }
                }
            }

            private Node makeRootNodeFromAccountDeviceInstanceNodes(final Node[] selectedNodes) {
                //Use lookup here? 
                final AccountDeviceInstanceNode adiNode = (AccountDeviceInstanceNode) selectedNodes[0];

                final Set<AccountDeviceInstanceKey> accountDeviceInstances = new HashSet<>();
                for (final Node n : selectedNodes) {
                    //Use lookup here?
                    accountDeviceInstances.add(((AccountDeviceInstanceNode) n).getAccountDeviceInstanceKey());
                }
                return SelectionNode.createFromAccounts(accountDeviceInstances, adiNode.getFilter(), adiNode.getCommsManager());
            }
        }
        );
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return gacExplorerManager;
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

        splitPane = new javax.swing.JSplitPane();
        messageDataContent = new org.sleuthkit.autopsy.communications.MessageDataContent();

        splitPane.setDividerLocation(400);
        splitPane.setDividerSize(10);
        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setBottomComponent(messageDataContent);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(splitPane))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(splitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1083, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.communications.MessageDataContent messageDataContent;
    private javax.swing.JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables

}
