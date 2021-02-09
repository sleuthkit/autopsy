/*
 * Central Repository
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

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.ListModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Dialog for managing CRUD operations with hosts from the UI.
 */
@Messages({
    "ManageHostsDialog_title_text=Manage Hosts"
})
public class ManageHostsDialog extends javax.swing.JDialog {

    private static final Logger logger = Logger.getLogger(ManageHostsDialog.class.getName());
    private static final long serialVersionUID = 1L;
    
    private List<Host> hostListData = Collections.emptyList();

    /**
     * 
     * @param parent
     * @param modal 
     */
    public ManageHostsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        refresh();

        // refreshes UI when selection changes including button enabled state and data.
        this.hostList.addListSelectionListener((evt) -> refreshComponents());
    }

    /**
     * @return The currently selected host in the list or null if no host is
     * selected.
     */
    Host getSelectedHost() {
        return this.hostList.getSelectedValue();
    }

    /**
     * Shows add/edit dialog, and if a value is returned, creates a new Host.
     */
    private void addHost() {
        String newHostName = getAddEditDialogName(null);
        if (newHostName != null) {
            try {
                Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().createHost(newHostName);
            } catch (NoCurrentCaseException | TskCoreException e) {
                logger.log(Level.WARNING, String.format("Unable to add new host '%s' at this time.", newHostName), e);
            }
            System.out.println(String.format("Should create a host of name %s", newHostName));
            refresh();
        }
    }

    /**
     * Deletes the selected host if possible.
     *
     * @param selectedHost
     */
    private void deleteHost(Host selectedHost) {
        if (selectedHost != null) {
            System.out.println("Deleting: " + selectedHost);
            refresh();
        }
    }

    /**
     * Shows add/edit dialog, and if a value is returned, creates a new Host.
     *
     * @param selectedHost The selected host.
     */
    private void editHost(Host selectedHost) {
        if (selectedHost != null) {
            String newHostName = getAddEditDialogName(selectedHost);
            if (newHostName != null) {
                //TODO
                logger.log(Level.SEVERE, String.format("This needs to edit host %d to change to %s.", selectedHost.getId(), newHostName));
                //Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().updateHost(selectedHost.getId(), newHostName);
                refresh();
            }
        }
    }

    /**
     * Shows the dialog to add or edit the name of a host.
     *
     * @param origValue The original values for the host or null if adding a
     * host.
     * @return The new name for the host or null if operation was cancelled.
     */
    private String getAddEditDialogName(Host origValue) {
        JFrame parent = (this.getRootPane() != null && this.getRootPane().getParent() instanceof JFrame)
                ? (JFrame) this.getRootPane().getParent()
                : null;

        AddEditHostDialog addEditDialog = new AddEditHostDialog(parent, hostListData, origValue);
        if (addEditDialog.isChanged()) {
            String newHostName = addEditDialog.getName();
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
        Host selectedHost = hostList.getSelectedValue();
        Long selectedId = selectedHost == null ? null : selectedHost.getId();
        hostListData = getHostListData();
        hostList.setListData(hostListData.toArray(new Host[0]));

        if (selectedId != null) {
            ListModel<Host> model = hostList.getModel();

            for (int i = 0; i < model.getSize(); i++) {
                Object o = model.getElementAt(i);
                if (o instanceof Host && ((Host) o).getId() == selectedId) {
                    hostList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /**
     * Retrieves the current list of hosts for the case.
     *
     * @return The list of hosts to be displayed in the list (sorted
     * alphabetically).
     */
    private List<Host> getHostListData() {
        List<Host> toRet = null;
        try {
            toRet = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getHosts();
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "There was an error while fetching hosts for current case.", ex);
        }
        return (toRet == null)
                ? Collections.emptyList()
                : toRet.stream()
                        .filter(h -> h != null)
                        .sorted((a, b) -> getNameOrEmpty(a).compareToIgnoreCase(getNameOrEmpty(b)))
                        .collect(Collectors.toList());
    }

    /**
     * Returns the name of the host or an empty string if the host or name of
     * host is null.
     *
     * @param h The host.
     * @return The name of the host or empty string.
     */
    private String getNameOrEmpty(Host h) {
        return (h == null || h.getName() == null) ? "" : h.getName();
    }

    /**
     * Refreshes component's enabled state and displayed host data.
     */
    private void refreshComponents() {
        Host selectedHost = this.hostList.getSelectedValue();
        boolean itemSelected = selectedHost != null;
        this.editButton.setEnabled(itemSelected);
        this.deleteButton.setEnabled(itemSelected);
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

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("com/mycompany/hostsmanagement/Bundle"); // NOI18N
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
        Host host = this.hostList.getSelectedValue();
        if (host != null) {
            deleteHost(host);
        }
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void editButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editButtonActionPerformed
        Host host = this.hostList.getSelectedValue();
        if (host != null) {
            editHost(host);
        }
    }//GEN-LAST:event_editButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JButton deleteButton;
    private javax.swing.JButton editButton;
    private javax.swing.JTextArea hostDescriptionTextArea;
    private javax.swing.JList<org.sleuthkit.datamodel.Host> hostList;
    private javax.swing.JTextField hostNameTextField;
    private javax.swing.JButton newButton;
    // End of variables declaration//GEN-END:variables
}
