/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.persona;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAlias;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaMetadata;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * JPanel for persona details
 */
@TopComponent.Description(preferredID = "PersonaDetailsTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "personadetails", openAtStartup = false)
@RetainLocation("personadetails")
@SuppressWarnings("PMD.SingularField")
public final class PersonaDetailsPanel extends javax.swing.JPanel {

    private PersonaDetailsMode mode;

    private Persona currentPersona;
    private String currentName = null;
    private ArrayList<PersonaAccount> currentAccounts = null;
    private ArrayList<PersonaMetadata> currentMetadata = null;
    private ArrayList<PersonaAlias> currentAliases = null;
    private ArrayList<CorrelationCase> currentCases = null;

    // Creation
    private PersonaDetailsTableModel accountsModel;
    private PersonaDetailsTableModel metadataModel;
    private PersonaDetailsTableModel aliasesModel;
    private PersonaDetailsTableModel casesModel;

    private static final Logger logger = Logger.getLogger(PersonaDetailsPanel.class.getName());

    @Messages({
        "PersonaDetailsPanel_NameEdit=Edit Persona",
        "PersonaDetailsPanel_NameCreate=Create Persona",
        "PersonaDetailsPanel_NameView=View Persona",})
    public PersonaDetailsPanel() {
        initComponents();

        addAccountBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAddAccountDialog();
            }
        });

        addAliasBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addAlias();
            }
        });

        deleteAliasBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteAlias();
            }
        });

        aliasesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                handleAliasListSelectionChange(e);
            }
        });

        saveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                savePressed();
            }
        });
    }

    private void showAddAccountDialog() {
        AddAccountDialog dialog = new AddAccountDialog(this);
    }

    private void addAlias() {
        aliasesModel.addRow(new String[]{""});
    }

    private void deleteAlias() {
        int idx = aliasesTable.getSelectedRow();
        if (idx != -1) {
            aliasesModel.removeRow(idx);
        }
        deleteAliasBtn.setEnabled(false);
    }

    private void handleAliasListSelectionChange(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        deleteAliasBtn.setEnabled(true);
    }

    private void savePressed() {
        //todo handle
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        detailsPanel = new javax.swing.JPanel();
        nameLbl = new javax.swing.JLabel();
        nameField = new javax.swing.JTextField();
        accountsLbl = new javax.swing.JLabel();
        accountsTablePane = new javax.swing.JScrollPane();
        accountsTable = new javax.swing.JTable();
        addAccountBtn = new javax.swing.JButton();
        deleteAccountBtn = new javax.swing.JButton();
        metadataLabel = new javax.swing.JLabel();
        metadataTablePane = new javax.swing.JScrollPane();
        metadataTable = new javax.swing.JTable();
        addMetadataBtn = new javax.swing.JButton();
        deleteMetadataBtn = new javax.swing.JButton();
        aliasesLabel = new javax.swing.JLabel();
        aliasesTablePane = new javax.swing.JScrollPane();
        aliasesTable = new javax.swing.JTable();
        addAliasBtn = new javax.swing.JButton();
        deleteAliasBtn = new javax.swing.JButton();
        casesLbl = new javax.swing.JLabel();
        casesTablePane = new javax.swing.JScrollPane();
        casesTable = new javax.swing.JTable();
        addCaseBtn = new javax.swing.JButton();
        deleteCaseBtn = new javax.swing.JButton();
        saveBtn = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(nameLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.nameLbl.text")); // NOI18N

        nameField.setEditable(false);
        nameField.setText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.nameField.text")); // NOI18N
        nameField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nameFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(accountsLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.accountsLbl.text")); // NOI18N

        accountsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        accountsTablePane.setViewportView(accountsTable);

        org.openide.awt.Mnemonics.setLocalizedText(addAccountBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addAccountBtn.text")); // NOI18N
        addAccountBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteAccountBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteAccountBtn.text")); // NOI18N
        deleteAccountBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(metadataLabel, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.metadataLabel.text")); // NOI18N

        metadataTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        metadataTablePane.setViewportView(metadataTable);

        org.openide.awt.Mnemonics.setLocalizedText(addMetadataBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addMetadataBtn.text")); // NOI18N
        addMetadataBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteMetadataBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteMetadataBtn.text")); // NOI18N
        deleteMetadataBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(aliasesLabel, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.aliasesLabel.text")); // NOI18N

        aliasesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        aliasesTablePane.setViewportView(aliasesTable);

        org.openide.awt.Mnemonics.setLocalizedText(addAliasBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addAliasBtn.text")); // NOI18N
        addAliasBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteAliasBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteAliasBtn.text")); // NOI18N
        deleteAliasBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(casesLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.casesLbl.text")); // NOI18N

        casesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        casesTablePane.setViewportView(casesTable);

        org.openide.awt.Mnemonics.setLocalizedText(addCaseBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addCaseBtn.text")); // NOI18N
        addCaseBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteCaseBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteCaseBtn.text")); // NOI18N
        deleteCaseBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(saveBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.saveBtn.text")); // NOI18N
        saveBtn.setToolTipText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.saveBtn.toolTipText")); // NOI18N
        saveBtn.setEnabled(false);

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(accountsTablePane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 469, Short.MAX_VALUE)
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addComponent(nameLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nameField))
                    .addComponent(accountsLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(metadataLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(metadataTablePane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(aliasesLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(aliasesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(casesLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, detailsPanelLayout.createSequentialGroup()
                        .addComponent(addCaseBtn)
                        .addGap(18, 18, 18)
                        .addComponent(deleteCaseBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(saveBtn))
                    .addComponent(casesTablePane)
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(detailsPanelLayout.createSequentialGroup()
                                .addComponent(addAccountBtn)
                                .addGap(18, 18, 18)
                                .addComponent(deleteAccountBtn))
                            .addGroup(detailsPanelLayout.createSequentialGroup()
                                .addComponent(addMetadataBtn)
                                .addGap(18, 18, 18)
                                .addComponent(deleteMetadataBtn))
                            .addGroup(detailsPanelLayout.createSequentialGroup()
                                .addComponent(addAliasBtn)
                                .addGap(18, 18, 18)
                                .addComponent(deleteAliasBtn)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        detailsPanelLayout.setVerticalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLbl)
                    .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(accountsLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountsTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAccountBtn)
                    .addComponent(deleteAccountBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(metadataLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(metadataTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addMetadataBtn)
                    .addComponent(deleteMetadataBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(aliasesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(aliasesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAliasBtn)
                    .addComponent(deleteAliasBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(casesLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(casesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(saveBtn)
                    .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(addCaseBtn)
                        .addComponent(deleteCaseBtn)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 481, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(detailsPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 583, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(detailsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nameFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nameFieldActionPerformed

    }//GEN-LAST:event_nameFieldActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel accountsLbl;
    private javax.swing.JTable accountsTable;
    private javax.swing.JScrollPane accountsTablePane;
    private javax.swing.JButton addAccountBtn;
    private javax.swing.JButton addAliasBtn;
    private javax.swing.JButton addCaseBtn;
    private javax.swing.JButton addMetadataBtn;
    private javax.swing.JLabel aliasesLabel;
    private javax.swing.JTable aliasesTable;
    private javax.swing.JScrollPane aliasesTablePane;
    private javax.swing.JLabel casesLbl;
    private javax.swing.JTable casesTable;
    private javax.swing.JScrollPane casesTablePane;
    private javax.swing.JButton deleteAccountBtn;
    private javax.swing.JButton deleteAliasBtn;
    private javax.swing.JButton deleteCaseBtn;
    private javax.swing.JButton deleteMetadataBtn;
    private javax.swing.JPanel detailsPanel;
    private javax.swing.JLabel metadataLabel;
    private javax.swing.JTable metadataTable;
    private javax.swing.JScrollPane metadataTablePane;
    private javax.swing.JTextField nameField;
    private javax.swing.JLabel nameLbl;
    private javax.swing.JButton saveBtn;
    // End of variables declaration//GEN-END:variables

    @Messages({
        "PersonaDetailsPanel_load_exception_Title=Initialization failure",
        "PersonaDetailsPanel_load_exception_msg=Failed to load persona",})
    private void loadPersona(Component parent, Persona persona) {
        String name;
        Collection<PersonaAccount> accounts;
        Collection<PersonaMetadata> metadata;
        Collection<PersonaAlias> aliases;
        Collection<CorrelationCase> cases;
        try {
            name = persona.getName();
            accounts = persona.getPersonaAccounts();
            metadata = persona.getMetadata();
            aliases = persona.getAliases();
            cases = persona.getCases();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Failed to load persona", ex);
            JOptionPane.showMessageDialog(parent,
                    Bundle.PersonaDetailsPanel_load_exception_Title(),
                    Bundle.PersonaDetailsPanel_load_exception_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.currentPersona = persona;
        this.currentName = name;
        this.currentAccounts = new ArrayList<>(accounts);
        this.currentMetadata = new ArrayList<>(metadata);
        this.currentAliases = new ArrayList<>(aliases);
        this.currentCases = new ArrayList<>(cases);
    }

    void clear() {
        currentPersona = null;
        nameField.setText("");
        nameField.setEditable(false);

        updateAccountsTable(Collections.EMPTY_LIST);

        saveBtn.setEnabled(false);
        addAccountBtn.setEnabled(false);
        addMetadataBtn.setEnabled(false);
        addAliasBtn.setEnabled(false);
        addCaseBtn.setEnabled(false);
        deleteAccountBtn.setEnabled(false);
        deleteMetadataBtn.setEnabled(false);
        deleteAliasBtn.setEnabled(false);
        deleteCaseBtn.setEnabled(false);
    }

    /**
     * Table model for persona details
     */
    final class PersonaDetailsTableModel extends DefaultTableModel {

        private static final long serialVersionUID = 1L;

        PersonaDetailsTableModel(Object[][] rows, String[] colNames) {
            super(rows, colNames);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return mode != PersonaDetailsMode.VIEW;
        }
    }

    private void updateAccountsTable(Collection<PersonaAccount> accounts) {
        Object[][] rows = new Object[accounts.size()][2];
        int i = 0;
        for (PersonaAccount account : accounts) {
            CentralRepoAccount acc = account.getAccount();
            rows[i] = new Object[]{
                acc.getAccountType().getAcctType().getDisplayName(),
                acc.getTypeSpecificId()
            };
            i++;
        }
        accountsModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Type", "ID"}
        );
        accountsTable.setModel(accountsModel);

        // Formatting
        accountsTable.getColumnModel().getColumn(0).setMaxWidth(100);
        accountsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    private void updateMetadataTable(Collection<PersonaMetadata> metadata) {
        Object[][] rows = new Object[metadata.size()][2];
        int i = 0;
        for (PersonaMetadata md : metadata) {
            rows[i] = new Object[]{md.getName(), md.getValue()};
            i++;
        }
        metadataModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Name", "Value"}
        );
        metadataTable.setModel(metadataModel);
    }

    private void updateAliasesTable(Collection<PersonaAlias> aliases) {
        Object[][] rows = new Object[aliases.size()][1];
        int i = 0;
        for (PersonaAlias alias : aliases) {
            rows[i] = new Object[]{alias.getAlias()};
            i++;
        }
        aliasesModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Alias"}
        );
        aliasesTable.setModel(aliasesModel);
    }

    private void updateCasesTable(Collection<CorrelationCase> cases) {
        Object[][] rows = new Object[cases.size()][1];
        int i = 0;
        for (CorrelationCase c : cases) {
            rows[i] = new Object[]{c.getDisplayName()};
            i++;
        }
        casesModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Case"}
        );
        casesTable.setModel(casesModel);
    }

    void enableEditUIComponents() {
        nameField.setEditable(true);
        addAccountBtn.setEnabled(true);
        addMetadataBtn.setEnabled(true);
        addAliasBtn.setEnabled(true);
        addCaseBtn.setEnabled(true);
    }

    void initializeFields() {
        nameField.setText(currentName);
        updateAccountsTable(currentAccounts);
        updateMetadataTable(currentMetadata);
        updateAliasesTable(currentAliases);
        updateCasesTable(currentCases);
    }

    void setMode(Component parent, PersonaDetailsMode mode, Persona persona) {
        clear();
        this.mode = mode;
        switch (mode) {
            case CREATE:
                currentName = Persona.getDefaultName();
                currentAccounts = new ArrayList<>();
                currentMetadata = new ArrayList<>();
                currentAliases = new ArrayList<>();
                currentCases = new ArrayList<>();
                enableEditUIComponents();
                break;
            case EDIT:
                loadPersona(parent, persona);
                enableEditUIComponents();
                break;
            case VIEW:
                loadPersona(parent, persona);
                break;
        }
        initializeFields();
    }
}
