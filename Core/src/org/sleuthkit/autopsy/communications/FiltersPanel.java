/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JCheckBox;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.AccountTypeFilter;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.DeviceFilter;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
final public class FiltersPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(FiltersPanel.class.getName());
    private static final long serialVersionUID = 1L;

    private ExplorerManager em;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final Map<Account.Type, JCheckBox> accountTypeMap = new HashMap<>();
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final Map<String, JCheckBox> devicesMap = new HashMap<>();

    public FiltersPanel() {
        initComponents();
        updateFilters();
    }

    void updateFilters() {
        updateAccountTypeFilter();
        updateDeviceFilter();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        em = ExplorerManager.find(this);
    }

    /**
     * Populate the Account Types filter widgets
     */
    private void updateAccountTypeFilter() {

        //TODO: something like this commented code could be used to show only
        //the account types that are found:
        //final CommunicationsManager communicationsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
        //List<Account.Type> accountTypesInUse = communicationsManager.getAccountTypesInUse();
        //accountTypesInUSe.forEach(...)
        Account.Type.PREDEFINED_ACCOUNT_TYPES.forEach(
                type -> {
                    if (type.equals(Account.Type.CREDIT_CARD)) {
                        //don't show a check box for credit cards
                    } else if (type.equals(Account.Type.DEVICE)) {
                        //don't show a check box fro device
                    } else {
                        accountTypeMap.computeIfAbsent(type, t -> {
                            final JCheckBox jCheckBox = new JCheckBox(
                                    "<html><table cellpadding=0><tr><td><img src=\""
                                    + FiltersPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/"
                                            + AccountUtils.getIconFileName(type))
                                    + "\"/></td><td width=" + 3 + "><td>" + type.getDisplayName() + "</td></tr></table></html>",
                                    true
                            );
                            accountTypePane.add(jCheckBox);
                            return jCheckBox;
                        });
                    }
                }
        );
    }

    /**
     * Populate the devices filter widgets
     */
    private void updateDeviceFilter() {
        try {
            final List<DataSource> dataSources = Case.getCurrentCase().getSleuthkitCase().getDataSources();
            dataSources.forEach(
                    dataSource -> devicesMap.computeIfAbsent(dataSource.getDeviceId(), ds -> {
                        final JCheckBox jCheckBox = new JCheckBox(dataSource.getDeviceId(), true);
                        devicesPane.add(jCheckBox);
                        return jCheckBox;
                    })
            );
        } catch (IllegalStateException ex) {
            logger.log(Level.WARNING, "Communications Visualization Tool opened with no open case.", ex);
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "There was a error loading the datasources for the case.", tskCoreException);
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

        jList1 = new javax.swing.JList<>();

        jList1.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(jList1);

        setPreferredSize(new java.awt.Dimension(256, 469));

        applyFiltersButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/control-double.png"))); // NOI18N
        applyFiltersButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.applyFiltersButton.text")); // NOI18N
        applyFiltersButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        applyFiltersButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyFiltersButtonActionPerformed(evt);
            }
        });

        filtersTitleLabel.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        filtersTitleLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/funnel.png"))); // NOI18N
        filtersTitleLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.filtersTitleLabel.text")); // NOI18N

        accountTypePane.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        accountTypePane.setLayout(new javax.swing.BoxLayout(accountTypePane, javax.swing.BoxLayout.Y_AXIS));

        unCheckAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllAccountTypesButton.text")); // NOI18N
        unCheckAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllAccountTypesButtonActionPerformed(evt);
            }
        });

        accountTypesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/accounts.png"))); // NOI18N
        accountTypesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypesLabel.text")); // NOI18N

        checkAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllAccountTypesButton.text")); // NOI18N
        checkAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllAccountTypesButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap(51, Short.MAX_VALUE)
                        .addComponent(unCheckAllAccountTypesButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkAllAccountTypesButton))
                    .addComponent(accountTypesLabel)
                    .addComponent(accountTypePane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(accountTypesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountTypePane, javax.swing.GroupLayout.DEFAULT_SIZE, 189, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkAllAccountTypesButton)
                    .addComponent(unCheckAllAccountTypesButton))
                .addGap(0, 0, 0))
        );

        unCheckAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllDevicesButton.text")); // NOI18N
        unCheckAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllDevicesButtonActionPerformed(evt);
            }
        });

        devicesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/image.png"))); // NOI18N
        devicesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.devicesLabel.text")); // NOI18N

        checkAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllDevicesButton.text")); // NOI18N
        checkAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllDevicesButtonActionPerformed(evt);
            }
        });

        devicesPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        devicesPane.setLayout(new javax.swing.BoxLayout(devicesPane, javax.swing.BoxLayout.Y_AXIS));

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(devicesLabel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(devicesPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(unCheckAllDevicesButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkAllDevicesButton)))
                .addGap(0, 0, 0))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(devicesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(devicesPane, javax.swing.GroupLayout.DEFAULT_SIZE, 86, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkAllDevicesButton)
                    .addComponent(unCheckAllDevicesButton))
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(filtersTitleLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(applyFiltersButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filtersTitleLabel)
                    .addComponent(applyFiltersButton))
                .addGap(18, 18, 18)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void applyFiltersButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyFiltersButtonActionPerformed

        /*
         * When the apply button is pressed, query for accounts using the
         * selected filters, and send the results to the AccountsBrowser via the
         * ExplorerManager.
         *
         * TODO: This will need to be adapted to any changes in the
         * Comunications Manager API, such as using Filter objcts built from the
         * state of the checkboxes and the result types changing from Accounts
         * to AccountDeviceInstances
         */
        try {
            List<AccountDeviceInstance> accountDeviceInstances = new ArrayList<>();
            CommunicationsFilter commsFilter = new CommunicationsFilter();
            commsFilter.addAndFilter(getDevceFilter());

            commsFilter.addAndFilter(getAccountTypeFilter());
            final CommunicationsManager communicationsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            accountDeviceInstances.addAll(communicationsManager.getAccountDeviceInstancesWithRelationships(commsFilter));

            em.setRootContext(new AbstractNode(new AccountsDeviceInstanceChildren(accountDeviceInstances, communicationsManager)));
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "There was a error loading the accounts.", ex);
        }
    }//GEN-LAST:event_applyFiltersButtonActionPerformed

    private DeviceFilter getDevceFilter() {
        DeviceFilter deviceFilter = new DeviceFilter(devicesMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Entry::getKey).collect(Collectors.toSet()));
        return deviceFilter;
    }

    private AccountTypeFilter getAccountTypeFilter() {
        AccountTypeFilter accountTypeFilter = new AccountTypeFilter(accountTypeMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(entry -> entry.getKey()).collect(Collectors.toSet()));
        return accountTypeFilter;
    }
    
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllTypesSelected(boolean selected) {
        setAllSelected(accountTypeMap, selected);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllDevicesSelected(boolean selected) {
        setAllSelected(devicesMap, selected);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllSelected(Map<?, JCheckBox> map, boolean selected) {
        map.values().forEach(box -> box.setSelected(selected));
    }
    private void unCheckAllAccountTypesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unCheckAllAccountTypesButtonActionPerformed
        setAllTypesSelected(false);
    }//GEN-LAST:event_unCheckAllAccountTypesButtonActionPerformed

    private void checkAllAccountTypesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkAllAccountTypesButtonActionPerformed
        setAllTypesSelected(true);
    }//GEN-LAST:event_checkAllAccountTypesButtonActionPerformed

    private void unCheckAllDevicesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unCheckAllDevicesButtonActionPerformed
        setAllDevicesSelected(false);
    }//GEN-LAST:event_unCheckAllDevicesButtonActionPerformed

    private void checkAllDevicesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkAllDevicesButtonActionPerformed
        setAllDevicesSelected(true);
    }//GEN-LAST:event_checkAllDevicesButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private final javax.swing.JPanel accountTypePane = new javax.swing.JPanel();
    private final javax.swing.JLabel accountTypesLabel = new javax.swing.JLabel();
    private final javax.swing.JButton applyFiltersButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllDevicesButton = new javax.swing.JButton();
    private final javax.swing.JLabel devicesLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel devicesPane = new javax.swing.JPanel();
    private final javax.swing.JLabel filtersTitleLabel = new javax.swing.JLabel();
    private javax.swing.JList<String> jList1;
    private final javax.swing.JPanel jPanel2 = new javax.swing.JPanel();
    private final javax.swing.JPanel jPanel3 = new javax.swing.JPanel();
    private final javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
    private final javax.swing.JButton unCheckAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton unCheckAllDevicesButton = new javax.swing.JButton();
    // End of variables declaration//GEN-END:variables
}
