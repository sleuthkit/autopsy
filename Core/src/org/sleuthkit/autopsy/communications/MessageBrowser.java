/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.communications.AccountsRootChildren.AccountDeviceInstanceNode;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;

/**
 * The right hand side of the CVT. Has a DataResultPanel to show messages and
 * other account details, and a ContentViewer to show individual
 */
final class MessageBrowser extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private final ExplorerManager gacExplorerManager;
    private final DataResultPanel messagesResultPanel;
    private DataResultViewerTable dataResultViewerTable;

    /**
     * Constructs the right hand side of the Communications Visualization Tool
     * (CVT).
     *
     * @param gacExplorerManager An explorer manager associated with the
     *                           GlobalActionsContext (GAC) so that selections
     *                           in the messages browser can be exposed to
     *                           context-sensitive actions.
     */
    MessageBrowser(ExplorerManager gacExplorerManager) {
        this.gacExplorerManager = gacExplorerManager;
        initComponents();
        //create an uninitialized DataResultPanel so we can control the ResultViewers that get added.
        messagesResultPanel = DataResultPanel.createInstanceUninitialized("Account", "", Node.EMPTY, 0, messageDataContent);
        splitPane.setTopComponent(messagesResultPanel);
        splitPane.setBottomComponent(messageDataContent);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ExplorerManager parentExplorerManager = ExplorerManager.find(this);
        parentExplorerManager.addPropertyChangeListener(pce -> {
            if (pce.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] selectedNodes = parentExplorerManager.getSelectedNodes();

                messagesResultPanel.setNumMatches(0);
                messagesResultPanel.setNode(null);

                if (selectedNodes.length == 0) {
                    //reset panel when there is no selection
                    messagesResultPanel.setPath("");
                } else {
                    final Node selectedNode = selectedNodes[0];
                    if (selectedNode instanceof AccountDeviceInstanceNode) {
                        AccountDeviceInstanceNode adiNode = (AccountDeviceInstanceNode) selectedNode;
                        CommunicationsFilter filter = adiNode.getFilter();
                        CommunicationsManager commsManager = adiNode.getCommsManager();
                        final Set<AccountDeviceInstance> accountDeviceInstances;

                        if (selectedNodes.length == 1) {
                            final AccountDeviceInstance accountDeviceInstance = adiNode.getAccountDeviceInstance();
                            accountDeviceInstances = Collections.singleton(accountDeviceInstance);
                            messagesResultPanel.setPath(accountDeviceInstance.getAccount().getTypeSpecificID());
                        } else {
                            accountDeviceInstances = Stream.of(selectedNodes)
                                    .map(node -> (AccountDeviceInstanceNode) node)
                                    .map(AccountDeviceInstanceNode::getAccountDeviceInstance)
                                    .collect(Collectors.toSet());
                            messagesResultPanel.setPath(selectedNodes.length + " accounts");
                        }
                        AccountDetailsNode accountDetailsNode =
                                new AccountDetailsNode(accountDeviceInstances, filter, commsManager);
                        TableFilterNode wrappedNode =
                                new TableFilterNode(new DataResultFilterNode(accountDetailsNode, parentExplorerManager), true);
                        messagesResultPanel.setNode(wrappedNode);
                    }
                }
            }
        });

        //add the required result viewers and THEN open the panel
        if (null == dataResultViewerTable) {
            dataResultViewerTable = new DataResultViewerTable(gacExplorerManager, "Messages");
            messagesResultPanel.addResultViewer(dataResultViewerTable);
        }
        messagesResultPanel.open();
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
