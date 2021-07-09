/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.Dialog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.ListModel;
import org.apache.commons.collections4.CollectionUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Dialog for managing CRUD operations with hosts from the UI.
 */
@Messages({
    "ManageHostsDialog_title_text=Manage Hosts"
})
public class ManageHostsDialog extends javax.swing.JDialog {

    /**
     * List item to be used with jlist.
     */
    private static class HostListItem {

        private final Host host;
        private final List<DataSource> dataSources;

        /**
         * Main constructor.
         *
         * @param host        The host.
         * @param dataSources The data sources that are children of this host.
         */
        HostListItem(Host host, List<DataSource> dataSources) {
            this.host = host;
            this.dataSources = dataSources;
        }

        /**
         * @return The host.
         */
        Host getHost() {
            return host;
        }

        /**
         * @return The data sources associated with this host.
         */
        List<DataSource> getDataSources() {
            return dataSources;
        }

        @Override
        public String toString() {
            return host == null ? "" : host.getName();
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89 * hash + Objects.hashCode(this.host == null ? 0 : this.host.getHostId());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HostListItem other = (HostListItem) obj;
            if (this.host == null || other.getHost() == null) {
                return this.host == null && other.getHost() == null;
            }

            return this.host.getHostId() == other.getHost().getHostId();
        }

    }

    private static final Logger logger = Logger.getLogger(ManageHostsDialog.class.getName());
    private static final long serialVersionUID = 1L;

    private Map<Host, List<DataSource>> hostChildrenMap = Collections.emptyMap();

    /**
     * Main constructor.
     *
     * @param parent The parent frame.
     */
    public ManageHostsDialog(java.awt.Frame parent) {
        super(parent, Bundle.ManageHostsDialog_title_text(), true);
        init();
    }

    /**
     * Main constructor.
     *
     * @param parent The parent dialog.
     */
    public ManageHostsDialog(Dialog parent) {
        super(parent, Bundle.ManageHostsDialog_title_text(), true);
        init();
    }

    /**
     * Initializes components, loads host data, and sets up list listener.
     */
    private void init() {
        initComponents();
        refresh();

        // refreshes UI when selection changes including button enabled state and data.
        this.hostList.addListSelectionListener((evt) -> refreshComponents());
    }

    /**
     * @return The currently selected host in the list or null if no host is
     *         selected.
     */
    Host getSelectedHost() {
        return (hostList.getSelectedValue() == null) ? null : hostList.getSelectedValue().getHost();
    }

    /**
     * Shows add/edit dialog, and if a value is returned, creates a new Host.
     */
    @NbBundle.Messages({"# {0} - hostname",
        "ManageHostsDialog.failureToAdd.txt=Unable to add new host {0} at this time.",
        "ManageHostsDialog.seeLog.txt=  See log for more information."})
    private void addHost() {
        String newHostName = getAddEditDialogName(null);
        if (newHostName != null) {
            Long selectedId = null;
            try {
                Host newHost = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().newHost(newHostName);
                selectedId = newHost == null ? null : newHost.getHostId();
            } catch (NoCurrentCaseException | TskCoreException e) {
                logger.log(Level.WARNING, Bundle.ManageHostsDialog_failureToAdd_txt(newHostName), e);
                MessageNotifyUtil.Message.warn(Bundle.ManageHostsDialog_failureToAdd_txt(newHostName) + Bundle.ManageHostsDialog_seeLog_txt());
            }
            refresh();
            setSelectedHostById(selectedId);
        }
    }

    /**
     * Deletes the selected host if possible.
     *
     * @param selectedHost
     */
    @NbBundle.Messages({"# {0} - hostname",
        "ManageHostsDialog.failureToDelete.txt=Unable to delete host {0} at this time."})
    private void deleteHost(Host selectedHost) {
        if (selectedHost != null && selectedHost.getName() != null) {
            try {
                Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().deleteHost(selectedHost.getName());
            } catch (NoCurrentCaseException | TskCoreException e) {
                logger.log(Level.WARNING, Bundle.ManageHostsDialog_failureToDelete_txt(selectedHost.getName()), e);
                MessageNotifyUtil.Message.error(Bundle.ManageHostsDialog_failureToDelete_txt(selectedHost.getName()) + Bundle.ManageHostsDialog_seeLog_txt());
            }
            refresh();
        }
    }

    /**
     * Selects the host with the given id. If no matching id found in list.
     *
     * @param selectedId The id of the host to select.
     */
    private void setSelectedHostById(Long selectedId) {
        ListModel<HostListItem> model = hostList.getModel();

        if (selectedId == null) {
            hostList.clearSelection();
        }

        for (int i = 0; i < model.getSize(); i++) {
            Object o = model.getElementAt(i);
            if (!(o instanceof HostListItem)) {
                continue;
            }

            Host host = ((HostListItem) o).getHost();
            if (host == null) {
                continue;
            }

            if (host.getHostId() == selectedId) {
                hostList.setSelectedIndex(i);
                return;
            }
        }

        hostList.clearSelection();
    }

    /**
     * Shows add/edit dialog, and if a value is returned, creates a new Host.
     *
     * @param selectedHost The selected host.
     */
    @NbBundle.Messages({"# {0} - hostname",
        "# {1} - hostId",
        "ManageHostsDialog.failureToEdit.txt=Unable to update host {0} with id: {1} at this time."})
    private void editHost(Host selectedHost) {

        if (selectedHost != null) {
            String newHostName = getAddEditDialogName(selectedHost);
            if (newHostName != null) {
                try {
                    Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().updateHostName(selectedHost, newHostName);
                } catch (NoCurrentCaseException | TskCoreException e) {
                    logger.log(Level.WARNING, Bundle.ManageHostsDialog_failureToEdit_txt(selectedHost.getName(), selectedHost.getHostId()), e);
                    MessageNotifyUtil.Message.warn(Bundle.ManageHostsDialog_failureToEdit_txt(selectedHost.getName(), selectedHost.getHostId()) + Bundle.ManageHostsDialog_seeLog_txt());
                }

                HostListItem selectedItem = hostList.getSelectedValue();
                Long selectedId = selectedItem == null || selectedItem.getHost() == null ? null : selectedItem.getHost().getHostId();

                refresh();

                setSelectedHostById(selectedId);
            }
        }
    }

    /**
     * Shows the dialog to add or edit the name of a host.
     *
     * @param origValue The original values for the host or null if adding a
     *                  host.
     *
     * @return The new name for the host or null if operation was cancelled.
     */
    private String getAddEditDialogName(Host origValue) {
        JFrame parent = (this.getRootPane() != null && this.getRootPane().getParent() instanceof JFrame)
                ? (JFrame) this.getRootPane().getParent()
                : null;

        AddEditHostDialog addEditDialog = new AddEditHostDialog(parent, hostChildrenMap.keySet(), origValue);
        addEditDialog.setResizable(false);
        addEditDialog.setLocationRelativeTo(parent);
        addEditDialog.setVisible(true);
        addEditDialog.toFront();

        if (addEditDialog.isChanged()) {
            String newHostName = addEditDialog.getValue();
            return newHostName;
        }

        return null;
    }

    /**
     * Refreshes the data and ui components for this dialog.
     */
    private void refresh() {
        refreshData();
        refreshComponents();
    }

    /**
     * Refreshes the data for this dialog and updates the host JList with the
     * hosts.
     */
    private void refreshData() {

        hostChildrenMap = getHostListData();

        Vector<HostListItem> jlistData = hostChildrenMap.entrySet().stream()
                .sorted((a, b) -> getNameOrEmpty(a.getKey()).compareTo(getNameOrEmpty(b.getKey())))
                .map(entry -> new HostListItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(Vector::new));

        hostList.setListData(jlistData);
    }

    /**
     * Returns the name of the host or an empty string if the host or name of
     * host is null.
     *
     * @param h The host.
     *
     * @return The name of the host or empty string.
     */
    private String getNameOrEmpty(Host h) {
        return (h == null || h.getName() == null) ? "" : h.getName();
    }

    /**
     * Retrieves the current list of hosts for the case.
     *
     * @return The list of hosts to be displayed in the list (sorted
     *         alphabetically).
     */
    @NbBundle.Messages({"ManageHostsDialog.failureToGetHosts.txt=There was an error while fetching hosts for current case."})
    private Map<Host, List<DataSource>> getHostListData() {
        Map<Host, List<DataSource>> hostMapping = new HashMap<>();
        try {
            SleuthkitCase curCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<Host> hosts = curCase.getHostManager().getAllHosts();
            List<DataSource> dataSources = curCase.getDataSources();

            if (dataSources != null) {
                for (DataSource ds : dataSources) {
                    List<DataSource> hostDataSources = hostMapping.computeIfAbsent(ds.getHost(), (d) -> new ArrayList<>());
                    hostDataSources.add(ds);
                }

            }

            if (hosts != null) {
                for (Host host : hosts) {
                    hostMapping.putIfAbsent(host, Collections.emptyList());
                }
            }

        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, Bundle.ManageHostsDialog_failureToGetHosts_txt(), ex);
            MessageNotifyUtil.Message.warn(Bundle.ManageHostsDialog_failureToGetHosts_txt() + Bundle.ManageHostsDialog_seeLog_txt());
        }

        return hostMapping;
    }

    /**
     * Refreshes component's enabled state and displayed host data.
     */
    private void refreshComponents() {
        HostListItem selectedItem = hostList.getSelectedValue();
        Host selectedHost = selectedItem == null ? null : selectedItem.getHost();
        List<DataSource> dataSources = selectedItem == null ? null : selectedItem.getDataSources();
        this.editButton.setEnabled(selectedHost != null);
        this.deleteButton.setEnabled(selectedHost != null && CollectionUtils.isEmpty(dataSources));
        String nameTextFieldStr = selectedHost != null && selectedHost.getName() != null ? selectedHost.getName() : "";
        this.hostNameTextField.setText(nameTextFieldStr);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane manageHostsScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel manageHostsPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane hostListScrollPane = new javax.swing.JScrollPane();
        hostList = new javax.swing.JList<>();
        javax.swing.JScrollPane hostDescriptionScrollPane = new javax.swing.JScrollPane();
        hostDescriptionTextArea = new javax.swing.JTextArea();
        newButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        javax.swing.JLabel hostListLabel = new javax.swing.JLabel();
        javax.swing.JSeparator jSeparator1 = new javax.swing.JSeparator();
        javax.swing.JLabel hostNameLabel = new javax.swing.JLabel();
        hostNameTextField = new javax.swing.JTextField();
        editButton = new javax.swing.JButton();
        javax.swing.JLabel hostDetailsLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(600, 450));

        manageHostsScrollPane.setMinimumSize(new java.awt.Dimension(600, 450));
        manageHostsScrollPane.setPreferredSize(new java.awt.Dimension(600, 450));

        manageHostsPanel.setPreferredSize(new java.awt.Dimension(527, 407));

        hostList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        hostListScrollPane.setViewportView(hostList);

        hostDescriptionTextArea.setEditable(false);
        hostDescriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        hostDescriptionTextArea.setColumns(20);
        hostDescriptionTextArea.setLineWrap(true);
        hostDescriptionTextArea.setRows(3);
        hostDescriptionTextArea.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.hostDescriptionTextArea.text")); // NOI18N
        hostDescriptionTextArea.setWrapStyleWord(true);
        hostDescriptionScrollPane.setViewportView(hostDescriptionTextArea);

        newButton.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.newButton.text")); // NOI18N
        newButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        newButton.setMaximumSize(new java.awt.Dimension(70, 23));
        newButton.setMinimumSize(new java.awt.Dimension(70, 23));
        newButton.setPreferredSize(new java.awt.Dimension(70, 23));
        newButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newButtonActionPerformed(evt);
            }
        });

        deleteButton.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.deleteButton.text")); // NOI18N
        deleteButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        deleteButton.setMaximumSize(new java.awt.Dimension(70, 23));
        deleteButton.setMinimumSize(new java.awt.Dimension(70, 23));
        deleteButton.setPreferredSize(new java.awt.Dimension(70, 23));
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        closeButton.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        hostListLabel.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.hostListLabel.text")); // NOI18N

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        hostNameLabel.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.hostNameLabel.text")); // NOI18N

        hostNameTextField.setEditable(false);

        editButton.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.editButton.text")); // NOI18N
        editButton.setMaximumSize(new java.awt.Dimension(70, 23));
        editButton.setMinimumSize(new java.awt.Dimension(70, 23));
        editButton.setPreferredSize(new java.awt.Dimension(70, 23));
        editButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editButtonActionPerformed(evt);
            }
        });

        hostDetailsLabel.setText(org.openide.util.NbBundle.getMessage(ManageHostsDialog.class, "ManageHostsDialog.hostDetailsLabel.text")); // NOI18N

        javax.swing.GroupLayout manageHostsPanelLayout = new javax.swing.GroupLayout(manageHostsPanel);
        manageHostsPanel.setLayout(manageHostsPanelLayout);
        manageHostsPanelLayout.setHorizontalGroup(
            manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(manageHostsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hostDescriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(hostListLabel)
                    .addGroup(manageHostsPanelLayout.createSequentialGroup()
                        .addComponent(newButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(editButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(hostListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 224, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(manageHostsPanelLayout.createSequentialGroup()
                        .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(manageHostsPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(closeButton))
                            .addGroup(manageHostsPanelLayout.createSequentialGroup()
                                .addGap(29, 29, 29)
                                .addComponent(hostNameLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(hostNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 79, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(manageHostsPanelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(hostDetailsLabel)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        manageHostsPanelLayout.setVerticalGroup(
            manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(manageHostsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(manageHostsPanelLayout.createSequentialGroup()
                        .addComponent(hostDetailsLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(hostNameLabel)
                            .addComponent(hostNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(closeButton))
                    .addComponent(jSeparator1)
                    .addGroup(manageHostsPanelLayout.createSequentialGroup()
                        .addComponent(hostDescriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hostListLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hostListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 325, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(manageHostsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(editButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/sleuthkit/autopsy/datamodel/hosts/Bundle"); // NOI18N
        newButton.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.newButton.text")); // NOI18N
        deleteButton.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.deleteButton.text")); // NOI18N
        closeButton.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.closeButton.text")); // NOI18N
        hostListLabel.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.hostListLabel.text")); // NOI18N
        hostNameLabel.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.hostNameLabel.text")); // NOI18N
        editButton.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.editButton.text")); // NOI18N
        hostDetailsLabel.getAccessibleContext().setAccessibleName(bundle.getString("ManageHostsDialog.hostDetailsLabel.text")); // NOI18N

        manageHostsScrollPane.setViewportView(manageHostsPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(manageHostsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(manageHostsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
        addHost();
    }//GEN-LAST:event_newButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        HostListItem listItem = this.hostList.getSelectedValue();
        if (listItem != null && listItem.getHost() != null) {
            deleteHost(listItem.getHost());
        }
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void editButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editButtonActionPerformed
        HostListItem listItem = this.hostList.getSelectedValue();
        if (listItem != null && listItem.getHost() != null) {
            editHost(listItem.getHost());
        }
    }//GEN-LAST:event_editButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JButton deleteButton;
    private javax.swing.JButton editButton;
    private javax.swing.JTextArea hostDescriptionTextArea;
    private javax.swing.JList<org.sleuthkit.autopsy.datamodel.hosts.ManageHostsDialog.HostListItem> hostList;
    private javax.swing.JTextField hostNameTextField;
    private javax.swing.JButton newButton;
    // End of variables declaration//GEN-END:variables
}
