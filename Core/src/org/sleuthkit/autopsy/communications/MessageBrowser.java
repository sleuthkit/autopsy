/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.communications.AccountsRootChildren.AccountDeviceInstanceNode;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;

/**
 * The right hand side of the CVT. Has a DataResultPanel to show messages and
 * account details, and a Content viewer to show individual
 */
final class MessageBrowser extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final Logger logger = Logger.getLogger(MessageBrowser.class.getName());

    private static final long serialVersionUID = 1L;

    private ExplorerManager parentExplorereManager;
    private final DataResultPanel messagesResultPanel;
    private ExplorerManager internalExplorerManager;

    MessageBrowser() {
        initComponents();
        messagesResultPanel = DataResultPanel.createInstanceUninitialized("Account", "", Node.EMPTY, 0, messageDataContent);
       
        splitPane.setTopComponent(messagesResultPanel);
        splitPane.setBottomComponent(messageDataContent);

    }

    @Override
    public void addNotify() {
        super.addNotify();
        this.parentExplorereManager = ExplorerManager.find(this);

        internalExplorerManager = new ExplorerManager();

        parentExplorereManager.addPropertyChangeListener(pce -> {
            if (pce.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] selectedNodes = parentExplorereManager.getSelectedNodes();
                if (selectedNodes.length == 0) {
                    messagesResultPanel.setNode(null);
                    messagesResultPanel.setPath("");
                } else {
                    Set<Account> accounts = new HashSet<>();
                    CommunicationsFilter filter = null;
                    CommunicationsManager commsManager = null;
                    for (Node n : selectedNodes) {
                        if (n instanceof AccountDeviceInstanceNode) {
                            final AccountDeviceInstanceNode adiNode = (AccountDeviceInstanceNode) n;
                            accounts.add(adiNode.getAccountDeviceInstance().getAccount());
                            if (commsManager == null) {
                                commsManager = adiNode.getCommsManager();
                            }
                            if (filter == null) {
                                filter = adiNode.getFilter();
                            } else if (filter != adiNode.getFilter()) {
                                ///this should never happen...
                                logger.log(Level.WARNING, "Not all AccountDeviceInstanceNodes have the same filter. Using the first.");
                            }
                        } else {
                            ///this should never happen...
                            logger.log(Level.WARNING, "Unexpected Node encountered: " + n.toString());
                        }
                    }
                    messagesResultPanel.setNode(new TableFilterNode(new AccountDetailsNode(accounts, filter, commsManager), true));
                    if (accounts.size() == 1) {
                        messagesResultPanel.setPath(Iterables.getOnlyElement(accounts).getAccountUniqueID());
                    } else {
                        messagesResultPanel.setPath(accounts.size() + " accounts");
                    }
                }
            }
        });
         messagesResultPanel.addResultViewer(new DataResultViewerTable(internalExplorerManager,"Messages"));
     
        messagesResultPanel.open();
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

    @Override
    public ExplorerManager getExplorerManager() {
        return internalExplorerManager;
    }
}
