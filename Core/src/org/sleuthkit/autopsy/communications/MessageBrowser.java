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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.AccountDeviceInstance;

/**
 * The right hand side of the CVT. Has a DataResultPanel to show messages and
 * other account details, and a ContentViewer to show individual
 */
public final class MessageBrowser extends JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private final ExplorerManager tableEM;
    private final ExplorerManager gacExplorerManager;
    private final DataResultPanel messagesResultPanel;

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
    MessageBrowser(ExplorerManager tableEM, ExplorerManager gacExplorerManager) {
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
                        String path = selectedNode.getDisplayName();
                        if (selectedNode instanceof AccountDeviceInstanceNode) {
                            rootNode = makeRootNodeFromAccountDeviceInstanceNodes(selectedNodes);
                        } else {
                            rootNode = selectedNode;
                        }
                        messagesResultPanel.setPath(path);
                        messagesResultPanel.setNode(new TableFilterNode(new DataResultFilterNode(rootNode, gacExplorerManager), true));
                    }
                }
            }

            private Node makeRootNodeFromAccountDeviceInstanceNodes(final Node[] selectedNodes) {
                AccountDeviceInstanceNode adiNode = (AccountDeviceInstanceNode) selectedNodes[0];
                final Set<AccountDeviceInstance> accountDeviceInstances;
                accountDeviceInstances = Stream.of(selectedNodes)
                        .map(AccountDeviceInstanceNode.class::cast)
                        .map(AccountDeviceInstanceNode::getAccountDeviceInstance)
                        .collect(Collectors.toSet());
                return new AccountDetailsNode(accountDeviceInstances, adiNode.getFilter(), adiNode.getCommsManager());
            }
        });
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return gacExplorerManager;
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
