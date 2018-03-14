/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A panel that goes in the Browse tab of the Communications Visualization Tool.
 * Hosts an OutlineView that shows information about Accounts, and a
 * MessageBrowser for viewing details of communications.
 *
 * The Lookup provided by getLookup will be proxied by the lookup of the
 * CVTTopComponent when this tab is active allowing for context sensitive
 * actions to work correctly.
 */
public final class AccountsBrowser extends JPanel implements ExplorerManager.Provider, Lookup.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AccountsBrowser.class.getName());

    private final Outline outline;

    private final ExplorerManager messageBrowserEM = new ExplorerManager();
    private final ExplorerManager accountsTableEM = new ExplorerManager();

    /*
     * This lookup proxies the selection lookup of both he accounts table and
     * the messages table.
     */
    private final ProxyLookup proxyLookup;

    public AccountsBrowser() {
        initComponents();
        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                "device", Bundle.AccountNode_device(),
                "type", Bundle.AccountNode_accountType(),
                "count", Bundle.AccountNode_messageCount()
        );

        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.AccountNode_accountName());
        outline.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        outline.setColumnSorted(3, false, 1); //it would be nice if the column index wasn't hardcoded

        accountsTableEM.addPropertyChangeListener(evt -> {
            if (ExplorerManager.PROP_ROOT_CONTEXT.equals(evt.getPropertyName())) {
                SwingUtilities.invokeLater(this::setColumnWidths);
            } else if (ExplorerManager.PROP_EXPLORED_CONTEXT.equals(evt.getPropertyName())) {
                SwingUtilities.invokeLater(this::setColumnWidths);
            }
        });
        final MessageBrowser messageBrowser = new MessageBrowser(accountsTableEM, messageBrowserEM);

        jSplitPane1.setRightComponent(messageBrowser);

        proxyLookup = new ProxyLookup(
               messageBrowser.getLookup(),
                ExplorerUtils.createLookup(accountsTableEM, getActionMap()));
    }

    private void setColumnWidths() {
        int margin = 4;
        int padding = 8;

        final int rows = Math.min(100, outline.getRowCount());

        for (int column = 0; column < outline.getModel().getColumnCount(); column++) {
            int columnWidthLimit = 500;
            int columnWidth = 0;

            // find the maximum width needed to fit the values for the first 100 rows, at most
            for (int row = 0; row < rows; row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, column);
                Component comp = outline.prepareRenderer(renderer, row, column);
                columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
            }

            columnWidth += 2 * margin + padding; // add margin and regular padding
            columnWidth = Math.min(columnWidth, columnWidthLimit);

            outline.getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
        }
    }

    @Subscribe
    public void handleFilterEvent(CVTEvents.FilterChangeEvent filterChangeEvent) {
        try {
            final CommunicationsManager commsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            accountsTableEM.setRootContext(new AbstractNode(Children.create(new AccountDeviceInstanceNodeFactory(commsManager, filterChangeEvent.getNewFilter()), true)));
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "There was an error getting the CommunicationsManager for the current case.", ex);
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

        jSplitPane1 = new javax.swing.JSplitPane();
        outlineView = new org.openide.explorer.view.OutlineView();

        setLayout(new java.awt.BorderLayout());

        jSplitPane1.setLeftComponent(outlineView);

        add(jSplitPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane jSplitPane1;
    private org.openide.explorer.view.OutlineView outlineView;
    // End of variables declaration//GEN-END:variables

    @Override
    public ExplorerManager getExplorerManager() {
        return accountsTableEM;
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }
}
