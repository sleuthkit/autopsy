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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
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

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(PersonaDetailsPanel.class.getName());

    private PersonaDetailsMode mode;

    private Persona currentPersona;
    private String currentName = null;
    private List<CentralRepoAccount> currentAccounts = new ArrayList();
    private List<PersonaMetadata> currentMetadata = new ArrayList();
    private List<PersonaAlias> currentAliases = new ArrayList();
    private List<CorrelationCase> currentCases = new ArrayList();

    // Not-yet-created
    private List<PMetadata> metadataToAdd = new ArrayList();
    private List<PAlias> aliasesToAdd = new ArrayList();

    private PersonaDetailsTableModel accountsModel;
    private PersonaDetailsTableModel metadataModel;
    private PersonaDetailsTableModel aliasesModel;
    private PersonaDetailsTableModel casesModel;

    @Messages({
        "PersonaDetailsPanel_NameEdit=Edit Persona",
        "PersonaDetailsPanel_NameCreate=Create Persona",
        "PersonaDetailsPanel_NameView=View Persona",})
    public PersonaDetailsPanel() {
        initComponents();
        clear();

        // Accounts
        addAccountBtn.addActionListener((ActionEvent e) -> {
            AddAccountDialog dialog = new AddAccountDialog(this);
        });
        deleteAccountBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = accountsTable.getSelectedRow();
            if (selectedRow != -1) {
                currentAccounts.remove(selectedRow);
                updateAccountsTable();
            }
        });
        accountsTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, deleteAccountBtn, accountsTable);
        });

        // Metadata
        addMetadataBtn.addActionListener((ActionEvent e) -> {
            AddMetadataDialog dialog = new AddMetadataDialog(this);
        });
        deleteMetadataBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = metadataTable.getSelectedRow();
            if (selectedRow != -1) {
                // We're keeping metadata in two separate data structures
                if (selectedRow >= currentMetadata.size()) {
                    metadataToAdd.remove(selectedRow - currentMetadata.size());
                } else {
                    currentMetadata.remove(selectedRow);
                }
                updateMetadataTable();
            }
        });
        metadataTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, deleteMetadataBtn, metadataTable);
        });

        // Aliases
        addAliasBtn.addActionListener((ActionEvent e) -> {
            AddAliasDialog dialog = new AddAliasDialog(this);
        });
        deleteAliasBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = aliasesTable.getSelectedRow();
            if (selectedRow != -1) {
                // We're keeping aliases in two separate data structures
                if (selectedRow >= currentAliases.size()) {
                    aliasesToAdd.remove(selectedRow - currentAliases.size());
                } else {
                    currentAliases.remove(selectedRow);
                }
                updateAliasesTable();
            }
        });
        aliasesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, deleteAliasBtn, aliasesTable);
        });
    }

    private void handleSelectionChange(ListSelectionEvent e, JButton btn, JTable table) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        btn.setEnabled(mode != PersonaDetailsMode.VIEW && table.getSelectedRow() != -1);
    }

    boolean addAccount(CentralRepoAccount account) {
        if (currentAccounts.contains(account)) {
            return false;
        }
        currentAccounts.add(account);
        updateAccountsTable();
        return true;
    }

    /**
     * A data bucket class for yet-to-be-created PersonaMetadata
     */
    private class PMetadata {

        private final String name;
        private final String value;
        private final String justification;
        private final Persona.Confidence confidence;

        PMetadata(String name, String value, String justification, Persona.Confidence confidence) {
            this.name = name;
            this.value = value;
            this.justification = justification;
            this.confidence = confidence;
        }
    }
    
    boolean metadataExists(String name) {
        for (PersonaMetadata pm : currentMetadata) {
            if (pm.getName().equals(name)) {
                return true;
            }
        }
        for (PMetadata pma : metadataToAdd) {
            if (pma.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    boolean addMetadata(String name, String value, String justification, Persona.Confidence confidence) {
        if (!metadataExists(name)) {
            metadataToAdd.add(new PMetadata(name, value, justification, confidence));
            updateMetadataTable();
            return true;
        }
        return false;
    }

    /**
     * A data bucket class for yet-to-be-created PersonaAlias
     */
    private class PAlias {

        private final String alias;
        private final String justification;
        private final Persona.Confidence confidence;

        PAlias(String alias, String justification, Persona.Confidence confidence) {
            this.alias = alias;
            this.justification = justification;
            this.confidence = confidence;
        }
    }
    
    boolean aliasExists(String alias) {
        for (PersonaAlias pa : currentAliases) {
            if (pa.getAlias().equals(alias)) {
                return true;
            }
        }
        for (PAlias p : aliasesToAdd) {
            if (p.alias.equals(alias)) {
                return true;
            }
        }
        return false;
    }

    boolean addAlias(String alias, String justification, Persona.Confidence confidence) {
        if (!aliasExists(alias)) {
            aliasesToAdd.add(new PAlias(alias, justification, confidence));
            updateAliasesTable();
            return true;
        }
        return false;
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

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(accountsTablePane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 549, Short.MAX_VALUE)
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addComponent(nameLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nameField))
                    .addComponent(accountsLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(metadataLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(metadataTablePane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(aliasesLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(aliasesTablePane)
                    .addComponent(casesLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(casesTablePane)
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(detailsPanelLayout.createSequentialGroup()
                                .addComponent(addCaseBtn)
                                .addGap(18, 18, 18)
                                .addComponent(deleteCaseBtn))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountsLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountsTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAccountBtn)
                    .addComponent(deleteAccountBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(metadataLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(metadataTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(deleteMetadataBtn)
                    .addComponent(addMetadataBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(aliasesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(aliasesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(deleteAliasBtn)
                    .addComponent(addAliasBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(casesLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(casesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addCaseBtn)
                    .addComponent(deleteCaseBtn))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 561, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(detailsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 559, Short.MAX_VALUE)
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
    // End of variables declaration//GEN-END:variables

    @Messages({
        "PersonaDetailsPanel_load_exception_Title=Initialization failure",
        "PersonaDetailsPanel_load_exception_msg=Failed to load persona",})
    private void loadPersona(Component parent, Persona persona) {
        String name;
        Collection<CentralRepoAccount> accounts;
        Collection<PersonaMetadata> metadata;
        Collection<PersonaAlias> aliases;
        Collection<CorrelationCase> cases;
        try {
            name = persona.getName();
            accounts = persona.getPersonaAccounts().stream().map(PersonaAccount::getAccount)
                    .collect(Collectors.toList());
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
        this.currentAccounts.addAll(accounts);
        this.currentMetadata.addAll(metadata);
        this.currentAliases.addAll(aliases);
        this.currentCases.addAll(cases);
    }

    void clear() {
        currentPersona = null;
        currentName = Persona.getDefaultName();
        currentAccounts = new ArrayList<>();
        currentMetadata = new ArrayList<>();
        currentAliases = new ArrayList<>();
        currentCases = new ArrayList<>();
        nameField.setEditable(false);

        initializeFields();

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
            return false;
        }
    }

    private void updateAccountsTable() {
        Object[][] rows = new Object[currentAccounts.size()][2];
        int i = 0;
        for (CentralRepoAccount account : currentAccounts) {
            rows[i] = new Object[]{
                account.getAccountType().getAcctType().getDisplayName(),
                account.getTypeSpecificId()
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

    private void updateMetadataTable() {
        Object[][] rows = new Object[currentMetadata.size() + metadataToAdd.size()][2];
        int i = 0;
        for (PersonaMetadata md : currentMetadata) {
            rows[i] = new Object[]{md.getName(), md.getValue()};
            i++;
        }
        for (PMetadata md : metadataToAdd) {
            rows[i] = new Object[]{md.name, md.value};
            i++;
        }
        metadataModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Name", "Value"}
        );
        metadataTable.setModel(metadataModel);
    }

    private void updateAliasesTable() {
        Object[][] rows = new Object[currentAliases.size() + aliasesToAdd.size()][1];
        int i = 0;
        for (PersonaAlias alias : currentAliases) {
            rows[i] = new Object[]{alias.getAlias()};
            i++;
        }
        for (PAlias alias : aliasesToAdd) {
            rows[i] = new Object[]{alias.alias};
            i++;
        }
        aliasesModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Alias"}
        );
        aliasesTable.setModel(aliasesModel);
    }

    private void updateCasesTable() {
        Object[][] rows = new Object[currentCases.size()][1];
        int i = 0;
        for (CorrelationCase c : currentCases) {
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
        //addCaseBtn.setEnabled(true); //todo
    }

    void initializeFields() {
        nameField.setText(currentName);
        updateAccountsTable();
        updateMetadataTable();
        updateAliasesTable();
        updateCasesTable();
    }

    void setMode(Component parent, PersonaDetailsMode mode, Persona persona) {
        clear();
        this.mode = mode;
        switch (mode) {
            case CREATE:
                enableEditUIComponents();
                break;
            case EDIT:
                loadPersona(parent, persona);
                enableEditUIComponents();
                break;
            case VIEW:
                loadPersona(parent, persona);
                break;
            default:
                logger.log(Level.WARNING, "Unsupported mode: {0}", mode);
                break;
        }
        initializeFields();
    }
}
